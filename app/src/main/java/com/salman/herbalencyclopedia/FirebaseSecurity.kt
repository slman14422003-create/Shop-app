package com.salman.herbalencyclopedia

/** Installs the build-appropriate Firebase App Check provider before Firebase data access. */
internal object FirebaseSecurity {
    fun install() {
        installAppCheckProvider()
    }
}
