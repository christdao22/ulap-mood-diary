// Repositories: the only place Room rows and network DTOs meet, mapped to :shared domain types
// and bound to core:domain's interfaces (README §5).
plugins {
    alias(libs.plugins.ulap.android.library)
    alias(libs.plugins.ulap.hilt)
}

android {
    namespace = "app.ulap.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
}
