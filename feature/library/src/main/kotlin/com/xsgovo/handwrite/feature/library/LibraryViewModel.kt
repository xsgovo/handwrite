package com.xsgovo.handwrite.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val documents: List<Document> = emptyList(),
    val deletingId: DocumentId? = null,
)

sealed interface LibraryUiEffect {
    data class ShowMessage(val message: String) : LibraryUiEffect
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState

    private val effectsChannel = Channel<LibraryUiEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeDocuments().collect { documents ->
                mutableState.update { it.copy(documents = documents) }
            }
        }
    }

    fun delete(documentId: DocumentId) {
        if (mutableState.value.deletingId != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(deletingId = documentId) }
            when (repository.deleteDocument(documentId)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> effectsChannel.send(LibraryUiEffect.ShowMessage("无法删除文档"))
            }
            mutableState.update { it.copy(deletingId = null) }
        }
    }
}
