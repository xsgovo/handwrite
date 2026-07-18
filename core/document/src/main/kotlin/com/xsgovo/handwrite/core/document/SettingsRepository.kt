package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.DomainResult
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit>
}
