package com.note.handwrite.util

import com.note.handwrite.model.CanvasPoint
import kotlin.math.max

/** Maps the fixed portrait document into the current viewport. */
data class CanvasTransform(
    val sourceWidth: Float,
    val sourceHeight: Float,
    val targetWidth: Float,
    val targetHeight: Float,
    val rotation: Int = 0,
    val zoomPercent: Float = 100f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    private val rotatedWidth = if (rotation == 1 || rotation == 3) sourceHeight else sourceWidth
    private val rotatedHeight = if (rotation == 1 || rotation == 3) sourceWidth else sourceHeight
    private val baseScale = if (rotatedWidth > 0f) targetWidth / rotatedWidth else 1f

    val scale: Float = baseScale * (zoomPercent.coerceAtLeast(100f) / 100f)
    private val centeredOffsetX = (targetWidth - rotatedWidth * scale) / 2f
    private val centeredOffsetY = (targetHeight - rotatedHeight * scale) / 2f
    private val resolvedPan = clampPan(panX, panY)
    private val offsetX = centeredOffsetX + resolvedPan.first
    private val offsetY = centeredOffsetY + resolvedPan.second

    fun map(point: CanvasPoint): CanvasPoint {
        val rotated = rotate(point)
        return CanvasPoint(offsetX + rotated.x * scale, offsetY + rotated.y * scale)
    }

    fun inverse(point: CanvasPoint): CanvasPoint {
        val rotated = CanvasPoint(
            (point.x - offsetX) / scale,
            (point.y - offsetY) / scale
        )
        val source = when (rotation) {
            1 -> CanvasPoint(rotated.y, sourceHeight - rotated.x)
            2 -> CanvasPoint(sourceWidth - rotated.x, sourceHeight - rotated.y)
            3 -> CanvasPoint(sourceWidth - rotated.y, rotated.x)
            else -> rotated
        }
        return CanvasPoint(
            source.x.coerceIn(0f, sourceWidth),
            source.y.coerceIn(0f, sourceHeight)
        )
    }

    fun clampPan(x: Float, y: Float): Pair<Float, Float> {
        if (zoomPercent <= 100f) return 0f to 0f
        val maxX = max(0f, (rotatedWidth * scale - targetWidth) / 2f)
        val maxY = max(0f, (rotatedHeight * scale - targetHeight) / 2f)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
    }

    private fun rotate(point: CanvasPoint): CanvasPoint = when (rotation) {
        1 -> CanvasPoint(sourceHeight - point.y, point.x)
        2 -> CanvasPoint(sourceWidth - point.x, sourceHeight - point.y)
        3 -> CanvasPoint(point.y, sourceWidth - point.x)
        else -> point
    }
}
