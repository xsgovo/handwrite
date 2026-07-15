package com.note.handwrite.model

import androidx.compose.ui.graphics.Color

data class Stroke(
    val points: List<NormalizedPoint>,
    val color: Color,
    val width: Float,
    val isEraser: Boolean = false
)
