package com.shopmanager.app.data.materials

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * BUG FIXED (material-manager web version): every read/write in that app's
 * `materials.js` and `prices.js` called three globals - `isFirebaseReady()`,
 * `getDB()`, and `COLLECTION` / `PRICES_COLLECTION` - that were never defined
 * anywhere in the project (confirmed: zero matches across the whole repo).
 * Every single Firestore call threw a ReferenceError before it could run, so
 * the app silently fell back to local-only storage on every load - it never
 * actually synced with Firebase at all, on any device. This native
 * repository talks to Firestore directly, so that failure mode doesn't exist
 * here.
 */
class MaterialsRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.materialsDb
    private val materialsCollection = "spices_final_v12"
    private val pricesCollection = "material_prices"

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
                            unit = doc.getString("unit") ?: "كغ",
                            section = doc.getString("section") ?: "main",
                            minQuantity = doc.getDouble("minQuantity") ?: 0.0
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

    suspend fun addMaterial(name: String, quantity: Double, unit: String, section: String, minQuantity: Double) {
        val data = mapOf(
            "name" to name,
            "quantity" to quantity,
            "unit" to unit,
            "section" to section,
            "minQuantity" to minQuantity,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection(materialsCollection).add(data).await()
    }

    suspend fun updateMaterial(id: String, name: String, quantity: Double, unit: String, section: String, minQuantity: Double) {
        val data = mapOf(
            "name" to name,
            "quantity" to quantity,
            "unit" to unit,
            "section" to section,
            "minQuantity" to minQuantity,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection(materialsCollection).document(id).update(data).await()
    }

    suspend fun deleteMaterial(id: String) {
        db.collection(materialsCollection).document(id).delete().await()
    }

    suspend fun setPrice(materialName: String, price: Double) {
        db.collection(pricesCollection).document(materialName)
            .set(mapOf("price" to price)).await()
    }
}
