package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xsgovo.handwrite.core.model.AppSettings
import kotlin.math.roundToInt

@Composable
internal fun ColorPickerMenu(
    expanded: Boolean,
    activeColor: Int,
    candidates: List<Int>,
    onColorChanged: (Int) -> Unit,
    onOpacityChanged: (Int) -> Unit,
    onCandidateAdd: (Int) -> Unit,
    onCandidateDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(360.dp),
    ) {
        ColorPickerContent(
            activeColor = activeColor,
            candidates = candidates,
            onColorChanged = onColorChanged,
            onOpacityChanged = onOpacityChanged,
            onCandidateAdd = onCandidateAdd,
            onCandidateDelete = onCandidateDelete,
        )
    }
}

@Composable
private fun ColorPickerContent(
    activeColor: Int,
    candidates: List<Int>,
    onColorChanged: (Int) -> Unit,
    onOpacityChanged: (Int) -> Unit,
    onCandidateAdd: (Int) -> Unit,
    onCandidateDelete: (Int) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        PickerTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        Spacer(Modifier.height(16.dp))
        if (selectedTab == 0) {
            PaletteGrid(activeColor = activeColor, onColorPicked = onColorChanged)
        } else {
            SpectrumArea(activeColor = activeColor, onColorChanged = onColorChanged)
        }
        Spacer(Modifier.height(18.dp))
        OpacityRow(activeColor = activeColor, onOpacityChanged = onOpacityChanged)
        Spacer(Modifier.height(18.dp))
        ValueRow(
            activeColor = activeColor,
            onColorEdited = onColorChanged,
        )
        Spacer(Modifier.height(16.dp))
        DottedDivider()
        Spacer(Modifier.height(10.dp))
        CandidateRow(
            candidates = candidates,
            activeColor = activeColor,
            onSelect = onColorChanged,
            onAdd = onCandidateAdd,
            onDelete = onCandidateDelete,
        )
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
private fun PaletteGrid(activeColor: Int, onColorPicked: (Int) -> Unit) {
    val rows = remember { paletteGridRows() }
    val ringColor = MaterialTheme.colorScheme.primary
    fun pick(position: Offset, size: IntSize) {
        paletteCellAt(position.x, position.y, size.width.toFloat(), size.height.toFloat())
            ?.let { (row, column) -> onColorPicked(rows[row][column]) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PALETTE_COLUMN_COUNT / PALETTE_ROW_COUNT.toFloat())
            .semantics { contentDescription = "色板颜色网格" }
            .pointerInput(rows) {
                detectTapGestures { position -> pick(position, size) }
            }
            .pointerInput(rows) {
                // 支持在色板上滑动连续取色。
                detectDragGestures { change, _ -> pick(change.position, size) }
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
        val selected = paletteCellOf(activeColor, rows) ?: return@Canvas
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(selected.second * cellWidth, selected.first * cellHeight),
            size = Size(cellWidth, cellHeight),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

// 透明度滑杆：轨道垫棋盘格并从全透明渐变到当前颜色，只在两端由用户显式调整。
@Composable
private fun OpacityRow(activeColor: Int, onOpacityChanged: (Int) -> Unit) {
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val percent = activeColor.opacityPercent()
    val opaqueColor = Color(activeColor.toOpaqueRgb())
    val thumbSize = 20.dp
    val trackHeight = 16.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.roundToPx() }
    val currentOnOpacityChanged by rememberUpdatedState(onOpacityChanged)

    fun pick(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= thumbPx) return
        val fraction = ((x - thumbPx / 2f) / (width - thumbPx)).coerceIn(0f, 1f)
        currentOnOpacityChanged((fraction * 100f).roundToInt())
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "透明度",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
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
                    .checkerboardBackdrop()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, opaqueColor))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((percent / 100f * (trackSize.width - thumbPx)).roundToInt(), 0) }
                    .size(thumbSize)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(opaqueColor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun ValueRow(
    activeColor: Int,
    onColorEdited: (Int) -> Unit,
) {
    val hexField = remember { mutableStateOf(TextFieldValue(formatHex(activeColor).removePrefix("#"))) }

    // 外部改动（色板、光谱、候选区）同步进输入框；用户正在输入的半截内容保持原样。
    LaunchedEffect(activeColor) {
        val hex = formatHex(activeColor).removePrefix("#")
        if (!hex.startsWith(hexField.value.text, ignoreCase = true)) {
            hexField.value = TextFieldValue(hex)
        }
    }

    fun editHex(input: TextFieldValue) {
        val digits = input.text.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6)
        hexField.value = input.copy(
            text = digits,
            selection = TextRange(
                minOf(input.selection.min, digits.length),
                minOf(input.selection.max, digits.length),
            ),
        )
        // 不输满也实时生效，按右补零解析。
        if (digits.isNotEmpty()) parseHex(digits.padEnd(6, '0'))?.let(onColorEdited)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorPreviewSwatch(color = activeColor)
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                "Hex",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            PickerViewField(
                value = hexField.value,
                onValueChange = ::editHex,
                prefix = "#",
                keyboardType = KeyboardType.Ascii,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// 当前颜色预览；添加候选的入口在候选区队尾的虚线圆，这里不再重复。
// 半透明时垫棋盘格让透明度可被感知；不透明颜色完全覆盖棋盘格，观感不变。
@Composable
private fun ColorPreviewSwatch(color: Int) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .checkerboardBackdrop()
            .background(Color(color)),
    )
}

// 透明度指示用的灰白棋盘格，按惯例固定用浅色两相，不随主题变化。
internal fun Modifier.checkerboardBackdrop(squareSize: Dp = 5.dp): Modifier = drawBehind {
    val square = squareSize.toPx()
    val light = Color.White
    val dark = Color(0xFFC6C9CE)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var column = 0
        while (x < size.width) {
            drawRect(
                color = if ((row + column) % 2 == 0) light else dark,
                topLeft = Offset(x, y),
                size = Size(minOf(square, size.width - x), minOf(square, size.height - y)),
            )
            x += square
            column++
        }
        y += square
        row++
    }
}

@Composable
private fun PickerViewField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType,
    prefix: String? = null,
    modifier: Modifier = Modifier,
) {
    // 未聚焦时用纯文本展示：输入框内部的触控笔悬停图标不会出现，点击后才进入编辑并全选。
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (prefix != null) {
            Text(
                prefix,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        if (editing) {
            var hasBeenFocused by remember { mutableStateOf(false) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = pickerViewTextStyle(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        // 首次组合时尚未获得焦点，不能因此退出编辑；只有拿到过焦点再失去才收起。
                        if (state.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused) {
                            editing = false
                        }
                    },
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                onValueChange(value.copy(selection = TextRange(0, value.text.length)))
            }
        } else {
            Text(
                value.text,
                style = pickerViewTextStyle(),
                modifier = Modifier
                    .weight(1f)
                    .clickable { editing = true },
            )
        }
    }
}

@Composable
private fun pickerViewTextStyle() = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Start,
)

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
private fun CandidateRow(
    candidates: List<Int>,
    activeColor: Int,
    onSelect: (Int) -> Unit,
    onAdd: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = CANDIDATES_PER_ROW,
    ) {
        candidates.forEachIndexed { index, argb ->
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                pendingDeleteIndex = null
                                onSelect(argb)
                            },
                            // 长按只弹出移除气泡，点击气泡里的移除才会删除。
                            onLongClick = { pendingDeleteIndex = index },
                        )
                        .padding(4.dp)
                        .background(Color(argb), CircleShape),
                )
                if (pendingDeleteIndex == index) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { pendingDeleteIndex = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("移除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                pendingDeleteIndex = null
                                onDelete(argb)
                            },
                        )
                    }
                }
            }
        }
        if (candidates.size < AppSettings.PICKER_CANDIDATE_COUNT) {
            AddCandidateCircle(onClick = { onAdd(activeColor) })
        }
    }
}

// 队列末尾的虚线圆：点击把当前颜色追加到候选区。
@Composable
private fun AddCandidateCircle(onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawCircle(
                    color = dashColor,
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2.5f, stroke * 2.5f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "新增候选颜色",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// 候选区单排容量：两排共 14 个（AppSettings.PICKER_CANDIDATE_COUNT）。
private const val CANDIDATES_PER_ROW = 7

@Preview(showBackground = true, widthDp = 420, heightDp = 820)
@Composable
private fun ColorPickerContentPreview() {
    MaterialTheme {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            ColorPickerContent(
                activeColor = 0xFF1F1F1F.toInt(),
                candidates = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF2E7D32.toInt(), 0xFF1976D2.toInt(), 0xFFF9A825.toInt(), 0xFF8E24AA.toInt()),
                onColorChanged = {},
                onOpacityChanged = {},
                onCandidateAdd = {},
                onCandidateDelete = {},
            )
        }
    }
}
