package com.xsgovo.handwrite.core.rendering

import android.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.StrokeElement
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

class InkDocumentRenderer {
    private val renderer = CanvasStrokeRenderer.create()
    private val strokeCache = StableIdCache<ElementId, Stroke>()

    fun prepare(strokes: List<StrokeElement>): List<Stroke> =
        strokeCache.update(strokes, StrokeElement::id, ::toInkStroke)

    fun draw(
        scope: DrawScope,
        strokes: List<Stroke>,
        scale: Float,
        left: Float,
        top: Float,
    ) {
        val transform = Matrix().apply {
            setValues(
                floatArrayOf(
                    scale, 0f, left,
                    0f, scale, top,
                    0f, 0f, 1f,
                ),
            )
        }
        scope.drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val saveCount = nativeCanvas.save()
            try {
                nativeCanvas.concat(transform)
                strokes.forEach { stroke -> renderer.draw(nativeCanvas, stroke, transform) }
            } finally {
                nativeCanvas.restoreToCount(saveCount)
            }
        }
    }

    fun drawInProgress(
        scope: DrawScope,
        strokes: List<Stroke>,
        scale: Float,
        left: Float,
        top: Float,
    ) {
        if (strokes.isEmpty()) return
        val transform = Matrix().apply {
            setValues(
                floatArrayOf(
                    scale, 0f, left,
                    0f, scale, top,
                    0f, 0f, 1f,
                ),
            )
        }
        scope.drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val saveCount = nativeCanvas.save()
            try {
                nativeCanvas.concat(transform)
                strokes.forEach { stroke -> renderer.draw(nativeCanvas, stroke, transform) }
            } finally {
                nativeCanvas.restoreToCount(saveCount)
            }
        }
    }
}

internal class StableIdCache<Key, Value> {
    private val entries = mutableMapOf<Key, Value>()

    fun <Source> update(
        sources: List<Source>,
        keyOf: (Source) -> Key,
        create: (Source) -> Value,
    ): List<Value> {
        val activeKeys = HashSet<Key>(sources.size)
        val values = ArrayList<Value>(sources.size)
        sources.forEach { source ->
            val key = keyOf(source)
            activeKeys += key
            values += entries.getOrPut(key) { create(source) }
        }
        entries.keys.retainAll(activeKeys)
        return values
    }
}

fun createInkBrush(
    brushId: BrushId,
    argb: Int,
    size: Float,
): Brush = Brush.createWithColorIntArgb(
    family = when (brushId) {
        BrushId.PRESSURE_PEN -> StockBrushes.pressurePen()
        BrushId.HIGHLIGHTER -> StockBrushes.highlighter()
        else -> StockBrushes.marker()
    },
    colorIntArgb = argb,
    size = size.coerceAtLeast(0.1f),
    epsilon = (size * 0.01f).coerceAtLeast(0.05f),
)

private fun toInkStroke(stroke: StrokeElement): Stroke {
    val inputs = MutableStrokeInputBatch()
    stroke.samples.forEach { sample ->
        val tilt = sample.tiltVector()
        inputs.add(
            type = InputToolType.STYLUS,
            x = sample.point.x.toFloat(),
            y = sample.point.y.toFloat(),
            elapsedTimeMillis = sample.elapsedMillis.toLong(),
            pressure = sample.pressure.toFloat() / com.xsgovo.handwrite.core.model.StrokeSample.MAX_PRESSURE,
            tiltRadians = tilt?.first ?: androidx.ink.strokes.StrokeInput.NO_TILT,
            orientationRadians = tilt?.second ?: androidx.ink.strokes.StrokeInput.NO_ORIENTATION,
        )
    }
    return Stroke(
        brush = createInkBrush(stroke.style.id, stroke.style.argb, stroke.style.width.toFloat()),
        inputs = inputs,
    )
}

private fun com.xsgovo.handwrite.core.model.StrokeSample.tiltVector(): Pair<Float, Float>? {
    val x = tiltX ?: return null
    val y = tiltY ?: return null
    val normalizedX = x.toFloat() / com.xsgovo.handwrite.core.model.StrokeSample.MAX_TILT
    val normalizedY = y.toFloat() / com.xsgovo.handwrite.core.model.StrokeSample.MAX_TILT
    val magnitude = hypot(normalizedX, normalizedY).coerceIn(0f, 1f)
    return asin(magnitude) to atan2(normalizedY, normalizedX).let { if (it < 0f) it + TWO_PI else it }
}

private const val TWO_PI = (Math.PI * 2).toFloat()
