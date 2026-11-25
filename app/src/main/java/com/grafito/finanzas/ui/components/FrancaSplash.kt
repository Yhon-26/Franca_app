package com.grafito.finanzas.ui.components

import android.os.Build
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.grafito.finanzas.ui.theme.Graphite

private val SplashBackground = Color(0xFF1A1A1D)
private const val TotalDuration = 3550

@Composable
fun FrancaSplash(onFinished: () -> Unit) {
    val time = remember { Animatable(0f) }
    val done by rememberUpdatedState(onFinished)
    val draw = remember { SplashSpring(90f, 18f) }
    val pop = remember { SplashSpring(320f, 14f) }
    val rise = remember { SplashSpring(140f, 13f) }
    val easing = remember { CubicBezierEasing(.22f, 1f, .36f, 1f) }
    LaunchedEffect(Unit) {
        // Compose applies Android's animator duration scale, including immediate completion at zero.
        time.animateTo(TotalDuration.toFloat(), tween(TotalDuration, easing = LinearEasing))
        done()
    }
    val elapsed = time.value
    val exit = ((elapsed - 3350) / 200).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(lerp(SplashBackground, Graphite.Background, exit))
            .semantics { contentDescription = "Franca. Iniciando" },
        contentAlignment = Alignment.Center
    ) {
        val width = minOf(maxWidth * .46f, maxHeight * .52f, 340.dp)
        Box(Modifier.width(width).aspectRatio(600f / 660f).graphicsLayer { alpha = 1 - exit }) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val scale = size.width / 600
                    native.save(); native.scale(scale, scale)
                    native.save(); native.translate(150f, -2.5f); native.scale(2.5f, 2.5f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(242, 242, 245)
                        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    }
                    fun trimmed(data: String, delay: Int, thickness: Float) {
                        val progress = draw.value(elapsed - delay).coerceIn(0f, 1f)
                        if (progress <= 0) return
                        val path = PathParser.createPathFromPathData(data)!!
                        val measure = PathMeasure(path, false)
                        val segment = Path()
                        measure.getSegment(0f, measure.length * progress, segment, true)
                        paint.strokeWidth = thickness
                        native.drawPath(segment, paint)
                    }
                    trimmed("M60 18 A38 38 0 0 0 22 56 V108", 260, 9f)
                    trimmed("M60 18 A38 38 0 0 1 98 56 V108", 260, 9f)
                    trimmed("M40 42 L84 42", 820, 6f)
                    trimmed("M40 42 L40 94", 860, 6f)
                    trimmed("M40 66 L72 66", 1020, 6f)
                    fun knot(x: Float, y: Float, radius: Float, delay: Int, border: Float = 2.5f) {
                        val factor = pop.value(elapsed - delay).coerceAtLeast(0f)
                        if (factor == 0f) return
                        native.save(); native.scale(factor, factor, x, y)
                        paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.rgb(137, 217, 177)
                        native.drawCircle(x, y, radius, paint)
                        paint.style = Paint.Style.STROKE; paint.strokeWidth = border
                        paint.color = android.graphics.Color.rgb(26, 26, 29)
                        native.drawCircle(x, y, radius, paint)
                        native.restore()
                    }
                    knot(54f, 42f, 5.5f, 1020); knot(66f, 42f, 5.5f, 1110); knot(78f, 42f, 5.5f, 1200)
                    knot(54f, 66f, 5.5f, 1220); knot(66f, 66f, 5.5f, 1310); knot(40f, 84f, 5.5f, 1260)
                    knot(60f, 18f, 9.5f, 120, 3f)
                    native.restore(); native.restore()
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                SplashArtwork.glyphs.forEachIndexed { index, data ->
                    val progress = rise.value(elapsed - 1500 - index * 75)
                    val fade = easing.transform(((elapsed - 1500 - index * 75) / 520).coerceIn(0f, 1f))
                    VectorSplashPath(data, SplashArtwork.wordmarkBounds, Color(0xFFF2F2F5),
                        Modifier.offset(x = maxWidth * .1f, y = maxHeight * (380f / 660f))
                            .width(maxWidth * .8f).height(maxHeight * (141.26f / 660f))
                            .graphicsLayer {
                                alpha = fade
                                transformOrigin = TransformOrigin(.5f, 1f)
                                rotationX = -78 * (1 - progress)
                                translationY = size.height * .42f * (1 - progress)
                                cameraDistance = 900 * density
                                if (Build.VERSION.SDK_INT >= 31) {
                                    val radius = 6 * density * (1 - fade)
                                    renderEffect = if (radius > .01f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL).asComposeRenderEffect() else null
                                }
                            })
                }
                val tagline = easing.transform(((elapsed - 2300) / 900).coerceIn(0f, 1f))
                VectorSplashPath(SplashArtwork.tagline, floatArrayOf(0f, 0f, 600f, 660f), Color(0xFFA9A9A6),
                    Modifier.fillMaxSize().graphicsLayer { alpha = tagline; translationY = 6 * density * (1 - tagline) },
                    translateX = 123.095995f, translateY = 600f)
            }
        }
    }
}

@Composable
private fun VectorSplashPath(data: String, bounds: FloatArray, color: Color, modifier: Modifier,
                             translateX: Float = 0f, translateY: Float = 0f) {
    val path = remember(data) { PathParser.createPathFromPathData(data)!! }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) {
        paint.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt())
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val scale = minOf(size.width / bounds[2], size.height / bounds[3])
            native.save()
            native.translate((size.width - bounds[2] * scale) / 2, (size.height - bounds[3] * scale) / 2)
            native.scale(scale, scale); native.translate(-bounds[0] + translateX, -bounds[1] + translateY)
            native.drawPath(path, paint); native.restore()
        }
    }
}
