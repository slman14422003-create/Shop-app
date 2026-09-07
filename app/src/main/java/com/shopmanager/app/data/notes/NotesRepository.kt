package com.shopmanager.app.data.notes

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
 * Same shared Firebase project/collection pattern as [com.shopmanager.app.data.debts.DebtsRepository]
 * and [com.shopmanager.app.data.materials.MaterialsRepository] - its own
 * collection, same hard write timeout so a stuck write can't hang the UI.
 */
class NotesRepository {

    private val db: FirebaseFirestore get() = FirebaseModule.notesDb
    private val notesCollection = "important_notes"

    fun listenNotes(): Flow<List<ImportantNote>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(notesCollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notes = snapshot?.documents
                    ?.map { it.toNote() }
                    // Pinned first, then newest first - matches how a
                    // person naturally scans a sticky-note list: the ones
                    // they starred stay glued to the top regardless of age.
                    ?.sortedWith(compareByDescending<ImportantNote> { it.isPinned }.thenByDescending { it.createdAt })
                    ?: emptyList()
                trySend(notes)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addNote(note: ImportantNote): String = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf(
            "title" to note.title,
            "content" to note.content,
            "linkType" to note.linkType.toStorage(),
            "linkedId" to note.linkedId,
            "linkedName" to note.linkedName,
            "reminderAt" to note.reminderAt,
            "isPinned" to note.isPinned,
            "isDone" to note.isDone,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection(notesCollection).add(data).await().id
    }

    suspend fun updateNote(note: ImportantNote) = withTimeout(WRITE_TIMEOUT_MS) {
        val data = mapOf(
            "title" to note.title,
            "content" to note.content,
            "linkType" to note.linkType.toStorage(),
            "linkedId" to note.linkedId,
            "linkedName" to note.linkedName,
            "reminderAt" to note.reminderAt,
            "isPinned" to note.isPinned,
            "isDone" to note.isDone
        )
        db.collection(notesCollection).document(note.id).update(data).await()
        Unit
    }

    suspend fun setDone(id: String, done: Boolean) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).update("isDone", done).await()
        Unit
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).update("isPinned", pinned).await()
        Unit
    }

    suspend fun deleteNote(id: String) = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).document(id).delete().await()
        Unit
    }

    /** One-off, all-at-once read - used only for the daily local backup
     * snapshot (see BackupManager); the rest of the app uses [listenNotes]. */
    suspend fun fetchAllForBackup(): List<ImportantNote> = withTimeout(WRITE_TIMEOUT_MS) {
        db.collection(notesCollection).get().await().documents.map { it.toNote() }
    }

    suspend fun restoreFromBackup(notes: List<ImportantNote>) = withTimeout(60_000L) {
        val existing = db.collection(notesCollection).get().await()
        existing.documents.map { db.collection(notesCollection).document(it.id) }
            .chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        notes.filter { it.id.isNotBlank() }.map {
            db.collection(notesCollection).document(it.id) to mapOf(
                "title" to it.title,
                "content" to it.content,
                "linkType" to it.linkType.toStorage(),
                "linkedId" to it.linkedId,
                "linkedName" to it.linkedName,
                "reminderAt" to it.reminderAt,
                "isPinned" to it.isPinned,
                "isDone" to it.isDone,
                "createdAt" to it.createdAt
            )
        }.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        Unit
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toNote() = ImportantNote(
        id = id,
        title = getString("title") ?: "",
        content = getString("content") ?: "",
        linkType = noteLinkTypeFromStorage(getString("linkType") ?: "none"),
        linkedId = getString("linkedId") ?: "",
        linkedName = getString("linkedName") ?: "",
        reminderAt = getLong("reminderAt") ?: 0L,
        isPinned = getBoolean("isPinned") ?: false,
        isDone = getBoolean("isDone") ?: false,
        createdAt = getLong("createdAt") ?: 0L
    )
}
