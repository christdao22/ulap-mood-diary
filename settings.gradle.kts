pluginManagement {
    // The ulap.* convention plugins are built from build-logic first (ulap-reference.md §4.1).
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Downloads the JDK that gradle/gradle-daemon-jvm.properties asks for, if it's missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // Modules can't declare their own repositories, so every dependency comes from these.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ulap"

// Lets build files write projects.core.model instead of project(":core:model").
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":shared")
include(":androidApp")
include(":server")

// Android modules (D1 in docs/ulap-reference.md §13, README §5).
include(":core:auth")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:domain")
include(":core:model")
include(":core:network")
include(":core:sync")
include(":core:testing")
include(":core:ui")
include(":feature:account")
include(":feature:checkin")
include(":feature:logs")
include(":feature:onboarding")
include(":feature:reports")
