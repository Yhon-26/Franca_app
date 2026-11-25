package com.grafito.finanzas.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Color
import android.graphics.Movie
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.grafito.finanzas.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.roundToInt

private const val CALM_START_MILLIS = 3_520
private const val FRAME_MILLIS = 40

@Composable
internal fun rememberDeskMascotScene(): DeskMascotScene? {
    val resources = LocalContext.current.resources
    return produceState<DeskMascotScene?>(initialValue = null, resources) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.franca_desk_scene).use(DeskMascotScene::decode)
        }
    }.value
}

@Composable
internal fun DeskMascotAnimation(scene: DeskMascotScene?) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(342f / 252f)
    val description = "Capibara de Franca trabajando frente a su laptop"
    if (scene == null) {
        Image(painterResource(R.drawable.franca_desk_preview), contentDescription = description, modifier = modifier)
        return
    }

    val elapsedMillis = remember(scene) { mutableLongStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()

    LaunchedEffect(lifecycle, motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val previousElapsed = elapsedMillis.longValue
            val startedAt = SystemClock.uptimeMillis()
            while (isActive) {
                val elapsed = previousElapsed + SystemClock.uptimeMillis() - startedAt
                elapsedMillis.longValue = elapsed / FRAME_MILLIS * FRAME_MILLIS
                delay(FRAME_MILLIS - elapsed % FRAME_MILLIS)
            }
        }
    }

    Canvas(modifier.semantics {
        contentDescription = description
        role = Role.Image
    }) {
        drawImage(scene.frameAt(elapsedMillis.longValue, motionEnabled),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

@Suppress("DEPRECATION") // Movie decodes the same local GIF on all supported Android versions.
internal class DeskMascotScene private constructor(private val movie: Movie) {
    private val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
    private val bitmapCanvas = BitmapCanvas(bitmap)
    private val image = bitmap.asImageBitmap()
    private val duration = movie.duration()
    private var lastTime = -1

    fun frameAt(elapsed: Long, motionEnabled: Boolean): ImageBitmap {
        val time = when {
            !motionEnabled -> CALM_START_MILLIS
            elapsed < duration -> elapsed.toInt()
            else -> CALM_START_MILLIS + ((elapsed - duration) % (duration - CALM_START_MILLIS)).toInt()
        }
        if (lastTime != time) {
            movie.setTime(time)
            bitmap.eraseColor(Color.TRANSPARENT)
            movie.draw(bitmapCanvas, 0f, 0f)
            lastTime = time
        }
        return image
    }

    companion object {
        fun decode(stream: InputStream): DeskMascotScene? =
            Movie.decodeStream(stream)?.takeIf { it.duration() > CALM_START_MILLIS }?.let(::DeskMascotScene)
    }
}
