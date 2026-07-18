package com.xsgovo.handwrite.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("输入")
            ToggleRow(
                title = "仅手写笔输入",
                subtitle = "开启后忽略手指绘制",
                checked = settings.inputMode == InputMode.STYLUS,
                onChecked = { viewModel.setInputMode(if (it) InputMode.STYLUS else InputMode.FINGER) },
            )
            MenuRow(
                title = "笔侧键动作",
                selected = sideButtonLabel(settings.sideButtonAction),
                options = SideButtonAction.entries,
                label = ::sideButtonLabel,
                onSelect = viewModel::setSideButtonAction,
            )
            HorizontalDivider()

            SectionTitle("外观")
            MenuRow(
                title = "主题",
                selected = themeLabel(settings.themeMode),
                options = ThemeMode.entries,
                label = ::themeLabel,
                onSelect = viewModel::setThemeMode,
            )
            HorizontalDivider()

            SectionTitle("导出")
            MenuRow(
                title = "图片编码",
                selected = imageFormatLabel(settings.imageFormat),
                options = ImageFormat.entries,
                label = ::imageFormatLabel,
                onSelect = viewModel::setImageFormat,
            )
            MenuRow(
                title = "压缩质量",
                selected = compressionLabel(settings.compressionQuality),
                options = CompressionQuality.entries,
                label = ::compressionLabel,
                onSelect = viewModel::setCompressionQuality,
            )
            HorizontalDivider()

            SectionTitle("导航")
            ToggleRow(
                title = "返回键进入文档库",
                subtitle = "关闭时从画布直接退出应用",
                checked = settings.backBehavior == BackBehavior.OPEN_LIBRARY,
                onChecked = {
                    viewModel.setBackBehavior(if (it) BackBehavior.OPEN_LIBRARY else BackBehavior.EXIT_APP)
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) },
    )
}

@Composable
private fun <T> MenuRow(
    title: String,
    selected: String,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row {
                TextButton(onClick = { expanded = true }) { Text(selected) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            onClick = { expanded = false; onSelect(option) },
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun themeLabel(value: ThemeMode): String = when (value) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun imageFormatLabel(value: ImageFormat): String = when (value) {
    ImageFormat.AUTO -> "自动"
    ImageFormat.PNG -> "PNG"
    ImageFormat.WEBP -> "WebP"
    ImageFormat.JPEG -> "JPEG"
}

private fun compressionLabel(value: CompressionQuality): String = when (value) {
    CompressionQuality.LOW -> "更小文件"
    CompressionQuality.BALANCED -> "平衡"
    CompressionQuality.HIGH -> "高质量"
}

private fun sideButtonLabel(value: SideButtonAction): String = when (value) {
    SideButtonAction.TEMPORARY_ERASER -> "按住临时橡皮"
    SideButtonAction.TOGGLE_ERASER -> "切换橡皮"
    SideButtonAction.UNDO -> "撤销"
}
