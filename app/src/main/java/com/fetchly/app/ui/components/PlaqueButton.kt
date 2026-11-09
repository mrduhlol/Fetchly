package com.fetchly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.FilledTonalButton as MaterialTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.fetchly.app.ui.theme.DisplayFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fetchly.app.ui.theme.ActionGoldEdge
import com.fetchly.app.ui.theme.OnActionGold
import kotlin.math.sqrt

private val PlaqueShape = CutCornerShape(10.dp)

/** Flat gold, clipped corners and an inset rule match the approved README plaque. */
@Composable
fun PlaqueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerAlpha: Float = 1f,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
        shape = PlaqueShape,
        color = FetchlyButtonColors.container.copy(alpha = containerAlpha.coerceIn(0f, 1f) * if (enabled) 1f else 0.60f),
        contentColor = FetchlyButtonColors.content.copy(alpha = if (enabled) 1f else 0.55f),
        border = BorderStroke(1.dp, ActionGoldEdge.copy(alpha = if (enabled) 1f else 0.45f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(
            fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
        )) {
            Row(
                modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                    .drawWithCache {
                        val inset = 4.dp.toPx()
                        val cut = (10.dp.toPx() - inset * (2f - sqrt(2f))).coerceAtLeast(0f)
                        val left = inset
                        val top = inset
                        val right = size.width - inset
                        val bottom = size.height - inset
                        val frame = Path().apply {
                            moveTo(left + cut, top); lineTo(right - cut, top)
                            lineTo(right, top + cut); lineTo(right, bottom - cut)
                            lineTo(right - cut, bottom); lineTo(left + cut, bottom)
                            lineTo(left, bottom - cut); lineTo(left, top + cut); close()
                        }
                        onDrawWithContent {
                            drawContent()
                            drawPath(frame, OnActionGold.copy(alpha = if (enabled) 0.24f else 0.12f),
                                style = Stroke(width = 0.75.dp.toPx()))
                        }
                    }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

/** Secondary controls share the silhouette without competing with the gold primary action. */
@Composable
fun PlaqueOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    content: @Composable RowScope.() -> Unit,
) {
    MaterialOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = PlaqueShape, colors = colors,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f)),
        content = content)
}

@Composable
fun PlaqueTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    MaterialTonalButton(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = PlaqueShape, border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content)
}
