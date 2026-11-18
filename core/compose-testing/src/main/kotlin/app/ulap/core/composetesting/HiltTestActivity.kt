package app.ulap.core.composetesting

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty activity that Hilt can inject, for flow tests that show real screens with their real
 * ViewModels: `hiltViewModel()` only works inside an @AndroidEntryPoint activity. Launch it with
 * `createAndroidComposeRule<HiltTestActivity>()` after a `HiltAndroidRule`, in a test that runs on
 * `HiltTestApplication` and swaps the repositories for fakes with `@BindValue`.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
