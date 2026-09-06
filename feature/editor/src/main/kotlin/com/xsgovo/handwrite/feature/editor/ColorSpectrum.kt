package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun SpectrumArea(activeColor: Int, onColorChanged: (Int) -> Unit) {
    // 光谱编辑维护独立的 HSV 状态：编辑期间直接保持用户设定的 H/S/V，
    // 不从 8-bit 量化后的颜色反推——低饱和/低明度时反推会让色相明显漂移，
    // 表现为拖动三角时色相环指示点跟着变。只有外部颜色改动（色板/候选/Hex）
    // 才重新同步。
    var hsv by remember { mutableStateOf(argbToHsv(activeColor)) }

    // 光谱持有独立的精确 H/S/V；activeColor 与最后一次发出的颜色不一致 = 外部改动
    // （色板/候选/Hex），此时才从量化颜色反推同步。自身写入的回声在同一次组合中
    // 即被识别跳过，拖动三角不会引起色相漂移。比较忽略 alpha：透明度滑杆只改
    // 槽位颜色的 alpha 通道，不应触发 H/S/V 反推。
    var lastEmittedRgb by remember { mutableStateOf<Int?>(null) }
    val activeRgb = activeColor.toOpaqueRgb()
    if (activeRgb != lastEmittedRgb) {
        hsv = argbToHsv(activeColor)
        lastEmittedRgb = activeRgb
    }

    fun edit(hue: Float = hsv.hue, saturation: Float = hsv.saturation, value: Float = hsv.value) {
        val updated = Hsv(
            ((hue % 360f) + 360f) % 360f,
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f),
        )
        hsv = updated
        val color = hsvToArgb(updated.hue, updated.saturation, updated.value)
        lastEmittedRgb = color
        onColorChanged(color)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HsvWheel(
            hue = hsv.hue,
            saturation = hsv.saturation,
            value = hsv.value,
            onHueChange = { hue -> edit(hue = hue) },
            onSvChange = { saturation, value -> edit(saturation = saturation, value = value) },
        )
        HsvSliderRow(
            label = "H",
            trackBrush = Brush.horizontalGradient(hueTrackColors),
            thumbColor = Color(hsvToArgb(hsv.hue, 1f, 1f)),
            value = hsv.hue / 360f,
            valueText = hsv.hue.roundToInt().toString(),
            onValueChange = { fraction -> edit(hue = fraction * 360f) },
        )
        HsvSliderRow(
            label = "S",
            trackBrush = Brush.horizontalGradient(
                listOf(
                    Color(hsvToArgb(hsv.hue, 0f, hsv.value)),
                    Color(hsvToArgb(hsv.hue, 1f, hsv.value)),
                ),
            ),
            thumbColor = Color(hsvToArgb(hsv.hue, hsv.saturation, hsv.value)),
            value = hsv.saturation,
            valueText = (hsv.saturation * 100f).roundToInt().toString(),
            onValueChange = { fraction -> edit(saturation = fraction) },
        )
        HsvSliderRow(
            label = "V",
            trackBrush = Brush.horizontalGradient(
                listOf(
                    Color(hsvToArgb(hsv.hue, hsv.saturation, 0f)),
                    Color(hsvToArgb(hsv.hue, hsv.saturation, 1f)),
                ),
            ),
            thumbColor = Color(hsvToArgb(hsv.hue, hsv.saturation, hsv.value)),
            value = hsv.value,
            valueText = (hsv.value * 100f).roundToInt().toString(),
            onValueChange = { fraction -> edit(value = fraction) },
        )
    }
}

private val hueTrackColors = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

// 拖动起始区域：色相环或明度饱和度三角。手势全程锁定起始区域，跨区路径不影响另一区域。
private enum class WheelRegion { HUE, SV }

@Composable
private fun HsvWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSvChange: (Float, Float) -> Unit,
) {
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }
    val ringWidth = 20.dp
    val ringWidthPx = with(LocalDensity.current) { ringWidth.toPx() }
    val trianglePaddingPx = with(LocalDensity.current) { 8.dp.toPx() }
    // 手势回调经由 rememberUpdatedState 读取最新回调，避免输入宿主重启打断拖动。
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    val currentOnSvChange by rememberUpdatedState(onSvChange)

    // 拖动起始区域锁定：从色相环开始的拖动全程只改色相，路径穿过三角也不影响 S/V；反之亦然。
    fun regionOf(position: Offset): WheelRegion? {
        val width = wheelSize.width.toFloat()
        val height = wheelSize.height.toFloat()
        if (width <= 0f || height <= 0f) return null
        val dx = position.x - width / 2f
        val dy = position.y - height / 2f
        val distance = sqrt(dx * dx + dy * dy)
        val innerRadius = (minOf(width, height) / 2f) - ringWidthPx
        return if (distance >= innerRadius) WheelRegion.HUE else WheelRegion.SV
    }

    fun applyAt(position: Offset, region: WheelRegion) {
        val width = wheelSize.width.toFloat()
        val height = wheelSize.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val dx = position.x - width / 2f
        val dy = position.y - height / 2f
        when (region) {
            WheelRegion.HUE -> {
                val angle = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                currentOnHueChange(angle)
            }
            WheelRegion.SV -> {
                val triangleRadius = (minOf(width, height) / 2f - ringWidthPx) - trianglePaddingPx
                val (saturation, value) = svFromPoint(dx, dy, triangleRadius)
                currentOnSvChange(saturation, value)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { wheelSize = it }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    regionOf(position)?.let { region -> applyAt(position, region) }
                }
            }
            .pointerInput(Unit) {
                // 区域由「按下点」决定并全程锁定：从色相环开始的手势只改色相，
                // 路径穿过三角区域也不影响 S/V；从三角开始的手势反之亦然。
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startRegion = regionOf(down.position) ?: return@awaitEachGesture
                    drag(down.id) { change ->
                        applyAt(change.position, startRegion)
                        change.consume()
                    }
                }
            },
    ) {
        val outerRadius = minOf(size.width, size.height) / 2f
        val ringWidthPx = ringWidth.toPx()
        val innerRadius = outerRadius - ringWidthPx
        val center = Offset(size.width / 2f, size.height / 2f)
        // 色相环
        drawCircle(
            brush = Brush.sweepGradient(hueTrackColors, center),
            radius = outerRadius - ringWidthPx / 2f,
            style = Stroke(ringWidthPx),
        )
        // 固定朝向的饱和度明度三角形，纯色顶点在右侧并随当前颜色变化。
        val triangleRadius = innerRadius - trianglePaddingPx
        val hueColor = Color(hsvToArgb(hue, 1f, 1f))
        val apex = center + Offset(triangleRadius, 0f)
        val white = center + Offset(-triangleRadius / 2f, -triangleRadius * 0.8660254f)
        val black = center + Offset(-triangleRadius / 2f, triangleRadius * 0.8660254f)
        val triangle = Path().apply {
            moveTo(apex.x, apex.y)
            lineTo(white.x, white.y)
            lineTo(black.x, black.y)
            close()
        }
        drawPath(
            triangle,
            Brush.verticalGradient(listOf(Color.White, Color.Black), startY = white.y, endY = black.y),
        )
        drawPath(
            triangle,
            Brush.horizontalGradient(listOf(Color.Transparent, hueColor), startX = white.x, endX = apex.x),
        )
        // 实心圆指示点：白色描边 + 当前颜色填充；色相环指示点固定用纯色相填充，
        // 拖动三角调整 S/V 时它保持完全不变。
        val thumbColor = Color(hsvToArgb(hue, saturation, value))
        val hueThumbColor = Color(hsvToArgb(hue, 1f, 1f))
        val (thumbX, thumbY) = svToPoint(saturation, value, triangleRadius)
        val svThumb = center + Offset(thumbX, thumbY)
        drawCircle(Color.Black.copy(alpha = 0.2f), radius = 11.dp.toPx() / 2f, center = svThumb)
        drawCircle(thumbColor, radius = 20.dp.toPx() / 2f, center = svThumb)
        drawCircle(Color.White, radius = 20.dp.toPx() / 2f, center = svThumb, style = Stroke(1.dp.toPx()))
        val hueRadians = hue * PI.toFloat() / 180f
        val midRadius = outerRadius - ringWidthPx / 2f
        val hueThumb = center + Offset(cos(hueRadians) * midRadius, sin(hueRadians) * midRadius)
        drawCircle(Color.Black.copy(alpha = 0.2f), radius = 13.dp.toPx() / 2f, center = hueThumb)
        drawCircle(hueThumbColor, radius = 28.dp.toPx() / 2f, center = hueThumb)
        drawCircle(Color.White, radius = 28.dp.toPx() / 2f, center = hueThumb, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HsvSliderRow(
    label: String,
    trackBrush: Brush,
    thumbColor: Color,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // 实心圆滑块比滑槽稍大，投影把它从渐变轨道上分离出来。
    val thumbSize = 20.dp
    val trackHeight = 16.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.roundToPx() }

    fun pick(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= thumbPx) return
        val fraction = ((x - thumbPx / 2f) / (width - thumbPx)).coerceIn(0f, 1f)
        currentOnValueChange(fraction)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(thumbSize)
                .onSizeChanged { trackSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { position -> pick(position.x) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> pick(change.position.x) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(trackBrush),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((value * (trackSize.width - thumbPx)).roundToInt(), 0) }
                    .size(thumbSize)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}
