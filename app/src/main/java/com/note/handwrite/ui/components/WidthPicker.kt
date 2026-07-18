package com.note.handwrite.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.NotePage
import kotlin.math.roundToInt

private val quickSteps = listOf(20, 50, 80)

@Composable
fun WidthPicker(
    currentStep: Int,
    currentColor: Color,
    onWidthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var panelAnchorStep by remember { mutableStateOf(50) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        quickSteps.forEach { step ->
            val selected = step == currentStep
            val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "widthPickerScale")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable {
                        if (selected) {
                            panelAnchorStep = step
                            expanded = true
                        } else {
                            onWidthSelected(step)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .size((4 + step / 10f).dp)
                        .clip(CircleShape)
                        .background(if (selected) currentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                )
                if (step == panelAnchorStep) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).size(width = 260.dp, height = 200.dp)
                        ) {
                            Text("笔尖粗细", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(12.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    Modifier.size((NotePage.widthForStep(currentStep) * 2.5f).coerceIn(4f, 56f).dp)
                                        .clip(CircleShape).background(currentColor)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "第 $currentStep 档 · ${"%.2f".format(NotePage.millimetersForStep(currentStep))} mm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = currentStep.toFloat(),
                                onValueChange = { onWidthSelected(it.roundToInt()) },
                                valueRange = 1f..100f,
                                steps = 98,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.size(width = 240.dp, height = 40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
