package com.note.handwrite.ui.components

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NormalizedPoint
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.util.buildSmoothPath
import com.note.handwrite.util.drawBackground
import com.note.handwrite.util.findHitStrokes

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    currentColor: Color,
    currentWidth: Float,
    currentTool: Tool,
    backgroundType: BackgroundType,
    useSpenMode: Boolean,
    onStrokeComplete: (Stroke) -> Unit,
    onEraseStart: () -> Unit,
    onEraseEnd: () -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit,
    onCanvasSizeChanged: (Size) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val erasedDuringGesture = remember { mutableStateListOf<Stroke>() }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestColor by rememberUpdatedState(currentColor)
    val latestWidth by rememberUpdatedState(currentWidth)
    val latestTool by rememberUpdatedState(currentTool)
    val latestSpenMode by rememberUpdatedState(useSpenMode)
    val latestOnStrokeComplete by rememberUpdatedState(onStrokeComplete)
    val latestOnEraseStart by rememberUpdatedState(onEraseStart)
    val latestOnEraseEnd by rememberUpdatedState(onEraseEnd)
    val latestOnStrokesErased by rememberUpdatedState(onStrokesErased)
    val latestOnTemporaryEraserChanged by rememberUpdatedState(onTemporaryEraserChanged)
    val input = remember { DrawingInputState() }
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                onCanvasSizeChanged(canvasSize)
            }
            .pointerInteropFilter { event ->
                handleMotionEvent(
                    event = event,
                    state = input,
                    strokes = latestStrokes,
                    currentPoints = currentPoints,
                    erasedDuringGesture = erasedDuringGesture,
                    currentTool = latestTool,
                    useSpenMode = latestSpenMode,
                    currentColor = latestColor,
                    currentWidth = latestWidth,
                    canvasWidth = canvasSize.width,
                    canvasHeight = canvasSize.height,
                    density = density,
                    onStrokeComplete = latestOnStrokeComplete,
                    onEraseStart = latestOnEraseStart,
                    onEraseEnd = latestOnEraseEnd,
                    onStrokesErased = latestOnStrokesErased,
                    onTemporaryEraserChanged = latestOnTemporaryEraserChanged
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackground(backgroundType)
            strokes.filterNot { erasedDuringGesture.contains(it) }.forEach { stroke ->
                drawStroke(stroke.points, stroke.color, stroke.width)
            }
            if (currentPoints.isNotEmpty() && !(input.temporaryEraser || currentTool == Tool.ERASER)) {
                drawStroke(currentPoints, currentColor, currentWidth)
            }
        }
    }
}

private class DrawingInputState {
    var activePointerId = MotionEvent.INVALID_POINTER_ID
    var pointerDown = false
    var temporaryEraser = false
    var originalTool = Tool.PEN
}

private fun handleMotionEvent(
    event: MotionEvent,
    state: DrawingInputState,
    strokes: List<Stroke>,
    currentPoints: MutableList<NormalizedPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    currentTool: Tool,
    useSpenMode: Boolean,
    currentColor: Color,
    currentWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
    onStrokeComplete: (Stroke) -> Unit,
    onEraseStart: () -> Unit,
    onEraseEnd: () -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit
): Boolean {
    val isStylus = event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_STYLUS ||
        event.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
    val acceptsInput = !useSpenMode || isStylus
    val isPrimaryButton = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

    if (isStylus && isPrimaryButton && !state.pointerDown && event.actionMasked != MotionEvent.ACTION_UP) {
        if (!state.temporaryEraser) {
            state.originalTool = currentTool
            state.temporaryEraser = true
            onTemporaryEraserChanged(true)
        }
    }

    if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE &&
        isStylus && event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY && !state.pointerDown
    ) {
        state.temporaryEraser = false
        onTemporaryEraserChanged(false)
    }

    if (!acceptsInput) return false

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.pointerDown = true
            state.activePointerId = event.getPointerId(event.actionIndex)
            currentPoints.clear()
            erasedDuringGesture.clear()
            if (state.temporaryEraser || currentTool == Tool.ERASER) onEraseStart()
            addPoint(event.x, event.y, canvasWidth, canvasHeight, currentPoints)
        }

        MotionEvent.ACTION_MOVE -> {
            if (!state.pointerDown) return true
            val pointerIndex = event.findPointerIndex(state.activePointerId)
            if (pointerIndex < 0) return true
            repeat(event.historySize) { historyIndex ->
                processPoint(
                    event.getHistoricalX(pointerIndex, historyIndex),
                    event.getHistoricalY(pointerIndex, historyIndex),
                    event,
                    pointerIndex,
                    state,
                    strokes,
                    currentTool,
                    currentColor,
                    currentWidth,
                    canvasWidth,
                    canvasHeight,
                    density,
                    currentPoints,
                    erasedDuringGesture,
                    onStrokesErased
                )
            }
            processPoint(
                event.getX(pointerIndex),
                event.getY(pointerIndex),
                event,
                pointerIndex,
                state,
                strokes,
                currentTool,
                currentColor,
                currentWidth,
                canvasWidth,
                canvasHeight,
                density,
                currentPoints,
                erasedDuringGesture,
                onStrokesErased
            )
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (state.pointerDown) {
                val erasing = state.temporaryEraser || currentTool == Tool.ERASER
                if (erasing) {
                    onEraseEnd()
                } else if (currentPoints.isNotEmpty()) {
                    onStrokeComplete(Stroke(currentPoints.toList(), currentColor, currentWidth))
                }
            }
            // The caller replaces the temporary color/width after this helper returns.
            currentPoints.clear()
            erasedDuringGesture.clear()
            state.pointerDown = false
            state.activePointerId = MotionEvent.INVALID_POINTER_ID
            if (state.temporaryEraser) {
                state.temporaryEraser = false
                onTemporaryEraserChanged(false)
            }
        }
    }
    return true
}

private fun processPoint(
    x: Float,
    y: Float,
    event: MotionEvent,
    pointerIndex: Int,
    state: DrawingInputState,
    strokes: List<Stroke>,
    currentTool: Tool,
    currentColor: Color,
    currentWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
    currentPoints: MutableList<NormalizedPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    onStrokesErased: (List<Stroke>) -> Unit
) {
    val erasing = state.temporaryEraser || currentTool == Tool.ERASER
    if (erasing) {
        val tolerance = 24f * density
        findHitStrokes(
            strokes,
            x,
            y,
            canvasWidth.coerceAtLeast(1f),
            canvasHeight.coerceAtLeast(1f),
            tolerance
        ).forEach { stroke ->
            if (!erasedDuringGesture.contains(stroke)) {
                erasedDuringGesture += stroke
                onStrokesErased(listOf(stroke))
            }
        }
    } else {
        val point = NormalizedPoint(
            (x / canvasWidth.coerceAtLeast(1f)).coerceIn(0f, 1f),
            (y / canvasHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
        )
        val previous = currentPoints.lastOrNull()
        if (previous == null || previous != point) currentPoints += point
    }
}

private fun addPoint(
    x: Float,
    y: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    currentPoints: MutableList<NormalizedPoint>
) {
    val width = canvasWidth.coerceAtLeast(1f)
    val height = canvasHeight.coerceAtLeast(1f)
    currentPoints += NormalizedPoint((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<NormalizedPoint>,
    color: Color,
    width: Float
) {
    val path = buildSmoothPath(points, size.width, size.height)
    drawPath(
        path = path,
        color = color,
        style = DrawStroke(width * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    if (points.size == 1) {
        val point = points.first()
        drawCircle(color, width * density / 2f, Offset(point.x * size.width, point.y * size.height))
    }
}
