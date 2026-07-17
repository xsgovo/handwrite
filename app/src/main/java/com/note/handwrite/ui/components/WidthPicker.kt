package com.note.handwrite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.NotePage
import kotlin.math.roundToInt

private val quickSteps = listOf(20, 50, 80)

@Composable
fun WidthPicker(
    currentStep: Int,
    onWidthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var panelAnchorStep by remember { mutableStateOf(50) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        quickSteps.forEach { step ->
            val selected = step == currentStep
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
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
                Box(Modifier.size((8 + step / 5).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                if (step == panelAnchorStep) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Column(Modifier.padding(16.dp)) {
                            Text("第 $currentStep 档 · ${"%.2f".format(NotePage.millimetersForStep(currentStep))} mm")
                            Slider(
                                value = currentStep.toFloat(),
                                onValueChange = { onWidthSelected(it.roundToInt()) },
                                valueRange = 1f..100f,
                                steps = 98,
                                modifier = Modifier.size(width = 240.dp, height = 48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
