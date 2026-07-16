package com.note.handwrite.util

import com.note.handwrite.model.CanvasPoint
import kotlin.math.min

/** Maps points between the original logical canvas and the current viewport. */
data class CanvasTransform(
    val sourceWidth: Float,
    val sourceHeight: Float,
    val targetWidth: Float,
    val targetHeight: Float
) {
    private val rotation: Int = when {
        sourceWidth < sourceHeight && targetWidth > targetHeight -> 90
        sourceWidth > sourceHeight && targetWidth < targetHeight -> -90
        else -> 0
    }

    private val rotatedWidth = if (rotation == 0) sourceWidth else sourceHeight
    private val rotatedHeight = if (rotation == 0) sourceHeight else sourceWidth
    val scale: Float = if (rotatedWidth > 0f && rotatedHeight > 0f) {
        min(targetWidth / rotatedWidth, targetHeight / rotatedHeight)
    } else {
        1f
    }
    private val offsetX = (targetWidth - rotatedWidth * scale) / 2f
    private val offsetY = (targetHeight - rotatedHeight * scale) / 2f

    fun map(point: CanvasPoint): CanvasPoint {
        val rotated = rotate(point)
        return CanvasPoint(
            offsetX + rotated.x * scale,
            offsetY + rotated.y * scale
        )
    }

    fun inverse(point: CanvasPoint): CanvasPoint {
        val rotated = CanvasPoint(
            (point.x - offsetX) / scale,
            (point.y - offsetY) / scale
        )
        val source = when (rotation) {
            90 -> CanvasPoint(rotated.y, sourceHeight - rotated.x)
            -90 -> CanvasPoint(sourceWidth - rotated.y, rotated.x)
            else -> rotated
        }
        return CanvasPoint(
            source.x.coerceIn(0f, sourceWidth),
            source.y.coerceIn(0f, sourceHeight)
        )
    }

    private fun rotate(point: CanvasPoint): CanvasPoint = when (rotation) {
        90 -> CanvasPoint(sourceHeight - point.y, point.x)
        -90 -> CanvasPoint(point.y, sourceWidth - point.x)
        else -> point
    }
}
