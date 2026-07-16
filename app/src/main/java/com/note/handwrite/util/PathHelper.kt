package com.note.handwrite.util

import androidx.compose.ui.graphics.Path
import com.note.handwrite.model.CanvasPoint

fun buildSmoothPath(
    points: List<CanvasPoint>
): Path {
    if (points.isEmpty()) return Path()

    val path = Path()
    val first = points.first()
    path.moveTo(first.x, first.y)

    if (points.size == 1) return path

    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        path.quadraticTo(
            current.x,
            current.y,
            (current.x + next.x) / 2f,
            (current.y + next.y) / 2f
        )
    }

    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}
