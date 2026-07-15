package com.note.handwrite.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.BackgroundType

fun DrawScope.drawBackground(type: BackgroundType) {
    drawRect(color = Color.White)
    val spacing = 40.dp.toPx()
    val lineColor = Color(0xFFE0E0E0)

    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += spacing
            }
        }
        BackgroundType.GRID -> {
            var x = spacing
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                x += spacing
            }
            var y = spacing
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += spacing
            }
        }
    }
}
