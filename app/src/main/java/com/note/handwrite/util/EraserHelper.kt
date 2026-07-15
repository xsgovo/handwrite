package com.note.handwrite.util

import com.note.handwrite.model.Stroke
import kotlin.math.hypot

fun findHitStrokes(
    strokes: List<Stroke>,
    touchX: Float,
    touchY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    tolerancePx: Float
): List<Stroke> {
    return strokes.filter { stroke ->
        for (point in stroke.points) {
            val pointX = point.x * canvasWidth
            val pointY = point.y * canvasHeight
            val distance = hypot(pointX - touchX, pointY - touchY)
            if (distance <= tolerancePx + stroke.width / 2f) return@filter true
        }
        false
    }
}
