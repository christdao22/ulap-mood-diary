package app.ulap.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.ulap.core.designsystem.theme.UlapTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's only Activity. Every screen is a composable inside it. AppCompatActivity (not
 * ComponentActivity) so the per-app language from AppLanguage applies on Android 8–12 too
 * (technical-plan.md §11).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; the Scaffold pads content back in.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UlapTheme {
                UlapAppRoot()
            }
        }
    }
}
