package com.xsgovo.handwrite.feature.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.designsystem.HandwriteTheme
import com.xsgovo.handwrite.core.designsystem.icons.CuteFlatSlider
import com.xsgovo.handwrite.core.designsystem.icons.CuteIconBadge
import com.xsgovo.handwrite.core.designsystem.icons.CuteValuePill
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcon
import com.xsgovo.handwrite.core.designsystem.icons.HandwriteIcons
import kotlin.math.roundToInt

private val BRUSH_WIDTH_QUICK_PRESETS = listOf(12, 32, 60, 90)
private val ERASER_SIZE_QUICK_PRESETS = listOf(300, 600, 1100, 1800)

@Composable
internal fun BrushWidthDropdownMenu(
    expanded: Boolean,
    widthStep: Float,
    activeColorArgb: Int,
    onPendingWidthChange: (Float) -> Unit,
    onCommitWidth: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        BrushWidthMenuContent(
            widthStep = widthStep,
            activeColorArgb = activeColorArgb,
            onPendingWidthChange = onPendingWidthChange,
            onCommitWidth = onCommitWidth,
        )
    }
}

@Composable
private fun BrushWidthMenuContent(
    widthStep: Float,
    activeColorArgb: Int,
    onPendingWidthChange: (Float) -> Unit,
    onCommitWidth: (Int) -> Unit,
) {
    val displayedStep = widthStep.roundToInt().coerceIn(1, 100)
    Column(
        modifier = Modifier
            .width(292.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CuteIconBadge(
                imageVector = HandwriteIcons.Pen,
                contentDescription = null,
                size = 30.dp,
                iconSize = 16.dp,
                shape = RoundedCornerShape(10.dp),
            )
            Text(
                text = stringResource(R.string.editor_brush_width),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            CuteValuePill(text = stringResource(R.string.editor_brush_width_value, displayedStep))
        }

        BrushStrokePreviewStrip(
            widthStep = displayedStep,
            color = Color(activeColorArgb),
        )

        CuteFlatSlider(
            value = widthStep.coerceIn(1f, 100f),
            onValueChange = onPendingWidthChange,
            onValueChangeFinished = { onCommitWidth(widthStep.roundToInt().coerceIn(1, 100)) },
            valueRange = 1f..100f,
            steps = 98,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            BRUSH_WIDTH_QUICK_PRESETS.forEach { preset ->
                val selected = displayedStep == preset
                BrushQuickPresetChip(
                    step = preset,
                    selected = selected,
                    onClick = {
                        onPendingWidthChange(preset.toFloat())
                        onCommitWidth(preset)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BrushStrokePreviewStrip(
    widthStep: Int,
    color: Color,
) {
    val previewShape = RoundedCornerShape(16.dp)
    val previewStrokeDp = (2f + (widthStep.coerceIn(1, 100) - 1) * (20f / 99f)).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(previewShape)
            .then(if (color.alpha < 0.99f) Modifier.checkerboardBackdrop(6.dp) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), previewShape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.68f)
                cubicTo(
                    w * 0.28f, h * 0.05f,
                    w * 0.44f, h * 0.98f,
                    w * 0.72f, h * 0.34f,
                )
                quadraticTo(w * 0.88f, h * 0.08f, w, h * 0.48f)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = previewStrokeDp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun BrushQuickPresetChip(
    step: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "WidthPresetBg",
    )
    val dotColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "WidthPresetDot",
    )
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
    val description = stringResource(R.string.editor_brush_width_preset_description, step)
    val dotDp = (4f + (step.coerceIn(1, 100) - 1) * (13f / 99f)).dp

    Surface(
        onClick = onClick,
        shape = shape,
        color = bgColor,
        border = border,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                drawCircle(
                    color = dotColor,
                    radius = dotDp.toPx() / 2f,
                )
            }
        }
    }
}

@Composable
internal fun EraserDropdownMenu(
    expanded: Boolean,
    eraserMode: EraserMode,
    eraserSize: Float,
    onSelectMode: (EraserMode) -> Unit,
    onPendingSizeChange: (Float) -> Unit,
    onCommitSize: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        EraserMenuContent(
            eraserMode = eraserMode,
            eraserSize = eraserSize,
            onSelectMode = onSelectMode,
            onPendingSizeChange = onPendingSizeChange,
            onCommitSize = onCommitSize,
        )
    }
}

@Composable
private fun EraserMenuContent(
    eraserMode: EraserMode,
    eraserSize: Float,
    onSelectMode: (EraserMode) -> Unit,
    onPendingSizeChange: (Float) -> Unit,
    onCommitSize: (Int) -> Unit,
) {
    val displayedSize = eraserSize.roundToInt().coerceIn(MIN_ERASER_SIZE, MAX_ERASER_SIZE)
    val isPixelMode = eraserMode == EraserMode.PIXEL
    val sizeSectionAlpha by animateFloatAsState(
        targetValue = if (isPixelMode) 1f else 0.62f,
        label = "EraserSizeSectionAlpha",
    )

    Column(
        modifier = Modifier
            .width(304.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            EraserModeCard(
                title = stringResource(R.string.editor_eraser_mode_stroke),
                subtitle = stringResource(R.string.editor_eraser_mode_stroke_desc),
                icon = HandwriteIcons.EraserStroke,
                selected = eraserMode == EraserMode.STROKE,
                onClick = { onSelectMode(EraserMode.STROKE) },
                modifier = Modifier.weight(1f),
            )
            EraserModeCard(
                title = stringResource(R.string.editor_eraser_mode_pixel),
                subtitle = stringResource(R.string.editor_eraser_mode_pixel_desc),
                icon = HandwriteIcons.EraserPixel,
                selected = isPixelMode,
                onClick = { onSelectMode(EraserMode.PIXEL) },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.alpha(sizeSectionAlpha),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CuteIconBadge(
                    imageVector = HandwriteIcons.EraserPixel,
                    contentDescription = null,
                    size = 28.dp,
                    iconSize = 15.dp,
                    shape = RoundedCornerShape(9.dp),
                    containerColor = if (isPixelMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isPixelMode) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = stringResource(R.string.editor_eraser_size),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                if (!isPixelMode) {
                    Text(
                        text = stringResource(R.string.editor_eraser_size_pixel_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                CuteValuePill(
                    text = stringResource(R.string.editor_eraser_size_value, displayedSize),
                    containerColor = if (isPixelMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isPixelMode) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            EraserSizePreviewStrip(eraserSize = displayedSize)

            CuteFlatSlider(
                value = eraserSize.coerceIn(MIN_ERASER_SIZE.toFloat(), MAX_ERASER_SIZE.toFloat()),
                onValueChange = {
                    if (!isPixelMode) onSelectMode(EraserMode.PIXEL)
                    onPendingSizeChange(it)
                },
                onValueChangeFinished = {
                    if (!isPixelMode) onSelectMode(EraserMode.PIXEL)
                    onCommitSize(eraserSize.roundToInt().coerceIn(MIN_ERASER_SIZE, MAX_ERASER_SIZE))
                },
                valueRange = MIN_ERASER_SIZE.toFloat()..MAX_ERASER_SIZE.toFloat(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ERASER_SIZE_QUICK_PRESETS.forEach { preset ->
                    val selected = displayedSize == preset
                    EraserQuickPresetChip(
                        eraserSize = preset,
                        selected = selected && isPixelMode,
                        onClick = {
                            if (!isPixelMode) onSelectMode(EraserMode.PIXEL)
                            onPendingSizeChange(preset.toFloat())
                            onCommitSize(preset)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EraserModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "EraserModeBg",
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "EraserModeFg",
    )
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = bgColor,
        contentColor = fgColor,
        border = border,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CuteIconBadge(
                    imageVector = icon,
                    contentDescription = null,
                    size = 34.dp,
                    iconSize = 20.dp,
                    shape = RoundedCornerShape(11.dp),
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else fgColor,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = fgColor.copy(alpha = 0.82f),
                )
            }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                ) {
                    HandwriteIcon(
                        imageVector = HandwriteIcons.Check,
                        contentDescription = null,
                        size = 12.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EraserSizePreviewStrip(eraserSize: Int) {
    val shape = RoundedCornerShape(16.dp)
    val cursorColor = MaterialTheme.colorScheme.primary
    val haloColor = MaterialTheme.colorScheme.surfaceContainerLow
    val inkGuideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val normalized = ((eraserSize - MIN_ERASER_SIZE).toFloat() / (MAX_ERASER_SIZE - MIN_ERASER_SIZE)).coerceIn(0f, 1f)
    val previewRadiusDp: Dp = (8f + normalized * 14f).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 柔和示例笔迹底纹，让用户直观感受圆形橡皮直径与纸面大小
            val guidePath = Path().apply {
                moveTo(w * 0.14f, h * 0.62f)
                cubicTo(
                    w * 0.32f, h * 0.24f,
                    w * 0.66f, h * 0.80f,
                    w * 0.86f, h * 0.38f,
                )
            }
            drawPath(
                path = guidePath,
                color = inkGuideColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCutePixelEraserCircle(
                center = Offset(w / 2f, h / 2f),
                radiusPx = previewRadiusDp.toPx(),
                accentColor = cursorColor,
                haloColor = haloColor,
            )
        }
    }
}

@Composable
private fun EraserQuickPresetChip(
    eraserSize: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "EraserPresetBg",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "EraserPresetRing",
    )
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
    val description = stringResource(R.string.editor_eraser_size_preset_description, eraserSize)
    val normalized = ((eraserSize - MIN_ERASER_SIZE).toFloat() / (MAX_ERASER_SIZE - MIN_ERASER_SIZE)).coerceIn(0f, 1f)
    val diameterDp = (8f + normalized * 12f).dp

    Surface(
        onClick = onClick,
        shape = shape,
        color = bgColor,
        border = border,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) {
                val radius = diameterDp.toPx() / 2f
                drawCircle(
                    color = ringColor.copy(alpha = 0.16f),
                    radius = radius,
                )
                drawCircle(
                    color = ringColor,
                    radius = radius,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 像素橡皮擦圆形指示器（画布擦除光标与二级菜单实时预览共用）：
 * - 柔和扁平淡色内盘 + 主题背景反差描边环 + 圆头虚线胶囊环 + 中心精准定位小圆点。
 */
internal fun DrawScope.drawCutePixelEraserCircle(
    center: Offset,
    radiusPx: Float,
    accentColor: Color,
    haloColor: Color,
) {
    if (radiusPx <= 0.5f) return
    val dashEffect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(3.5.dp.toPx(), 6.dp.toPx()),
        phase = 0f,
    )
    // 1. 柔和扁平内盘
    drawCircle(
        color = accentColor.copy(alpha = 0.14f),
        radius = radiusPx,
        center = center,
    )
    // 2. 深浅纸面通用的柔和外衬环，保证高对比度与干净边界
    drawCircle(
        color = haloColor.copy(alpha = 0.76f),
        radius = radiusPx,
        center = center,
        style = Stroke(
            width = 3.2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = dashEffect,
        ),
    )
    // 3. 可爱圆头胶囊虚线环
    drawCircle(
        color = accentColor,
        radius = radiusPx,
        center = center,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = dashEffect,
        ),
    )
    // 4. 中心精准定位小圆点（圆盘足够大时显示）
    if (radiusPx >= 8.dp.toPx()) {
        drawCircle(
            color = haloColor.copy(alpha = 0.88f),
            radius = 2.4.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = accentColor,
            radius = 1.4.dp.toPx(),
            center = center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrushWidthMenuPreview() {
    HandwriteTheme {
        BrushWidthMenuContent(
            widthStep = 42f,
            activeColorArgb = 0xFF006C51.toInt(),
            onPendingWidthChange = {},
            onCommitWidth = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EraserMenuPreview() {
    HandwriteTheme {
        EraserMenuContent(
            eraserMode = EraserMode.PIXEL,
            eraserSize = 600f,
            onSelectMode = {},
            onPendingSizeChange = {},
            onCommitSize = {},
        )
    }
}
