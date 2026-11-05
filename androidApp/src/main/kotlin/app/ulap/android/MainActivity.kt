package app.ulap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ulap.core.designsystem.theme.UlapTheme
import dagger.hilt.android.AndroidEntryPoint

/** The app's only Activity. Every screen is a composable inside it. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
