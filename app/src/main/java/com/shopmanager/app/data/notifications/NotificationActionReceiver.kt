package com.shopmanager.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * "إشعارات أكثر تطورًا وكفاءة": handles the action buttons attached to
 * [NotificationHelper]'s notifications directly — snoozing a note
 * reminder or dismissing the shopping-list card happens right here,
 * without ever launching [com.shopmanager.app.MainActivity] first, the
 * same "quick reply / mark as done" pattern messaging and task apps use
 * for their own notification action buttons.
 *
 * Registered with `android:exported="false"` in the manifest — every
 * `PendingIntent` that targets this receiver is built inside this app
 * itself (see [NotificationHelper.buildActionIntent]), so nothing
 * outside the app can ever trigger these actions directly.
 *
 * WorkManager's on-demand init (see ShopManagerApplication) means its
 * first-ever `getInstance()` call can touch disk to build its Room
 * database — `goAsync()` + a background-dispatcher coroutine keeps that
 * off this receiver's `onReceive` (which the platform expects back
 * quickly) instead of risking it blocking the main thread.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE_NOTE -> handleSnoozeNote(context, intent)
                    ACTION_DISMISS_SHOPPING -> NotificationHelper.cancelShoppingListNotification(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnoozeNote(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "تذكير"
        val content = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId)

        val snoozeUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        NoteReminderWorker.schedule(context, noteId, title, content, snoozeUntil)
    }

    companion object {
        const val ACTION_SNOOZE_NOTE = "com.shopmanager.app.action.SNOOZE_NOTE"
        const val ACTION_DISMISS_SHOPPING = "com.shopmanager.app.action.DISMISS_SHOPPING"

        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_NOTE_CONTENT = "extra_note_content"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
