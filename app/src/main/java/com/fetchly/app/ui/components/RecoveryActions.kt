package com.fetchly.app.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fetchly.app.MainActivity
import com.fetchly.app.domain.recovery.FailureKind
import com.fetchly.app.domain.recovery.FailureRecovery
import com.fetchly.app.domain.security.UrlSecurity
import com.fetchly.app.domain.security.UrlValidation

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecoveryActions(
    raw: String?, sourceUrl: String, onRetry: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null, onChooseFormat: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val kind = FailureRecovery.kind(raw)
    var browserError by remember(sourceUrl) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(FailureRecovery.nextStep(raw), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRetry != null && kind !in setOf(FailureKind.BOT_CHECK, FailureKind.SIGN_IN,
                    FailureKind.REGION, FailureKind.REMOVED, FailureKind.DRM)) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            if (kind == FailureKind.ENGINE && onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) { Text("Engine updates") }
            }
            TextButton(onClick = { browserError = !ExternalLinks.open(context, sourceUrl) }) { Text("Open source") }
            if (onChooseFormat != null) {
                TextButton(onClick = onChooseFormat) { Text("Choose another format") }
            }
        }
        if (browserError) Text("No app could open this source link.", color = MaterialTheme.colorScheme.error)
    }
}

object ExternalLinks {
    const val APP_RELEASES = "https://github.com/mrduhlol/Fetchly/releases"
    fun open(context: Context, url: String): Boolean {
        if (UrlSecurity.validate(url) !is UrlValidation.Valid) return false
        val target = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val chooser = Intent.createChooser(target, "Open source").putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)),
        )
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }
}
