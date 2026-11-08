package com.fetchly.app.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fetchly.app.BuildConfig
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.SectionHeader

/**
 * Version, the promises Fetchly makes, and what it honestly does not do yet.
 */
@Composable
fun AboutScreen(engine: com.fetchly.app.data.engine.YtDlpEngine, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            ScreenTopBar("About", onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.fetchly.app.R.drawable.fetchly_logo),
                    contentDescription = "Fetchly Apollo logo",
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp),
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Fetchly ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Version ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "© mrduhlol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Card {
                    Text(
                        "No account or API key is required. Link resolution, downloading and merging all " +
                            "happen on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                SectionHeader("Good to know")
                Card {
                    Bullet("Retry resumes eligible direct files when the source still provides the same file. Other media downloads may restart.")
                    Bullet("Public links from YouTube, Instagram, LinkedIn, X, Pinterest and other sites are resolved on-device. Availability depends on the post and source; sign-in and region restrictions can prevent downloads.")
                    Bullet("Merging and saving continue after the transfer finishes. Completed files offer Open file in Downloads.")
                }

                SectionHeader("Licences")
                Card {
                    Text(
                        "Fetchly is open source under AGPL-3.0. It bundles yt-dlp (Unlicense), " +
                            "Chaquopy, and ffmpeg-kit (LGPL-3.0).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
