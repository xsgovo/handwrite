package com.note.handwrite.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NormalizedPoint
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.util.buildSmoothPath
import com.note.handwrite.util.drawBackground
import com.note.handwrite.util.findHitStroke

@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    currentColor: Color,
    currentWidth: Float,
    currentTool: Tool,
    backgroundType: BackgroundType,
    onStrokeComplete: (Stroke) -> Unit,
    onStrokeTapped: (Stroke) -> Unit,
    onCanvasSizeChanged: (Size) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val latestColor by rememberUpdatedState(currentColor)
    val latestWidth by rememberUpdatedState(currentWidth)
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnStrokeComplete by rememberUpdatedState(onStrokeComplete)
    val latestOnStrokeTapped by rememberUpdatedState(onStrokeTapped)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { onCanvasSizeChanged(Size(it.width.toFloat(), it.height.toFloat())) }
            .pointerInput(currentTool) {
                if (currentTool == Tool.PEN) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset.toNormalized(size.width, size.height))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints.add(change.position.toNormalized(size.width, size.height))
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                latestOnStrokeComplete(
                                    Stroke(currentPoints.toList(), latestColor, latestWidth)
                                )
                                currentPoints.clear()
                            }
                        },
                        onDragCancel = { currentPoints.clear() }
                    )
                } else {
                    detectTapGestures { offset ->
                        val point = offset.toNormalized(size.width, size.height)
                        findHitStroke(latestStrokes, point.x, point.y)?.let(latestOnStrokeTapped)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackground(backgroundType)
            strokes.forEach { stroke ->
                drawStroke(stroke.points, stroke.color, stroke.width)
            }
            if (currentPoints.isNotEmpty()) {
                drawStroke(currentPoints, currentColor, currentWidth)
            }
        }
    }
}

private fun Offset.toNormalized(width: Int, height: Int): NormalizedPoint = NormalizedPoint(
    (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
    (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<NormalizedPoint>,
    color: Color,
    width: Float
) {
    val path = buildSmoothPath(points, size.width, size.height)
    drawPath(
        path = path,
        color = color,
        style = DrawStroke(
            width = width * density,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    if (points.size == 1) {
        val point = points.first()
        drawCircle(
            color = color,
            radius = width * density / 2f,
            center = Offset(point.x * size.width, point.y * size.height)
        )
    }
}
