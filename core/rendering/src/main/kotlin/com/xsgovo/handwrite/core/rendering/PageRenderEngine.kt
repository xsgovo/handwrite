package com.xsgovo.handwrite.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BrushBlendMode
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.StrokeElement
import kotlin.math.roundToInt

class PageRenderEngine(
    private val resources: BackgroundResourceRepository,
) {
    suspend fun renderPage(
        content: PageContent,
        width: Int,
        height: Int,
        forceOpaque: Boolean = false,
    ): Bitmap {
        require(width > 0 && height > 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(Canvas(bitmap), content, width, height, forceOpaque)
        return bitmap
    }

    suspend fun drawPage(
        canvas: Canvas,
        content: PageContent,
        width: Int,
        height: Int,
        forceOpaque: Boolean = false,
    ) {
        val page = content.page
        val background = page.background
        if (forceOpaque || background !is PageBackground.Transparent) {
            canvas.drawColor(background.baseColor(forceOpaque))
        }

        if (background is PageBackground.Pattern) drawPattern(canvas, width, height, background.type)
        if (background is PageBackground.Asset) {
            val resource = resources.find(background.resourceId)
            if (resource is DomainResult.Success) {
                val bitmap = runCatching {
                    when (background.kind) {
                        BackgroundAssetKind.IMAGE -> decodeImage(resource.value.absolutePath, maxOf(width, height))
                        BackgroundAssetKind.PDF -> decodePdf(
                            resource.value.absolutePath,
                            background.pdfPageIndex ?: 0,
                            maxOf(width, height),
                        )
                    }
                }.getOrNull()
                bitmap?.let {
                    drawAsset(canvas, it, width, height, page.size.width, page.size.height, background)
                    it.recycle()
                }
            }
        }

        val scaleX = width.toFloat() / page.size.width
        val scaleY = height.toFloat() / page.size.height
        content.elements.filterIsInstance<StrokeElement>().forEach { stroke ->
            drawStroke(canvas, stroke, scaleX, scaleY)
        }
    }
}

private fun PageBackground.baseColor(forceOpaque: Boolean): Int = when (this) {
    is PageBackground.Solid -> if (forceOpaque) argb or Color.BLACK else argb
    PageBackground.Transparent -> if (forceOpaque) Color.WHITE else Color.TRANSPARENT
    is PageBackground.Pattern -> if (forceOpaque) baseArgb or Color.BLACK else baseArgb
    is PageBackground.Asset -> Color.WHITE
}

private fun drawPattern(canvas: Canvas, width: Int, height: Int, type: PatternType) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2F60746B
        strokeWidth = 1f
    }
    val step = (minOf(width, height) / 16f).coerceAtLeast(12f)
    var y = step
    while (y < height) {
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
        y += step
    }
    if (type == PatternType.GRID) {
        var x = step
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += step
        }
    }
}

private fun drawAsset(
    canvas: Canvas,
    bitmap: Bitmap,
    width: Int,
    height: Int,
    logicalWidth: Int,
    logicalHeight: Int,
    background: PageBackground.Asset,
) {
    val transform = background.transform
    val scaleX = width.toFloat() / logicalWidth
    val scaleY = height.toFloat() / logicalHeight
    canvas.save()
    canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
    transform.crop?.let { crop ->
        canvas.clipRect(
            crop.left * scaleX,
            crop.top * scaleY,
            crop.right * scaleX,
            crop.bottom * scaleY,
        )
    }
    canvas.translate(transform.translation.x * scaleX, transform.translation.y * scaleY)
    canvas.rotate(transform.rotationMilliDegrees / 1_000f, width / 2f, height / 2f)
    val scale = transform.scalePermille / 1_000f
    canvas.scale(scale, scale, width / 2f, height / 2f)
    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), ASSET_PAINT)
    canvas.restore()
}

private fun drawStroke(canvas: Canvas, stroke: StrokeElement, scaleX: Float, scaleY: Float) {
    if (stroke.samples.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke.style.argb
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = (stroke.style.width * minOf(scaleX, scaleY)).coerceAtLeast(1f)
        if (stroke.style.blendMode == BrushBlendMode.HIGHLIGHT) alpha = (alpha * 0.4f).roundToInt()
    }
    val path = Path().apply {
        val first = stroke.samples.first().point
        moveTo(first.x * scaleX, first.y * scaleY)
        stroke.samples.drop(1).forEach { sample ->
            lineTo(sample.point.x * scaleX, sample.point.y * scaleY)
        }
    }
    if (stroke.samples.size == 1) {
        val point = stroke.samples.first().point
        canvas.drawCircle(point.x * scaleX, point.y * scaleY, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
    } else {
        canvas.drawPath(path, paint)
    }
}

private val ASSET_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
