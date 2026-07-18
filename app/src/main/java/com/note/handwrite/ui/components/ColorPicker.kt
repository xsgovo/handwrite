package com.note.handwrite.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.note.handwrite.ui.theme.PenBlack
import com.note.handwrite.ui.theme.PenBlue
import com.note.handwrite.ui.theme.PenGreen
import com.note.handwrite.ui.theme.PenOrange
import com.note.handwrite.ui.theme.PenPurple
import com.note.handwrite.ui.theme.PenRed
import kotlin.math.roundToInt

@Composable
fun ColorPicker(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetColors = listOf(PenBlack, PenBlue, PenRed, PenGreen, PenOrange, PenPurple)
    var expanded by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(currentColor) }

    LaunchedEffect(currentColor) { selectedColor = currentColor }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable {
                    selectedColor = currentColor
                    expanded = true
                }
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            PaletteMenu(
                expanded = expanded,
                currentColor = currentColor,
                selectedColor = selectedColor,
                onColorChange = { selectedColor = it },
                onConfirm = {
                    onColorSelected(selectedColor)
                    expanded = false
                },
                onDismiss = { expanded = false }
            )
        }
        presetColors.forEach { color ->
            val selected = color == currentColor
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onColorSelected(color) }
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 20.dp else 24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun PaletteMenu(
    expanded: Boolean,
    currentColor: Color,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var spectrumMode by remember { mutableStateOf(false) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(310.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaletteTab("色板", !spectrumMode, Modifier.weight(1f)) { spectrumMode = false }
                PaletteTab("光谱", spectrumMode, Modifier.weight(1f)) { spectrumMode = true }
            }
            if (spectrumMode) {
                SpectrumPicker(selectedColor, onColorChange)
            } else {
                PaletteGrid(selectedColor, onColorChange)
            }
            PreviewAndChannels(currentColor, selectedColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Color(0xFF2ECC71), Color.Black, Color.White, Color(0xFFFFD1DC), Color(0xFFD4E6F1)).forEach { color ->
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(color)
                            .border(1.dp, if (color == Color.White) Color.LightGray else Color.Transparent, CircleShape)
                            .clickable { onColorChange(color) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Action("取消", Modifier.weight(1f), onDismiss, MaterialTheme.colorScheme.onSurfaceVariant)
                Action("完成", Modifier.weight(1f), onConfirm, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PaletteTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
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
            modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(8.dp))
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
                        drag(down.id) { change -> update(change.position); change.consume() }
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
private fun PreviewAndChannels(currentColor: Color, selectedColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(6.dp))) {
            Box(Modifier.weight(1f).background(currentColor))
            Box(Modifier.weight(1f).background(selectedColor))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "#%06X  R%d G%d B%d".format(
                selectedColor.toArgb() and 0xFFFFFF,
                (selectedColor.red * 255).roundToInt(),
                (selectedColor.green * 255).roundToInt(),
                (selectedColor.blue * 255).roundToInt()
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun Action(text: String, modifier: Modifier, onClick: () -> Unit, color: Color) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.height(40.dp).clickable(onClick = onClick)) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

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
