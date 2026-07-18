package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    onZoomChanged: (Int) -> Unit,
    onStrokeFinished: (List<StrokeSample>) -> Unit,
    onEraseFinished: (Set<ElementId>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var liveSamples by remember { mutableStateOf<List<StrokeSample>>(emptyList()) }
    var erasedIds by remember { mutableStateOf<Set<ElementId>>(emptySet()) }
    var gestureStart by remember { mutableStateOf(0L) }
    var acceptingGesture by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var transforming by remember { mutableStateOf(false) }
    var previousCentroid by remember { mutableStateOf(Offset.Zero) }
    var previousSpan by remember { mutableStateOf(0f) }

    val transform = remember(canvasSize, pageSize, zoomPercent, pan) {
        CanvasPageTransform.create(canvasSize, pageSize, zoomPercent / 100f, pan)
    }

    LaunchedEffect(zoomPercent) {
        if (zoomPercent == 100) pan = Offset.Zero
    }

    fun centroid(event: MotionEvent): Offset = Offset(
        (event.getX(0) + event.getX(1)) / 2f,
        (event.getY(0) + event.getY(1)) / 2f,
    )

    fun span(event: MotionEvent): Float = hypot(
        event.getX(0) - event.getX(1),
        event.getY(0) - event.getY(1),
    )

    fun accepts(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        return inputMode == InputMode.FINGER ||
            toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    fun isEraser(event: MotionEvent): Boolean =
        tool == EditorTool.ERASER || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

    fun sample(x: Float, y: Float, pressure: Float, time: Long): StrokeSample? =
        transform.toLogical(Offset(x, y))?.let { point ->
            StrokeSample(
                point = point,
                pressure = (pressure.coerceIn(0f, 1f) * StrokeSample.MAX_PRESSURE).roundToInt(),
                elapsedMillis = (time - gestureStart).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
        }

    fun eraseAt(point: LogicalPoint) {
        val radius = (900f / (zoomPercent / 100f)).coerceAtLeast(180f)
        val hits = strokes.asSequence()
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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8EBE8))
            .onSizeChanged { canvasSize = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        acceptingGesture = accepts(event) && transform.pageRect.contains(Offset(event.x, event.y))
                        if (!acceptingGesture) return@pointerInteropFilter true
                        gestureStart = event.eventTime
                        if (isEraser(event)) {
                            transform.toLogical(Offset(event.x, event.y))?.let(::eraseAt)
                        } else {
                            liveSamples = listOfNotNull(sample(event.x, event.y, event.pressure, event.eventTime))
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (transforming && event.pointerCount >= 2) {
                            val currentCentroid = centroid(event)
                            val currentSpan = span(event)
                            pan += currentCentroid - previousCentroid
                            if (previousSpan > 0f) {
                                onZoomChanged((zoomPercent * currentSpan / previousSpan).roundToInt().coerceIn(100, 400))
                            }
                            previousCentroid = currentCentroid
                            previousSpan = currentSpan
                            return@pointerInteropFilter true
                        }
                        if (!acceptingGesture) return@pointerInteropFilter false
                        for (index in 0 until event.historySize) {
                            val point = sample(
                                event.getHistoricalX(0, index),
                                event.getHistoricalY(0, index),
                                event.getHistoricalPressure(0, index),
                                event.getHistoricalEventTime(index),
                            ) ?: continue
                            if (isEraser(event)) eraseAt(point.point) else liveSamples = liveSamples + point
                        }
                        sample(event.x, event.y, event.pressure, event.eventTime)?.let { point ->
                            if (isEraser(event)) eraseAt(point.point) else liveSamples = liveSamples + point
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (transforming) {
                            transforming = false
                            return@pointerInteropFilter true
                        }
                        if (!acceptingGesture) return@pointerInteropFilter false
                        if (isEraser(event)) {
                            val completed = erasedIds
                            erasedIds = emptySet()
                            onEraseFinished(completed)
                        } else {
                            sample(event.x, event.y, event.pressure, event.eventTime)?.let { liveSamples = liveSamples + it }
                            val completed = liveSamples.distinctBy { it.point }
                            liveSamples = emptyList()
                            onStrokeFinished(completed)
                        }
                        acceptingGesture = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        liveSamples = emptyList()
                        erasedIds = emptySet()
                        acceptingGesture = false
                        transforming = false
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount >= 2) {
                            liveSamples = emptyList()
                            erasedIds = emptySet()
                            acceptingGesture = false
                            transforming = true
                            previousCentroid = centroid(event)
                            previousSpan = span(event)
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        transforming = false
                        acceptingGesture = false
                        true
                    }
                    else -> acceptingGesture
                }
            },
    ) {
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
            strokes.filterNot { it.id in erasedIds }.forEach { stroke ->
                drawStroke(stroke.samples, stroke.style.argb, stroke.style.width.toFloat(), transform)
            }
            if (liveSamples.isNotEmpty()) {
                drawStroke(liveSamples, activeColor, activeWidth.toFloat(), transform)
            }
        }
    }
}

private data class CanvasPageTransform(
    val pageSize: LogicalSize,
    val scale: Float,
    val pageRect: Rect,
) {
    fun toScreen(point: LogicalPoint): Offset = Offset(
        pageRect.left + point.x * scale,
        pageRect.top + point.y * scale,
    )

    fun toLogical(point: Offset): LogicalPoint? {
        if (!pageRect.contains(point)) return null
        return LogicalPoint(
            ((point.x - pageRect.left) / scale).roundToInt().coerceIn(0, pageSize.width),
            ((point.y - pageRect.top) / scale).roundToInt().coerceIn(0, pageSize.height),
        )
    }

    companion object {
        fun create(canvas: IntSize, page: LogicalSize, zoom: Float, pan: Offset): CanvasPageTransform {
            if (canvas.width == 0 || canvas.height == 0) {
                return CanvasPageTransform(page, 1f, Rect.Zero)
            }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    samples: List<StrokeSample>,
    argb: Int,
    logicalWidth: Float,
    transform: CanvasPageTransform,
) {
    if (samples.isEmpty()) return
    val path = Path().apply {
        val first = transform.toScreen(samples.first().point)
        moveTo(first.x, first.y)
        samples.drop(1).forEach { sample ->
            val point = transform.toScreen(sample.point)
            lineTo(point.x, point.y)
        }
    }
    val width = (logicalWidth * transform.scale).coerceAtLeast(1f)
    drawPath(path, Color(argb), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (samples.size == 1) drawCircle(Color(argb), width / 2f, transform.toScreen(samples.first().point))
}
