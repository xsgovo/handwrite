package com.note.handwrite.model

import androidx.compose.ui.graphics.Color
import com.note.handwrite.ui.theme.PenBlack

data class NoteSettings(
    val inputMode: InputMode = InputMode.SPEN,
    val tool: Tool = Tool.PEN,
    val color: Color = PenBlack,
    val width: Float = 8f,
    val background: BackgroundType = BackgroundType.PLAIN
)
