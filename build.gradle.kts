plugins {
    // BUILD UPDATE (٢٠٢٦-٠٩): now actually on AGP's latest stable LINE —
    // 9.4.0 (Sept 2026), not just the latest patch of the old 8.x line.
    // AGP 9.0+ switches Kotlin support on by default to its own
    // "built-in Kotlin" (no more org.jetbrains.kotlin.android plugin) plus
    // a new declarative DSL — a real source-incompatible migration
    // (typed property syntax throughout, no compileSdkVersion()-style
    // method calls, etc.) that Google's own docs call out, and one that
    // can't be safely made blind without a real Android build environment
    // to verify the result actually compiles.
    //
    // Google ships an official, documented escape hatch for exactly this:
    // android.builtInKotlin=false + android.newDsl=false in
    // gradle.properties keep AGP 9.x running the classic
    // org.jetbrains.kotlin.android plugin + the classic property-assignment
    // DSL this project already uses untouched — this is what actually lets
    // the version bump below happen safely. Confirmed still valid through
    // AGP 9.4 (Google's migration guide: "this will no longer work in AGP
    // 10.0", i.e. it explicitly is supported for the whole 9.x line). See
    // the comment on those two lines in gradle.properties for the tracking
    // note on when this stops being an option.
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    // Since Kotlin 2.0, the Compose compiler is no longer a separate
    // artifact pinned via composeOptions{kotlinCompilerExtensionVersion}
    // (see the old comment removed from app/build.gradle.kts) — Kotlin
    // ships and versions it itself as this plugin instead, matching the
    // Kotlin version above exactly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
