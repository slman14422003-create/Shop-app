package com.shopmanager.app.data.chat

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * إعدادات مشروع Firebase الخاص بالدردشة المحلية فقط.
 *
 * ⚠ هذا المشروع (shop-app-1670f) لا علاقة له بمشروع الديون/المواد (shop-app-6d35e، انظر
 * [com.shopmanager.app.data.FirebaseModule]): لكل مشروع قاعدة بيانات وقواعد وحصة مستقلة، ولكل
 * واحد FirebaseApp باسم مختلف داخل التطبيق، فلا يتداخلان أبداً.
 *
 * القيم مأخوذة من google-services.json الخاص بالدردشة، والتهيئة يدوية (FirebaseOptions) بنفس
 * أسلوب التطبيق، فلا حاجة لإضافة google-services.json ولا للـ plugin — وهذا ما يمنع أي تعارض مع
 * المشروع القديم. لو غيّرت التطبيق المسجّل في Firebase (مثلاً أضفت التطبيق بالـ package الصحيح
 * com.shopmanager.app) استبدل APP_ID و API_KEY بالقيم الجديدة هنا فقط.
 */
internal object ChatFirebaseConfig {
    const val API_KEY = "AIzaSyAraLFrEZKImJFWRlbGqD-3MXvvAWT-z8o"
    const val APP_ID = "1:631188161296:android:bc4e11dc05e84448081b95"
    const val PROJECT_ID = "shop-app-1670f"
    const val SENDER_ID = "631188161296"
}

object ChatFirebase {

    private var settingsApplied = false

    /** خيط خلفي واحد للمستمعات (نفس فكرة FirebaseModule.snapshotExecutor) فلا يُحلَّل شيء على الخيط الرئيسي. */
    val executor: Executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "chat-snapshots").apply { isDaemon = true } }
    }

    @Synchronized
    fun app(context: Context): FirebaseApp {
        val app = try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            val options = FirebaseOptions.Builder()
                .setApiKey(ChatFirebaseConfig.API_KEY)
                .setApplicationId(ChatFirebaseConfig.APP_ID)
                .setProjectId(ChatFirebaseConfig.PROJECT_ID)
                .setGcmSenderId(ChatFirebaseConfig.SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options)
        }
        if (!settingsApplied) {
            // الكاش المحلي محدود بـ 40MB: الرسائل الصوتية تُخزَّن كنص داخل المستندات، فبدون سقف قد تكبر
            // مساحة الكاش. Firestore يحذف الأقدم تلقائياً عند بلوغ الحد.
            try {
                FirebaseFirestore.getInstance(app).firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder().setSizeBytes(40L * 1024 * 1024).build()
                    )
                    .build()
            } catch (_: IllegalStateException) {
            }
            settingsApplied = true
        }
        return app
    }

    fun auth(context: Context): FirebaseAuth = FirebaseAuth.getInstance(app(context))

    fun db(context: Context): FirebaseFirestore = FirebaseFirestore.getInstance(app(context))
}
