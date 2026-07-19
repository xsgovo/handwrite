package com.xsgovo.handwrite

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.rendering.PageImageEncoder
import com.xsgovo.handwrite.core.rendering.PageRenderEngine
import com.xsgovo.handwrite.core.rendering.pageImageFileFormat
import com.xsgovo.handwrite.feature.editor.EditorShareRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageImageSharer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    resources: BackgroundResourceRepository,
) {
    private val encoder = PageImageEncoder(PageRenderEngine(resources))

    suspend fun share(request: EditorShareRequest, settings: AppSettings): Result<Unit> = try {
        val image = withContext(Dispatchers.IO) { createImage(request, settings) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            image.file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = image.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, request.documentName)
            clipData = ClipData.newUri(context.contentResolver, request.documentName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "分享图片").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    private suspend fun createImage(
        request: EditorShareRequest,
        settings: AppSettings,
    ): ShareableImage {
        val directory = File(context.cacheDir, SHARED_IMAGES_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create share cache")
        pruneExpiredImages(directory)

        val format = settings.imageFormat.pageImageFileFormat()
        val file = File.createTempFile("note-", ".${format.fileExtension}", directory)
        try {
            file.outputStream().buffered().use { output ->
                encoder.write(
                    content = request.pageContent,
                    output = output,
                    imageFormat = settings.imageFormat,
                    resolution = settings.exportResolution,
                    quality = settings.compressionQuality,
                )
            }
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
        return ShareableImage(file, format.mimeType)
    }

    private fun pruneExpiredImages(directory: File) {
        val cutoff = System.currentTimeMillis() - SHARED_IMAGE_MAX_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    private data class ShareableImage(
        val file: File,
        val mimeType: String,
    )

    private companion object {
        const val SHARED_IMAGES_DIRECTORY = "shared_notes"
        const val SHARED_IMAGE_MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
