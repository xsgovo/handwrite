package com.note.handwrite.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.model.NotePage

fun DrawScope.drawBackground(type: BackgroundType, transform: CanvasTransform) {
    val pageStart = transform.map(CanvasPoint(0f, 0f))
    val pageEnd = transform.map(CanvasPoint(NotePage.WIDTH, NotePage.HEIGHT))
    drawRect(color = Color(0xFFF2F2F2))
    drawRect(
        color = Color.White,
        topLeft = Offset(pageStart.x, pageStart.y),
        size = androidx.compose.ui.geometry.Size(pageEnd.x - pageStart.x, pageEnd.y - pageStart.y)
    )
    val lineColor = Color(0xFFE0E0E0)
    val lineWidth = NotePage.BACKGROUND_LINE_WIDTH_MM * NotePage.LOGICAL_UNITS_PER_MM * transform.scale
    val logicalWidth = NotePage.WIDTH
    val logicalHeight = NotePage.HEIGHT

    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = NotePage.LINE_SPACING
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth, y))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                y += NotePage.LINE_SPACING
            }
        }
        BackgroundType.GRID -> {
            var x = NotePage.GRID_SPACING
            while (x < logicalWidth) {
                val start = transform.map(CanvasPoint(x, 0f))
                val end = transform.map(CanvasPoint(x, logicalHeight))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                x += NotePage.GRID_SPACING
            }
            var y = NotePage.GRID_SPACING
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth, y))
                drawLine(lineColor, Offset(start.x, start.y), Offset(end.x, end.y), lineWidth)
                y += NotePage.GRID_SPACING
            }
        }
    }
}
