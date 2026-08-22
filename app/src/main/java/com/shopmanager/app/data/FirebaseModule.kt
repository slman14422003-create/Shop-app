package com.shopmanager.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * The two original web apps each talked to a *different* Firebase project
 * (debt-app -> "slx23m", material-manager -> "abo-slman"). Rather than forcing
 * a risky data migration into one project, both are kept alive here as two
 * named FirebaseApp instances inside this single native app. If you'd rather
 * unify everything into one Firestore database later, that's a follow-up
 * migration, not a rewrite of this app.
 *
 * BUG FIXED (debt-app): the old web config called BOTH
 * `initializeFirestore(..., persistentLocalCache(...))` AND
 * `enableIndexedDbPersistence(...)` on the same instance. Those two calls
 * conflict - the second one throws, is swallowed by a `.catch`, and offline
 * persistence silently misbehaves. Here we configure the cache exactly once.
 */
object FirebaseModule {

    private const val DEBTS_APP_NAME = "debtsApp"
    private const val MATERIALS_APP_NAME = "materialsApp"

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val debtsOptions = FirebaseOptions.Builder()
            .setApiKey("AIzaSyBSQD0eam2rAczlUqnV4zIUjYey1Yyic_I")
            .setApplicationId("1:903745007698:web:2c1aa9ab9aed95ad2eaf8b")
            .setProjectId("slx23m")
            .setStorageBucket("slx23m.firebasestorage.app")
            .build()

        val materialsOptions = FirebaseOptions.Builder()
            .setApiKey("AIzaSyDQbf5LJRCquRsheFYqvEQBQbI_EoXNOFw")
            .setApplicationId("1:874996942668:web:f31da5ca778fb92845f1e9")
            .setProjectId("abo-slman")
            .setStorageBucket("abo-slman.firebasestorage.app")
            .build()

        val debtsApp = FirebaseApp.initializeApp(context, debtsOptions, DEBTS_APP_NAME)
        val materialsApp = FirebaseApp.initializeApp(context, materialsOptions, MATERIALS_APP_NAME)

        // Configure persistent cache exactly once per app (this is the fix for the
        // "enableIndexedDbPersistence called twice" bug from the web version).
        configureFirestore(FirebaseFirestore.getInstance(debtsApp))
        configureFirestore(FirebaseFirestore.getInstance(materialsApp))
    }

    private fun configureFirestore(db: FirebaseFirestore) {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    val debtsDb: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(FirebaseApp.getInstance(DEBTS_APP_NAME))

    val materialsDb: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(FirebaseApp.getInstance(MATERIALS_APP_NAME))
}
