package com.xsgovo.handwrite.core.rendering

import android.graphics.Bitmap
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ExportResolution
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.PageContent
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt

data class PageImageFileFormat(
    val mimeType: String,
    val fileExtension: String,
)

class PageImageEncoder(
    private val renderer: PageRenderEngine,
) {
    suspend fun write(
        content: PageContent,
        output: OutputStream,
        imageFormat: ImageFormat,
        resolution: ExportResolution,
        quality: CompressionQuality,
    ) {
        val (width, height) = content.outputSize(resolution.longEdge())
        val bitmap = renderer.renderPage(content, width, height, forceOpaque = imageFormat.isLossy())
        try {
            if (!bitmap.compress(imageFormat.compressFormat(), quality.percent(), output)) {
                throw IOException("Encoding failed")
            }
        } finally {
            bitmap.recycle()
        }
    }
}

fun ImageFormat.pageImageFileFormat(): PageImageFileFormat = when (this) {
    ImageFormat.PNG -> PageImageFileFormat("image/png", "png")
    ImageFormat.JPEG -> PageImageFileFormat("image/jpeg", "jpg")
    ImageFormat.AUTO, ImageFormat.WEBP -> PageImageFileFormat("image/webp", "webp")
}

private fun PageContent.outputSize(longEdge: Int): Pair<Int, Int> {
    val size = page.size
    return if (size.width >= size.height) {
        longEdge to (longEdge * size.height.toDouble() / size.width).roundToInt().coerceAtLeast(1)
    } else {
        (longEdge * size.width.toDouble() / size.height).roundToInt().coerceAtLeast(1) to longEdge
    }
}

private fun ExportResolution.longEdge(): Int = when (this) {
    ExportResolution.SMALL -> 1_280
    ExportResolution.STANDARD -> 2_048
    ExportResolution.HIGH -> 3_072
}

private fun CompressionQuality.percent(): Int = when (this) {
    CompressionQuality.LOW -> 65
    CompressionQuality.BALANCED -> 82
    CompressionQuality.HIGH -> 95
}

private fun ImageFormat.compressFormat(): Bitmap.CompressFormat = when (this) {
    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
    ImageFormat.AUTO, ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
}

private fun ImageFormat.isLossy(): Boolean = this != ImageFormat.PNG
