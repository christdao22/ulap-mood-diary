// Puts every plugin on the build's classpath once, at the catalog's version. Modules apply
// them through the ulap.* convention plugins in build-logic (ulap-reference.md §4.1).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.hilt) apply false
    // AGP 9 compiles Kotlin itself; declaring KGP here picks the Kotlin version it uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.room) apply false

    // Code checks for the whole build, run from here once (ulap-reference.md §4.4, D10).
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// Formatting: `gradlew spotlessApply` fixes it, `spotlessCheck` (CI) fails on any difference.
// Style rules come from .editorconfig. Generated code under build/ is never touched.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
            .customRuleSets(listOf(composeRules))
            // The only CompositionLocal allowed: theme values Material has no slot for
            // (core:designsystem). Set here because Spotless doesn't pass this .editorconfig key on.
            .editorConfigOverride(mapOf("compose_allowed_composition_locals" to "LocalUlapColors"))
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

// Static analysis: likely bugs and code smells that formatting can't see. Rules beyond
// detekt's defaults are in config/detekt/detekt.yml, each with a reason.
detekt {
    source.setFrom(
        fileTree(projectDir) {
            include("**/src/*/kotlin/**/*.kt")
            exclude("**/build/**")
        },
    )
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    parallel = true
}
