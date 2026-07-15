package com.note.handwrite.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.core.content.FileProvider
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NormalizedPoint
import com.note.handwrite.model.Stroke
import java.io.File
import java.io.FileOutputStream

fun exportToClipboard(
    context: Context,
    strokes: List<Stroke>,
    backgroundType: BackgroundType,
    canvasWidth: Int,
    canvasHeight: Int,
    density: Float
): Boolean {
    if (canvasWidth <= 0 || canvasHeight <= 0) return false

    return try {
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        drawBackgroundOnAndroidCanvas(canvas, backgroundType, canvasWidth, canvasHeight, density)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            paint.color = stroke.color.toAndroidColor()
            paint.strokeWidth = stroke.width * density
            canvas.drawPath(
                buildAndroidPath(stroke.points, canvasWidth.toFloat(), canvasHeight.toFloat()),
                paint
            )
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    point.x * canvasWidth,
                    point.y * canvasHeight,
                    stroke.width * density / 2f,
                    paint
                )
                paint.style = Paint.Style.STROKE
            }
        }

        val tempFile = File(context.cacheDir, "note_${System.currentTimeMillis()}.png")
        FileOutputStream(tempFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "随手写笔记", uri))
        bitmap.recycle()
        true
    } catch (_: Exception) {
        false
    }
}

private fun androidx.compose.ui.graphics.Color.toAndroidColor(): Int =
    android.graphics.Color.argb(
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
    density: Float
) {
    val spacing = 40f * density
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        strokeWidth = density
    }
    when (type) {
        BackgroundType.PLAIN -> Unit
        BackgroundType.LINED -> {
            var y = spacing
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
                y += spacing
            }
        }
        BackgroundType.GRID -> {
            var x = spacing
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
                x += spacing
            }
            var y = spacing
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
                y += spacing
            }
        }
    }
}

private fun buildAndroidPath(
    points: List<NormalizedPoint>,
    canvasWidth: Float,
    canvasHeight: Float
): AndroidPath {
    val path = AndroidPath()
    if (points.isEmpty()) return path
    val first = points.first()
    path.moveTo(first.x * canvasWidth, first.y * canvasHeight)
    if (points.size == 1) return path
    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        path.quadTo(
            current.x * canvasWidth,
            current.y * canvasHeight,
            (current.x + next.x) / 2f * canvasWidth,
            (current.y + next.y) / 2f * canvasHeight
        )
    }
    val last = points.last()
    path.lineTo(last.x * canvasWidth, last.y * canvasHeight)
    return path
}
