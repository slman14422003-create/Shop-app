import java.util.Properties

// ⚠️ MANUAL RELEASE NUMBER — bump this by +1 before every single manual
// build+upload of a new Release APK, and give the matching GitHub Release
// a tag ending in the SAME number (e.g. 3 → tag "v1.0.3"). See the long
// comment on versionCode/versionName below for why this matters — this is
// the one line in the whole project you now have to remember to touch by
// hand each release, since nothing builds this automatically anymore.
val MANUAL_VERSION_CODE = 1

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// SIGNING: reads the real Play Store release keystore's credentials from
// keystore/keystore.properties.local — a file that is NEVER committed
// (see .gitignore) and must never leave this machine/CI secret store.
// If that file isn't present (a fresh checkout, a contributor's machine,
// a CI run with no signing secret configured), release builds fall back
// to Gradle's own auto-generated debug key exactly as before, so
// `assembleRelease` never breaks — it just produces a build that isn't
// the one Play will accept until the real keystore is available.
//
// BUILD FIX: this used to reference `java.util.Properties` inline
// (fully-qualified, no import). That fails specifically in an Android
// module's build.gradle.kts: AGP applies the `java-base` plugin
// internally, which makes Gradle's Kotlin DSL auto-generate a top-level
// `java` accessor (for JavaPluginExtension) on the script — and that
// accessor shadows the `java` *package* name, so `java.util.Properties`
// tried to resolve `.util` as a member of the accessor instead of the
// JDK package ("Unresolved reference: util"). Importing `Properties`
// explicitly (the standard pattern used everywhere else for this exact
// keystore-loading snippet) sidesteps the shadowing entirely.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties.local")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.shopmanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shopmanager.app"
        minSdk = 24
        targetSdk = 34
        // MANUAL RELEASES: you're now building and uploading Release APKs
        // by hand (not through GitHub Actions/release.yml anymore), so
        // GITHUB_RUN_NUMBER never exists at build time — versionCode was
        // silently falling back to 1 on EVERY manual build, forever. That
        // breaks update-checking completely: the installed app always
        // reports itself as version 1, so it either never sees an update
        // (if the release tag's number isn't higher than 1) or nags with
        // "update available" forever even right after installing the
        // "latest" one (since the freshly-installed app is still, itself,
        // version 1 — it can never catch up).
        //
        // Fix: bump MANUAL_VERSION_CODE by hand, by at least +1, every
        // single time you build a new Release APK — and make sure the
        // GitHub Release's tag you create for it ends in that exact same
        // number (e.g. tag "v1.0.3" for MANUAL_VERSION_CODE = 3). Those
        // two numbers — this one, and the release tag's trailing number —
        // MUST match, because UpdateChecker.kt compares this app's own
        // versionCode against the number it reads back out of the tag.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: MANUAL_VERSION_CODE
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: MANUAL_VERSION_CODE.toString()}"

        // GITHUB_REPOSITORY is another env var GitHub Actions sets
        // automatically ("owner/repo") — baked into BuildConfig so
        // UpdateChecker can build the GitHub Releases API URL itself with
        // no manifest URL ever needing to be typed in by hand anywhere
        // (see AdminPanelScreen's "رابط التحديثات" field, which now shows
        // this as a read-only default instead of a blank field to fill
        // in). Blank on a local/non-CI build — see the empty-string
        // handling already in UpdateChecker.
        buildConfigField("String", "GITHUB_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: ""}\"")

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

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties.getProperty("STORE_FILE")}")
                storePassword = keystoreProperties.getProperty("STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            }
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
            // SIGNING: uses the real release keystore (see keystore/
            // shopmanager-release.jks + keystore.properties.local) when
            // it's present on this machine, so `assembleRelease`/
            // `bundleRelease` produce a build that's actually acceptable
            // to the Play Store — the same signing identity every future
            // update must keep using. Falls back to the debug key only
            // when the real keystore isn't available locally, so this
            // still never breaks a plain checkout/CI run with no signing
            // secret configured.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // JAVA VERSION: bumped from 17 to 21 (current LTS — AGP 8.5.2 / Gradle
    // 8.7 / Kotlin 1.9.24, see the root build.gradle.kts and CI workflow,
    // all officially support building with and targeting JDK 21). minSdk 24
    // is unaffected: D8 still desugars whatever the target device's runtime
    // can't run natively, exactly as it did for Java 17 language features.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
