// Repository interfaces and use cases that features call. Plain Kotlin: features depend on
// these, never on Room or the network (README §5, ulap-reference.md §5).
plugins {
    alias(libs.plugins.ulap.jvm.library)
}

dependencies {
    api(projects.core.model)
    // LocalDate: check-ins are read by the local day.
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)
}
