package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun ColorPickerMenu(
    expanded: Boolean,
    slots: List<Int>,
    activeSlot: Int,
    onSlotSelected: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.width(360.dp),
    ) {
        ColorPickerContent(
            slots = slots,
            activeSlot = activeSlot,
            onSlotSelected = onSlotSelected,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ColorPickerContent(
    slots: List<Int>,
    activeSlot: Int,
    onSlotSelected: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialColor = slots[activeSlot]
    var workingColor by remember(initialColor) { mutableIntStateOf(initialColor) }
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        PickerTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        Spacer(Modifier.height(16.dp))
        if (selectedTab == 0) {
            PaletteGrid(workingColor = workingColor, onColorPicked = { workingColor = it })
        } else {
            SpectrumArea(workingColor = workingColor, onColorPicked = { workingColor = it })
        }
        Spacer(Modifier.height(18.dp))
        ValueRow(
            initialColor = initialColor,
            workingColor = workingColor,
            onHexEdited = { parsed -> parsed?.let { workingColor = it } },
        )
        Spacer(Modifier.height(16.dp))
        DottedDivider()
        Spacer(Modifier.height(10.dp))
        SlotRow(slots = slots, activeSlot = activeSlot, onSelect = onSlotSelected)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PickerAction(label = "取消", modifier = Modifier.weight(1f), onClick = onDismiss)
            VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            PickerAction(
                label = "完成",
                modifier = Modifier.weight(1f),
                onClick = { onConfirm(workingColor) },
            )
        }
    }
}

@Composable
private fun PickerTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        listOf("色板", "光谱").forEachIndexed { index, label ->
            val selected = index == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteGrid(workingColor: Int, onColorPicked: (Int) -> Unit) {
    val rows = remember { paletteGridRows() }
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PALETTE_COLUMN_COUNT / PALETTE_ROW_COUNT.toFloat())
            .semantics { contentDescription = "色板颜色网格" }
            .pointerInput(rows) {
                detectTapGestures { position ->
                    paletteCellAt(position.x, position.y, size.width.toFloat(), size.height.toFloat())
                        ?.let { (row, column) -> onColorPicked(rows[row][column]) }
                }
            },
    ) {
        val cellWidth = size.width / PALETTE_COLUMN_COUNT
        val cellHeight = size.height / PALETTE_ROW_COUNT
        rows.forEachIndexed { row, cells ->
            cells.forEachIndexed { column, cellColor ->
                drawRect(
                    color = Color(cellColor),
                    topLeft = Offset(column * cellWidth, row * cellHeight),
                    size = Size(cellWidth + 0.5f, cellHeight + 0.5f),
                )
            }
        }
        val selected = paletteCellOf(workingColor, rows) ?: return@Canvas
        val outset = 2.dp.toPx()
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(selected.second * cellWidth - outset, selected.first * cellHeight - outset),
            size = Size(cellWidth + outset * 2, cellHeight + outset * 2),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

@Composable
private fun SpectrumArea(workingColor: Int, onColorPicked: (Int) -> Unit) {
    val hsv = remember(workingColor) { argbToHsv(workingColor) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaturationValueArea(hsv = hsv, onColorPicked = onColorPicked)
        HueBar(hue = hsv.hue, onHuePicked = { hue -> onColorPicked(hsvToArgb(hue, hsv.saturation, hsv.value)) })
    }
}

@Composable
private fun SaturationValueArea(hsv: Hsv, onColorPicked: (Int) -> Unit) {
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .onSizeChanged { areaSize = it }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, Color(hsvToArgb(hsv.hue, 1f, 1f)))))
            .pointerInput(Unit) {
                detectTapGestures { position -> pickSaturationValue(position, areaSize, hsv, onColorPicked) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pickSaturationValue(change.position, areaSize, hsv, onColorPicked) }
            },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset {
                    IntOffset(
                        (hsv.saturation * areaSize.width).roundToInt() - (12.dp.roundToPx() / 2),
                        ((1f - hsv.value) * areaSize.height).roundToInt() - (12.dp.roundToPx() / 2),
                    )
                }
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

private fun pickSaturationValue(position: Offset, areaSize: IntSize, hsv: Hsv, onColorPicked: (Int) -> Unit) {
    if (areaSize.width <= 0 || areaSize.height <= 0) return
    val saturation = (position.x / areaSize.width).coerceIn(0f, 1f)
    val value = 1f - (position.y / areaSize.height).coerceIn(0f, 1f)
    onColorPicked(hsvToArgb(hsv.hue, saturation, value))
}

@Composable
private fun HueBar(hue: Float, onHuePicked: (Float) -> Unit) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val rainbow = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { barSize = it }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(rainbow))
            .pointerInput(Unit) {
                detectTapGestures { position -> pickHue(position, barSize, onHuePicked) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pickHue(change.position, barSize, onHuePicked) }
            },
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .offset {
                    IntOffset(
                        (hue / 360f * barSize.width).roundToInt() - (13.dp.roundToPx() / 2),
                        (16.dp.roundToPx() - 13.dp.roundToPx() / 2),
                    )
                }
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

private fun pickHue(position: Offset, barSize: IntSize, onHuePicked: (Float) -> Unit) {
    if (barSize.width <= 0) return
    onHuePicked((position.x / barSize.width).coerceIn(0f, 1f) * 360f)
}

@Composable
private fun ValueRow(initialColor: Int, workingColor: Int, onHexEdited: (Int?) -> Unit) {
    var hexText by remember { mutableStateOf(formatHex(initialColor)) }
    LaunchedEffect(workingColor) {
        if (parseHex(hexText) != workingColor) hexText = formatHex(workingColor)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SplitColorSwatch(initialColor = initialColor, workingColor = workingColor)
        Spacer(Modifier.width(20.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.weight(1f),
        ) {
            ValueColumn(label = "十六进制") {
                BasicTextField(
                    value = hexText,
                    onValueChange = { text ->
                        if (text.length <= 7) {
                            hexText = text
                            onHexEdited(parseHex(text))
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.width(88.dp),
                )
            }
            ValueColumn(label = "红色") { ValueText(value = workingColor shr 16 and 0xFF) }
            ValueColumn(label = "绿色") { ValueText(value = workingColor shr 8 and 0xFF) }
            ValueColumn(label = "蓝色") { ValueText(value = workingColor and 0xFF) }
        }
    }
}

@Composable
private fun SplitColorSwatch(initialColor: Int, workingColor: Int) {
    Row(
        modifier = Modifier
            .size(width = 80.dp, height = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxSize().background(Color(initialColor)))
        Box(Modifier.weight(1f).fillMaxSize().background(Color(workingColor)))
    }
}

@Composable
private fun ValueColumn(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ValueText(value: Int) {
    Text(
        value.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DottedDivider() {
    val dotColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        val dot = 2.dp.toPx()
        drawLine(
            color = dotColor,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = dot,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot, dot * 2.4f)),
        )
    }
}

@Composable
private fun SlotRow(slots: List<Int>, activeSlot: Int, onSelect: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        slots.forEachIndexed { index, argb ->
            val selected = index == activeSlot
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(index) }
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(3.dp)
                    .background(Color(argb), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun PickerAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 820)
@Composable
private fun ColorPickerContentPreview() {
    MaterialTheme {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            ColorPickerContent(
                slots = listOf(0xFF1F1F1F.toInt(), 0xFFE53935.toInt(), 0xFF2E7D32.toInt()),
                activeSlot = 0,
                onSlotSelected = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
