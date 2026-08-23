plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shopmanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shopmanager.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // SIZE FIX: Firestore/gRPC ship native .so libraries for four ABIs
        // (armeabi-v7a, arm64-v8a, x86, x86_64). x86/x86_64 exist only for
        // emulators — no real phone (not the Redmi A10, not the Samsung
        // A16, not any real ARM device) uses them, so they were pure dead
        // weight on every install. Restricting to the two ARM ABIs shrinks
        // the APK real users download/install without removing anything
        // that runs on real hardware. (If this app is ever distributed as
        // an .aab through Play, Play already does this splitting
        // automatically per-device and this filter is a no-op there — it
        // only matters for a directly-installed/sideloaded .apk.)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            // PERF FIX: this was `false`, which meant the release build
            // shipped completely unshrunk — every class from every
            // dependency (Compose, Firestore, and especially
            // material-icons-extended, which alone bundles several
            // thousand icon classes for icons this app never uses) stayed
            // in the APK at full size, unobfuscated, un-dead-code-eliminated.
            // That's a heavier APK to download/install and more classes for
            // the runtime to load at cold start — exactly what shows up as
            // "needs a full-spec device" on an entry-level phone with a
            // slow eMMC and little RAM. The app has no reflection-based
            // Firestore mapping (every read uses doc.getString()/
            // getDouble(), never toObject()), so there's nothing here R8
            // could break by renaming/removing unused classes — safe to
            // shrink. See proguard-rules.pro for the few explicit keep
            // rules Firebase itself still needs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // SIGNING: an Android APK can't be installed unless it's
            // signed — a release build with no signingConfig builds fine
            // but the .apk it produces is unusable. This app has no Play
            // Store keystore of its own, so release is signed with the
            // same auto-generated debug key Gradle already uses for the
            // debug build (both locally and in CI). That's the right
            // tradeoff for an internal/sideloaded shop app: it makes
            // `assembleRelease` produce a real installable, shrunk APK
            // without any keystore secret to manage. It is NOT suitable
            // if this app is ever published to the Play Store — that
            // requires generating a dedicated release keystore and
            // keeping it private (never in the repo).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // BUILD FIX: "Unable to strip the following libraries, packaging
        // them as they are: libandroidx.graphics.path.so" — this is AGP
        // trying to run the NDK's `strip` tool on a prebuilt native lib
        // that ships inside an AndroidX artifact, but the build machine
        // (this project has no NDK/JNI code of its own, so no NDK is
        // installed here or in CI) doesn't have that tool available. AGP
        // then just packages the lib unstripped and prints it as a
        // warning — build output still succeeds either way, but the
        // warning is noise on every build. Telling AGP up front to keep
        // debug symbols for this lib (rather than attempt-then-fall-back)
        // skips the failed strip attempt entirely, so the warning is gone
        // and the packaged .apk is byte-for-byte the same as before.
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Standard AndroidX SplashScreen API — shows a static app icon on a
    // flat background immediately at cold start instead of a blank/white
    // starting window, and is kept on screen (see MainActivity) until the
    // first real frame is fully ready. This is what fixes the startup
    // jitter/flash.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // FIX: bumped from 2024.06.00 (material3 1.2.x) so the app can use the
    // stable Material3 pull-to-refresh API (PullToRefreshBox), which only
    // shipped starting material3 1.3.0. Used for the new Facebook-style
    // pull-down-to-refresh gesture, applied consistently across Home,
    // Debts, and Materials (see ui/common/PullToRefreshContent.kt).
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Firebase — initialized manually via FirebaseOptions (no google-services.json / plugin needed)
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.1.2")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-common-ktx")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Free periodic background check for new debts / shortage-list changes
    // (see data/notifications/BackgroundSyncWorker.kt) — no server or paid
    // Firebase plan needed, unlike a real Cloud-Function-triggered push.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // In-app WebView (دليل الاستخدام / help screen). androidx.webkit gives
    // access to the WebViewFeature/WebSettingsCompat compat-shims needed to
    // correctly force-dark WebView content and check per-feature support
    // across API levels (the plain android.webkit APIs for this only
    // stabilized piecemeal from API 29 through 33, so a raw SDK check alone
    // is not reliable on Android 11/12 devices — this library picks the
    // right mechanism at runtime).
    implementation("androidx.webkit:webkit:1.11.0")
}
