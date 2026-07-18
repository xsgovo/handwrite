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
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.strokes.StrokeInput
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
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
    onStrokeFinished: (List<StrokeSample>) -> Unit,
    onEraseFinished: (Set<ElementId>) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var erasedIds by remember { mutableStateOf<Set<ElementId>>(emptySet()) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val transform = remember(canvasSize, pageSize, zoomPercent, pan) {
        CanvasPageTransform.create(canvasSize, pageSize, zoomPercent / 100f, pan)
    }
    val currentTransform by rememberUpdatedState(transform)
    val currentZoom by rememberUpdatedState(zoomPercent)
    val currentStrokes by rememberUpdatedState(strokes)
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
            .filterNot { it.id in erasedIds }
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

    val gestureModifier = Modifier.pointerInput(tool, inputMode, sideButtonAction) {
        awaitEachGesture {
            var previousCentroid: Offset? = null
            var previousSpan = 0f
            var gestureZoom = currentZoom
            var startedInside = false
            var sideActionHandled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { down ->
                    startedInside = currentTransform.pageRect.contains(down.position)
                }

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
                        val unsupportedPointer = inputMode == InputMode.STYLUS && change.type != PointerType.Stylus &&
                            change.type != PointerType.Eraser
                        when {
                            sidePressed && sideButtonAction != SideButtonAction.TEMPORARY_ERASER -> {
                                change.consume()
                            }
                            erasing -> {
                                change.consume()
                                currentTransform.toLogical(change.position)?.let(::eraseAt)
                            }
                            unsupportedPointer -> {
                                change.consume()
                                pan += change.positionChange()
                            }
                            !startedInside -> change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (erasedIds.isNotEmpty()) {
                val completed = erasedIds
                erasedIds = emptySet()
                eraseCallback(completed)
            }
        }
    }

    val inkRenderer = remember { InkDocumentRenderer() }
    val visibleStrokes = remember(strokes, erasedIds) { strokes.filterNot { it.id in erasedIds } }
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
            .then(gestureModifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val page = transform.pageRect
            drawRect(Color(0x26000000), topLeft = page.topLeft + Offset(0f, 3f), size = page.size)
            drawRect(background.baseColor(), topLeft = page.topLeft, size = page.size)
            clipRect(page.left, page.top, page.right, page.bottom) {
                when (background) {
                    is PageBackground.Pattern -> drawPattern(background, page)
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
        InProgressStrokes(
            defaultBrush = wetBrush,
            maskPath = maskPath,
            onStrokesFinished = { completed ->
                completed.forEach { stroke ->
                    val samples = buildList {
                        val scratch = StrokeInput()
                        repeat(stroke.inputs.size) { index ->
                            stroke.inputs.populate(index, scratch)
                            val point = currentTransform.toLogicalClamped(Offset(scratch.x, scratch.y))
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
                    }.distinctBy { it.point }
                    if (samples.isNotEmpty()) onStrokeFinished(samples)
                }
            },
        )
    }
}

private const val LOG_TAG = "HandwriteCanvas"

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
) {
    val color = Color(0x2F60746B)
    val step = 28f
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
