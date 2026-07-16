package com.note.handwrite.util

import com.note.handwrite.model.Stroke
import com.note.handwrite.model.CanvasPoint
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

fun findHitStrokesInViewport(
    strokes: List<Stroke>,
    touchX: Float,
    touchY: Float,
    tolerancePx: Float,
    mapPoint: (CanvasPoint) -> CanvasPoint,
    widthScale: Float
): List<Stroke> = strokes.filter { stroke ->
    stroke.points.any { point ->
        val mapped = mapPoint(point)
        val distance = hypot(mapped.x - touchX, mapped.y - touchY)
        distance <= tolerancePx + stroke.width * widthScale / 2f
    }
}
