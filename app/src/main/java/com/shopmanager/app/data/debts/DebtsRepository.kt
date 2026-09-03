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

    /**
     * BUG FIXED: marking a person as paid (the checkmark on the Debts list)
     * only ever deleted their debts — it never deleted the person, by
     * design, so their balance can go back up later. But `savePerson()`
     * used to treat *any* existing name as a hard duplicate and simply
     * refuse to save, with no way forward. So paying a customer off, then
     * trying to give them a new debt the normal way (typing their name
     * again in "عميل جديد"), hit "\"$name\" موجود مسبقاً!" every time and
     * went nowhere — the person had to already know to scroll down and
     * open the existing (zero-balance) customer instead. This now returns
     * the existing person's id (or null) so the caller can route a
     * same-name save into "add a debt to them" instead of a dead-end error.
     */
    suspend fun findPersonIdByName(name: String): String? = withTimeout(WRITE_TIMEOUT_MS) {
        val snapshot = db.collection("persons").whereEqualTo("name", name).get().await()
        snapshot.documents.firstOrNull()?.id
    }

    /**
     * One-off, server-sourced re-fetch used by pull-to-refresh. The screens
     * already stay live via [listenPersons]/[listenAllDebts], so this isn't
     * needed to see new data — but forcing a real round trip to the server
     * (bypassing Firestore's local cache) is a cheap way to detect and
     * recover a snapshot listener that silently stalled after a network
     * blip, instead of leaving the person pulling down with nothing to show
     * for it.
     */
    suspend fun refreshFromServer() = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection("persons").get(com.google.firebase.firestore.Source.SERVER).await()
        db.collection("debts").get(com.google.firebase.firestore.Source.SERVER).await()
        Unit
    }

    /**
     * BUG FIXED: adding a new person used to only write the "persons" doc
     * with an `amount` field — it never created a matching row in the
     * "debts" collection. Since Person Detail's debt history is driven
     * entirely by the debts collection, that initial amount silently never
     * showed up there; the person had to open the new client and re-enter
     * the exact same amount as a debt for it to actually appear. Now both
     * documents are written together, atomically, in one batch.
     *
     * BUG FIXED (PERMISSION_DENIED on "إضافة الدين" after paying a customer
     * off): this initial debt document used to be written WITHOUT a "note"
     * field, while every debt created afterward through [addDebt]/
     * [updateDebt] always writes one (even as ""). So two different field
     * shapes existed for documents in the same "debts" collection. If the
     * Firestore rules deployed on the console validate the write schema
     * (e.g. `request.resource.data.keys().hasOnly([...])`) against the
     * narrower shape written here, any *later* debt add — which is exactly
     * what happens right after settling someone and giving them a new
     * debt — carries a key the rules never saw permitted for that customer
     * and gets rejected as PERMISSION_DENIED. Every debt document now has
     * an identical field set no matter which code path created it.
     */
    /**
     * BUG FIXED (self-notification / "إشعار عميل جديد عند إضافتي أنا له"):
     * this used to return Unit, discarding the ids generated for the new
     * person/debt documents. DebtsViewModel's "new debt" notification is
     * driven by diffing the live debts listener (so it also catches debts
     * added from *other* devices) — without the id coming back from here,
     * it had no way to tell that particular apart from "I just added this
     * myself, seconds ago, on this exact screen" and notified the same
     * device that just typed it in. Returning the new debt id (when one was
     * created) lets the caller mark it as a local/self change so the diff
     * can skip notifying for it — see DebtsViewModel.selfCreatedDebtIds.
     */
    suspend fun addPerson(name: String, amount: Double, date: String): String? = withTimeout(WRITE_TIMEOUT_MS) {
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
        var debtId: String? = null
        if (amount > 0) {
            val debtRef = db.collection("debts").document()
            debtId = debtRef.id
            batch.set(
                debtRef,
                mapOf(
                    "personId" to personRef.id,
                    "amount" to amount,
                    "date" to date,
                    "note" to "",
                    "createdAt" to System.currentTimeMillis()
                )
            )
        }
        batch.commit().await()
        debtId
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

    /**
     * Settles a person's whole outstanding balance from the main debts list
     * (the checkmark on each person row): deletes every one of their debts
     * in one batch, same as marking each individual debt as paid one by
     * one, but without deleting the person itself.
     */
    suspend fun markAllDebtsAsPaid(personId: String) = withTimeout(WRITE_TIMEOUT_MS) {
        val debts = db.collection("debts").whereEqualTo("personId", personId).get().await()
        val batch = db.batch()
        for (doc in debts.documents) {
            batch.delete(db.collection("debts").document(doc.id))
        }
        batch.commit().await()
        Unit
    }

    /** Returns the new debt's id — see [addPerson] for why the caller needs it. */
    suspend fun addDebt(personId: String, amount: Double, date: String, note: String = ""): String = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf(
            "personId" to personId,
            "amount" to amount,
            "date" to date,
            "note" to note,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("debts").add(data).await().id
    }

    suspend fun updateDebt(id: String, amount: Double, date: String, note: String = "") = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf("amount" to amount, "date" to date, "note" to note)
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

    /**
     * One-off, all-at-once read of every person and debt — used only for
     * the daily local backup snapshot (see BackupManager); the rest of the
     * app always uses the live listeners above.
     */
    suspend fun fetchAllForBackup(): Pair<List<Person>, List<Debt>> = withTimeout(WRITE_TIMEOUT_MS) {
        val personsSnap = db.collection("persons").get().await()
        val debtsSnap = db.collection("debts").get().await()
        val persons = personsSnap.documents.map { doc ->
            Person(
                id = doc.id,
                name = doc.getString("name") ?: "",
                amount = doc.getDouble("amount") ?: 0.0,
                date = doc.getString("date") ?: "",
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }
        val debts = debtsSnap.documents.map { it.toDebt() }
        persons to debts
    }

    /**
     * Restores a full local backup snapshot back into Firestore: wipes
     * every existing "persons"/"debts" document and rewrites the
     * backed-up ones with their original document ids (so person <-> debt
     * links by id keep working and nothing re-orders). Batched in chunks
     * of 400 to stay under Firestore's 500-operation batch limit. Only
     * ever called after explicit confirmation from Settings, or from the
     * one-tap "server unavailable, restore local backup" prompt — never
     * silently.
     */
    suspend fun restoreFromBackup(persons: List<Person>, debts: List<Debt>) = withTimeout(60_000L) {
        val existingPersons = db.collection("persons").get().await()
        val existingDebts = db.collection("debts").get().await()

        val deletes = existingPersons.documents.map { db.collection("persons").document(it.id) } +
            existingDebts.documents.map { db.collection("debts").document(it.id) }
        deletes.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }

        val personWrites = persons.filter { it.id.isNotBlank() }.map {
            db.collection("persons").document(it.id) to mapOf(
                "name" to it.name, "amount" to it.amount, "date" to it.date, "createdAt" to it.createdAt
            )
        }
        val debtWrites = debts.filter { it.id.isNotBlank() }.map {
            db.collection("debts").document(it.id) to mapOf(
                "personId" to it.personId, "amount" to it.amount, "date" to it.date,
                "note" to it.note, "createdAt" to it.createdAt
            )
        }
        (personWrites + debtWrites).chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        Unit
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toDebt() = Debt(
        id = id,
        personId = getString("personId") ?: "",
        amount = getDouble("amount") ?: 0.0,
        date = getString("date") ?: "",
        note = getString("note") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )
}
