package com.xsgovo.handwrite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HandwriteAppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
}
