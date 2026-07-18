package com.note.handwrite.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Color
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.CanvasPoint
import com.note.handwrite.model.NotePage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val EXPORT_TAG = "GalleryExport"
private val exportMutex = Mutex()

suspend fun saveNoteToGallery(
    context: Context,
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    canvasWidth: Int = NotePage.EXPORT_WIDTH,
    canvasHeight: Int = NotePage.EXPORT_HEIGHT
): Result<Uri> {
    val appContext = context.applicationContext
    return exportMutex.withLock {
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            var pendingUri: Uri? = null
            var published = false
            var bitmap: Bitmap? = null

            try {
                currentCoroutineContext().ensureActive()
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "note_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/随手写"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Unable to create gallery entry")
                pendingUri = uri

                currentCoroutineContext().ensureActive()
                val exportBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                bitmap = exportBitmap
                renderNoteBitmap(exportBitmap, strokes, backgroundType)

                currentCoroutineContext().ensureActive()
                resolver.openOutputStream(uri)?.use { output ->
                    check(exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } ?: throw IOException("Unable to open gallery output stream")

                currentCoroutineContext().ensureActive()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null
                    )
                }
                published = true
                Result.success(uri)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(EXPORT_TAG, "Failed to save note to gallery", exception)
                Result.failure(exception)
            } finally {
                bitmap?.recycle()
                if (!published) {
                    pendingUri?.let { uri -> deletePendingUri(resolver, uri) }
                }
            }
        }
    }
}

fun shareImage(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("image/png", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "分享随手写图片").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}

suspend fun prepareNoteForSharing(
    context: Context,
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    canvasWidth: Int = NotePage.EXPORT_WIDTH,
    canvasHeight: Int = NotePage.EXPORT_HEIGHT
): Result<Uri> {
    val appContext = context.applicationContext
    var cacheFile: File? = null
    var prepared = false

    return exportMutex.withLock {
        try {
            withContext(Dispatchers.IO) {
                var bitmap: Bitmap? = null
                try {
                    currentCoroutineContext().ensureActive()
                    val exportBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                    bitmap = exportBitmap
                    renderNoteBitmap(exportBitmap, strokes, backgroundType)

                    currentCoroutineContext().ensureActive()
                    val file = File(appContext.cacheDir, "shared_note_${System.currentTimeMillis()}.png")
                    cacheFile = file
                    FileOutputStream(file).use { output ->
                        check(exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }

                    currentCoroutineContext().ensureActive()
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file
                    )
                    currentCoroutineContext().ensureActive()
                    prepared = true
                    Result.success(uri)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(EXPORT_TAG, "Failed to prepare note for sharing", exception)
                    Result.failure(exception)
                } finally {
                    bitmap?.recycle()
                    if (!prepared) deleteCacheFile(cacheFile)
                }
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                deleteCacheFile(cacheFile)
            }
            throw exception
        }
    }
}

private fun renderNoteBitmap(
    bitmap: Bitmap,
    strokes: List<Stroke>,
    backgroundType: BackgroundType
) {
    val width = bitmap.width
    val height = bitmap.height
    val canvas = AndroidCanvas(bitmap)
    val transform = CanvasTransform(
        sourceWidth = NotePage.WIDTH,
        sourceHeight = NotePage.HEIGHT,
        targetWidth = width.toFloat(),
        targetHeight = height.toFloat()
    )
    canvas.drawColor(android.graphics.Color.WHITE)
    drawBackgroundOnAndroidCanvas(
        canvas, backgroundType, width, height,
        NotePage.WIDTH.toInt(), NotePage.HEIGHT.toInt(), transform
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toAndroidColor()
        paint.strokeWidth = stroke.width * transform.scale
        stroke.paths().forEach { points ->
            canvas.drawPath(buildAndroidPath(points.map(transform::map)), paint)
            if (points.size == 1) {
                val point = points.first()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    transform.map(point).x,
                    transform.map(point).y,
                    stroke.width * transform.scale / 2f,
                    paint
                )
                paint.style = Paint.Style.STROKE
            }
        }
    }
}

private fun deletePendingUri(resolver: android.content.ContentResolver, uri: Uri) {
    try {
        resolver.delete(uri, null, null)
    } catch (exception: Exception) {
        Log.e(EXPORT_TAG, "Failed to delete pending gallery entry", exception)
    }
}

private fun deleteCacheFile(file: File?) {
    if (file == null || !file.exists()) return
    try {
        if (!file.delete()) {
            Log.e(EXPORT_TAG, "Failed to delete incomplete share cache file")
        }
    } catch (exception: Exception) {
        Log.e(EXPORT_TAG, "Failed to delete incomplete share cache file", exception)
    }
}

private fun Color.toAndroidColor(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)

private fun drawBackgroundOnAndroidCanvas(
    canvas: AndroidCanvas,
    type: BackgroundType,
    width: Int,
    height: Int,
    logicalWidth: Int,
    logicalHeight: Int,
    transform: CanvasTransform
) {
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        strokeWidth = NotePage.BACKGROUND_LINE_WIDTH_MM * NotePage.LOGICAL_UNITS_PER_MM * transform.scale
    }
    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = NotePage.LINE_SPACING
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth.toFloat(), y))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                y += NotePage.LINE_SPACING
            }
        }
        BackgroundType.GRID -> {
            var x = NotePage.GRID_SPACING
            while (x < logicalWidth) {
                val start = transform.map(CanvasPoint(x, 0f))
                val end = transform.map(CanvasPoint(x, logicalHeight.toFloat()))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                x += NotePage.GRID_SPACING
            }
            var y = NotePage.GRID_SPACING
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth.toFloat(), y))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                y += NotePage.GRID_SPACING
            }
        }
    }
}

private fun buildAndroidPath(
    points: List<CanvasPoint>
): AndroidPath {
    val path = AndroidPath()
    if (points.isEmpty()) return path
    val first = points.first()
    path.moveTo(first.x, first.y)
    if (points.size == 1) return path
    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        path.quadTo(
            current.x,
            current.y,
            (current.x + next.x) / 2f,
            (current.y + next.y) / 2f
        )
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}
