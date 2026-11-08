package com.fetchly.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fetchly.app.R
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.pow

/** Native, seekable artwork layers. Readiness always preempts the six-second showreel.
 * progress is null when startup has no measurable work total; never fabricate a percentage.
 */
@Composable
fun FetchlySplash(ready: Boolean, progress: Float? = null, onFinished: () -> Unit) {
    val opacity = remember { Animatable(1f) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
    }
    LaunchedEffect(ready) {
        if (ready) {
            // Let the destination compose underneath, then crossfade immediately.
            withFrameNanos { }
            opacity.animateTo(0f, tween(350))
            finished()
        }
    }
    val apollo = ImageBitmap.imageResource(R.drawable.splash_apollo)
    val word = ImageBitmap.imageResource(R.drawable.splash_wordmark)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }.background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = min(size.width / 2160f, size.height / 3840f)
            val dx = (size.width - 2160f * s) / 2f
            val dy = (size.height - 3840f * s) / 2f
            val t = seconds
            val gold = Color(0xFFD3B372)
            fun phase(start: Float, duration: Float) = ((t - start) / duration).coerceIn(0f, 1f)
            fun ease(p: Float) = 1f - (1f - p).pow(4)
            translate(dx, dy) { scale(s, s, Offset.Zero) {
                val awakening = ease(phase(.15f, 1.85f))
                drawRect(Brush.radialGradient(listOf(Color(0xFF231C12).copy(alpha = awakening), Color.Black), Offset(1080f, 1600f), 1900f), size = Size(2160f, 3840f))
                val beam = Path().apply { moveTo(1350f, 0f); lineTo(400f, 2850f); lineTo(1800f, 2850f); close() }
                drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, gold.copy(alpha = .045f * awakening), Color.Transparent), 100f, 2800f))
                drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .10f * awakening), Color.Transparent), Offset(1080f, 1400f), 1050f), 1050f, Offset(1080f, 1400f))
                val y = (760f + 240f * (1f - awakening)).toInt()
                drawContext.canvas.saveLayer(Rect(470f, y.toFloat(), 1690f, y + 1220f), Paint())
                drawImage(apollo, dstOffset = IntOffset(470, y), dstSize = IntSize(1220, 1220), alpha = awakening)
                drawRect(Brush.verticalGradient(listOf(Color.White, Color.Transparent), y + 930f, y + 1220f), Offset(470f, y.toFloat()), Size(1220f, 1220f), blendMode = BlendMode.DstIn)
                drawContext.canvas.restore()
                for (i in 0 until 24) {
                    val x = 300f + (i * 347) % 1560
                    val py = 600f + (i * 271) % 2230 - min(t, 6f) * 12f
                    drawCircle(gold.copy(alpha = (.13f + i % 4 * .08f) * phase(.5f, 1f)), 1.4f + i % 3 * .7f, Offset(x, py))
                }
                clipRect(280f, 2200f, 280f + 1600f * phase(2f, 1.05f), 2500f) {
                    drawContext.canvas.saveLayer(Rect(280f,2200f,1880f,2500f), Paint())
                    drawImage(word, dstOffset = IntOffset(280, 2200), dstSize = IntSize(1600, 300))
                    if (t in 2.2f..3.2f) {
                        val sweepX=280f+1600f*phase(2.2f,1f)
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent,gold.copy(alpha=.55f),Color.Transparent),sweepX-170f,sweepX+170f),Offset(280f,2200f),Size(1600f,300f),blendMode=BlendMode.SrcAtop)
                    }
                    drawContext.canvas.restore()
                }
                // Two separately mirrored branches, each with individually revealed leaves.
                for (mirror in listOf(false, true)) {
                    translate(530f, 2570f) {
                        scale(if (mirror) -1f else 1f, 1f, Offset(550f, 0f)) {
                            val stem = Path().apply { moveTo(565f,190f); quadraticBezierTo(790f,180f,1030f,74f) }
                            val measure = PathMeasure().apply { setPath(stem, false) }
                            val segment = Path()
                            measure.getSegment(0f, measure.length * ease(phase(3.2f,.72f)), segment)
                            drawPath(segment, gold, style = Stroke(5f, cap = StrokeCap.Round))
                            for (i in 0 until 9) {
                                val x = 575f + i*49f
                                val py = 186f - .00044f * (x-555f).pow(2)
                                val a = phase(3.24f+i*.065f,.25f)
                                for (side in 0..1) {
                                    val angle = if (side==0) 18f+i*1.3f else 67f-i*1.5f
                                    translate(x,py) { rotate(angle,Offset.Zero) {
                                        val leaf=Path().apply { moveTo(0f,0f); cubicTo(-23f,-18f,-22f,-60f,0f,-91f); cubicTo(26f,-66f,27f,-22f,0f,0f); close() }
                                        drawPath(leaf, Brush.linearGradient(listOf(Color(0xFF705329),Color(0xFFF5DF9D),Color(0xFF9B783B)),Offset(-15f,-91f),Offset(20f,0f)), alpha=a)
                                        drawLine(Color(0xFFF2D996).copy(alpha=a*.65f),Offset(0f,-5f),Offset(0f,-82f),1.1f)
                                    } }
                                }
                            }
                        }
                    }
                }
                val loading=phase(4.2f,.24f)
                if (loading>0f) {
                    drawLine(gold.copy(alpha=.22f*loading),Offset(700f,3000f),Offset(1460f,3000f),3f)
                    val p = progress?.takeIf { it.isFinite() }?.coerceIn(0f,1f)
                    if (p!=null) drawLine(gold.copy(alpha=loading),Offset(700f,3000f),Offset(700f+760f*p,3000f),3f)
                    // Unknown work total: a travelling highlight, without a false fill.
                    val head=700f+760f*(p ?: (((t-4.2f)*.42f)%1f))
                    drawCircle(Brush.radialGradient(listOf(gold.copy(alpha=loading*.8f),Color.Transparent),Offset(head,3000f),22f),22f,Offset(head,3000f))
                }
            } }
        }
    }
}
