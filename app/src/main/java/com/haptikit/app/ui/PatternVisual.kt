package com.haptikit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Draws a pattern as a timeline: each vibrating segment is a bar whose
 * width is its duration and height its strength; silences are gaps.
 */
@Composable
fun PatternVisual(timings: List<Long>, amplitudes: List<Int>, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = timings.sum().coerceAtLeast(1L).toFloat()
        val radius = CornerRadius(3f, 3f)
        drawRoundRect(track, topLeft = Offset(0f, size.height / 2 - 1f), size = Size(size.width, 2f))
        var x = 0f
        timings.forEachIndexed { i, t ->
            val w = size.width * t / total
            val amp = amplitudes.getOrElse(i) { 0 }
            if (amp > 0) {
                val h = size.height * (0.25f + 0.75f * amp / 255f)
                drawRoundRect(color, topLeft = Offset(x, (size.height - h) / 2), size = Size(w.coerceAtLeast(3f), h), cornerRadius = radius)
            }
            x += w
        }
    }
}
