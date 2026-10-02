package com.xsgovo.handwrite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.PressureSensitivity
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
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
            SettingSection(stringResource(R.string.settings_section_input)) {
                ToggleRow(
                    title = stringResource(R.string.settings_stylus_only),
                    subtitle = stringResource(R.string.settings_stylus_only_subtitle),
                    checked = settings.inputMode == InputMode.STYLUS,
                    onChecked = { viewModel.setInputMode(if (it) InputMode.STYLUS else InputMode.FINGER) },
                )
                ToggleRow(
                    title = stringResource(R.string.settings_pressure),
                    subtitle = stringResource(R.string.settings_pressure_subtitle),
                    checked = settings.pressureSensitivity != PressureSensitivity.OFF,
                    onChecked = {
                        viewModel.setPressureSensitivity(
                            if (it) PressureSensitivity.STANDARD else PressureSensitivity.OFF,
                        )
                    },
                )
                MenuRow(
                    title = stringResource(R.string.settings_side_button_action),
                    selectedResId = sideButtonLabel(settings.sideButtonAction),
                    options = SideButtonAction.entries,
                    label = ::sideButtonLabel,
                    onSelect = viewModel::setSideButtonAction,
                )
            }

            SettingSection(stringResource(R.string.settings_section_appearance)) {
                ToggleRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                    checked = settings.dynamicColor,
                    onChecked = viewModel::setDynamicColor,
                )
                MenuRow(
                    title = stringResource(R.string.settings_theme),
                    selectedResId = themeLabel(settings.themeMode),
                    options = ThemeMode.entries,
                    label = ::themeLabel,
                    onSelect = viewModel::setThemeMode,
                )
            }

            SettingSection(stringResource(R.string.settings_section_export)) {
                MenuRow(
                    title = stringResource(R.string.settings_image_format),
                    selectedResId = imageFormatLabel(settings.imageFormat),
                    options = ImageFormat.entries,
                    label = ::imageFormatLabel,
                    onSelect = viewModel::setImageFormat,
                )
                MenuRow(
                    title = stringResource(R.string.settings_compression_quality),
                    selectedResId = compressionLabel(settings.compressionQuality),
                    options = CompressionQuality.entries,
                    label = ::compressionLabel,
                    onSelect = viewModel::setCompressionQuality,
                )
            }

            SettingSection(stringResource(R.string.settings_section_navigation)) {
                ToggleRow(
                    title = stringResource(R.string.settings_back_opens_library),
                    subtitle = stringResource(R.string.settings_back_opens_library_subtitle),
                    checked = settings.backBehavior == BackBehavior.OPEN_LIBRARY,
                    onChecked = {
                        viewModel.setBackBehavior(if (it) BackBehavior.OPEN_LIBRARY else BackBehavior.EXIT_APP)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large),
    ) {
        content()
    }
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun <T> MenuRow(
    title: String,
    selectedResId: Int,
    options: List<T>,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row {
                TextButton(onClick = { expanded = true }) { Text(stringResource(selectedResId)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label(option))) },
                            onClick = { expanded = false; onSelect(option) },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun themeLabel(value: ThemeMode): Int = when (value) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun imageFormatLabel(value: ImageFormat): Int = when (value) {
    ImageFormat.AUTO -> R.string.settings_image_format_auto
    ImageFormat.PNG -> R.string.settings_image_format_png
    ImageFormat.WEBP -> R.string.settings_image_format_webp
    ImageFormat.JPEG -> R.string.settings_image_format_jpeg
}

private fun compressionLabel(value: CompressionQuality): Int = when (value) {
    CompressionQuality.LOW -> R.string.settings_compression_low
    CompressionQuality.BALANCED -> R.string.settings_compression_balanced
    CompressionQuality.HIGH -> R.string.settings_compression_high
}

private fun sideButtonLabel(value: SideButtonAction): Int = when (value) {
    SideButtonAction.TEMPORARY_ERASER -> R.string.settings_side_button_temporary_eraser
    SideButtonAction.TOGGLE_ERASER -> R.string.settings_side_button_toggle_eraser
    SideButtonAction.UNDO -> R.string.settings_side_button_undo
}
