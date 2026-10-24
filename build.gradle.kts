// Top-level build file
plugins {
    id("com.android.application") version "9.4.0" apply false
    // org.jetbrains.kotlin.android حُذفت هنا أيضًا — لم تعد مُطبَّقة في أي
    // module (دعم Kotlin مدمج في AGP 9+). راجع app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
