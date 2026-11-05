// Test helpers shared by modules: fakes and MainDispatcherRule (ulap-reference.md §5).
// Only test source sets depend on it.
plugins {
    alias(libs.plugins.ulap.jvm.library)
}

dependencies {
    api(projects.core.domain)
    // MainDispatcherRule is a JUnit 4 rule that swaps in a test dispatcher.
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
