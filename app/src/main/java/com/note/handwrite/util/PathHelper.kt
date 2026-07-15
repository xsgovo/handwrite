package com.note.handwrite.util

import androidx.compose.ui.graphics.Path
import com.note.handwrite.model.NormalizedPoint

fun buildSmoothPath(
    points: List<NormalizedPoint>,
    canvasWidth: Float,
    canvasHeight: Float
): Path {
    if (points.isEmpty()) return Path()

    val path = Path()
    val first = points.first()
    path.moveTo(first.x * canvasWidth, first.y * canvasHeight)

    if (points.size == 1) return path

    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        path.quadraticTo(
            current.x * canvasWidth,
            current.y * canvasHeight,
            (current.x + next.x) / 2f * canvasWidth,
            (current.y + next.y) / 2f * canvasHeight
        )
    }

    val last = points.last()
    path.lineTo(last.x * canvasWidth, last.y * canvasHeight)
    return path
}

fun NormalizedPoint.toCanvasPoint(canvasWidth: Float, canvasHeight: Float): Pair<Float, Float> =
    x * canvasWidth to y * canvasHeight
