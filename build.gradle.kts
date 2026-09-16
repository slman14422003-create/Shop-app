plugins {
    // BUILD UPDATE (٢٠٢٦-٠٩): latest STABLE releases only, never a beta/RC
    // — AGP 8.13.2 (Sept 2025, still the current 8.x line as of this
    // update; verified compileSdk 36 support and explicit Kotlin 2.3
    // compatibility via its bundled R8). Deliberately NOT AGP 9.0+: that
    // major version requires dropping this project's
    // "org.jetbrains.kotlin.android" plugin entirely for AGP's new
    // built-in-Kotlin DSL (a real source-incompatible migration Google's
    // own docs call out), plus Gradle 9.1+ — a breaking jump that can't be
    // safely made blind, without a real Android build environment to
    // verify the result actually compiles. 8.13.2 is the newest version
    // that upgrades every dependency below to its own latest stable line
    // with zero DSL/plugin-identity changes required.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    // Since Kotlin 2.0, the Compose compiler is no longer a separate
    // artifact pinned via composeOptions{kotlinCompilerExtensionVersion}
    // (see the old comment removed from app/build.gradle.kts) — Kotlin
    // ships and versions it itself as this plugin instead, matching the
    // Kotlin version above exactly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
