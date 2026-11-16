// Kotlin Multiplatform module shared by the app and the server: models, MoodCatalog,
// ResponseRules, CheckInValidator and the API DTOs (technical-plan.md §4). No Android and no DI
// library in commonMain, so an iOS app can reuse it later (D2).
plugins {
    alias(libs.plugins.ulap.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Line coverage from the jvm() tests (PHASES.md Phase 9). `./gradlew check` runs
// koverVerify, so the build fails if shared drops below 80%. Report: koverHtmlReport.
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

kotlin {
    android {
        namespace = "app.ulap.shared"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            // Calendar months for Reports periods (technical-plan.md §10).
            // api: ReportInsight exposes DayOfWeek to the app modules.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
