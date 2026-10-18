package com.fetchly.app.ui.about

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fetchly.app.BuildConfig
import com.fetchly.app.data.engine.YtDlpEngine
import com.fetchly.app.ui.components.ScreenTopBar
import com.fetchly.app.ui.components.SectionHeader

/**
 * Version, engine, the promises Fetchly makes, and what it honestly does not
 * do yet. The yt-dlp version is asked live so a stale bundled engine is
 * visible on-screen instead of guessed at.
 */
@Composable
fun AboutScreen(engine: YtDlpEngine, onBack: () -> Unit) {
    val ytDlp by produceState<String?>(initialValue = null) { value = engine.engineVersion() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTopBar("About", onBack = onBack)

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Fetchly ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "Version ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Card {
            Text(
                "No accounts. No servers. No API keys. Fetching, downloading and merging all " +
                    "happen on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionHeader("Engine")
        Card {
            Text(
                ytDlp?.let { "yt-dlp $it, bundled inside the app" } ?: "Checking the engine…",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionHeader("Good to know")
        Card {
            Bullet("A download runs to the end or is cancelled — pause and resume aren't supported yet.")
            Bullet("YouTube links work fully. Other sites are best effort, and when one fails Fetchly shows the real reason.")
            Bullet("This is a debug-signed build: uninstall any older Fetchly before installing it.")
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

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
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
