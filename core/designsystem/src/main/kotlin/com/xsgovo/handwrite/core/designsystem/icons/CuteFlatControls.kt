package com.xsgovo.handwrite.core.designsystem.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.designsystem.HandwriteTheme
import kotlin.math.roundToInt

/**
 * 可爱扁平胶囊滑块：
 * - 10dp 圆角胶囊轨道 + 扁平圆环滑块（无阴影），支持点击跳转与平滑拖拽。
 * - 拖拽时滑块轻微弹性放大，提供直观触感反馈。
 */
@Composable
fun CuteFlatSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbInnerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging && enabled) 1.14f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "CuteSliderThumbScale",
    )
    val resolvedActiveColor by animateColorAsState(
        targetValue = if (enabled) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        label = "CuteSliderActiveColor",
    )
    val resolvedTrackColor by animateColorAsState(
        targetValue = if (enabled) inactiveTrackColor else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        label = "CuteSliderTrackColor",
    )

    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)
    val latestSteps by rememberUpdatedState(steps)
    val latestEnabled by rememberUpdatedState(enabled)

    fun mapXToValue(x: Float, widthPx: Float, thumbRadiusPx: Float): Float {
        val left = thumbRadiusPx
        val right = (widthPx - thumbRadiusPx).coerceAtLeast(left + 1f)
        val frac = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val range = latestRange
        val raw = range.start + frac * (range.endInclusive - range.start)
        if (latestSteps <= 0) return raw
        val stepCount = latestSteps + 1
        val stepSize = (range.endInclusive - range.start) / stepCount
        val snapped = range.start + ((raw - range.start) / stepSize).roundToInt() * stepSize
        return snapped.coerceIn(range.start, range.endInclusive)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                    steps = steps,
                )
            }
            .pointerInput(Unit) {
                val thumbRadiusPx = 11.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!latestEnabled) return@awaitEachGesture
                    down.consume()
                    isDragging = true
                    try {
                        latestOnChange(mapXToValue(down.position.x, size.width.toFloat(), thumbRadiusPx))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                change?.consume()
                                break
                            }
                            change.consume()
                            latestOnChange(mapXToValue(change.position.x, size.width.toFloat(), thumbRadiusPx))
                        }
                        latestOnFinished?.invoke()
                    } finally {
                        isDragging = false
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val thumbRadius = 10.5.dp.toPx()
            val trackHeight = 10.dp.toPx()
            val trackLeft = thumbRadius
            val trackRight = (size.width - thumbRadius).coerceAtLeast(trackLeft + 1f)
            val trackWidth = trackRight - trackLeft
            val centerY = size.height / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            // 扁平底轨
            drawRoundRect(
                color = resolvedTrackColor,
                topLeft = Offset(trackLeft, centerY - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = corner,
            )

            // 激活段填充
            val thumbCenterX = trackLeft + trackWidth * normalized
            val activeWidth = (thumbCenterX - trackLeft).coerceAtLeast(trackHeight / 2f)
            drawRoundRect(
                color = resolvedActiveColor,
                topLeft = Offset(trackLeft, centerY - trackHeight / 2f),
                size = Size(activeWidth.coerceAtMost(trackWidth), trackHeight),
                cornerRadius = corner,
            )

            // 可爱双层圆环滑块（柔和底晕 + 纯平内圆 + 圆润主色描边）
            val scaledRadius = thumbRadius * thumbScale
            if (isDragging && enabled) {
                drawCircle(
                    color = resolvedActiveColor.copy(alpha = 0.18f),
                    radius = scaledRadius + 4.dp.toPx(),
                    center = Offset(thumbCenterX, centerY),
                )
            }
            drawCircle(
                color = thumbInnerColor,
                radius = scaledRadius,
                center = Offset(thumbCenterX, centerY),
            )
            drawCircle(
                color = resolvedActiveColor,
                radius = scaledRadius - 1.5.dp.toPx(),
                center = Offset(thumbCenterX, centerY),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/**
 * 扁平胶囊数值标签，用于二级菜单标题右侧显示实时参数值。
 */
@Composable
fun CuteValuePill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CuteFlatControlsPreview() {
    HandwriteTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            CuteValuePill("45")
            CuteFlatSlider(
                value = 45f,
                onValueChange = {},
                valueRange = 1f..100f,
            )
        }
    }
}
