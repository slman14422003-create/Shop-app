package com.shopmanager.app.data.notifications

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.shopmanager.app.data.chat.ChatFirebase
import com.shopmanager.app.data.chat.ChatNotifyInput
import com.shopmanager.app.data.chat.ChatNotifyRules
import com.shopmanager.app.data.chat.ChatOpenTracker
import com.shopmanager.app.data.settings.SettingsRepository
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object ChatMessageWatcher {

    private const val RETRY_MS = 30_000L
    private const val PREFS = "chat_notif_baseline"

    private val executor: Executor by lazy { Executors.newSingleThreadExecutor() }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var auth: FirebaseAuth? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var registration: ListenerRegistration? = null
    private var initialDone = false

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        val a = try {
            ChatFirebase.auth(app)
        } catch (_: Exception) {
            return
        }
        auth = a
        if (authListener == null) {
            val listener = FirebaseAuth.AuthStateListener { fa -> attach(app, fa.currentUser?.uid) }
            authListener = listener
            a.addAuthStateListener(listener)
        } else {
            attach(app, a.currentUser?.uid)
        }
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        registration?.remove()
        registration = null
        authListener?.let { auth?.removeAuthStateListener(it) }
        authListener = null
        auth = null
        initialDone = false
    }

    @Synchronized
    private fun attach(app: Context, uid: String?) {
        registration?.remove()
        registration = null
        initialDone = false
        if (uid == null) return
        registration = ChatFirebase.db(app).collection("chats")
            .whereArrayContains("members", uid)
            .addSnapshotListener(executor) { snap, err ->
                if (err != null) {
                    handler.postDelayed({ start(app) }, RETRY_MS)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                try {
                    handle(app, uid, snap)
                } catch (_: Exception) {
                }
            }
    }

    private fun handle(app: Context, uid: String, snap: QuerySnapshot) {
        val trusted = !snap.metadata.isFromCache || snap.metadata.hasPendingWrites()
        if (!trusted) return
        process(app, uid, snap, !initialDone)
        initialDone = true
    }

    private val lock = Any()

    internal fun process(app: Context, uid: String, snap: QuerySnapshot, initial: Boolean) {
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val enabled = SettingsRepository(app).notificationsEnabled
            val edit = prefs.edit()
            for (change in snap.documentChanges) {
                if (change.type == DocumentChange.Type.REMOVED) continue
                val doc = change.document
                val unread = ((doc.get("unread") as? Map<*, *>)?.get(uid) as? Number)?.toInt() ?: 0
                if (unread <= 0) NotificationHelper.cancelChatNotification(app, doc.id)
                val lastTime = doc.getTimestamp("lastTime", ServerTimestampBehavior.NONE)?.toMs() ?: continue
                val key = "last_${doc.id}"
                val cleared = ((doc.get("clears", ServerTimestampBehavior.ESTIMATE) as? Map<*, *>)?.get(uid) as? Timestamp)?.toMs() ?: 0L
                val decision = ChatNotifyRules.decide(
                    seenMs = prefs.getLong(key, ChatNotifyRules.NO_BASELINE),
                    initial = initial,
                    myUid = uid,
                    openChatId = ChatOpenTracker.openChatId,
                    enabled = enabled,
                    doc = ChatNotifyInput(doc.id, lastTime, doc.getString("lastFrom").orEmpty(), unread, cleared)
                )
                if (decision.advanceBaseline) edit.putLong(key, lastTime)
                if (!decision.notify) continue
                val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val other = members.firstOrNull { it != uid } ?: continue
                val names = doc.get("names") as? Map<*, *>
                val name = (names?.get(other) as? String).orEmpty().ifBlank { "مستخدم" }
                NotificationHelper.showChatMessageNotification(
                    app, doc.id, other, name, doc.getString("lastType"), doc.getString("lastText").orEmpty(), lastTime, unread
                )
            }
            edit.apply()
        }
    }

    private fun Timestamp.toMs(): Long = seconds * 1000 + nanoseconds / 1_000_000
}
