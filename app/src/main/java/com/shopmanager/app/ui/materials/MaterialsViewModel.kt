package com.shopmanager.app.ui.materials

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.notifications.RemoteChangeProcessor
import com.shopmanager.app.data.notifications.SelfChangeLedger
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.sync.SyncStatusStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MaterialsUiState(
    val materials: List<Material> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true
)

class MaterialsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MaterialsRepository()
    private val settings = SettingsRepository(application)

    private val section = MutableStateFlow("main")

    // PERF: same fix as DebtsViewModel — Firestore's listener can re-fire
    // with a list that's identical to the last one (e.g. a local write
    // getting server-acknowledged). distinctUntilChanged stops that from
    // recomputing/recomposing the whole screen for no visible change.
    // See DebtsViewModel.hasSyncError — same idea: a real listener error
    // (not just a legitimately empty collection) flips this, so Settings
    // can offer restoring the last local daily backup.
    private val _hasSyncError = MutableStateFlow(false)
    val hasSyncError: StateFlow<Boolean> = _hasSyncError

    // BUG FIXED (النسخ الاحتياطي التلقائي لا يعمل إلا على الجهاز الذي أضاف
    // المادة): InstantBackupWorker used to be requested only from this
    // ViewModel's own add/edit/delete functions further below — i.e. only
    // on the device where the person physically made the change. Every
    // other device signed into the same shop only ever learns about that
    // change through this same Firestore listener (materialsFlow/
    // pricesFlow), so it never took its own local snapshot until *its
    // own* user happened to add something. materialsFlow/pricesFlow below
    // now request a local instant backup on every real update they see —
    // regardless of which device produced it — so every signed-in device
    // stays backed up together instead of only the one that made the
    // edit. That onEach also resets [_hasSyncError] back to false: it used
    // to only ever be set true (on a listener error) and never cleared, so
    // a single network blip left Settings offering to restore from the
    // local backup forever afterward, even long after the connection and
    // live data had fully recovered.

    private val materialsFlow = channelFlow {
        section.collect { s ->
            repo.listenMaterials(s).collect { send(it) }
        }
    }.catch { _hasSyncError.value = true; emit(emptyList()) }
        .distinctUntilChanged()
        .onEach {
            _hasSyncError.value = false
            // PERF: طلب مؤجَّل ومُخفَّف (لا كتابة في قاعدة WorkManager مع كل لقطة).
            InstantBackupWorker.requestDeferred(getApplication())
            // "طبقة مساعدة للمزامنة" — see SyncStatus.kt.
            SyncStatusStore.recordSuccess(getApplication())
        }

    private val pricesFlow = repo.listenPrices()
        .catch { _hasSyncError.value = true; emit(emptyMap()) }
        .distinctUntilChanged()
        .onEach {
            _hasSyncError.value = false
            // PERF: طلب مؤجَّل ومُخفَّف (لا كتابة في قاعدة WorkManager مع كل لقطة).
            InstantBackupWorker.requestDeferred(getApplication())
            SyncStatusStore.recordSuccess(getApplication())
        }

    // Same fix as DebtsViewModel.uiState: debounce the cache→server settle
    // burst on cold start so the dashboard's "قائمة النواقص" counter counts
    // up once to the real total instead of jittering through partial ones.
    val uiState: StateFlow<MaterialsUiState> = combine(materialsFlow, pricesFlow) { materials, prices ->
        MaterialsUiState(materials = materials, prices = prices, isLoading = false)
    }.debounce(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaterialsUiState())

    val catalog: StateFlow<List<MaterialCatalogItem>> = repo.listenCatalog()
        .catch { emit(emptyList()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull-to-refresh: forces a real server round trip and keeps the
     * spinner up for a minimum, tactile duration either way. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val start = System.currentTimeMillis()
            try {
                repo.refreshFromServer()
            } catch (_: Exception) {
                // Live listeners remain the source of truth.
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < 400) kotlinx.coroutines.delay(400 - elapsed)
            _isRefreshing.value = false
        }
    }

    /**
     * "التفريق بين هاتف وهاتف آخر" (المواد): اكتشاف تغيّر قائمة النواقص القادم
     * من الأجهزة الأخرى صار في مكان واحد (RemoteChangeWatcher +
     * RemoteChangeProcessor) يراقب **كل الأقسام** ويعمل حتى والتطبيق مغلق.
     *
     * الخطأ القديم هنا وأُصلح: كان هذا الـ ViewModel يحتفظ بمجموعة ids
     * "لمستُها أنا" في الذاكرة، ولا يحذف الـ id منها إلا إذا حدث تغيير فعلي.
     * فلو ضغطتَ "حفظ" على تعديل دون تغيير حقيقي بقي الـ id مُعلَّماً، ثم إذا
     * عدّل هاتف آخر نفس المادة لاحقاً اعتُبر تعديله "منّي" ولم يصلك إشعار.
     * الآن نسجّل في [SelfChangeLedger] **بصمة الحالة التي كتبها هذا الجهاز**
     * (اسم|كمية|وحدة)، ويُعتبر التغيير منّي فقط إذا طابقت الحالة الحالية هذه
     * البصمة؛ أي تعديل من هاتف آخر يعطي بصمة مختلفة فيُعامَل كتغيير خارجي.
     */
    private fun markWrittenHere(id: String, name: String, quantity: Double, unit: String) {
        SelfChangeLedger.markMaterialWritten(
            getApplication(), id, RemoteChangeProcessor.materialSignature(name, quantity, unit)
        )
    }

    private fun markDeletedHere(ids: Collection<String>) {
        SelfChangeLedger.markMaterialsDeleted(getApplication(), ids)
    }

    // BUG FIXED (لا يوجد مؤشر تحميل بواجهة إضافة/تعديل مادة): مثل نفس
    // مشكلة PersonEditDialog قديماً - onDone هون تسمح للواجهة (MaterialEditDialog/
    // QuantityEntryDialog) توقف زر الحفظ وتعرض دائرة تحميل لحد ما الكتابة
    // بفايرستور تخلص فعلياً، بدل ما توهم إنها علقت أو تسمح بضغطات متكررة.
    fun addMaterial(name: String, quantity: Double, unit: String, notes: String = "", onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                // BUG FIXED (see MaterialsRepository.addMaterial): register
                // the id via onIdAssigned the instant it's generated
                // (client-side, before the write is even sent) instead of
                // after the whole suspend call returns — closes the race
                // where the live listener could fire first and notify this
                // same device about the material it just added.
                repo.addMaterial(name, quantity, unit, section.value, notes) { id -> markWrittenHere(id, name, quantity, unit) }
                _message.value = "تمت إضافة النقص بنجاح"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun updateMaterial(id: String, name: String, quantity: Double, unit: String, notes: String = "", onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                markWrittenHere(id, name, quantity, unit)
                repo.updateMaterial(id, name, quantity, unit, section.value, notes)
                _message.value = "تم تعديل النقص بنجاح"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun deleteMaterial(id: String) {
        viewModelScope.launch {
            try {
                markDeletedHere(listOf(id))
                repo.deleteMaterial(id)
                _message.value = "تم حذف المادة بنجاح"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "خطأ أثناء الحذف: ${e.message}"
            }
        }
    }

    /**
     * FEATURE ADDED ("نجمة الأهمية"): toggling on pins the material to the
     * top of the list by giving it an `order` one less than the current
     * minimum among today's materials (so it sorts before literally
     * everything else); toggling off just flips the flag back and leaves
     * its position as-is.
     */
    fun setImportant(material: Material, important: Boolean) {
        viewModelScope.launch {
            try {
                val topOrder = if (important) {
                    (uiState.value.materials.minOfOrNull { it.order } ?: 0L) - 1
                } else null
                repo.setImportant(material.id, important, topOrder)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر التحديث: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /**
     * FEATURE ADDED ("ترتيب المواد بالضغط المطول"): called once a drag
     * finishes with the full list in its new order; persists a fresh
     * sequential `order` per id in one batch (see
     * MaterialsRepository.updateMaterialsOrder).
     */
    fun reorderMaterials(orderedIds: List<String>) {
        viewModelScope.launch {
            try {
                repo.updateMaterialsOrder(orderedIds)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حفظ الترتيب: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /**
     * "مسح الكل" (clear all): deletes every material currently on the list
     * for the active section in one batched call, same effect as
     * select-all-then-delete but without needing a whole multi-select mode
     * on the list UI.
     */
    fun deleteAllMaterials() {
        val ids = uiState.value.materials.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                markDeletedHere(ids)
                repo.deleteMaterials(ids)
                _message.value = "تم حذف كل المواد (${ids.size})"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حذف المواد: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun setPrice(materialName: String, price: Double) {
        viewModelScope.launch {
            try {
                repo.setPrice(materialName, price)
                _message.value = "تم حفظ السعر"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر حفظ السعر: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun addCatalogItem(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (repo.catalogNameExists(name)) {
                    _message.value = "\"$name\" موجودة بالقائمة مسبقاً"
                    onDone(false)
                    return@launch
                }
                repo.addCatalogItem(name)
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    /**
     * FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): renames a fixed
     * catalog entry. Blocks renaming to a name that already exists
     * elsewhere in the catalog, same duplicate check as adding a new one -
     * but allows saving with no change (renaming "X" to "X" again).
     */
    fun updateCatalogItem(id: String, currentName: String, newName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (newName != currentName && repo.catalogNameExists(newName)) {
                    _message.value = "\"$newName\" موجودة بالقائمة مسبقاً"
                    onDone(false)
                    return@launch
                }
                repo.updateCatalogItem(id, newName)
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun deleteCatalogItem(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteCatalogItem(id)
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر الحذف: ${e.message}"
            }
        }
    }
}
