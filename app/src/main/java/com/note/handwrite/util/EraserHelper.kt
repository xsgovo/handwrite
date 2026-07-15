package com.note.handwrite.util

import com.note.handwrite.model.Stroke
import kotlin.math.hypot

fun findHitStroke(
    strokes: List<Stroke>,
    touchX: Float,
    touchY: Float,
    tolerance: Float = 0.02f
): Stroke? {
    for (stroke in strokes.asReversed()) {
        for (point in stroke.points) {
            val distance = hypot(point.x - touchX, point.y - touchY)
            val strokeTolerance = tolerance + stroke.width / 1000f
            if (distance <= strokeTolerance) return stroke
        }
    }
    return null
}
