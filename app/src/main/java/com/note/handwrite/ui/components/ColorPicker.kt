package com.note.handwrite.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.note.handwrite.ui.theme.PenBlack
import com.note.handwrite.ui.theme.PenBlue
import com.note.handwrite.ui.theme.PenGreen
import com.note.handwrite.ui.theme.PenOrange
import com.note.handwrite.ui.theme.PenPurple
import com.note.handwrite.ui.theme.PenRed

private data class ColorPickerSnapshot(
    val colors: List<Color>,
    val activeSlot: Int
)

private val quickColors = listOf(PenBlack, PenRed, PenGreen, PenBlue, PenOrange, PenPurple)
private val hexColorPattern = Regex("#[0-9A-F]{6}")

@Composable
fun ColorPicker(
    colorSlots: List<Color>,
    activeSlot: Int,
    onSlotSelected: (Int) -> Unit,
    onActiveColorChanged: (Color) -> Unit,
    onColorSlotsSaved: () -> Unit,
    onColorSlotsRestored: (List<Color>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (colorSlots.isEmpty() || activeSlot !in colorSlots.indices) return

    var expanded by remember { mutableStateOf(false) }
    var spectrumMode by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(ColorPickerSnapshot(colorSlots, activeSlot)) }
    var hexValue by remember { mutableStateOf(colorSlots[activeSlot].toHex()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestExpanded by rememberUpdatedState(expanded)
    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestRestore by rememberUpdatedState(onColorSlotsRestored)
    val currentColor = colorSlots[activeSlot]

    LaunchedEffect(currentColor) {
        hexValue = currentColor.toHex()
    }
    DisposableEffect(Unit) {
        onDispose {
            if (latestExpanded) {
                latestRestore(latestSnapshot.colors, latestSnapshot.activeSlot)
            }
        }
    }

    fun closeAndSave() {
        focusManager.clearFocus()
        keyboardController?.hide()
        onColorSlotsSaved()
        expanded = false
    }
    fun discardAndClose() {
        focusManager.clearFocus()
        keyboardController?.hide()
        onColorSlotsRestored(snapshot.colors, snapshot.activeSlot)
        expanded = false
    }
    fun applyColor(color: Color) {
        focusManager.clearFocus()
        keyboardController?.hide()
        onActiveColorChanged(color)
        hexValue = color.toHex()
    }

    BackHandler(enabled = expanded) { discardAndClose() }

    Box(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colorSlots.forEachIndexed { index, color ->
                val selected = index == activeSlot
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (selected) {
                                if (!expanded) {
                                    snapshot = ColorPickerSnapshot(colorSlots.toList(), activeSlot)
                                    hexValue = currentColor.toHex()
                                    expanded = true
                                }
                            } else {
                                if (expanded) closeAndSave()
                                onSlotSelected(index)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (selected) 20.dp else 24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { closeAndSave() },
            modifier = Modifier
                .width(310.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaletteTab("色板", !spectrumMode, Modifier.weight(1f)) { spectrumMode = false }
                    PaletteTab("光谱", spectrumMode, Modifier.weight(1f)) { spectrumMode = true }
                }
                QuickColorRow { applyColor(it) }
                if (spectrumMode) {
                    SpectrumPicker(currentColor, ::applyColor)
                } else {
                    PaletteGrid(currentColor, ::applyColor)
                }
                ColorPreview(currentColor)
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { candidate ->
                        val normalized = candidate.uppercase()
                        if (normalized.isValidHexInput()) {
                            hexValue = normalized
                            if (hexColorPattern.matches(normalized)) {
                                applyColor(Color(android.graphics.Color.parseColor(normalized)))
                            }
                        }
                    },
                    singleLine = true,
                    label = { Text("HEX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PaletteTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickColorRow(onColorSelected: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quickColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, if (color == Color.White) Color.LightGray else Color.Transparent, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

@Composable
private fun PaletteGrid(selectedColor: Color, onColorChange: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until 10) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 11) {
                    val color = gridColor(column, row)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                            .border(
                                if (color == selectedColor) 2.dp else 0.5.dp,
                                if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onColorChange(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumPicker(selectedColor: Color, onColorChange: (Color) -> Unit) {
    val hsv = remember(selectedColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(selectedColor.toArgb(), it) }
    }
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(value) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(position: Offset) = onColorChange(
                            Color.hsv(
                                (position.x / size.width).coerceIn(0f, 1f) * 360f,
                                (position.y / size.height).coerceIn(0f, 1f),
                                value
                            )
                        )
                        update(down.position)
                        drag(down.id) { change ->
                            update(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                drawRect(Brush.verticalGradient(listOf(Color.White, Color.Transparent)))
                val center = Offset(hue / 360f * size.width, saturation * size.height)
                drawCircle(Color.White, 7.dp.toPx(), center)
                drawCircle(Color.Black, 9.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        Slider(
            value = value,
            onValueChange = { onColorChange(Color.hsv(hue, saturation, it)) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorPreview(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    )
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun String.isValidHexInput(): Boolean =
    isEmpty() || this == "#" || matches(Regex("#[0-9A-F]{0,6}"))

private fun gridColor(column: Int, row: Int): Color {
    if (column == 0) {
        val gray = 1f - row / 9f
        return Color(gray, gray, gray)
    }
    val hues = listOf(0f, 25f, 50f, 85f, 125f, 180f, 210f, 240f, 280f, 325f)
    val lightness = 0.95f - row * 0.085f
    val saturation = if (row < 2) 0.6f + row * 0.2f else 1f
    return Color.hsl(hues[column - 1], saturation, lightness.coerceIn(0f, 1f))
}
