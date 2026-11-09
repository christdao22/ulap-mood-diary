package app.ulap.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.domain.settings.SettingsRepository
import app.ulap.core.ui.applyAppLanguage
import app.ulap.core.ui.chosenAppLanguage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The app's only Activity. Every screen is a composable inside it. AppCompatActivity (not
 * ComponentActivity) so the per-app language applies on Android 8–12 too (technical-plan.md §11).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; the Scaffold pads content back in.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        followChosenLanguage()
        setContent {
            // A DataStore read takes a moment. Until it's in, show only the background, so a
            // returning person never sees onboarding flash and a new one never sees Check in.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            UlapTheme {
                settings?.let { UlapAppRoot(onboardingDone = it.onboardingDone) }
            }
        }
    }

    /**
     * The language lives in DataStore (technical-plan.md §11); screens only save it there. This
     * hands each new choice to the per-app language API, which recreates the Activity in it. After
     * the recreate the two already agree, so it doesn't loop. Before the first choice (null) the
     * app follows the phone's language.
     */
    private fun followChosenLanguage() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.settings
                    .map { it.language }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { language -> if (language != chosenAppLanguage()) applyAppLanguage(language) }
            }
        }
    }
}
