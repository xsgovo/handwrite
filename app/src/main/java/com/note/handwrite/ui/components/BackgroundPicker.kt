package com.note.handwrite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.note.handwrite.model.BackgroundType

@Composable
fun BackgroundPicker(
    currentType: BackgroundType,
    onTypeSelected: (BackgroundType) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgrounds = listOf(
        BackgroundType.PLAIN to Icons.Default.CheckBoxOutlineBlank,
        BackgroundType.LINED to Icons.Default.HorizontalRule,
        BackgroundType.GRID to Icons.Default.GridView
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        backgrounds.forEach { (type, icon) ->
            val selected = type == currentType
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onTypeSelected(type) }
            ) {
                Icon(icon, contentDescription = type.name, modifier = Modifier.size(20.dp))
            }
        }
    }
}
