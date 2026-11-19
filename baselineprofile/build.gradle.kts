// وحدة توليد Baseline Profile حقيقي. غير مضمّنة في settings.gradle.kts عمداً حتى لا تكسر البناء؛
// فعّلها باتباع baselineprofile/README.md. لم تُبنَ هنا (لا شبكة/SDK)، فراجع الإصدارات قبل التفعيل.
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.shopmanager.baselineprofile"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
