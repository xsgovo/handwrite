package com.xsgovo.handwrite.core.data

import androidx.datastore.core.DataStore
import com.xsgovo.handwrite.core.data.settings.AppSettingsPayload
import com.xsgovo.handwrite.core.data.settings.toDomain
import com.xsgovo.handwrite.core.data.settings.toProto
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProtoSettingsRepository(
    private val dataStore: DataStore<AppSettingsPayload>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data.map(AppSettingsPayload::toDomain)

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> = try {
        dataStore.updateData { current -> transform(current.toDomain()).toProto() }
        DomainResult.Success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IOException) {
        DomainResult.Failure(DomainFailure.DatabaseUnavailable)
    }
}
