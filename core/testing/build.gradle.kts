// Test helpers shared by modules: fakes and MainDispatcherRule (ulap-reference.md §5).
// Only test source sets depend on it.
plugins {
    alias(libs.plugins.ulap.jvm.library)
}

dependencies {
    api(projects.core.domain)
}
