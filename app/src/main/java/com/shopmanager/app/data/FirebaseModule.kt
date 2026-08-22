package com.shopmanager.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * UPDATED: both features (الديون / debts and المواد / materials) now share a
 * single Firebase project - "shop-app-6d35e" - instead of the two separate
 * legacy projects ("slx23m" for debts, "abo-slman" for materials) that the
 * app previously juggled as two named FirebaseApp instances. There is no
 * collision risk between the two features: they already live in disjoint
 * Firestore collections (see [com.shopmanager.app.data.debts.DebtsRepository]
 * and [com.shopmanager.app.data.materials.MaterialsRepository]), so pointing
 * both at one project is a pure simplification - one app, one dashboard, one
 * set of security rules (see /firebase-rules/firestore.rules), one quota.
 *
 * `debtsDb` and `materialsDb` are both kept below (now returning the *same*
 * Firestore instance) purely so nothing else in the codebase has to change.
 *
 * The values here are the Web SDK config values translated into the native
 * FirebaseOptions builder - this app never used google-services.json /
 * the Google Services Gradle plugin, so this manual init is intentional,
 * not a workaround.
 */
object FirebaseModule {

    private const val APP_NAME = "shopApp"

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val options = FirebaseOptions.Builder()
            .setApiKey("AIzaSyAa0vKXHkoiA-odIKucgKXDLMqsbwhdMXw")
            .setApplicationId("1:583934573941:web:e5ba92706635f06945e73c")
            .setProjectId("shop-app-6d35e")
            .setStorageBucket("shop-app-6d35e.firebasestorage.app")
            .setGcmSenderId("583934573941") // messagingSenderId
            .build()

        val app = FirebaseApp.initializeApp(context, options, APP_NAME)

        // Configure the persistent cache exactly once for this app instance
        // (still guarding against the old "enableIndexedDbPersistence called
        // twice" bug class from the original web version).
        configureFirestore(FirebaseFirestore.getInstance(app))
    }

    private fun configureFirestore(db: FirebaseFirestore) {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    private val sharedDb: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(FirebaseApp.getInstance(APP_NAME))

    val debtsDb: FirebaseFirestore get() = sharedDb
    val materialsDb: FirebaseFirestore get() = sharedDb
}
