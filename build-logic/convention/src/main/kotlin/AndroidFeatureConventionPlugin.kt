import app.ulap.buildlogic.VerifyHiltViewModelsTask
import app.ulap.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
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

            // Runs after KSP and before compiling, so a missing Hilt ViewModel stops the build
            // instead of crashing the app on that screen.
            val androidComponents = extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>
            androidComponents.onVariants { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val verify = tasks.register<VerifyHiltViewModelsTask>("verify${variantName}HiltViewModels") {
                    sources.from(
                        fileTree("src") {
                            include("main/kotlin/**", "${variant.name}/kotlin/**", "${variant.buildType}/kotlin/**")
                        },
                    )
                    kspOutput.set(layout.buildDirectory.dir("generated/ksp/${variant.name}"))
                    dependsOn("ksp${variantName}Kotlin")
                }
                tasks.matching { it.name == "compile${variantName}Kotlin" }.configureEach { dependsOn(verify) }
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
                // Every screen's text lives here, in all three languages (README §13).
                "implementation"(project(":core:ui"))

                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())

                "testImplementation"(project(":core:testing"))
                // Touch target and 200% font checks for every screen test (design-spec.md §11).
                "testImplementation"(project(":core:compose-testing"))
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
                "testImplementation"(libs.findLibrary("robolectric").get())
                // Flow tests (@HiltAndroidTest) run the real ViewModels with fake repositories.
                "kspTest"(libs.findLibrary("hilt-compiler").get())
                "kspTest"(libs.findLibrary("kotlin-metadata").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
    }
}
