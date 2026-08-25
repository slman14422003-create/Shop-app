package com.shopmanager.app.data.materials

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val WRITE_TIMEOUT_MS = 15_000L

/**
 * BUG FIXED (material-manager web version): every read/write in that app's
 * `materials.js` and `prices.js` called three globals - `isFirebaseReady()`,
 * `getDB()`, and `COLLECTION` / `PRICES_COLLECTION` - that were never defined
 * anywhere in the project. Every Firestore call threw before it could run,
 * so the app silently fell back to local-only storage. This repository talks
 * to Firestore directly, and every write below has a hard timeout so it can
 * never hang the UI indefinitely (e.g. before Firestore rules are published).
 */
class MaterialsRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.materialsDb
    private val materialsCollection = "spices_final_v12"
    private val pricesCollection = "material_prices"
    private val catalogCollection = "materials_catalog"

    fun listenMaterials(section: String): Flow<List<Material>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(materialsCollection)
            .whereEqualTo("section", section)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.map { doc ->
                        Material(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            quantity = doc.getDouble("quantity") ?: 0.0,
                            unit = doc.getString("unit") ?: MaterialUnit.KG.label,
                            section = doc.getString("section") ?: "main",
                            updatedAt = doc.getLong("timestamp") ?: 0L
                        )
                    }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /** price is keyed by material name, matching the original web schema. */
    fun listenPrices(): Flow<Map<String, Double>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(pricesCollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val prices = snapshot?.documents
                    ?.associate { it.id to (it.getDouble("price") ?: 0.0) }
                    ?: emptyMap()
                trySend(prices)
            }
        awaitClose { registration.remove() }
    }

    fun listenCatalog(): Flow<List<MaterialCatalogItem>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(catalogCollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.map { MaterialCatalogItem(id = it.id, name = it.getString("name") ?: "") }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addMaterial(name: String, quantity: Double, unit: String, section: String) =
        withTimeout(WRITE_TIMEOUT_MS) {
            val data = mapOf(
                "name" to name,
                "quantity" to quantity,
                "unit" to unit,
                "section" to section,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(materialsCollection).add(data).await()
            Unit
        }

    suspend fun updateMaterial(id: String, name: String, quantity: Double, unit: String, section: String) =
        withTimeout(WRITE_TIMEOUT_MS) {
            val data = mapOf(
                "name" to name,
                "quantity" to quantity,
                "unit" to unit,
                "section" to section,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(materialsCollection).document(id).update(data).await()
            Unit
        }

    suspend fun deleteMaterial(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(materialsCollection).document(id).delete().await()
        Unit
    }

    suspend fun setPrice(materialName: String, price: Double) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(pricesCollection).document(materialName).set(mapOf("price" to price)).await()
        Unit
    }

    suspend fun catalogNameExists(name: String): Boolean = withTimeout(WRITE_TIMEOUT_MS) {
        val snapshot = db.collection(catalogCollection).whereEqualTo("name", name).get().await()
        !snapshot.isEmpty
    }

    suspend fun addCatalogItem(name: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(catalogCollection).add(mapOf("name" to name)).await()
        Unit
    }

    suspend fun deleteCatalogItem(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(catalogCollection).document(id).delete().await()
        Unit
    }

    /** One-off, server-sourced re-fetch used by pull-to-refresh — see
     * [com.shopmanager.app.data.debts.DebtsRepository.refreshFromServer]. */
    suspend fun refreshFromServer() = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(materialsCollection).get(com.google.firebase.firestore.Source.SERVER).await()
        db.collection(pricesCollection).get(com.google.firebase.firestore.Source.SERVER).await()
        Unit
    }

    /**
     * One-off, all-at-once read of materials + prices + catalog — used
     * only for the daily local backup snapshot (see BackupManager); the
     * rest of the app always uses the live listeners above.
     */
    suspend fun fetchAllForBackup(): Triple<List<Material>, Map<String, Double>, List<MaterialCatalogItem>> =
        withTimeout(WRITE_TIMEOUT_MS) {
            val materialsSnap = db.collection(materialsCollection).get().await()
            val pricesSnap = db.collection(pricesCollection).get().await()
            val catalogSnap = db.collection(catalogCollection).get().await()

            val materials = materialsSnap.documents.map { doc ->
                Material(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    quantity = doc.getDouble("quantity") ?: 0.0,
                    unit = doc.getString("unit") ?: MaterialUnit.KG.label,
                    section = doc.getString("section") ?: "main",
                    updatedAt = doc.getLong("timestamp") ?: 0L
                )
            }
            val prices = pricesSnap.documents.associate { it.id to (it.getDouble("price") ?: 0.0) }
            val catalog = catalogSnap.documents.map { MaterialCatalogItem(id = it.id, name = it.getString("name") ?: "") }
            Triple(materials, prices, catalog)
        }

    /**
     * Restores a full local backup snapshot back into Firestore, wiping
     * every existing document in the three collections first and
     * rewriting the backed-up ones with their original ids. Batched in
     * chunks of 400 to stay under Firestore's 500-operation batch limit.
     * Only ever called after explicit confirmation — see
     * [com.shopmanager.app.data.debts.DebtsRepository.restoreFromBackup].
     */
    suspend fun restoreFromBackup(
        materials: List<Material>,
        prices: Map<String, Double>,
        catalog: List<MaterialCatalogItem>
    ) = withTimeout(60_000L) {
        val existingMaterials = db.collection(materialsCollection).get().await()
        val existingPrices = db.collection(pricesCollection).get().await()
        val existingCatalog = db.collection(catalogCollection).get().await()

        val deletes = existingMaterials.documents.map { db.collection(materialsCollection).document(it.id) } +
            existingPrices.documents.map { db.collection(pricesCollection).document(it.id) } +
            existingCatalog.documents.map { db.collection(catalogCollection).document(it.id) }
        deletes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }

        val materialWrites = materials.filter { it.id.isNotBlank() }.map {
            db.collection(materialsCollection).document(it.id) to mapOf(
                "name" to it.name, "quantity" to it.quantity, "unit" to it.unit,
                "section" to it.section, "timestamp" to it.updatedAt
            )
        }
        val priceWrites = prices.map { (name, price) ->
            db.collection(pricesCollection).document(name) to mapOf("price" to price)
        }
        val catalogWrites = catalog.filter { it.id.isNotBlank() }.map {
            db.collection(catalogCollection).document(it.id) to mapOf("name" to it.name)
        }
        (materialWrites + priceWrites + catalogWrites).chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        Unit
    }
}
