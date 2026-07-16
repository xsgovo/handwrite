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
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Color
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.CanvasPoint
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.geometry.Offset

fun saveNoteToGallery(
    context: Context,
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    canvasWidth: Int,
    canvasHeight: Int,
    zoomPercent: Float,
    pan: Offset,
    density: Float
): Uri? {
    if (canvasWidth <= 0 || canvasHeight <= 0) return null

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

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

    return try {
        val bitmap = renderNoteBitmap(
            strokes = strokes,
            backgroundType = backgroundType,
            width = canvasWidth,
            height = canvasHeight,
            zoomPercent = zoomPercent,
            pan = pan,
            density = density
        )
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } ?: error("Unable to open gallery output stream")
        bitmap.recycle()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        }
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

fun shareImage(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "分享随手写图片").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}

fun shareNoteDirectly(
    context: Context,
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    canvasWidth: Int,
    canvasHeight: Int,
    zoomPercent: Float,
    pan: Offset,
    density: Float
): Boolean {
    if (canvasWidth <= 0 || canvasHeight <= 0) return false

    return try {
        val bitmap = renderNoteBitmap(
            strokes,
            backgroundType,
            canvasWidth,
            canvasHeight,
            zoomPercent,
            pan,
            density
        )
        val file = File(context.cacheDir, "shared_note_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
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
        true
    } catch (_: Exception) {
        false
    }
}

private fun renderNoteBitmap(
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    width: Int,
    height: Int,
    zoomPercent: Float,
    pan: Offset,
    density: Float
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val transform = CanvasTransform(
        sourceWidth = width.toFloat(),
        sourceHeight = height.toFloat(),
        targetWidth = width.toFloat(),
        targetHeight = height.toFloat(),
        zoomPercent = zoomPercent,
        panX = pan.x,
        panY = pan.y
    )
    canvas.drawColor(android.graphics.Color.WHITE)
    drawBackgroundOnAndroidCanvas(
        canvas, backgroundType, width, height,
        transform.sourceWidth.toInt(), transform.sourceHeight.toInt(), transform, density
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toAndroidColor()
        paint.strokeWidth = stroke.width * density * transform.scale
        canvas.drawPath(
            buildAndroidPath(stroke.points.map(transform::map)),
            paint
        )
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                transform.map(point).x,
                transform.map(point).y,
                stroke.width * density * transform.scale / 2f,
                paint
            )
            paint.style = Paint.Style.STROKE
        }
    }
    return bitmap
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
    transform: CanvasTransform,
    density: Float
) {
    val spacing = 40f * density
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        strokeWidth = density * transform.scale
    }
    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = spacing
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth.toFloat(), y))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                y += spacing
            }
        }
        BackgroundType.GRID -> {
            var x = spacing
            while (x < logicalWidth) {
                val start = transform.map(CanvasPoint(x, 0f))
                val end = transform.map(CanvasPoint(x, logicalHeight.toFloat()))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                x += spacing
            }
            var y = spacing
            while (y < logicalHeight) {
                val start = transform.map(CanvasPoint(0f, y))
                val end = transform.map(CanvasPoint(logicalWidth.toFloat(), y))
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                y += spacing
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
