package com.xsgovo.handwrite.core.model

import kotlin.math.roundToInt

object LogicalCanvas {
    const val LONG_EDGE = 65_535
}

object PagePattern {
    const val LOGICAL_SPACING = LogicalCanvas.LONG_EDGE / 25
}

data class LogicalPoint(
    val x: Int,
    val y: Int,
)

data class LogicalSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width in 1..LogicalCanvas.LONG_EDGE)
        require(height in 1..LogicalCanvas.LONG_EDGE)
        require(maxOf(width, height) == LogicalCanvas.LONG_EDGE)
    }

    fun contains(point: LogicalPoint): Boolean =
        point.x in 0..width && point.y in 0..height
}

data class LogicalRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left <= right)
        require(top <= bottom)
    }
}

enum class PageTemplate(
    val size: LogicalSize,
) {
    LEGACY_PORTRAIT(sizeForRatio(width = 1_000, height = 1_414)),
    THREE_BY_FOUR(sizeForRatio(width = 3, height = 4)),
    FOUR_BY_THREE(sizeForRatio(width = 4, height = 3)),
    SQUARE(sizeForRatio(width = 1, height = 1)),
    NINE_BY_SIXTEEN(sizeForRatio(width = 9, height = 16)),
}

private fun sizeForRatio(width: Int, height: Int): LogicalSize {
    require(width > 0 && height > 0)
    return if (width >= height) {
        LogicalSize(
            width = LogicalCanvas.LONG_EDGE,
            height = (LogicalCanvas.LONG_EDGE * height.toDouble() / width).roundToInt(),
        )
    } else {
        LogicalSize(
            width = (LogicalCanvas.LONG_EDGE * width.toDouble() / height).roundToInt(),
            height = LogicalCanvas.LONG_EDGE,
        )
    }
}
