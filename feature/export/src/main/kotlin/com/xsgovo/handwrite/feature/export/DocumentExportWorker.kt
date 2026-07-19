package com.xsgovo.handwrite.feature.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ExportResolution
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.rendering.PageImageEncoder
import com.xsgovo.handwrite.core.rendering.PageRenderEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DocumentExportWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getLong(KEY_DOCUMENT_ID, -1L).takeIf { it > 0 } ?: return Result.failure()
        val destination = inputData.getString(KEY_DESTINATION_URI)?.let(Uri::parse) ?: return Result.failure()
        val format = inputData.enumValue<DocumentExportFormat>(KEY_EXPORT_FORMAT) ?: return Result.failure()
        val imageFormat = inputData.enumValue<ImageFormat>(KEY_IMAGE_FORMAT) ?: ImageFormat.AUTO
        val resolution = inputData.enumValue<ExportResolution>(KEY_EXPORT_RESOLUTION) ?: ExportResolution.STANDARD
        val quality = inputData.enumValue<CompressionQuality>(KEY_COMPRESSION_QUALITY) ?: CompressionQuality.BALANCED
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            ExportWorkerDependencies::class.java,
        )
        val snapshot = when (val result = dependencies.documents().loadSnapshot(DocumentId(documentId))) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return Result.failure()
        }

        return try {
            applicationContext.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                val renderer = PageRenderEngine(dependencies.resources())
                when (format) {
                    DocumentExportFormat.PAGE_IMAGE -> writePageImage(snapshot, renderer, output, imageFormat, resolution, quality)
                    DocumentExportFormat.LONG_IMAGE -> writeLongImage(snapshot, renderer, output, imageFormat, resolution, quality)
                    DocumentExportFormat.HYBRID_PDF -> writeHybridPdf(snapshot, renderer, output)
                    DocumentExportFormat.NATIVE_PACKAGE -> NativePackageWriter(dependencies.resources()).write(snapshot, output)
                }
            } ?: return Result.failure()
            Result.success()
        } catch (exception: IOException) {
            Result.failure()
        } catch (exception: IllegalArgumentException) {
            Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ExportWorkerDependencies {
    fun documents(): DocumentRepository

    fun resources(): BackgroundResourceRepository
}

private suspend fun writePageImage(
    snapshot: DocumentSnapshot,
    renderer: PageRenderEngine,
    output: OutputStream,
    imageFormat: ImageFormat,
    resolution: ExportResolution,
    quality: CompressionQuality,
) {
    val page = snapshot.pages.firstOrNull { it.page.id == snapshot.document.lastActivePageId } ?: snapshot.pages.first()
    PageImageEncoder(renderer).write(page, output, imageFormat, resolution, quality)
}

private suspend fun writeLongImage(
    snapshot: DocumentSnapshot,
    renderer: PageRenderEngine,
    output: OutputStream,
    imageFormat: ImageFormat,
    resolution: ExportResolution,
    quality: CompressionQuality,
) {
    val rawWidth = resolution.longEdge()
    val rawGap = (rawWidth / 100).coerceAtLeast(8)
    val rawHeights = snapshot.pages.map { page ->
        (rawWidth * page.page.size.height.toDouble() / page.page.size.width).roundToInt().coerceAtLeast(1)
    }
    val rawTotalHeight = rawHeights.sum().toLong() + rawGap.toLong() * (snapshot.pages.size - 1).coerceAtLeast(0)
    val scaleForPixels = sqrt(MAX_LONG_IMAGE_PIXELS.toDouble() / (rawWidth.toLong() * rawTotalHeight)).coerceAtMost(1.0)
    val scaleForHeight = (MAX_LONG_IMAGE_EDGE.toDouble() / rawTotalHeight).coerceAtMost(1.0)
    val scale = minOf(scaleForPixels, scaleForHeight)
    val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
    val gap = (rawGap * scale).roundToInt().coerceAtLeast(1)
    val heights = rawHeights.map { (it * scale).roundToInt().coerceAtLeast(1) }
    val totalHeight = heights.sum() + gap * (snapshot.pages.size - 1).coerceAtLeast(0)
    val combined = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(combined).apply { drawColor(Color.WHITE) }
    var top = 0
    try {
        snapshot.pages.zip(heights).forEach { (page, height) ->
            val rendered = renderer.renderPage(page, width, height, forceOpaque = imageFormat.isLossy())
            canvas.drawBitmap(rendered, 0f, top.toFloat(), null)
            rendered.recycle()
            top += height + gap
        }
        if (!combined.compress(imageFormat.compressFormat(), quality.percent(), output)) throw IOException("Encoding failed")
    } finally {
        combined.recycle()
    }
}

private suspend fun writeHybridPdf(
    snapshot: DocumentSnapshot,
    renderer: PageRenderEngine,
    output: OutputStream,
) {
    val pdf = PdfDocument()
    try {
        snapshot.pages.forEachIndexed { index, page ->
            val (width, height) = page.outputSize(PDF_LONG_EDGE)
            val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            renderer.drawPage(pdfPage.canvas, page, width, height, forceOpaque = true)
            pdf.finishPage(pdfPage)
        }
        pdf.writeTo(output)
    } finally {
        pdf.close()
    }
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

private inline fun <reified T : Enum<T>> androidx.work.Data.enumValue(key: String): T? =
    getString(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private const val PDF_LONG_EDGE = 1_440
private const val MAX_LONG_IMAGE_EDGE = 30_000
private const val MAX_LONG_IMAGE_PIXELS = 36_000_000L
