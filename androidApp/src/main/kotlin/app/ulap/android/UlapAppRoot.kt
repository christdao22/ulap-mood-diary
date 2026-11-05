package app.ulap.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.ulap.core.designsystem.theme.UlapTheme
import app.ulap.core.ui.R

/**
 * The app's root. Until the Phase 2 screens and the NavHost exist, it shows the app's name. That
 * proves the build, Compose, Hilt and the shared strings work together.
 */
@Composable
fun UlapAppRoot(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview
@Composable
private fun UlapAppRootPreview() {
    UlapTheme {
        UlapAppRoot()
    }
}
