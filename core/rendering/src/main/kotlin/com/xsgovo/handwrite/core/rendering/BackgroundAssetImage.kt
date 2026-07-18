package com.xsgovo.handwrite.core.rendering

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.PageBackground
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberBackgroundAssetImage(
    background: PageBackground,
    resource: StoredResource?,
): State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = background,
    key2 = resource?.absolutePath,
) {
    val asset = background as? PageBackground.Asset
    value = if (asset == null || resource?.id != asset.resourceId) {
        null
    } else {
        withContext(Dispatchers.IO) {
            runCatching {
                when (asset.kind) {
                    BackgroundAssetKind.IMAGE -> decodeImage(resource.absolutePath, PREVIEW_DECODE_EDGE)
                    BackgroundAssetKind.PDF -> decodePdf(resource.absolutePath, asset.pdfPageIndex ?: 0, PREVIEW_DECODE_EDGE)
                }.asImageBitmap()
            }.getOrNull()
        }
    }
}

internal fun decodeImage(path: String, maximumEdge: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maximumEdge) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options) ?: throw IOException("Unable to decode image")
}

internal fun decodePdf(path: String, pageIndex: Int, maximumEdge: Int): Bitmap {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) throw IOException("PDF page is unavailable")
            renderer.openPage(pageIndex).use { page ->
                val scale = (maximumEdge.toFloat() / maxOf(page.width, page.height)).coerceAtMost(1f)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private const val PREVIEW_DECODE_EDGE = 2_048
