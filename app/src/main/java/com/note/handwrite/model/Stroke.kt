package com.note.handwrite.model

import androidx.compose.ui.graphics.Color

data class Stroke(
    val points: List<CanvasPoint>,
    val color: Color,
    val width: Float,
    val isEraser: Boolean = false,
    /** Indices that start a new visible segment of the same pen gesture. */
    val breakIndices: Set<Int> = emptySet()
) {
    fun paths(): List<List<CanvasPoint>> = points.foldIndexed(mutableListOf<MutableList<CanvasPoint>>()) { index, paths, point ->
        if (paths.isEmpty() || index in breakIndices) paths.add(mutableListOf())
        paths.last() += point
        paths
    }
}
