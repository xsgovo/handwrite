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
import com.xsgovo.handwrite.core.model.PagePattern
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.StrokeElement
import kotlin.math.roundToInt

class PageRenderEngine(
    private val resources: BackgroundResourceRepository,
) {
    // 背景位图缓存：长图导出逐页渲染时，同一背景资源原本会被逐页重复解码（PDF 光栅化尤其昂贵）。
    // 容量 2 的 LRU 覆盖“全文档统一背景”与“相邻页交替背景”两种主流场景，同时控制内存峰值。
    // 资源层是内容寻址（同字节内容即同文件），键中的路径即可代表内容身份，无需额外失效策略。
    // 引擎实例不再使用时必须调用 release() 回收缓存位图。
    private val assetCache = object : LinkedHashMap<AssetCacheKey, Bitmap>(
        ASSET_CACHE_CAPACITY, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AssetCacheKey, Bitmap>?): Boolean =
            (size > ASSET_CACHE_CAPACITY).also { evict ->
                if (evict) eldest?.value?.recycle()
            }
    }

    /** 回收缓存的全部背景位图；渲染会话结束（导出完成、分享完成）后必须调用。 */
    fun release() {
        assetCache.values.forEach(Bitmap::recycle)
        assetCache.clear()
    }

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

        if (background is PageBackground.Pattern) {
            drawPattern(canvas, width, height, page.size.width, page.size.height, background.type)
        }
        if (background is PageBackground.Asset) {
            val resource = resources.find(background.resourceId)
            if (resource is DomainResult.Success) {
                val maxEdge = maxOf(width, height)
                val key = AssetCacheKey(
                    path = resource.value.absolutePath,
                    kind = background.kind,
                    pdfPageIndex = background.pdfPageIndex,
                    maxEdge = maxEdge,
                )
                val bitmap = assetCache[key] ?: runCatching {
                    when (background.kind) {
                        BackgroundAssetKind.IMAGE -> decodeImage(resource.value.absolutePath, maxEdge)
                        BackgroundAssetKind.PDF -> decodePdf(
                            resource.value.absolutePath,
                            background.pdfPageIndex ?: 0,
                            maxEdge,
                        )
                    }
                }.getOrNull()?.also { assetCache[key] = it }
                bitmap?.let {
                    drawAsset(canvas, it, width, height, page.size.width, page.size.height, background)
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

private data class AssetCacheKey(
    val path: String,
    val kind: BackgroundAssetKind,
    val pdfPageIndex: Int?,
    val maxEdge: Int,
)

private const val ASSET_CACHE_CAPACITY = 2

private fun PageBackground.baseColor(forceOpaque: Boolean): Int = when (this) {
    is PageBackground.Solid -> if (forceOpaque) argb or Color.BLACK else argb
    PageBackground.Transparent -> if (forceOpaque) Color.WHITE else Color.TRANSPARENT
    is PageBackground.Pattern -> when {
        // 透明底样式在 JPEG 等不透明格式导出时用白色衬底，不能让 OR 黑变成黑底。
        !forceOpaque -> baseArgb
        (baseArgb and 0x00FFFFFF) == 0 -> Color.WHITE
        else -> baseArgb or Color.BLACK
    }
    is PageBackground.Asset -> Color.WHITE
}

private fun drawPattern(
    canvas: Canvas,
    width: Int,
    height: Int,
    logicalWidth: Int,
    logicalHeight: Int,
    type: PatternType,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2F60746B
        strokeWidth = 1f
    }
    val horizontalStep = PagePattern.LOGICAL_SPACING * width.toFloat() / logicalWidth
    val verticalStep = PagePattern.LOGICAL_SPACING * height.toFloat() / logicalHeight
    var y = verticalStep
    while (y < height) {
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
        y += verticalStep
    }
    if (type == PatternType.GRID) {
        var x = horizontalStep
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += horizontalStep
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
        if (stroke.style.blendMode == BrushBlendMode.HIGHLIGHT) alpha = (alpha * 0.4f).roundToInt()
    }
    val scale = minOf(scaleX, scaleY)
    if (stroke.style.rendersWithPressure()) {
        drawPressureStroke(canvas, stroke, paint, scale, scaleX, scaleY)
    } else {
        paint.strokeWidth = (stroke.style.width * scale).coerceAtLeast(1f)
        drawFlatStroke(canvas, stroke, paint, scaleX, scaleY)
    }
}

private fun drawFlatStroke(canvas: Canvas, stroke: StrokeElement, paint: Paint, scaleX: Float, scaleY: Float) {
    if (stroke.samples.size == 1) {
        val point = stroke.samples.first().point
        canvas.drawCircle(point.x * scaleX, point.y * scaleY, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
        return
    }
    val path = Path().apply {
        val first = stroke.samples.first().point
        moveTo(first.x * scaleX, first.y * scaleY)
        stroke.samples.drop(1).forEach { sample ->
            lineTo(sample.point.x * scaleX, sample.point.y * scaleY)
        }
    }
    canvas.drawPath(path, paint)
}

private fun drawPressureStroke(
    canvas: Canvas,
    stroke: StrokeElement,
    paint: Paint,
    scale: Float,
    scaleX: Float,
    scaleY: Float,
) {
    fun strokeWidthFor(pressure: Int): Float =
        (stroke.style.width * pressureWidthMultiplier(pressure) * scale).coerceAtLeast(1f)

    if (stroke.samples.size == 1) {
        val sample = stroke.samples.first()
        paint.strokeWidth = strokeWidthFor(sample.pressure)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(sample.point.x * scaleX, sample.point.y * scaleY, paint.strokeWidth / 2f, paint)
        return
    }
    stroke.samples.zipWithNext().forEach { (first, second) ->
        paint.strokeWidth = strokeWidthFor((first.pressure + second.pressure) / 2)
        canvas.drawLine(
            first.point.x * scaleX,
            first.point.y * scaleY,
            second.point.x * scaleX,
            second.point.y * scaleY,
            paint,
        )
    }
}

private val ASSET_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
