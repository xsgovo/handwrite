package com.note.handwrite.util

import com.note.handwrite.model.Stroke
import kotlin.math.hypot

fun findHitStrokes(
    strokes: List<Stroke>,
    touchX: Float,
    touchY: Float,
    tolerancePx: Float
): List<Stroke> {
    return strokes.filter { stroke ->
        for (point in stroke.points) {
            val distance = hypot(point.x - touchX, point.y - touchY)
            if (distance <= tolerancePx + stroke.width / 2f) return@filter true
        }
        false
    }
}
