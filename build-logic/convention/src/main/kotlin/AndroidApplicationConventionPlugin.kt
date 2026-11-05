import app.ulap.buildlogic.Sdk
import app.ulap.buildlogic.addUnitTestDependencies
import app.ulap.buildlogic.configureKotlinAndroid
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** The `app` module: an Android application with this project's SDK and Kotlin settings. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = Sdk.TARGET
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // Android Lint, run once from the app over every module it uses
                // (`gradlew :androidApp:lintDebug`). Warnings fail the build (ulap-reference.md §4.4).
                lint {
                    checkDependencies = true
                    warningsAsErrors = true
                    abortOnError = true
                    // "A newer version is available" would fail CI on every upstream
                    // release; dependency updates are a deliberate catalog change instead.
                    disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
                    // Fires on local.properties even in the exact form Android Studio writes
                    // (sdk.dir=C\:\\Users\\…). That file is per machine and gitignored, and the
                    // repo has no other .properties file holding a path.
                    disable += "PropertyEscape"
                }
            }
            addUnitTestDependencies()
        }
    }
}
