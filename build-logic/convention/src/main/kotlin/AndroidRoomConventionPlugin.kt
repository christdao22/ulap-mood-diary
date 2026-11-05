import androidx.room.gradle.RoomExtension
import app.ulap.buildlogic.libs
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Adapted from android/nowinandroid build-logic AndroidRoomConventionPlugin.kt (Apache-2.0).

/** Room: KSP generates the DAO code, and each schema version is exported for migrations. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "androidx.room")
            apply(plugin = "com.google.devtools.ksp")

            // One JSON file per database version, committed. Migration tests and
            // @AutoMigration compare them (ulap-reference.md §5).
            val schemas = "$projectDir/schemas"
            extensions.configure<RoomExtension> {
                schemaDirectory(schemas)
            }

            // The Room plugin hands the schemas to device tests only. MigrationTestHelper also
            // runs under Robolectric, where it reads them from the unit test's assets.
            // Looked up by name: AGP registers it under the app or library subtype.
            val androidComponents = extensions.getByName("androidComponents") as AndroidComponentsExtension<*, *, *>
            androidComponents.onVariants { variant ->
                (variant as? HasUnitTest)?.unitTest?.sources?.assets?.addStaticSourceDirectory(schemas)
            }

            dependencies {
                "implementation"(libs.findLibrary("room-runtime").get())
                "implementation"(libs.findLibrary("room-ktx").get())
                "ksp"(libs.findLibrary("room-compiler").get())
            }
        }
    }
}
