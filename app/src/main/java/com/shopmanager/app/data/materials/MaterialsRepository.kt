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
}
