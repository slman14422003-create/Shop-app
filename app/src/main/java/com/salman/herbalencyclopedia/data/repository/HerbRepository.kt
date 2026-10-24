package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.tasks.await

/**
 * Talks to the same Firestore collections as the web app:
 * "herbs" and "categories".
 *
 * Sync model:
 * - [observeHerbs] / [observeCategories] keep a *live* Firestore listener open
 *   (`addSnapshotListener`) so any change made here, from another device, or
 *   from the web app's admin panel is pushed to this app in real time -
 *   including the app's own local writes, which are reflected instantly from
 *   Firestore's local cache before the server round-trip even completes
 *   (optimistic UI), then reconciled automatically when the server confirms.
 * - [fetchHerbs] / [fetchCategories] remain available as one-shot reads
 *   (used for the manual "retry/refresh" action and for the admin tools that
 *   need a definite snapshot, like [restoreBackup] verification).
 * - Offline persistence is enabled with a *bounded* cache size (see
 *   [PERSISTENT_CACHE_BYTES]) so the full catalog (including base64 image
 *   data) still stays available offline and all local writes made offline
 *   still queue up and sync automatically once the connection returns, but
 *   Firestore's own garbage collector now trims the least-recently-used
 *   documents once the cache grows past the cap instead of retaining every
 *   historical snapshot forever. An unlimited cache was the main reason the
 *   app's on-disk footprint kept climbing well past the actual catalog size
 *   the longer the app stayed installed and synced. This setting is applied
 *   once in [com.salman.herbalencyclopedia.HerbalApp.onCreate] - *not* here -
 *   because Firestore rejects any settings change after the shared
 *   FirebaseFirestore instance has already been touched by anything else
 *   (e.g. SemoLearningRepository, which uses the same instance with no
 *   custom settings); doing it in Application.onCreate guarantees it always
 *   runs first, regardless of which repository happens to be used first.
 * - [observeCollection] auto-retries with exponential backoff on listener
 *   errors instead of permanently ending the live sync (see its retryWhen).
 */
class HerbRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ---------------------------------------------------------------------
    // Live (real-time) sync
    // ---------------------------------------------------------------------

    /** Emits the full herb list every time Firestore's data changes, locally or on the server. */
    fun observeHerbs(): Flow<List<Herb>> = observeCollection("herbs", Herb::class.java)

    /** Emits the full category list every time Firestore's data changes, locally or on the server. */
    fun observeCategories(): Flow<List<Category>> = observeCollection("categories", Category::class.java)

    /** Emits the full blends ("الخلطات") list every time Firestore's data changes, locally or on the server. */
    fun observeBlends(): Flow<List<Blend>> = observeCollection("blends", Blend::class.java)

    private fun <T> observeCollection(collection: String, clazz: Class<T>): Flow<List<T>> {
        var failures = 0
        return callbackFlow {
            val emitted = AtomicBoolean(false)
            val registration = db.collection(collection)
                .addSnapshotListener(Dispatchers.Default.asExecutor(), MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    if (snapshot.metadata.isFromCache && snapshot.isEmpty) return@addSnapshotListener
                    if (emitted.get() && snapshot.documentChanges.isEmpty()) return@addSnapshotListener
                    emitted.set(true)
                    trySend(snapshot.toObjects(clazz))
                }
            awaitClose { registration.remove() }
        }.onEach { failures = 0 }.retryWhen { cause, _ ->
            if (!isTransientListenerError(cause)) return@retryWhen false
            val delayMs = (1000L shl failures.coerceAtMost(5)).coerceAtMost(30_000L)
            failures++
            delay(delayMs)
            true
        }
    }

    suspend fun fetchHerbsPaged(
        fromServer: Boolean = false,
        pageSize: Int = 40,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Herb> {
        val source = if (fromServer) Source.SERVER else Source.DEFAULT
        val total = runCatching {
            db.collection("herbs").count().get(AggregateSource.SERVER).await().count.toInt()
        }.getOrDefault(0)
        val result = ArrayList<Herb>()
        var last: DocumentSnapshot? = null
        while (true) {
            var query = db.collection("herbs").orderBy(FieldPath.documentId()).limit(pageSize.toLong())
            val cursor = last
            if (cursor != null) query = query.startAfter(cursor)
            val snapshot = query.get(source).await()
            if (snapshot.isEmpty) break
            result += withContext(Dispatchers.Default) { snapshot.toObjects(Herb::class.java) }
            last = snapshot.documents.last()
            onProgress(result.size, total)
            if (snapshot.size() < pageSize) break
        }
        return result
    }

    suspend fun fetchCatalogVersion(): Long? =
        db.collection("meta").document("catalog").get(Source.SERVER).await().getLong("version")

    suspend fun ensureVersionDoc() {
        val ref = db.collection("meta").document("catalog")
        val snapshot = ref.get(Source.SERVER).await()
        if (!snapshot.exists()) {
            ref.set(hashMapOf<String, Any>("version" to 1L, "updated_at" to FieldValue.serverTimestamp())).await()
        }
    }

    private suspend fun bumpVersion() {
        runCatching {
            db.collection("meta").document("catalog")
                .set(
                    hashMapOf<String, Any>(
                        "version" to FieldValue.increment(1),
                        "updated_at" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    suspend fun setImageUrl(collection: String, id: String, url: String) {
        db.collection(collection).document(id)
            .update(hashMapOf<String, Any>("image_url" to url, "updated_at" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun bumpCatalogVersion() = bumpVersion()

    private suspend fun deleteCollection(name: String) {
        val docs = db.collection(name).get(Source.SERVER).await().documents
        docs.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ---------------------------------------------------------------------
    // One-shot reads
    // ---------------------------------------------------------------------

    suspend fun fetchHerbs(fromServer: Boolean = false): List<Herb> {
        val snapshot = db.collection("herbs").get(if (fromServer) Source.SERVER else Source.DEFAULT).await()
        return withContext(Dispatchers.Default) { snapshot.toObjects(Herb::class.java) }
    }

    suspend fun fetchCategories(fromServer: Boolean = false): List<Category> {
        val snapshot = db.collection("categories").get(if (fromServer) Source.SERVER else Source.DEFAULT).await()
        return withContext(Dispatchers.Default) { snapshot.toObjects(Category::class.java) }
    }

    suspend fun fetchBlends(fromServer: Boolean = false): List<Blend> {
        val snapshot = db.collection("blends").get(if (fromServer) Source.SERVER else Source.DEFAULT).await()
        return withContext(Dispatchers.Default) { snapshot.toObjects(Blend::class.java) }
    }

    // ---------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------

    suspend fun addHerb(herb: Herb) {
        val data: HashMap<String, Any?> = hashMapOf(
            "name" to herb.name.trim(),
            "category_id" to herb.categoryId,
            "benefits" to herb.benefits.trim().ifBlank { "—" },
            "warnings" to herb.warnings.trim().ifBlank { "—" },
            "harms" to herb.harms.trim().ifBlank { "—" },
            "usage" to herb.usage.trim().ifBlank { "—" },
            "notes" to herb.notes.trim().ifBlank { "—" },
            "image_url" to herb.imageUrl,
            "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("herbs").add(data).await()
        bumpVersion()
    }

    suspend fun updateHerb(herb: Herb) {
        val data: HashMap<String, Any?> = hashMapOf(
            "name" to herb.name.trim(),
            "category_id" to herb.categoryId,
            "benefits" to herb.benefits.trim().ifBlank { "—" },
            "warnings" to herb.warnings.trim().ifBlank { "—" },
            "harms" to herb.harms.trim().ifBlank { "—" },
            "usage" to herb.usage.trim().ifBlank { "—" },
            "notes" to herb.notes.trim().ifBlank { "—" },
            "image_url" to herb.imageUrl,
            "updated_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("herbs").document(herb.id).update(data).await()
        bumpVersion()
    }

    suspend fun deleteHerb(id: String) {
        db.collection("herbs").document(id).delete().await()
        bumpVersion()
    }

    // ---------------------------------------------------------------------
    // Blends ("الخلطات") — same admin-only write model as herbs above.
    // ---------------------------------------------------------------------

    suspend fun addBlend(blend: Blend) {
        val data: HashMap<String, Any?> = hashMapOf(
            "name" to blend.name.trim(),
            "herb_ids" to blend.herbIds,
            "benefits" to blend.benefits.trim().ifBlank { "—" },
            "usage" to blend.usage.trim().ifBlank { "—" },
            "warnings" to blend.warnings.trim().ifBlank { "—" },
            "notes" to blend.notes.trim().ifBlank { "—" },
            "image_url" to blend.imageUrl,
            "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("blends").add(data).await()
        bumpVersion()
    }

    suspend fun updateBlend(blend: Blend) {
        val data: HashMap<String, Any?> = hashMapOf(
            "name" to blend.name.trim(),
            "herb_ids" to blend.herbIds,
            "benefits" to blend.benefits.trim().ifBlank { "—" },
            "usage" to blend.usage.trim().ifBlank { "—" },
            "warnings" to blend.warnings.trim().ifBlank { "—" },
            "notes" to blend.notes.trim().ifBlank { "—" },
            "image_url" to blend.imageUrl,
            "updated_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("blends").document(blend.id).update(data).await()
        bumpVersion()
    }

    suspend fun deleteBlend(id: String) {
        db.collection("blends").document(id).delete().await()
        bumpVersion()
    }

    suspend fun addCategory(name: String) {
        db.collection("categories").add(
            hashMapOf(
                "name" to name.trim(),
                "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).await()
        bumpVersion()
    }

    suspend fun updateCategory(id: String, name: String) {
        db.collection("categories").document(id).update("name", name.trim()).await()
        bumpVersion()
    }

    suspend fun deleteCategory(id: String) {
        db.collection("categories").document(id).delete().await()
        bumpVersion()
    }

    suspend fun deleteAllHerbs() {
        deleteCollection("herbs")
        bumpVersion()
    }

    suspend fun deleteAllData() {
        deleteCollection("herbs")
        deleteCollection("categories")
        deleteCollection("blends")
        bumpVersion()
    }

    /** Forces a real round-trip to the server (bypassing the local cache) to verify connectivity. */
    suspend fun testConnection(): Boolean {
        db.collection("categories").limit(1).get(Source.SERVER).await()
        return true
    }

    suspend fun restoreBackup(json: String) {
        val operations = withContext(Dispatchers.Default) { parseBackup(json) }
        operations.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
            batch.commit().await()
        }
        bumpVersion()
    }

    private fun parseBackup(json: String): List<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>> {
        val root = org.json.JSONObject(json)
        val operations = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()
        val categories = root.optJSONArray("categories") ?: org.json.JSONArray()
        for (i in 0 until categories.length()) {
            val o = categories.getJSONObject(i)
            val id = o.optString("id")
            if (id.isNotBlank()) {
                val data = hashMapOf<String, Any?>(
                    "name" to o.optString("name"),
                    "icon" to o.optString("icon").ifBlank { null }
                )
                operations += db.collection("categories").document(id) to data
            }
        }
        val herbs = root.optJSONArray("herbs") ?: org.json.JSONArray()
        for (i in 0 until herbs.length()) {
            val o = herbs.getJSONObject(i)
            val id = o.optString("id")
            if (id.isNotBlank()) {
                val data = hashMapOf<String, Any?>(
                    "name" to o.optString("name"),
                    "category_id" to o.optString("categoryId").ifBlank { null },
                    "benefits" to o.optString("benefits"),
                    "warnings" to o.optString("warnings"),
                    "harms" to o.optString("harms"),
                    "usage" to o.optString("usage"),
                    "notes" to o.optString("notes"),
                    "image_url" to o.optString("imageUrl").ifBlank { null }
                )
                operations += db.collection("herbs").document(id) to data
            }
        }
        val blends = root.optJSONArray("blends") ?: org.json.JSONArray()
        for (i in 0 until blends.length()) {
            val o = blends.getJSONObject(i)
            val id = o.optString("id")
            if (id.isNotBlank()) {
                val herbIds = ArrayList<String>()
                val ids = o.optJSONArray("herbIds")
                if (ids != null) for (j in 0 until ids.length()) herbIds += ids.optString(j)
                val data = hashMapOf<String, Any?>(
                    "name" to o.optString("name"),
                    "herb_ids" to herbIds,
                    "benefits" to o.optString("benefits"),
                    "usage" to o.optString("usage"),
                    "warnings" to o.optString("warnings"),
                    "notes" to o.optString("notes"),
                    "image_url" to o.optString("imageUrl").ifBlank { null }
                )
                operations += db.collection("blends").document(id) to data
            }
        }
        return operations
    }

    companion object {
        /**
         * سقف كاش Firestore المحلي (٤٠ ميجابايت) - القيمة الفعلية تُطبَّق من
         * [com.salman.herbalencyclopedia.HerbalApp.onCreate]، راجع تعليقها
         * هناك لسبب نقل نقطة التطبيق الفعلية إلى مستوى Application بدل
         * init{} هذه الفئة. كانت القيمة السابقة CACHE_SIZE_UNLIMITED تعني
         * عدم وجود أي سقف إطلاقاً، فيستمر الكاش بالتضخم بلا حدود مع كل
         * مزامنة أو تحديث حتى لصور/مستندات لم تعد تُعرض فعلياً — وهذا هو
         * السبب الرئيسي لكون حجم التطبيق بعد التثبيت والاستخدام أكبر بكثير
         * من حجم بيانات الموسوعة الفعلي. بسقف محدود، Firestore يشغّل تنظيفاً
         * تلقائياً (LRU garbage collection) يحذف أقدم المستندات غير
         * المستخدمة عند تجاوز هذا الحد، مع إبقاء العمل بلا إنترنت يعمل بشكل
         * طبيعي تماماً (الموسوعة كاملة أصغر من هذا السقف بمراحل، فتبقى كل
         * البيانات الفعلية محفوظة محلياً دوماً).
         */
        const val PERSISTENT_CACHE_BYTES: Long = 40L * 1024 * 1024

        /**
         * صحيح إذا كان الاستثناء غالباً مؤقتاً بسبب ضعف/انقطاع الاتصال
         * (فيستحق إعادة محاولة تلقائية داخلية - راجع
         * [com.salman.herbalencyclopedia.ui.AppViewModel.syncCatalogFromServer])
         * لا أخطاء دائمة كرفض الصلاحيات أو عدم تسجيل الدخول، التي لن تتغير
         * بإعادة المحاولة.
         */
        fun isTransientError(e: Throwable): Boolean {
            val code = (e as? FirebaseFirestoreException)?.code
            return code == null ||
                code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                code == FirebaseFirestoreException.Code.ABORTED ||
                code == FirebaseFirestoreException.Code.CANCELLED ||
                code == FirebaseFirestoreException.Code.UNKNOWN
        }

        /** Turns a Firestore/network exception into a short, user-facing Arabic message. */
        fun isTransientListenerError(e: Throwable): Boolean {
            val code = (e as? FirebaseFirestoreException)?.code
            if (code != null) {
                return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                    code == FirebaseFirestoreException.Code.ABORTED ||
                    code == FirebaseFirestoreException.Code.CANCELLED ||
                    code == FirebaseFirestoreException.Code.UNKNOWN
            }
            return e is IOException || e is TimeoutException
        }

        fun describeError(e: Throwable): String {
            val code = (e as? FirebaseFirestoreException)?.code
            return when (code) {
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "لا يوجد اتصال بالإنترنت حالياً. سيتم عرض آخر بيانات محفوظة والمزامنة تلقائياً عند عودة الاتصال."
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "ليست لديك صلاحية الوصول إلى هذه البيانات."
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "يجب تسجيل الدخول أولاً."
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "انتهت مهلة الاتصال بالخادم. حاول مرة أخرى."
                else -> e.localizedMessage ?: "حدث خطأ غير متوقع أثناء المزامنة."
            }
        }
    }
}
