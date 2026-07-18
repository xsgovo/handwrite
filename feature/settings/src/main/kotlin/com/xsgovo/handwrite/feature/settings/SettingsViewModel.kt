package com.xsgovo.handwrite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setInputMode(value: InputMode) = update { it.copy(inputMode = value) }
    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }
    fun setImageFormat(value: ImageFormat) = update { it.copy(imageFormat = value) }
    fun setCompressionQuality(value: CompressionQuality) = update { it.copy(compressionQuality = value) }
    fun setBackBehavior(value: BackBehavior) = update { it.copy(backBehavior = value) }
    fun setSideButtonAction(value: SideButtonAction) = update { it.copy(sideButtonAction = value) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
