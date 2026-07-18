package com.xsgovo.handwrite.feature.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.DocumentId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ExportUiState(
    val documentName: String = "文档",
    val format: DocumentExportFormat = DocumentExportFormat.PAGE_IMAGE,
    val settings: AppSettings = AppSettings(),
    val isExporting: Boolean = false,
)

sealed interface ExportUiEffect {
    data class ShowMessage(val message: String) : ExportUiEffect
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val documents: DocumentRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val mutableState = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutableState

    private val effectsChannel = Channel<ExportUiEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()
    private var documentJob: Job? = null
    private var observedDocumentId: DocumentId? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableState.update { it.copy(settings = settings) }
            }
        }
    }

    fun openDocument(documentId: Long) {
        val id = DocumentId(documentId)
        if (observedDocumentId == id) return
        observedDocumentId = id
        documentJob?.cancel()
        documentJob = viewModelScope.launch {
            documents.observeDocument(id).filterNotNull().collect { document ->
                mutableState.update { it.copy(documentName = document.name.value) }
            }
        }
    }

    fun setFormat(format: DocumentExportFormat) {
        if (!mutableState.value.isExporting) mutableState.update { it.copy(format = format) }
    }

    fun export(documentId: Long, destination: Uri) {
        if (mutableState.value.isExporting) return
        val current = mutableState.value
        val request = OneTimeWorkRequestBuilder<DocumentExportWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(KEY_DOCUMENT_ID, documentId)
                    .putString(KEY_DESTINATION_URI, destination.toString())
                    .putString(KEY_EXPORT_FORMAT, current.format.name)
                    .putString(KEY_IMAGE_FORMAT, current.settings.imageFormat.name)
                    .putString(KEY_EXPORT_RESOLUTION, current.settings.exportResolution.name)
                    .putString(KEY_COMPRESSION_QUALITY, current.settings.compressionQuality.name)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(EXPORT_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        mutableState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            while (true) {
                val info = withContext(Dispatchers.IO) { workManager.getWorkInfoById(request.id).get() }
                if (info == null) {
                    mutableState.update { it.copy(isExporting = false) }
                    effectsChannel.send(ExportUiEffect.ShowMessage("导出失败"))
                    break
                }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        mutableState.update { it.copy(isExporting = false) }
                        effectsChannel.send(ExportUiEffect.ShowMessage("导出完成"))
                        break
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        mutableState.update { it.copy(isExporting = false) }
                        effectsChannel.send(ExportUiEffect.ShowMessage("导出失败"))
                        break
                    }
                    else -> Unit
                }
                delay(WORK_STATUS_POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val WORK_STATUS_POLL_MILLIS = 250L
    }
}
