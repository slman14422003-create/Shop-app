package com.shopmanager.app.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.data.notes.NotesRepository
import com.shopmanager.app.data.notifications.BackgroundSyncWorker
import com.shopmanager.app.data.notifications.NoteReminderWorker
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<ImportantNote> = emptyList(),
    val isLoading: Boolean = true
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NotesRepository()
    private val settings = SettingsRepository(application)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val uiState: StateFlow<NotesUiState> = repo.listenNotes()
        .map { notes -> NotesUiState(notes = notes, isLoading = false) }
        .onStart { emit(NotesUiState(isLoading = true)) }
        .catch { e ->
            _message.value = "تعذر تحميل الملاحظات: ${e.message ?: "تحقق من الاتصال"}"
            emit(NotesUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesUiState())

    fun clearMessage() { _message.value = null }

    // null = haven't loaded once yet, so the whole existing list doesn't
    // fire a notification the very first time it loads — same pattern as
    // DebtsViewModel.knownDebtIds / MaterialsViewModel.lastNotifiedMaterials.
    private var knownNoteIds: Set<String>? = null

    /**
     * BUG FIXED ("ما ترسل إشعار لبقية الأجهزة إني ضفت ملاحظة"): Notes never
     * had ANY cross-device notification — the only notification a note
     * ever produced was its own optional reminder
     * ([com.shopmanager.app.data.notifications.NoteReminderWorker]),
     * scheduled purely locally on whichever device created it. Every other
     * device signed into the same shop had no way to learn a note was
     * added at all, unlike debts/materials which both already diff their
     * live listener the same way this now does. Same self-suppression
     * pattern as [selfCreatedNoteIds] below: an id this device just wrote
     * itself is consumed here instead of notifying its own creator.
     */
    private val selfCreatedNoteIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            NotificationHelper.ensureChannels(getApplication())
            uiState.collect { state ->
                if (state.isLoading) return@collect
                val currentIds = state.notes.map { it.id }.toSet()
                val previous = knownNoteIds
                if (previous != null) {
                    val newIds = (currentIds - previous)
                        .filterNot { selfCreatedNoteIds.remove(it) }
                        .toSet()
                    if (newIds.isNotEmpty() && settings.notificationsEnabled) {
                        state.notes.filter { it.id in newIds }.forEach { note ->
                            NotificationHelper.showNewNoteNotification(getApplication(), note.title, note.content, note.id)
                        }
                    }
                }
                knownNoteIds = currentIds
                // Keep the background worker's own baseline in sync too —
                // see BackgroundSyncWorker.syncKnownDebtIds for why this is
                // needed on every emission, not just self-created ones.
                BackgroundSyncWorker.syncKnownNoteIds(getApplication(), currentIds)
            }
        }
    }

    fun addNote(
        title: String,
        content: String,
        linkType: NoteLinkType,
        linkedId: String,
        linkedName: String,
        reminderAt: Long,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = repo.addNote(
                    ImportantNote(
                        title = title, content = content, linkType = linkType,
                        linkedId = linkedId, linkedName = linkedName, reminderAt = reminderAt
                    )
                )
                selfCreatedNoteIds += id
                if (reminderAt > 0) {
                    NoteReminderWorker.schedule(getApplication(), id, title, content, reminderAt)
                }
                _message.value = "تمت إضافة الملاحظة"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذرت الإضافة: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun updateNote(
        note: ImportantNote,
        title: String,
        content: String,
        linkType: NoteLinkType,
        linkedId: String,
        linkedName: String,
        reminderAt: Long,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val updated = note.copy(
                    title = title, content = content, linkType = linkType,
                    linkedId = linkedId, linkedName = linkedName, reminderAt = reminderAt
                )
                repo.updateNote(updated)
                // Always reschedule from scratch: schedule() itself cancels
                // any previous work for this id first, and if reminderAt is
                // now 0 the new schedule() call is a no-op past its cancel,
                // which is exactly "reminder removed".
                if (reminderAt > 0 && !updated.isDone) {
                    NoteReminderWorker.schedule(getApplication(), note.id, title, content, reminderAt)
                } else {
                    NoteReminderWorker.cancel(getApplication(), note.id)
                }
                _message.value = "تم تعديل الملاحظة"
                InstantBackupWorker.requestNow(getApplication())
                onDone(true)
            } catch (e: Exception) {
                _message.value = "تعذر التعديل: ${e.message ?: "تحقق من الاتصال"}"
                onDone(false)
            }
        }
    }

    fun deleteNote(note: ImportantNote) {
        viewModelScope.launch {
            try {
                repo.deleteNote(note.id)
                NoteReminderWorker.cancel(getApplication(), note.id)
                NotificationHelper.cancelNoteReminderNotification(getApplication(), note.id)
                _message.value = "تم حذف الملاحظة"
                InstantBackupWorker.requestNow(getApplication())
            } catch (e: Exception) {
                _message.value = "تعذر الحذف: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    /** Marking a note done also cancels any pending reminder for it - a
     * finished task shouldn't still ring later. */
    fun setDone(note: ImportantNote, done: Boolean) {
        viewModelScope.launch {
            try {
                repo.setDone(note.id, done)
                if (done) {
                    NoteReminderWorker.cancel(getApplication(), note.id)
                    NotificationHelper.cancelNoteReminderNotification(getApplication(), note.id)
                } else if (note.reminderAt > System.currentTimeMillis()) {
                    NoteReminderWorker.schedule(getApplication(), note.id, note.title, note.content, note.reminderAt)
                }
            } catch (e: Exception) {
                _message.value = "تعذر التحديث: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }

    fun setPinned(note: ImportantNote, pinned: Boolean) {
        viewModelScope.launch {
            try {
                repo.setPinned(note.id, pinned)
            } catch (e: Exception) {
                _message.value = "تعذر التحديث: ${e.message ?: "تحقق من الاتصال"}"
            }
        }
    }
}
