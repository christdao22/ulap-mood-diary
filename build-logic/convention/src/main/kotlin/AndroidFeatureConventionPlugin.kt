import app.ulap.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

// Adapted from android/nowinandroid build-logic AndroidFeatureImplConventionPlugin.kt (Apache-2.0).

/**
 * The modules a feature may depend on (README §5, ulap-reference.md §5): the domain, the design
 * system and :shared, never a data source (core:database, core:network, core:auth) or another feature.
 */
private val ALLOWED_FEATURE_DEPENDENCIES = setOf(
    ":core:common",
    ":core:designsystem",
    ":core:domain",
    ":core:model",
    ":core:ui",
    ":shared",
)

/**
 * A `feature:*` module: Compose screens, their ViewModels (Hilt) and a navigation graph with
 * type-safe routes. Tests can drive the screens on the JVM through Robolectric.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "ulap.android.library")
            apply(plugin = "ulap.android.library.compose")
            apply(plugin = "ulap.hilt")
            // Routes are @Serializable classes, so navigation can save and restore them.
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<LibraryExtension> {
                // Robolectric needs the merged resources to run a screen's stringResource() calls.
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            // Those merged resources count as test sources, so Gradle 9 would fail a feature that
            // has no tests yet. Stay strict once the feature has a src/test folder.
            val hasTests = file("src/test").exists()
            tasks.withType<Test>().configureEach {
                failOnNoDiscoveredTests.set(hasTests)
            }

            configurations.configureEach {
                // Tests may use helpers like core:testing; the feature's own code may not.
                if (name.contains("test", ignoreCase = true)) return@configureEach
                val configurationName = name
                dependencies.withType<ProjectDependency>().configureEach {
                    check(path in ALLOWED_FEATURE_DEPENDENCIES) {
                        "${target.path} may not depend on $path ($configurationName). Features use the " +
                            "domain's interfaces instead (README §5)."
                    }
                }
            }

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:domain"))

                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
                "testImplementation"(libs.findLibrary("robolectric").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
    }
}
