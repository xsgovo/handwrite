package com.xsgovo.handwrite.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 可爱、简洁、扁平化的应用 UI 图标集。
 * 统一采用 24dp 网格、圆润端帽/拐角与柔和双色扁平填充（前景 100% + 辅层 24% 透明度），
 * 在任意主题色下都能自动保持一致的双层扁平质感。
 */
object HandwriteIcons {
    private val InkColor = SolidColor(Color.Black)
    private const val STROKE_WIDTH = 1.95f
    private const val BOLD_STROKE_WIDTH = 2.3f
    private const val SOFT_FILL_ALPHA = 0.24f

    val ArrowBack: ImageVector by lazy {
        flatIcon("Handwrite.ArrowBack", autoMirror = true) {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18.5f, 12f)
                lineTo(6f, 12f)
                moveTo(11.2f, 6.8f)
                quadTo(8.2f, 9.6f, 5.8f, 11.7f)
                quadTo(5.4f, 12f, 5.8f, 12.3f)
                quadTo(8.2f, 14.4f, 11.2f, 17.2f)
            }
        }
    }

    val Add: ImageVector by lazy {
        flatIcon("Handwrite.Add") {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                moveTo(5.5f, 12f)
                lineTo(18.5f, 12f)
            }
        }
    }

    val Remove: ImageVector by lazy {
        flatIcon("Handwrite.Remove") {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5.5f, 12f)
                lineTo(18.5f, 12f)
            }
        }
    }

    val Check: ImageVector by lazy {
        flatIcon("Handwrite.Check") {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 12.4f)
                lineTo(9.8f, 16.8f)
                lineTo(18.5f, 7.6f)
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        flatIcon("Handwrite.MoreVert") {
            path(fill = InkColor) {
                addCircle(12f, 6f, 1.85f)
                addCircle(12f, 12f, 1.85f)
                addCircle(12f, 18f, 1.85f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        flatIcon("Handwrite.Settings") {
            // 可爱圆润八瓣花形齿轮外框 + 扁平柔填
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                val lobes = 8
                val outerR = 9.2f
                val innerR = 7.0f
                val step = (2.0 * PI) / lobes
                val startAngle = -PI / 2.0
                moveTo(
                    12f + (innerR * cos(startAngle - step * 0.5)).toFloat(),
                    12f + (innerR * sin(startAngle - step * 0.5)).toFloat(),
                )
                for (i in 0 until lobes) {
                    val mid = startAngle + i * step
                    val next = startAngle + (i + 0.5) * step
                    quadTo(
                        12f + (outerR * 1.12f * cos(mid)).toFloat(),
                        12f + (outerR * 1.12f * sin(mid)).toFloat(),
                        12f + (innerR * cos(next)).toFloat(),
                        12f + (innerR * sin(next)).toFloat(),
                    )
                }
                close()
            }
            path(
                fill = InkColor,
                fillAlpha = 0.12f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
            ) {
                addCircle(12f, 12f, 2.9f)
            }
        }
    }

    val Pen: ImageVector by lazy {
        flatIcon("Handwrite.Pen") {
            // 柔和扁平笔身填充
            path(fill = InkColor, fillAlpha = SOFT_FILL_ALPHA) {
                moveTo(6.2f, 13.6f)
                lineTo(14.5f, 5.3f)
                curveTo(16.1f, 3.7f, 20.3f, 7.9f, 18.7f, 9.5f)
                lineTo(10.4f, 17.8f)
                close()
            }
            // 可爱圆润笔头实心点缀
            path(fill = InkColor, fillAlpha = 0.85f) {
                moveTo(5.1f, 17.4f)
                lineTo(6.6f, 18.9f)
                lineTo(5.6f, 19.1f)
                quadTo(4.7f, 19.3f, 4.9f, 18.4f)
                close()
            }
            // 圆润轮廓与笔帽横环
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 13.6f)
                lineTo(14.5f, 5.3f)
                curveTo(16.1f, 3.7f, 20.3f, 7.9f, 18.7f, 9.5f)
                lineTo(10.4f, 17.8f)
                lineTo(5.6f, 19.1f)
                quadTo(4.7f, 19.3f, 4.9f, 18.4f)
                close()
                moveTo(12.8f, 7.0f)
                lineTo(17.0f, 11.2f)
                moveTo(6.2f, 13.6f)
                lineTo(10.4f, 17.8f)
            }
        }
    }

    val EraserStroke: ImageVector by lazy {
        flatIcon("Handwrite.EraserStroke") {
            // 纸套部分扁平填色
            path(fill = InkColor, fillAlpha = 0.28f) {
                moveTo(8.8f, 10.6f)
                lineTo(13.3f, 6.1f)
                quadTo(14.6f, 4.8f, 15.9f, 6.1f)
                lineTo(18.6f, 8.8f)
                quadTo(19.9f, 10.1f, 18.6f, 11.4f)
                lineTo(14.1f, 15.9f)
                close()
            }
            // 圆润块状橡皮轮廓 + 轻扫底线
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.4f, 14.0f)
                lineTo(13.3f, 6.1f)
                quadTo(14.6f, 4.8f, 15.9f, 6.1f)
                lineTo(18.6f, 8.8f)
                quadTo(19.9f, 10.1f, 18.6f, 11.4f)
                lineTo(10.7f, 19.3f)
                quadTo(9.4f, 20.4f, 8.1f, 19.3f)
                lineTo(5.4f, 16.6f)
                quadTo(4.1f, 15.3f, 5.4f, 14.0f)
                close()
                moveTo(8.8f, 10.6f)
                lineTo(14.1f, 15.9f)
                moveTo(13.2f, 19.5f)
                lineTo(19.8f, 19.5f)
            }
        }
    }

    val EraserPixel: ImageVector by lazy {
        flatIcon("Handwrite.EraserPixel") {
            // 左上圆润小橡皮
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.0f, 12.8f)
                lineTo(11.6f, 6.2f)
                quadTo(12.8f, 5.0f, 14.0f, 6.2f)
                lineTo(16.6f, 8.8f)
                quadTo(17.8f, 10.0f, 16.6f, 11.2f)
                lineTo(10.0f, 17.8f)
                quadTo(8.8f, 19.0f, 7.6f, 17.8f)
                lineTo(5.0f, 15.2f)
                quadTo(3.8f, 14.0f, 5.0f, 12.8f)
                close()
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(7.9f, 9.9f)
                lineTo(12.9f, 14.9f)
            }
            // 可爱圆润像素碎屑点
            path(fill = InkColor) {
                addRoundedRect(14.6f, 16.2f, 17.4f, 19.0f, 0.8f)
                addRoundedRect(18.2f, 12.6f, 20.6f, 15.0f, 0.7f)
                addCircle(19.3f, 18.2f, 1.15f)
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        flatIcon("Handwrite.FolderOpen") {
            // 文件夹背板与便签页
            path(
                fill = InkColor,
                fillAlpha = 0.18f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 8.2f)
                lineTo(4f, 6.8f)
                quadTo(4f, 5.2f, 5.6f, 5.2f)
                lineTo(9.4f, 5.2f)
                quadTo(10.5f, 5.2f, 11.2f, 6.1f)
                lineTo(12.2f, 7.4f)
                lineTo(18.4f, 7.4f)
                quadTo(20f, 7.4f, 20f, 9.0f)
                lineTo(20f, 10.6f)
            }
            // 前方可爱圆润收纳袋
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(3.6f, 10.0f, 20.4f, 19.0f, 3.0f)
            }
            // 袋面小标签短线
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9.5f, 14.5f)
                lineTo(14.5f, 14.5f)
            }
        }
    }

    val PageManager: ImageVector by lazy {
        flatIcon("Handwrite.PageManager") {
            // 左上当前高亮页缩略图（较深柔填，强调当前聚焦页）
            path(
                fill = InkColor,
                fillAlpha = 0.36f,
                stroke = InkColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(4.0f, 3.0f, 10.2f, 10.5f, 1.7f)
            }
            // 其余三张可爱竖版页面缩略图（柔和扁平填充）
            path(
                fill = InkColor,
                fillAlpha = 0.14f,
                stroke = InkColor,
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(13.8f, 3.0f, 20.0f, 10.5f, 1.7f)
                addRoundedRect(4.0f, 13.5f, 10.2f, 21.0f, 1.7f)
                addRoundedRect(13.8f, 13.5f, 20.0f, 21.0f, 1.7f)
            }
        }
    }

    val NoteDocument: ImageVector by lazy {
        flatIcon("Handwrite.NoteDocument") {
            // 纸面柔填
            path(fill = InkColor, fillAlpha = 0.16f) {
                moveTo(5.5f, 6.2f)
                quadTo(5.5f, 3.8f, 7.9f, 3.8f)
                lineTo(14.2f, 3.8f)
                lineTo(18.5f, 8.1f)
                lineTo(18.5f, 17.8f)
                quadTo(18.5f, 20.2f, 16.1f, 20.2f)
                lineTo(7.9f, 20.2f)
                quadTo(5.5f, 20.2f, 5.5f, 17.8f)
                close()
            }
            // 折角三角形柔和强调
            path(fill = InkColor, fillAlpha = 0.30f) {
                moveTo(14.2f, 3.8f)
                lineTo(14.2f, 6.7f)
                quadTo(14.2f, 8.1f, 15.6f, 8.1f)
                lineTo(18.5f, 8.1f)
                close()
            }
            // 圆润轮廓与笔记线条
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 6.2f)
                quadTo(5.5f, 3.8f, 7.9f, 3.8f)
                lineTo(14.2f, 3.8f)
                lineTo(18.5f, 8.1f)
                lineTo(18.5f, 17.8f)
                quadTo(18.5f, 20.2f, 16.1f, 20.2f)
                lineTo(7.9f, 20.2f)
                quadTo(5.5f, 20.2f, 5.5f, 17.8f)
                close()
                moveTo(14.2f, 3.8f)
                lineTo(14.2f, 6.7f)
                quadTo(14.2f, 8.1f, 15.6f, 8.1f)
                lineTo(18.5f, 8.1f)
                moveTo(8.5f, 12.0f)
                lineTo(15.5f, 12.0f)
                moveTo(8.5f, 15.6f)
                lineTo(13.2f, 15.6f)
            }
        }
    }

    val Share: ImageVector by lazy {
        flatIcon("Handwrite.Share") {
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8.5f, 10.7f)
                lineTo(14.3f, 7.3f)
                moveTo(8.5f, 13.3f)
                lineTo(14.3f, 16.7f)
            }
            path(
                fill = InkColor,
                fillAlpha = 0.28f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
            ) {
                addCircle(6.2f, 12f, 2.5f)
                addCircle(16.6f, 6.2f, 2.5f)
                addCircle(16.6f, 17.8f, 2.5f)
            }
        }
    }

    val Download: ImageVector by lazy {
        flatIcon("Handwrite.Download") {
            path(fill = InkColor, fillAlpha = SOFT_FILL_ALPHA) {
                addRoundedRect(4.5f, 14.5f, 19.5f, 19.5f, 2.2f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                lineTo(12f, 14.0f)
                moveTo(8.2f, 10.6f)
                lineTo(12f, 14.4f)
                lineTo(15.8f, 10.6f)
                moveTo(4.6f, 14.8f)
                lineTo(4.6f, 17.2f)
                quadTo(4.6f, 19.4f, 6.8f, 19.4f)
                lineTo(17.2f, 19.4f)
                quadTo(19.4f, 19.4f, 19.4f, 17.2f)
                lineTo(19.4f, 14.8f)
            }
        }
    }

    val Undo: ImageVector by lazy {
        flatIcon("Handwrite.Undo", autoMirror = true) {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 5.4f)
                lineTo(5.0f, 8.9f)
                lineTo(8.6f, 12.4f)
                moveTo(5.5f, 8.9f)
                lineTo(14.0f, 8.9f)
                curveTo(17.6f, 8.9f, 19.4f, 11.2f, 19.4f, 13.7f)
                curveTo(19.4f, 16.3f, 17.5f, 18.5f, 14.0f, 18.5f)
                lineTo(9.2f, 18.5f)
            }
        }
    }

    val Redo: ImageVector by lazy {
        flatIcon("Handwrite.Redo", autoMirror = true) {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15.4f, 5.4f)
                lineTo(19.0f, 8.9f)
                lineTo(15.4f, 12.4f)
                moveTo(18.5f, 8.9f)
                lineTo(10.0f, 8.9f)
                curveTo(6.4f, 8.9f, 4.6f, 11.2f, 4.6f, 13.7f)
                curveTo(4.6f, 16.3f, 6.5f, 18.5f, 10.0f, 18.5f)
                lineTo(14.8f, 18.5f)
            }
        }
    }

    val ClearPage: ImageVector by lazy {
        flatIcon("Handwrite.ClearPage") {
            // 竖直圆润扫把柄
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(10.6f, 3.2f, 13.4f, 8.9f, 1.4f)
            }
            // 横箍束腰
            path(
                fill = InkColor,
                fillAlpha = 0.34f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(7.4f, 8.9f, 16.6f, 12.1f, 1.6f)
            }
            // 宽扇形圆润刷毛主体
            path(
                fill = InkColor,
                fillAlpha = 0.16f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.8f, 12.1f)
                lineTo(16.2f, 12.1f)
                lineTo(19.0f, 18.2f)
                quadTo(19.5f, 19.8f, 17.7f, 19.8f)
                lineTo(6.3f, 19.8f)
                quadTo(4.5f, 19.8f, 5.0f, 18.2f)
                close()
            }
            // 两条清晰刷毛齿线
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9.8f, 16.0f)
                lineTo(9.4f, 19.6f)
                moveTo(14.2f, 16.0f)
                lineTo(14.6f, 19.6f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        flatIcon("Handwrite.Lock") {
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(5.2f, 10.6f, 18.8f, 20.2f, 3.2f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8.5f, 10.6f)
                lineTo(8.5f, 7.6f)
                curveTo(8.5f, 5.1f, 10.1f, 3.8f, 12f, 3.8f)
                curveTo(13.9f, 3.8f, 15.5f, 5.1f, 15.5f, 7.6f)
                lineTo(15.5f, 10.6f)
                moveTo(12f, 15.4f)
                lineTo(12f, 17.0f)
            }
            path(fill = InkColor) {
                addCircle(12f, 14.6f, 1.3f)
            }
        }
    }

    val LockOpen: ImageVector by lazy {
        flatIcon("Handwrite.LockOpen") {
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(5.2f, 10.6f, 18.8f, 20.2f, 3.2f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8.5f, 10.6f)
                lineTo(8.5f, 7.4f)
                curveTo(8.5f, 5.0f, 10.1f, 3.8f, 12.1f, 3.8f)
                curveTo(13.9f, 3.8f, 15.4f, 4.9f, 15.6f, 6.8f)
                moveTo(12f, 15.4f)
                lineTo(12f, 17.0f)
            }
            path(fill = InkColor) {
                addCircle(12f, 14.6f, 1.3f)
            }
        }
    }

    val Background: ImageVector by lazy {
        flatIcon("Handwrite.Background") {
            // 软绵绵的小山丘填充
            path(fill = InkColor, fillAlpha = 0.28f) {
                moveTo(3.8f, 16.5f)
                quadTo(7.4f, 11.4f, 11.2f, 14.6f)
                quadTo(15.2f, 10.6f, 20.2f, 15.2f)
                lineTo(20.2f, 17.0f)
                quadTo(20.2f, 19.6f, 17.6f, 19.6f)
                lineTo(6.4f, 19.6f)
                quadTo(3.8f, 19.6f, 3.8f, 17.0f)
                close()
            }
            // 可爱圆润小太阳
            path(fill = InkColor, fillAlpha = 0.85f) {
                addCircle(8.4f, 9.2f, 1.75f)
            }
            // 外框与山丘线
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(3.8f, 4.4f, 20.2f, 19.6f, 3.2f)
                moveTo(4.0f, 16.4f)
                quadTo(7.4f, 11.4f, 11.2f, 14.6f)
                quadTo(15.2f, 10.6f, 20.0f, 15.0f)
            }
        }
    }

    val AddImage: ImageVector by lazy {
        flatIcon("Handwrite.AddImage") {
            path(fill = InkColor, fillAlpha = SOFT_FILL_ALPHA) {
                moveTo(3.8f, 16.2f)
                quadTo(7.4f, 12.2f, 11.2f, 15.0f)
                quadTo(14.8f, 12.0f, 18.6f, 15.8f)
                lineTo(18.6f, 17.6f)
                quadTo(18.6f, 19.8f, 16.4f, 19.8f)
                lineTo(6.0f, 19.8f)
                quadTo(3.8f, 19.8f, 3.8f, 17.6f)
                close()
            }
            path(fill = InkColor, fillAlpha = 0.85f) {
                addCircle(8.0f, 9.6f, 1.55f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 带右上留白的圆角画框
                moveTo(13.2f, 5.2f)
                lineTo(6.4f, 5.2f)
                quadTo(3.8f, 5.2f, 3.8f, 7.8f)
                lineTo(3.8f, 17.2f)
                quadTo(3.8f, 19.8f, 6.4f, 19.8f)
                lineTo(16.0f, 19.8f)
                quadTo(18.6f, 19.8f, 18.6f, 17.2f)
                lineTo(18.6f, 11.4f)
                // 山丘线
                moveTo(4.0f, 16.2f)
                quadTo(7.4f, 12.2f, 11.2f, 15.0f)
                quadTo(14.8f, 12.0f, 18.4f, 15.6f)
                // 右上小加号
                moveTo(18.2f, 3.8f)
                lineTo(18.2f, 8.6f)
                moveTo(15.8f, 6.2f)
                lineTo(20.6f, 6.2f)
            }
        }
    }

    val AddPage: ImageVector by lazy {
        flatIcon("Handwrite.AddPage") {
            path(
                fill = InkColor,
                fillAlpha = 0.20f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(5.2f, 3.8f, 18.8f, 20.2f, 3.0f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 9.0f)
                lineTo(12f, 15.0f)
                moveTo(9.0f, 12f)
                lineTo(15.0f, 12f)
            }
        }
    }

    val DeletePage: ImageVector by lazy {
        flatIcon("Handwrite.DeletePage") {
            path(
                fill = InkColor,
                fillAlpha = 0.20f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                addRoundedRect(5.2f, 3.8f, 18.8f, 20.2f, 3.0f)
            }
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9.0f, 12f)
                lineTo(15.0f, 12f)
            }
        }
    }

    val Delete: ImageVector by lazy {
        flatIcon("Handwrite.Delete") {
            // 可爱圆润小垃圾桶身
            path(
                fill = InkColor,
                fillAlpha = SOFT_FILL_ALPHA,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.8f, 7.6f)
                lineTo(17.2f, 7.6f)
                lineTo(16.4f, 17.6f)
                quadTo(16.2f, 19.6f, 14.2f, 19.6f)
                lineTo(9.8f, 19.6f)
                quadTo(7.8f, 19.6f, 7.6f, 17.6f)
                close()
            }
            path(
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 桶盖与小提手
                moveTo(5.0f, 7.6f)
                lineTo(19.0f, 7.6f)
                moveTo(9.4f, 7.6f)
                lineTo(9.4f, 5.8f)
                quadTo(9.4f, 4.5f, 10.7f, 4.5f)
                lineTo(13.3f, 4.5f)
                quadTo(14.6f, 4.5f, 14.6f, 5.8f)
                lineTo(14.6f, 7.6f)
                // 两条圆润竖纹
                moveTo(10.3f, 11.2f)
                lineTo(10.5f, 15.8f)
                moveTo(13.7f, 11.2f)
                lineTo(13.5f, 15.8f)
            }
        }
    }

    val BookmarkFilled: ImageVector by lazy {
        flatIcon("Handwrite.BookmarkFilled") {
            path(
                fill = InkColor,
                pathFillType = PathFillType.EvenOdd,
                stroke = InkColor,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.6f, 6.2f)
                quadTo(6.6f, 4.0f, 8.8f, 4.0f)
                lineTo(15.2f, 4.0f)
                quadTo(17.4f, 4.0f, 17.4f, 6.2f)
                lineTo(17.4f, 19.0f)
                quadTo(17.4f, 20.1f, 16.4f, 19.5f)
                lineTo(12.0f, 16.4f)
                lineTo(7.6f, 19.5f)
                quadTo(6.6f, 20.1f, 6.6f, 19.0f)
                close()
                // 丝带中央可爱小镂空圆点
                addCircle(12f, 9.6f, 1.4f)
            }
        }
    }

    val BookmarkOutline: ImageVector by lazy {
        flatIcon("Handwrite.BookmarkOutline") {
            path(
                fill = InkColor,
                fillAlpha = 0.16f,
                stroke = InkColor,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.6f, 6.2f)
                quadTo(6.6f, 4.0f, 8.8f, 4.0f)
                lineTo(15.2f, 4.0f)
                quadTo(17.4f, 4.0f, 17.4f, 6.2f)
                lineTo(17.4f, 19.0f)
                quadTo(17.4f, 20.1f, 16.4f, 19.5f)
                lineTo(12.0f, 16.4f)
                lineTo(7.6f, 19.5f)
                quadTo(6.6f, 20.1f, 6.6f, 19.0f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        flatIcon("Handwrite.Close") {
            path(
                stroke = InkColor,
                strokeLineWidth = BOLD_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(7.0f, 7.0f)
                lineTo(17.0f, 17.0f)
                moveTo(17.0f, 7.0f)
                lineTo(7.0f, 17.0f)
            }
        }
    }

    private inline fun flatIcon(
        name: String,
        autoMirror: Boolean = false,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(block).build()

    private fun PathBuilder.addRoundedRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ) {
        val cr = radius.coerceAtMost(minOf(right - left, bottom - top) / 2f)
        moveTo(left + cr, top)
        lineTo(right - cr, top)
        quadTo(right, top, right, top + cr)
        lineTo(right, bottom - cr)
        quadTo(right, bottom, right - cr, bottom)
        lineTo(left + cr, bottom)
        quadTo(left, bottom, left, bottom - cr)
        lineTo(left, top + cr)
        quadTo(left, top, left + cr, top)
        close()
    }

    private fun PathBuilder.addCircle(cx: Float, cy: Float, r: Float) {
        val k = 0.5522848f * r
        moveTo(cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        close()
    }
}
