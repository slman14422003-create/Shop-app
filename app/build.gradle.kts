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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
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

    // In-app WebView (دليل الاستخدام / help screen). androidx.webkit gives
    // access to the WebViewFeature/WebSettingsCompat compat-shims needed to
    // correctly force-dark WebView content and check per-feature support
    // across API levels (the plain android.webkit APIs for this only
    // stabilized piecemeal from API 29 through 33, so a raw SDK check alone
    // is not reliable on Android 11/12 devices — this library picks the
    // right mechanism at runtime).
    implementation("androidx.webkit:webkit:1.11.0")
}
