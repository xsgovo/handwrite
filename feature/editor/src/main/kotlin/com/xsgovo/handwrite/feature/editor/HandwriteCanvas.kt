package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PagePattern
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import com.xsgovo.handwrite.core.rendering.InkDocumentRenderer
import com.xsgovo.handwrite.core.rendering.createInkBrush
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HandwriteCanvas(
    pageSize: LogicalSize,
    background: PageBackground,
    backgroundImage: ImageBitmap?,
    strokes: List<StrokeElement>,
    tool: EditorTool,
    inputMode: InputMode,
    zoomPercent: Int,
    activeColor: Int,
    activeWidth: Int,
    activeBrushId: BrushId,
    pressureSensitivity: PressureSensitivity,
    sideButtonAction: SideButtonAction,
    onZoomChanged: (Int) -> Unit,
    onStrokesFinished: (List<List<StrokeSample>>, onCompleted: () -> Unit) -> Unit,
    onEraseFinished: (Set<ElementId>, onCompleted: () -> Unit) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containingView = LocalView.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var erasedIds by remember { mutableStateOf<Set<ElementId>>(emptySet()) }
    var pendingErasedIds by remember { mutableStateOf<Set<ElementId>>(emptySet()) }
    var pendingStrokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val interopNavigation = remember { InteropNavigationState() }

    val transform = remember(canvasSize, pageSize, zoomPercent, pan) {
        CanvasPageTransform.create(canvasSize, pageSize, zoomPercent / 100f, pan)
    }
    val currentTransform by rememberUpdatedState(transform)
    val currentZoom by rememberUpdatedState(zoomPercent)
    val currentStrokes by rememberUpdatedState(strokes)
    val strokesCallback by rememberUpdatedState(onStrokesFinished)
    val zoomCallback by rememberUpdatedState(onZoomChanged)
    val eraseCallback by rememberUpdatedState(onEraseFinished)
    val toggleEraserCallback by rememberUpdatedState(onToggleEraser)
    val undoCallback by rememberUpdatedState(onUndo)

    LaunchedEffect(zoomPercent) {
        if (zoomPercent == 100) pan = Offset.Zero
    }

    fun eraseAt(point: LogicalPoint) {
        val radius = (900f / (currentZoom / 100f)).coerceAtLeast(180f)
        val hits = currentStrokes.asSequence()
            .filterNot { it.id in erasedIds || it.id in pendingErasedIds }
            .filter { stroke ->
                stroke.samples.any { candidate ->
                    hypot(
                        (candidate.point.x - point.x).toFloat(),
                        (candidate.point.y - point.y).toFloat(),
                    ) <= radius + stroke.style.width / 2f
                }
            }
            .map(StrokeElement::id)
            .toSet()
        if (hits.isNotEmpty()) erasedIds = erasedIds + hits
    }

    fun resetInteropNavigation() {
        interopNavigation.reset()
    }

    fun updateInteropNavigation(event: MotionEvent, excludedPointerIndex: Int? = null) {
        val pointers = buildList {
            repeat(event.pointerCount) { index ->
                if (index != excludedPointerIndex) add(Offset(event.getX(index), event.getY(index)))
            }
        }
        if (pointers.isEmpty()) return

        val centroid = pointers.reduce(Offset::plus) / pointers.size.toFloat()
        val span = if (pointers.size >= 2) {
            hypot(
                pointers[0].x - pointers[1].x,
                pointers[0].y - pointers[1].y,
            )
        } else {
            0f
        }
        if (interopNavigation.previousPointerCount == pointers.size) {
            interopNavigation.previousCentroid?.let { pan += centroid - it }
            if (interopNavigation.previousSpan > 0f && span > 0f) {
                interopNavigation.zoom = (interopNavigation.zoom * span / interopNavigation.previousSpan)
                    .roundToInt()
                    .coerceIn(100, 400)
                zoomCallback(interopNavigation.zoom)
            }
        } else {
            interopNavigation.zoom = currentZoom
        }
        interopNavigation.previousCentroid = centroid
        interopNavigation.previousSpan = span
        interopNavigation.previousPointerCount = pointers.size
    }

    val touchNavigationModifier = Modifier.pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!shouldCaptureTouchNavigation(inputMode, event.getToolType(event.actionIndex))) {
                    false
                } else {
                    containingView.requestUnbufferedDispatch(event)
                    interopNavigation.active = true
                    interopNavigation.zoom = currentZoom
                    updateInteropNavigation(event)
                    true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!interopNavigation.active) {
                    false
                } else {
                    updateInteropNavigation(event)
                    true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!interopNavigation.active) {
                    false
                } else {
                    updateInteropNavigation(event)
                    true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!interopNavigation.active) {
                    false
                } else {
                    updateInteropNavigation(event, event.actionIndex)
                    true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!interopNavigation.active) {
                    false
                } else {
                    resetInteropNavigation()
                    true
                }
            }
            else -> interopNavigation.active
        }
    }

    val gestureModifier = Modifier.pointerInput(tool, inputMode, sideButtonAction) {
        awaitEachGesture {
            var previousCentroid: Offset? = null
            var previousSpan = 0f
            var gestureZoom = currentZoom
            var sideActionHandled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }

                val motionEvent = event.motionEvent
                val sidePressed = motionEvent?.buttonState?.and(
                    MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY,
                ) != 0
                val sideForEraser = sidePressed && sideButtonAction == SideButtonAction.TEMPORARY_ERASER
                if (sidePressed && !sideActionHandled) {
                    when (sideButtonAction) {
                        SideButtonAction.TEMPORARY_ERASER -> Unit
                        SideButtonAction.TOGGLE_ERASER -> toggleEraserCallback()
                        SideButtonAction.UNDO -> undoCallback()
                    }
                    sideActionHandled = true
                }

                if (pressed.size >= 2) {
                    event.changes.forEach { it.consume() }
                    val centroid = pressed.map { it.position }.reduce(Offset::plus) / pressed.size.toFloat()
                    val span = hypot(
                        pressed[0].position.x - pressed[1].position.x,
                        pressed[0].position.y - pressed[1].position.y,
                    )
                    previousCentroid?.let { pan += centroid - it }
                    if (previousSpan > 0f && span > 0f) {
                        gestureZoom = (gestureZoom * span / previousSpan).roundToInt().coerceIn(100, 400)
                        zoomCallback(gestureZoom)
                    }
                    previousCentroid = centroid
                    previousSpan = span
                } else {
                    previousCentroid = null
                    previousSpan = 0f
                    val change = pressed.firstOrNull() ?: event.changes.firstOrNull()
                    if (change != null) {
                        val erasing = tool == EditorTool.ERASER || change.type == PointerType.Eraser || sideForEraser
                        val navigationPointer = isSingleFingerNavigationPointer(
                            inputMode = inputMode,
                            pointerType = change.type,
                            rawToolType = motionEvent?.takeIf { it.pointerCount > 0 }?.getToolType(0),
                        )
                        when {
                            interopNavigation.active -> change.consume()
                            sidePressed && sideButtonAction != SideButtonAction.TEMPORARY_ERASER -> {
                                change.consume()
                            }
                            navigationPointer -> {
                                change.consume()
                                pan += change.positionChange()
                            }
                            erasing -> {
                                change.consume()
                                currentTransform.toLogical(change.position)?.let(::eraseAt)
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (erasedIds.isNotEmpty()) {
                val completed = erasedIds
                erasedIds = emptySet()
                pendingErasedIds = pendingErasedIds + completed
                eraseCallback(completed) {
                    pendingErasedIds = pendingErasedIds - completed
                }
            }
        }
    }

    val inkRenderer = remember { InkDocumentRenderer() }
    val visibleStrokes = remember(strokes, erasedIds, pendingErasedIds) {
        strokes.filterNot { it.id in erasedIds || it.id in pendingErasedIds }
    }
    val preparedStrokes = remember(visibleStrokes) {
        runCatching { inkRenderer.prepare(visibleStrokes) }
            .onFailure { error -> Log.e(LOG_TAG, "Unable to prepare persisted Ink stroke", error) }
            .getOrDefault(emptyList())
    }
    val wetBrush = remember(activeBrushId, activeColor, activeWidth, transform.scale, tool) {
        if (tool == EditorTool.PEN) {
            createInkBrush(activeBrushId, activeColor, activeWidth * transform.scale)
        } else {
            null
        }
    }
    val currentWetBrush by rememberUpdatedState(wetBrush)
    val maskPath = remember(canvasSize, transform.pageRect) {
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat()))
            addRect(transform.pageRect)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8EBE8))
            .onSizeChanged { canvasSize = it }
            .then(touchNavigationModifier)
            .then(gestureModifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val page = transform.pageRect
            drawRect(Color(0x26000000), topLeft = page.topLeft + Offset(0f, 3f), size = page.size)
            drawRect(background.baseColor(), topLeft = page.topLeft, size = page.size)
            clipRect(page.left, page.top, page.right, page.bottom) {
                when (background) {
                    is PageBackground.Pattern -> drawPattern(background, page, transform.scale)
                    is PageBackground.Asset -> backgroundImage?.let { image ->
                        val scale = background.transform.scalePermille / 1_000f
                        val translation = Offset(
                            background.transform.translation.x * transform.scale,
                            background.transform.translation.y * transform.scale,
                        )
                        withTransform({
                            translate(translation.x, translation.y)
                            rotate(background.transform.rotationMilliDegrees / 1_000f, page.center)
                            scale(scale, scale, page.center)
                        }) {
                            drawImage(
                                image = image,
                                dstOffset = IntOffset(page.left.roundToInt(), page.top.roundToInt()),
                                dstSize = IntSize(page.width.roundToInt(), page.height.roundToInt()),
                            )
                        }
                    }
                    else -> Unit
                }
                inkRenderer.draw(this, preparedStrokes, transform.scale, page.left, page.top)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val page = transform.pageRect
            clipRect(page.left, page.top, page.right, page.bottom) {
                inkRenderer.drawInProgress(this, pendingStrokes)
            }
        }
        InProgressStrokes(
            defaultBrush = wetBrush,
            nextBrush = { currentWetBrush },
            maskPath = maskPath,
            onStrokesFinished = { completed ->
                completed.forEach { stroke ->
                    pendingStrokes = pendingStrokes + stroke
                    val samples = buildList {
                        val scratch = StrokeInput()
                        repeat(stroke.inputs.size) { index ->
                            stroke.inputs.populate(index, scratch)
                            val point = currentTransform.toLogicalUnclamped(Offset(scratch.x, scratch.y))
                            val tilt = scratch.toTiltVector()
                            add(
                                StrokeSample(
                                    point = point,
                                    pressure = if (scratch.hasPressure) {
                                        (scratch.pressure * StrokeSample.MAX_PRESSURE).roundToInt()
                                            .coerceIn(0, StrokeSample.MAX_PRESSURE)
                                    } else {
                                        StrokeSample.MAX_PRESSURE
                                    },
                                    elapsedMillis = scratch.elapsedTimeMillis.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                                    tiltX = tilt?.first,
                                    tiltY = tilt?.second,
                                ),
                            )
                        }
                    }
                    val clippedStrokes = clipStrokeToPage(samples, pageSize)
                    if (clippedStrokes.isNotEmpty()) {
                        strokesCallback(clippedStrokes) {
                            pendingStrokes = pendingStrokes.filterNot { it === stroke }
                        }
                    } else {
                        pendingStrokes = pendingStrokes.filterNot { it === stroke }
                    }
                }
            },
        )
    }
}

private const val LOG_TAG = "HandwriteCanvas"

private class InteropNavigationState {
    var active: Boolean = false
    var previousCentroid: Offset? = null
    var previousSpan: Float = 0f
    var previousPointerCount: Int = 0
    var zoom: Int = 100

    fun reset() {
        active = false
        previousCentroid = null
        previousSpan = 0f
        previousPointerCount = 0
    }
}

internal fun isSingleFingerNavigationPointer(
    inputMode: InputMode,
    pointerType: PointerType,
    rawToolType: Int?,
): Boolean = inputMode == InputMode.STYLUS &&
    pointerType != PointerType.Stylus &&
    pointerType != PointerType.Eraser &&
    rawToolType != MotionEvent.TOOL_TYPE_STYLUS &&
    rawToolType != MotionEvent.TOOL_TYPE_ERASER

internal fun shouldCaptureTouchNavigation(inputMode: InputMode, rawToolType: Int): Boolean =
    inputMode == InputMode.STYLUS &&
        rawToolType != MotionEvent.TOOL_TYPE_STYLUS &&
        rawToolType != MotionEvent.TOOL_TYPE_ERASER

internal fun clipStrokeToPage(
    samples: List<StrokeSample>,
    pageSize: LogicalSize,
): List<List<StrokeSample>> {
    if (samples.isEmpty()) return emptyList()
    if (samples.size == 1) return if (pageSize.contains(samples.single().point)) listOf(samples) else emptyList()

    val clippedStrokes = mutableListOf<List<StrokeSample>>()
    val activeStroke = mutableListOf<StrokeSample>()

    fun append(sample: StrokeSample) {
        if (activeStroke.lastOrNull()?.point != sample.point) activeStroke += sample
    }

    fun finishSegment() {
        if (activeStroke.size >= 2) clippedStrokes += activeStroke.toList()
        activeStroke.clear()
    }

    samples.zipWithNext().forEach { (first, second) ->
        val clipped = clipSegmentToPage(first, second, pageSize)
        if (clipped == null) {
            finishSegment()
        } else {
            append(clipped.first)
            append(clipped.second)
            if (clipped.secondParameter < 1.0) finishSegment()
        }
    }
    finishSegment()
    return clippedStrokes
}

private data class ClippedSegment(
    val first: StrokeSample,
    val second: StrokeSample,
    val secondParameter: Double,
)

private fun clipSegmentToPage(
    first: StrokeSample,
    second: StrokeSample,
    pageSize: LogicalSize,
): ClippedSegment? {
    val x0 = first.point.x.toDouble()
    val y0 = first.point.y.toDouble()
    val dx = second.point.x - first.point.x.toDouble()
    val dy = second.point.y - first.point.y.toDouble()
    val boundaries = listOf(
        -dx to x0,
        dx to pageSize.width - x0,
        -dy to y0,
        dy to pageSize.height - y0,
    )
    var entry = 0.0
    var exit = 1.0
    boundaries.forEach { (delta, distance) ->
        if (delta == 0.0) {
            if (distance < 0.0) return null
        } else {
            val parameter = distance / delta
            if (delta < 0.0) {
                entry = maxOf(entry, parameter)
            } else {
                exit = minOf(exit, parameter)
            }
        }
    }
    if (entry > exit || exit < 0.0 || entry > 1.0) return null
    val firstParameter = entry.coerceIn(0.0, 1.0)
    val secondParameter = exit.coerceIn(0.0, 1.0)
    return ClippedSegment(
        first = first.interpolate(second, firstParameter, pageSize),
        second = first.interpolate(second, secondParameter, pageSize),
        secondParameter = secondParameter,
    )
}

private fun StrokeSample.interpolate(
    other: StrokeSample,
    parameter: Double,
    pageSize: LogicalSize,
): StrokeSample = StrokeSample(
    point = LogicalPoint(
        (point.x + (other.point.x - point.x) * parameter).roundToInt().coerceIn(0, pageSize.width),
        (point.y + (other.point.y - point.y) * parameter).roundToInt().coerceIn(0, pageSize.height),
    ),
    pressure = (pressure + (other.pressure - pressure) * parameter).roundToInt()
        .coerceIn(0, StrokeSample.MAX_PRESSURE),
    elapsedMillis = (elapsedMillis + (other.elapsedMillis - elapsedMillis) * parameter).roundToInt()
        .coerceAtLeast(0),
    tiltX = interpolateTilt(tiltX, other.tiltX, parameter),
    tiltY = interpolateTilt(tiltY, other.tiltY, parameter),
)

private fun interpolateTilt(first: Int?, second: Int?, parameter: Double): Int? = when {
    first == null || second == null -> if (parameter < 0.5) first else second
    else -> (first + (second - first) * parameter).roundToInt()
}

private data class CanvasPageTransform(
    val pageSize: LogicalSize,
    val scale: Float,
    val pageRect: Rect,
) {
    fun toLogical(point: Offset): LogicalPoint? {
        if (!pageRect.contains(point)) return null
        return toLogicalClamped(point)
    }

    fun toLogicalClamped(point: Offset): LogicalPoint = LogicalPoint(
        ((point.x - pageRect.left) / scale).roundToInt().coerceIn(0, pageSize.width),
        ((point.y - pageRect.top) / scale).roundToInt().coerceIn(0, pageSize.height),
    )

    fun toLogicalUnclamped(point: Offset): LogicalPoint = LogicalPoint(
        ((point.x - pageRect.left) / scale).roundToInt(),
        ((point.y - pageRect.top) / scale).roundToInt(),
    )

    companion object {
        fun create(canvas: IntSize, page: LogicalSize, zoom: Float, pan: Offset): CanvasPageTransform {
            if (canvas.width == 0 || canvas.height == 0) return CanvasPageTransform(page, 1f, Rect.Zero)
            val padding = 24f
            val fit = minOf(
                (canvas.width - padding * 2) / page.width,
                (canvas.height - padding * 2) / page.height,
            ).coerceAtLeast(0.0001f)
            val scale = fit * zoom
            val width = page.width * scale
            val height = page.height * scale
            val overflowX = ((width - canvas.width) / 2f + padding).coerceAtLeast(0f)
            val overflowY = ((height - canvas.height) / 2f + padding).coerceAtLeast(0f)
            val left = (canvas.width - width) / 2f + pan.x.coerceIn(-overflowX, overflowX)
            val top = (canvas.height - height) / 2f + pan.y.coerceIn(-overflowY, overflowY)
            return CanvasPageTransform(page, scale, Rect(left, top, left + width, top + height))
        }
    }
}

private fun PageBackground.baseColor(): Color = when (this) {
    is PageBackground.Solid -> Color(argb)
    PageBackground.Transparent -> Color.Transparent
    is PageBackground.Pattern -> Color(baseArgb)
    is PageBackground.Asset -> Color.White
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPattern(
    pattern: PageBackground.Pattern,
    page: Rect,
    scale: Float,
) {
    val color = Color(0x2F60746B)
    val step = PagePattern.LOGICAL_SPACING * scale
    var y = page.top + step
    while (y < page.bottom) {
        drawLine(color, Offset(page.left, y), Offset(page.right, y), strokeWidth = 1f)
        y += step
    }
    if (pattern.type == PatternType.GRID) {
        var x = page.left + step
        while (x < page.right) {
            drawLine(color, Offset(x, page.top), Offset(x, page.bottom), strokeWidth = 1f)
            x += step
        }
    }
}

private fun StrokeInput.toTiltVector(): Pair<Int, Int>? {
    if (!hasTilt || !hasOrientation) return null
    val magnitude = sin(tiltRadians)
    return (
        magnitude * cos(orientationRadians) * StrokeSample.MAX_TILT
    ).roundToInt().coerceIn(StrokeSample.MIN_TILT, StrokeSample.MAX_TILT) to (
        magnitude * sin(orientationRadians) * StrokeSample.MAX_TILT
    ).roundToInt().coerceIn(StrokeSample.MIN_TILT, StrokeSample.MAX_TILT)
}
