package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.DomainResult
import kotlinx.coroutines.flow.Flow

/** 应用设置的读写契约；同一 Flow 允许多个订阅者并存。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** 以最新值为基线应用变换后原子写回；并发更新时后写覆盖前写。 */
    suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit>
}
