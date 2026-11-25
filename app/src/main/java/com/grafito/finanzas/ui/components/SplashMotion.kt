package com.grafito.finanzas.ui.components

import kotlin.math.abs
import kotlin.math.roundToInt

/** Same 600 Hz simulation and 64 CSS linear() intervals as the Figma Make export. */
internal class SplashSpring(stiffness: Float, damping: Float) {
    val duration: Int
    private val stops: List<Float>
    init {
        val samples = mutableListOf<Float>()
        var x = 0.0; var velocity = 0.0; var time = 0.0; var settled = 0.0
        val dt = 1.0 / 600
        while (time < 4) {
            velocity += (-stiffness * (x - 1) - damping * velocity) * dt
            x += velocity * dt; time += dt; samples += x.toFloat()
            if (abs(1 - x) < .0015 && abs(velocity) < .02) {
                settled += dt
                if (settled > .05) break
            } else settled = 0.0
        }
        duration = (time * 1000).roundToInt()
        stops = listOf(0f) + (1 until 64).map {
            (samples[((it / 64.0) * (samples.size - 1)).roundToInt()] * 1000).roundToInt() / 1000f
        } + 1f
    }
    fun value(elapsed: Float): Float {
        val position = (elapsed / duration).coerceIn(0f, 1f) * 64
        val index = position.toInt().coerceAtMost(63)
        return stops[index] + (stops[index + 1] - stops[index]) * (position - index)
    }
}
