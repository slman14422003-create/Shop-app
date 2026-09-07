package com.shopmanager.app.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.backup.InstantBackupWorker
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.data.notes.NotesRepository
import com.shopmanager.app.data.notifications.NoteReminderWorker
import com.shopmanager.app.data.notifications.NotificationHelper
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
