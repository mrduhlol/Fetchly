package com.fetchly.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fetchly.app.ui.components.PlatformIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupportedApps() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Supported apps", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4,
        ) {
            SupportedApp.entries.forEach { app ->
                val scale = remember { Animatable(1f) }
                val tilt = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                Column(
                    modifier = Modifier.width(70.dp).heightIn(min = 76.dp)
                        .semantics { contentDescription = app.label }
                        .clickable {
                            scope.launch {
                                launch { scale.animateTo(1.13f, spring(dampingRatio = 0.42f, stiffness = 650f)); scale.animateTo(1f, spring()) }
                                launch { tilt.animateTo(-7f, spring(stiffness = 900f)); tilt.animateTo(7f, spring(stiffness = 900f)); tilt.animateTo(0f, spring()) }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(62.dp).scale(scale.value).rotate(tilt.value)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlatformIcon(app.platform, large = true, music = app == SupportedApp.YOUTUBE_MUSIC,
                            modifier = Modifier.size(44.dp))
                    }
                    Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
