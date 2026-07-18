package com.note.handwrite.model

import androidx.compose.ui.graphics.Color
import com.note.handwrite.ui.theme.PenBlack
import com.note.handwrite.ui.theme.PenGreen
import com.note.handwrite.ui.theme.PenRed

val DefaultColorSlots = listOf(PenBlack, PenRed, PenGreen)

data class NoteSettings(
    val inputMode: InputMode = InputMode.SPEN,
    val tool: Tool = Tool.PEN,
    val colorSlots: List<Color> = DefaultColorSlots,
    val activeColorSlot: Int = 0,
    val widthStep: Int = 50,
    val background: BackgroundType = BackgroundType.PLAIN
) {
    val color: Color
        get() = colorSlots.getOrElse(activeColorSlot) { PenBlack }
}
