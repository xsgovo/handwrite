package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.PageBackground

// 待添加页的停留交互层：纸面本体由画布按真实页面渲染（同样的底色、阴影与翻页过渡），
// 此层只提供纸面上的操作入口，由画布定位在纸面中心并随纸面平移（滑入阶段即随纸面跟入）。
// interactive 为 false 的滑入阶段按钮只作视觉展示：拖拽中的手指恰好落在按钮上时不得误触。
// 右滑返回上一页的手势由画布统一处理：把上一页从滑出位置拉回，形成自然过渡。
@Composable
fun AddPagePlaceholder(
    pendingBackground: PageBackground,
    interactive: Boolean,
    onAddPage: () -> Unit,
    onChangeBackground: (PageBackground) -> Unit,
    modifier: Modifier = Modifier,
) {
    var backgroundMenuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onAddPage,
            enabled = interactive,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("添加新页面", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box {
            TextButton(onClick = { backgroundMenuOpen = true }, enabled = interactive) {
                Icon(
                    Icons.Default.Wallpaper,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "自定义背景",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            DropdownMenu(
                expanded = backgroundMenuOpen,
                onDismissRequest = { backgroundMenuOpen = false },
            ) {
                BackgroundMenuPanel(
                    background = pendingBackground,
                    onSelect = {
                        backgroundMenuOpen = false
                        onChangeBackground(it)
                    },
                    onBack = { backgroundMenuOpen = false },
                )
            }
        }
    }
}
