package com.shopmanager.app.data.debts

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * BUG FIXED (debt-app web version): the header stats (عدد الديون / إجمالي المبلغ)
 * were computed with a ONE-TIME `getDocs()` per customer, run only inside the
 * "persons" real-time listener. Adding/editing/deleting a debt never
 * re-triggered that listener, so the totals silently went stale until you
 * added or edited a *customer*. Here, persons and debts are each their own
 * live listener, and the UI layer combines both flows - so totals recompute
 * the instant any debt changes, on any device.
 */
class DebtsRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.debtsDb

    fun listenPersons(): Flow<List<Person>> = callbackFlow {
        val registration: ListenerRegistration = db.collection("persons")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val persons = snapshot?.documents?.map { doc ->
                    Person(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        date = doc.getString("date") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                } ?: emptyList()
                trySend(persons)
            }
        awaitClose { registration.remove() }
    }

    /** Listens to the WHOLE debts collection so totals stay live everywhere. */
    fun listenAllDebts(): Flow<List<Debt>> = callbackFlow {
        val registration: ListenerRegistration = db.collection("debts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val debts = snapshot?.documents?.map { it.toDebt() } ?: emptyList()
                trySend(debts)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Filtered by personId only (no orderBy in the query itself) so this never
     * needs a Firestore composite index - the small per-customer debt list is
     * sorted client-side instead.
     */
    fun listenDebtsForPerson(personId: String): Flow<List<Debt>> = callbackFlow {
        val registration: ListenerRegistration = db.collection("debts")
            .whereEqualTo("personId", personId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val debts = snapshot?.documents
                    ?.map { it.toDebt() }
                    ?.sortedByDescending { it.date }
                    ?: emptyList()
                trySend(debts)
            }
        awaitClose { registration.remove() }
    }

    suspend fun personNameExists(name: String): Boolean {
        val snapshot = db.collection("persons").whereEqualTo("name", name).get().await()
        return !snapshot.isEmpty
    }

    suspend fun addPerson(name: String, amount: Double, date: String) {
        val data = mapOf(
            "name" to name,
            "amount" to amount,
            "date" to date,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("persons").add(data).await()
    }

    suspend fun updatePerson(id: String, name: String, amount: Double, date: String) {
        val data = mapOf("name" to name, "amount" to amount, "date" to date)
        db.collection("persons").document(id).update(data).await()
    }

    suspend fun deletePersonWithDebts(id: String) {
        val debts = db.collection("debts").whereEqualTo("personId", id).get().await()
        for (doc in debts.documents) {
            db.collection("debts").document(doc.id).delete().await()
        }
        db.collection("persons").document(id).delete().await()
    }

    suspend fun addDebt(personId: String, amount: Double, date: String) {
        val data = mapOf(
            "personId" to personId,
            "amount" to amount,
            "date" to date,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("debts").add(data).await()
    }

    suspend fun updateDebt(id: String, amount: Double, date: String) {
        val data = mapOf("amount" to amount, "date" to date)
        db.collection("debts").document(id).update(data).await()
    }

    suspend fun deleteDebt(id: String) {
        db.collection("debts").document(id).delete().await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toDebt() = Debt(
        id = id,
        personId = getString("personId") ?: "",
        amount = getDouble("amount") ?: 0.0,
        date = getString("date") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )
}
