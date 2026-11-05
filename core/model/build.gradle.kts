// The app's view of the domain types. They live in :shared (technical-plan.md §5), so this
// module re-exports them to the Android modules (ulap-reference.md §3).
plugins {
    alias(libs.plugins.ulap.jvm.library)
}

dependencies {
    api(projects.shared)
}
