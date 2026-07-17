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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.clipRect
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.model.NotePage
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.util.CanvasTransform
import com.note.handwrite.util.buildSmoothPath
import com.note.handwrite.util.drawBackground
import com.note.handwrite.util.findHitStrokesInViewport
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    currentColor: Color,
    currentWidth: Float,
    currentTool: Tool,
    backgroundType: BackgroundType,
    zoomPercent: Float,
    pan: Offset,
    topAligned: Boolean,
    useSpenMode: Boolean,
    onViewportChanged: (zoomPercent: Float, pan: Offset) -> Unit,
    onStrokeComplete: (Stroke) -> Unit,
    onEraseStart: () -> Unit,
    onEraseEnd: () -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    onCanvasSizeChanged: (Size) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<CanvasPoint>() }
    val erasedDuringGesture = remember { mutableStateListOf<Stroke>() }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestColor by rememberUpdatedState(currentColor)
    val latestWidth by rememberUpdatedState(currentWidth)
    val latestTool by rememberUpdatedState(currentTool)
    val latestSpenMode by rememberUpdatedState(useSpenMode)
    val latestViewportChanged by rememberUpdatedState(onViewportChanged)
    val latestOnStrokeComplete by rememberUpdatedState(onStrokeComplete)
    val latestOnEraseStart by rememberUpdatedState(onEraseStart)
    val latestOnEraseEnd by rememberUpdatedState(onEraseEnd)
    val latestOnStrokesErased by rememberUpdatedState(onStrokesErased)
    val latestOnTemporaryEraserChanged by rememberUpdatedState(onTemporaryEraserChanged)
    val latestOnGestureActiveChanged by rememberUpdatedState(onGestureActiveChanged)
    val input = remember { DrawingInputState() }
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val transform = CanvasTransform(
        sourceWidth = NotePage.WIDTH,
        sourceHeight = NotePage.HEIGHT,
        targetWidth = canvasSize.width,
        targetHeight = canvasSize.height,
        zoomPercent = zoomPercent,
        panX = pan.x,
        panY = pan.y,
        topAligned = topAligned
    )

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
                    transform = transform,
                    density = density,
                    onViewportChanged = latestViewportChanged,
                    onStrokeComplete = latestOnStrokeComplete,
                    onEraseStart = latestOnEraseStart,
                    onEraseEnd = latestOnEraseEnd,
                    onStrokesErased = latestOnStrokesErased,
                    onTemporaryEraserChanged = latestOnTemporaryEraserChanged,
                    onGestureActiveChanged = latestOnGestureActiveChanged
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackground(backgroundType, transform)
            val pageStart = transform.map(CanvasPoint(0f, 0f))
            val pageEnd = transform.map(CanvasPoint(NotePage.WIDTH, NotePage.HEIGHT))
            clipRect(pageStart.x, pageStart.y, pageEnd.x, pageEnd.y) {
                strokes.filterNot { erasedDuringGesture.contains(it) }.forEach { stroke ->
                    drawStroke(stroke, transform)
                }
                if (input.gesture == Gesture.DRAW && currentPoints.isNotEmpty() &&
                    !input.isErasing(currentTool)
                ) {
                    drawStroke(
                        Stroke(currentPoints, currentColor, currentWidth, breakIndices = input.segmentBreaks),
                        transform
                    )
                }
            }
        }
    }
}

private enum class Gesture { NONE, DRAW, PAN, TRANSFORM }

private class DrawingInputState {
    val pointers = linkedMapOf<Int, Offset>()
    val pointerTools = linkedMapOf<Int, Int>()
    // Compose must observe this so an in-progress first stroke is rendered immediately.
    var gesture by mutableStateOf(Gesture.NONE)
    var drawPointerId = MotionEvent.INVALID_POINTER_ID
    var temporaryEraser = false
    var stylusButtonDown = false
    var initialZoom = 100f
    var initialPan = Offset.Zero
    var initialMidpoint = Offset.Zero
    var initialDistance = 0f
    var focalPoint = CanvasPoint(0f, 0f)
    var lastDrawPoint: CanvasPoint? = null
    var startsNewSegment = false
    val segmentBreaks = mutableSetOf<Int>()

    fun isErasing(currentTool: Tool): Boolean = temporaryEraser || currentTool == Tool.ERASER
    fun hasStylus(): Boolean = pointerTools.values.any { it == MotionEvent.TOOL_TYPE_STYLUS }
}

private fun handleMotionEvent(
    event: MotionEvent,
    state: DrawingInputState,
    strokes: List<Stroke>,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    currentTool: Tool,
    useSpenMode: Boolean,
    currentColor: Color,
    currentWidth: Float,
    transform: CanvasTransform,
    density: Float,
    onViewportChanged: (Float, Offset) -> Unit,
    onStrokeComplete: (Stroke) -> Unit,
    onEraseStart: () -> Unit,
    onEraseEnd: () -> Unit,
    onStrokesErased: (List<Stroke>) -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit
): Boolean {
    val actionIndex = event.actionIndex.coerceIn(0, (event.pointerCount - 1).coerceAtLeast(0))
    val actionTool = event.getToolType(actionIndex)
    val isStylusAction = actionTool == MotionEvent.TOOL_TYPE_STYLUS ||
        event.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
    val buttonPressed = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
        event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY
    val buttonReleased = event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE &&
        event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY
    val buttonDown = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
    val wasButtonDown = state.stylusButtonDown

    if (isStylusAction && state.pointers.isEmpty() &&
        (buttonPressed || (!wasButtonDown && buttonDown))
    ) {
        state.temporaryEraser = true
        onTemporaryEraserChanged(true)
    }
    if (isStylusAction && state.pointers.isEmpty() &&
        (buttonReleased || (wasButtonDown && !buttonDown))
    ) {
        state.temporaryEraser = false
        onTemporaryEraserChanged(false)
    }
    state.stylusButtonDown = when {
        buttonReleased -> false
        isStylusAction -> buttonDown
        else -> state.stylusButtonDown
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            onGestureActiveChanged(true)
            state.pointers.clear()
            state.pointerTools.clear()
            rememberPointer(event, 0, state)
            if (useSpenMode && actionTool != MotionEvent.TOOL_TYPE_STYLUS) {
                state.gesture = Gesture.PAN
                state.initialMidpoint = Offset(event.x, event.y)
            } else {
                startDrawing(state, currentPoints, erasedDuringGesture, currentTool, transform,
                    event.x, event.y, onEraseStart)
            }
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            rememberAllPointers(event, state)
            if (!useSpenMode && event.pointerCount >= 2) {
                // Preserve the active finger stroke before this gesture becomes a viewport transform.
                if (state.gesture == Gesture.DRAW) {
                    finishDrawing(
                        state, currentTool, currentColor, currentWidth, currentPoints,
                        erasedDuringGesture, onStrokeComplete, onEraseEnd, onTemporaryEraserChanged
                    )
                    rememberAllPointers(event, state)
                }
                startTransform(state, transform)
                state.gesture = Gesture.TRANSFORM
                state.initialZoom = transform.zoomPercent
                state.initialPan = Offset(transform.panX, transform.panY)
                state.initialMidpoint = midpoint(state.pointers.values.toList())
                state.initialDistance = distance(state.pointers.values.toList())
                state.focalPoint = transform.inverse(state.initialMidpoint.toCanvasPoint())
            } else if (state.hasStylus()) {
                val stylusId = state.pointerTools.entries.first { it.value == MotionEvent.TOOL_TYPE_STYLUS }.key
                if (state.gesture != Gesture.DRAW || state.drawPointerId != stylusId) {
                    cancelGesture(state, currentPoints, erasedDuringGesture, currentTool, onEraseEnd)
                    state.gesture = Gesture.DRAW
                    state.drawPointerId = stylusId
                    val index = event.findPointerIndex(stylusId)
                    if (index >= 0) startDrawSegment(state, event.getX(index), event.getY(index), transform, currentPoints)
                    if (state.isErasing(currentTool)) onEraseStart()
                }
            } else if (event.pointerCount >= 2) {
                startTransform(state, transform)
                cancelGesture(state, currentPoints, erasedDuringGesture, currentTool, onEraseEnd)
                state.gesture = Gesture.TRANSFORM
                state.initialZoom = transform.zoomPercent
                state.initialPan = Offset(transform.panX, transform.panY)
                state.initialMidpoint = midpoint(state.pointers.values.toList())
                state.initialDistance = distance(state.pointers.values.toList())
                state.focalPoint = transform.inverse(state.initialMidpoint.toCanvasPoint())
            }
        }

        MotionEvent.ACTION_MOVE -> {
            rememberAllPointers(event, state)
            when (state.gesture) {
                Gesture.DRAW -> processDrawingMove(
                    event, state, strokes, currentTool, currentColor, currentWidth,
                    transform, density, currentPoints, erasedDuringGesture, onStrokesErased
                )
                Gesture.PAN -> {
                    if (state.pointers.size == 1) {
                        val current = state.pointers.values.first()
                        val previous = state.initialMidpoint
                        val nextPan = Offset(
                            transform.panX + current.x - previous.x,
                            transform.panY + current.y - previous.y
                        )
                        state.initialMidpoint = current
                        onViewportChanged(transform.zoomPercent, transform.clampPan(nextPan.x, nextPan.y).toOffset())
                    }
                }
                Gesture.TRANSFORM -> updateTransform(state, transform, useSpenMode, onViewportChanged)
                Gesture.NONE -> Unit
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val leavingId = event.getPointerId(event.actionIndex)
            val leavingWasDraw = leavingId == state.drawPointerId
            state.pointers.remove(leavingId)
            state.pointerTools.remove(leavingId)
            if (leavingWasDraw) {
                finishDrawing(state, currentTool, currentColor, currentWidth, currentPoints,
                    erasedDuringGesture, onStrokeComplete, onEraseEnd, onTemporaryEraserChanged)
            } else if (state.pointers.size < 2 && state.gesture == Gesture.TRANSFORM) {
                state.gesture = Gesture.NONE
            }
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (state.gesture == Gesture.DRAW) {
                finishDrawing(state, currentTool, currentColor, currentWidth, currentPoints,
                    erasedDuringGesture, onStrokeComplete, onEraseEnd, onTemporaryEraserChanged)
            } else {
                state.gesture = Gesture.NONE
                state.pointers.clear()
                state.pointerTools.clear()
                currentPoints.clear()
                erasedDuringGesture.clear()
                state.stylusButtonDown = false
                if (state.temporaryEraser) {
                    state.temporaryEraser = false
                    onTemporaryEraserChanged(false)
                }
            }
            onGestureActiveChanged(false)
        }
    }
    return true
}

private fun startDrawing(
    state: DrawingInputState,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    currentTool: Tool,
    transform: CanvasTransform,
    x: Float,
    y: Float,
    onEraseStart: () -> Unit
) {
    state.gesture = Gesture.DRAW
    state.drawPointerId = state.pointers.keys.first()
    currentPoints.clear()
    erasedDuringGesture.clear()
    if (state.isErasing(currentTool)) onEraseStart()
    state.lastDrawPoint = null
    state.startsNewSegment = false
    state.segmentBreaks.clear()
    startDrawSegment(state, x, y, transform, currentPoints)
}

private fun startTransform(state: DrawingInputState, transform: CanvasTransform) {
    state.initialZoom = transform.zoomPercent
    state.initialPan = Offset(transform.panX, transform.panY)
}

private fun updateTransform(
    state: DrawingInputState,
    transform: CanvasTransform,
    useSpenMode: Boolean,
    onViewportChanged: (Float, Offset) -> Unit
) {
    val points = state.pointers.values.toList()
    if (points.size < 2) return
    val midpoint = midpoint(points)
    val distance = distance(points)
    val zoom = if (state.initialDistance > 0f) {
        val raw = (state.initialZoom * distance / state.initialDistance).coerceIn(100f, 400f)
        (raw / 5f).roundToInt() * 5f
    } else {
        state.initialZoom
    }
    val candidate = CanvasTransform(
        sourceWidth = transform.sourceWidth,
        sourceHeight = transform.sourceHeight,
        targetWidth = transform.targetWidth,
        targetHeight = transform.targetHeight,
        rotation = transform.rotation,
        zoomPercent = zoom,
        panX = 0f,
        panY = 0f,
        topAligned = transform.topAligned
    )
    val mappedFocal = candidate.map(state.focalPoint)
    val pan = Offset(midpoint.x - mappedFocal.x, midpoint.y - mappedFocal.y)
    val clamped = candidate.clampPan(pan.x, pan.y)
    onViewportChanged(zoom, clamped.toOffset())
}

private fun processDrawingMove(
    event: MotionEvent,
    state: DrawingInputState,
    strokes: List<Stroke>,
    currentTool: Tool,
    currentColor: Color,
    currentWidth: Float,
    transform: CanvasTransform,
    density: Float,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    onStrokesErased: (List<Stroke>) -> Unit
) {
    val pointerIndex = event.findPointerIndex(state.drawPointerId)
    if (pointerIndex < 0) return
    repeat(event.historySize) { historyIndex ->
        processPoint(event.getHistoricalX(pointerIndex, historyIndex), event.getHistoricalY(pointerIndex, historyIndex),
            state, strokes, currentTool, currentWidth, transform, density, currentPoints, erasedDuringGesture,
            onStrokesErased)
    }
    processPoint(event.getX(pointerIndex), event.getY(pointerIndex), state, strokes, currentTool, currentWidth,
        transform, density, currentPoints, erasedDuringGesture, onStrokesErased)
}

private fun processPoint(
    x: Float,
    y: Float,
    state: DrawingInputState,
    strokes: List<Stroke>,
    currentTool: Tool,
    currentWidth: Float,
    transform: CanvasTransform,
    density: Float,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    onStrokesErased: (List<Stroke>) -> Unit
) {
    val point = transform.inverseUnclamped(CanvasPoint(x, y))
    if (state.isErasing(currentTool)) {
        if (!point.isOnPage()) return
        findHitStrokesInViewport(
            strokes, x, y,
            NotePage.ERASER_HIT_RADIUS_MM * NotePage.LOGICAL_UNITS_PER_MM * transform.scale,
            transform::map, transform.scale
        )
            .forEach { stroke ->
                if (!erasedDuringGesture.contains(stroke)) {
                    erasedDuringGesture += stroke
                    onStrokesErased(listOf(stroke))
                }
            }
    } else {
        val previous = state.lastDrawPoint
        state.lastDrawPoint = point
        val clipped = previous?.clipToPage(point)
        when {
            previous == null && point.isOnPage() -> currentPoints += point
            clipped == null -> state.startsNewSegment = currentPoints.isNotEmpty()
            else -> {
                val (start, end) = clipped
                if (state.startsNewSegment) {
                    state.startsNewSegment = false
                    currentPoints += start
                    // Mark the start of a disconnected page-internal segment.
                    state.segmentBreaks += currentPoints.lastIndex
                } else if (currentPoints.lastOrNull() != start) {
                    currentPoints += start
                }
                if (currentPoints.lastOrNull() != end) currentPoints += end
            }
        }
    }
}

private fun finishDrawing(
    state: DrawingInputState,
    currentTool: Tool,
    currentColor: Color,
    currentWidth: Float,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    onStrokeComplete: (Stroke) -> Unit,
    onEraseEnd: () -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit
) {
    if (state.isErasing(currentTool)) onEraseEnd()
    else if (currentPoints.isNotEmpty()) onStrokeComplete(
        Stroke(currentPoints.toList(), currentColor, currentWidth, breakIndices = state.segmentBreaks.toSet())
    )
    state.gesture = Gesture.NONE
    state.drawPointerId = MotionEvent.INVALID_POINTER_ID
    state.lastDrawPoint = null
    state.segmentBreaks.clear()
    currentPoints.clear()
    erasedDuringGesture.clear()
    state.pointers.clear()
    state.pointerTools.clear()
    state.stylusButtonDown = false
    if (state.temporaryEraser) {
        state.temporaryEraser = false
        onTemporaryEraserChanged(false)
    }
}

private fun cancelGesture(
    state: DrawingInputState,
    currentPoints: MutableList<CanvasPoint>,
    erasedDuringGesture: MutableList<Stroke>,
    currentTool: Tool,
    onEraseEnd: () -> Unit
) {
    if (state.gesture == Gesture.DRAW && state.isErasing(currentTool)) onEraseEnd()
    state.gesture = Gesture.NONE
    state.drawPointerId = MotionEvent.INVALID_POINTER_ID
    state.lastDrawPoint = null
    state.segmentBreaks.clear()
    currentPoints.clear()
    erasedDuringGesture.clear()
}

private fun rememberPointer(event: MotionEvent, index: Int, state: DrawingInputState) {
    val id = event.getPointerId(index)
    state.pointers[id] = Offset(event.getX(index), event.getY(index))
    state.pointerTools[id] = event.getToolType(index)
}

private fun rememberAllPointers(event: MotionEvent, state: DrawingInputState) {
    repeat(event.pointerCount) { rememberPointer(event, it, state) }
}

private fun midpoint(points: List<Offset>): Offset = Offset(
    points.map { it.x }.average().toFloat(),
    points.map { it.y }.average().toFloat()
)

private fun distance(points: List<Offset>): Float = hypot(
    points[0].x - points[1].x,
    points[0].y - points[1].y
)

private fun Offset.toCanvasPoint(): CanvasPoint = CanvasPoint(x, y)

private fun Pair<Float, Float>.toOffset(): Offset = Offset(first, second)

private fun startDrawSegment(
    state: DrawingInputState,
    x: Float,
    y: Float,
    transform: CanvasTransform,
    points: MutableList<CanvasPoint>
) {
    val point = transform.inverseUnclamped(CanvasPoint(x, y))
    state.lastDrawPoint = point
    if (point.isOnPage()) points += point
}

private fun CanvasPoint.isOnPage(): Boolean =
    x in 0f..NotePage.WIDTH && y in 0f..NotePage.HEIGHT

private fun CanvasPoint.clipToPage(end: CanvasPoint): Pair<CanvasPoint, CanvasPoint>? {
    var t0 = 0f
    var t1 = 1f
    val dx = end.x - x
    val dy = end.y - y
    fun clip(p: Float, q: Float): Boolean {
        if (p == 0f) return q >= 0f
        val r = q / p
        return if (p < 0f) {
            if (r > t1) false else { if (r > t0) t0 = r; true }
        } else {
            if (r < t0) false else { if (r < t1) t1 = r; true }
        }
    }
    if (!clip(-dx, x) || !clip(dx, NotePage.WIDTH - x) ||
        !clip(-dy, y) || !clip(dy, NotePage.HEIGHT - y)) return null
    fun at(t: Float) = CanvasPoint(x + dx * t, y + dy * t)
    return at(t0) to at(t1)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: Stroke,
    transform: CanvasTransform
) {
    stroke.paths().forEach { points ->
        drawPath(
            buildSmoothPath(points.map(transform::map)),
            stroke.color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke.width * transform.scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        if (points.size == 1) {
            val mapped = transform.map(points.first())
            drawCircle(stroke.color, stroke.width * transform.scale / 2f, Offset(mapped.x, mapped.y))
        }
    }
}
