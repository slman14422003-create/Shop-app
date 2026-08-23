package com.shopmanager.app.data.debts

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.shopmanager.app.data.FirebaseModule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val WRITE_TIMEOUT_MS = 15_000L

/**
 * BUG FIXED (debt-app web version): the header stats (عدد الديون / إجمالي المبلغ)
 * were computed with a ONE-TIME `getDocs()` per customer, run only inside the
 * "persons" real-time listener. Adding/editing/deleting a debt never
 * re-triggered that listener, so the totals silently went stale until you
 * added or edited a *customer*. Here, persons and debts are each their own
 * live listener, and the UI layer combines both flows - so totals recompute
 * the instant any debt changes, on any device.
 *
 * BUG FIXED (this app): every write used to wait on Firestore with no upper
 * bound. If a write is rejected by security rules (e.g. before the rules
 * from firebase-rules/ are published) or the network stalls, the call could
 * hang indefinitely and the "جارِ الحفظ..." button would spin forever with
 * no way out. Every write below now has a hard 15s timeout, after which it
 * fails loudly (caught by the ViewModel, which resets isSaving and shows an
 * error) instead of hanging silently.
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

    suspend fun personNameExists(name: String): Boolean = withTimeout(WRITE_TIMEOUT_MS) {
        val snapshot = db.collection("persons").whereEqualTo("name", name).get().await()
        !snapshot.isEmpty
    }

    /**
     * BUG FIXED: adding a new person used to only write the "persons" doc
     * with an `amount` field — it never created a matching row in the
     * "debts" collection. Since Person Detail's debt history is driven
     * entirely by the debts collection, that initial amount silently never
     * showed up there; the person had to open the new client and re-enter
     * the exact same amount as a debt for it to actually appear. Now both
     * documents are written together, atomically, in one batch.
     */
    suspend fun addPerson(name: String, amount: Double, date: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val personRef = db.collection("persons").document()
        val batch = db.batch()
        batch.set(
            personRef,
            mapOf(
                "name" to name,
                "amount" to amount,
                "date" to date,
                "createdAt" to System.currentTimeMillis()
            )
        )
        if (amount > 0) {
            val debtRef = db.collection("debts").document()
            batch.set(
                debtRef,
                mapOf(
                    "personId" to personRef.id,
                    "amount" to amount,
                    "date" to date,
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
        batch.commit().await()
        Unit
    }

    suspend fun updatePerson(id: String, name: String, amount: Double, date: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf("name" to name, "amount" to amount, "date" to date)
        db.collection("persons").document(id).update(data).await()
        Unit
    }

    suspend fun deletePersonWithDebts(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val debts = db.collection("debts").whereEqualTo("personId", id).get().await()
        for (doc in debts.documents) {
            db.collection("debts").document(doc.id).delete().await()
        }
        db.collection("persons").document(id).delete().await()
        Unit
    }

    suspend fun addDebt(personId: String, amount: Double, date: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf(
            "personId" to personId,
            "amount" to amount,
            "date" to date,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("debts").add(data).await()
        Unit
    }

    suspend fun updateDebt(id: String, amount: Double, date: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf("amount" to amount, "date" to date)
        db.collection("debts").document(id).update(data).await()
        Unit
    }

    suspend fun deleteDebt(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("debts").document(id).delete().await()
        Unit
    }

    /**
     * Marks a single debt as paid: removes the debt entry. The person's
     * displayed total is derived live from the sum of their remaining debts
     * (see DebtsViewModel), so no separate "update the person's amount"
     * write is needed here — one less place for the two to drift apart.
     */
    suspend fun markDebtAsPaid(debtId: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("debts").document(debtId).delete().await()
        Unit
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toDebt() = Debt(
        id = id,
        personId = getString("personId") ?: "",
        amount = getDouble("amount") ?: 0.0,
        date = getString("date") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )
}
