import app.ulap.buildlogic.configureAndroidCompose
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

/** Adds Compose to an Android library. Apply after ulap.android.library. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            configureAndroidCompose(extensions.getByType<LibraryExtension>())
        }
    }
}
