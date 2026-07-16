package com.note.handwrite.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.CanvasPoint

fun DrawScope.drawBackground(type: BackgroundType, transform: CanvasTransform) {
    drawRect(color = Color.White)
    val spacing = 40.dp.toPx()
    val lineColor = Color(0xFFE0E0E0)
    val lineWidth = 1.dp.toPx() * transform.scale
    val logicalWidth = transform.sourceWidth
    val logicalHeight = transform.sourceHeight

    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = spacing
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth, y))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                y += spacing
            }
        }
        BackgroundType.GRID -> {
            var x = spacing
            while (x < logicalWidth) {
                val start = transform.map(CanvasPoint(x, 0f))
                val end = transform.map(CanvasPoint(x, logicalHeight))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                x += spacing
            }
            var y = spacing
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth, y))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                y += spacing
            }
        }
    }
}
