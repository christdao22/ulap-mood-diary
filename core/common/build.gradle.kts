// Small helpers every module may use: coroutine dispatchers and result helpers (README §5).
// Plain Kotlin, so its tests run on the JVM without an emulator.
plugins {
    alias(libs.plugins.ulap.jvm.library)
    alias(libs.plugins.ulap.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
