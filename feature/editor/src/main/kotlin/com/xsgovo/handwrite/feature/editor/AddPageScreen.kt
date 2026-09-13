package com.xsgovo.handwrite.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.model.PageBackground

// 待添加页：最后一页继续左滑时从页面之下露出并停留的界面。桌面底色与画布一致，
// 居中提供大号“添加新页面”按钮，下方小按钮可先自定义新页背景；
// 右滑返回上一页的手势由画布统一处理：把上一页从滑出位置拉回，形成自然过渡。
@Composable
fun AddPagePlaceholder(
    pendingBackground: PageBackground,
    onAddPage: () -> Unit,
    onChangeBackground: (PageBackground) -> Unit,
    modifier: Modifier = Modifier,
) {
    var backgroundMenuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalHandwriteColors.current.canvasDesk),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                onClick = onAddPage,
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
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
                TextButton(onClick = { backgroundMenuOpen = true }) {
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
}
