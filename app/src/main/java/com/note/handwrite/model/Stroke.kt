package com.note.handwrite.model

import androidx.compose.ui.graphics.Color

data class Stroke(
    val points: List<CanvasPoint>,
    val color: Color,
    val width: Float,
    val isEraser: Boolean = false
)
