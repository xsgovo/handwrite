package com.xsgovo.handwrite.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xsgovo.handwrite.core.document.CommandHistory
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.DurableCommandExecutor
import com.xsgovo.handwrite.core.document.EpochClock
import com.xsgovo.handwrite.core.document.HistoryLimits
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.PageTemplate
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class EditorTool {
    PEN,
    ERASER,
}

data class EditorUiState(
    val documentId: DocumentId? = null,
    val pageId: PageId? = null,
    val documentName: String = "新文档",
    val pageSize: LogicalSize = PageTemplate.LEGACY_PORTRAIT.size,
    val background: PageBackground = PageBackground.Solid(),
    val backgroundResource: StoredResource? = null,
    val elements: List<PageElement> = emptyList(),
    val tool: EditorTool = EditorTool.PEN,
    val inputMode: InputMode = InputMode.FINGER,
    val colorSlots: List<Int> = AppSettings.DEFAULT_COLOR_SLOTS,
    val activeColorSlot: Int = 0,
    val widthStep: Int = 50,
    val activeBrushId: BrushId = BrushId.MONOLINE,
    val pressureSensitivity: PressureSensitivity = PressureSensitivity.STANDARD,
    val sideButtonAction: SideButtonAction = SideButtonAction.TEMPORARY_ERASER,
    val zoomPercent: Int = 100,
    val backBehavior: BackBehavior = BackBehavior.EXIT_APP,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
) {
    val strokes: List<StrokeElement> get() = elements.filterIsInstance<StrokeElement>()
    val activeColor: Int get() = colorSlots[activeColorSlot]
    val activeWidth: Int get() = 16 + widthStep.coerceIn(1, 100) * 8
}

sealed interface EditorUiEffect {
    data class ShowMessage(val message: String) : EditorUiEffect
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documents: DocumentRepository,
    private val commands: DurableCommandExecutor,
    private val settingsRepository: SettingsRepository,
    private val backgroundResources: BackgroundResourceRepository,
    private val clock: EpochClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState

    private val effectsChannel = Channel<EditorUiEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    private val history = CommandHistory(HistoryLimits(maxCommands = 100, maxEstimatedBytes = 64L * 1024 * 1024))
    private val writeMutex = Mutex()
    private val nextElementId = AtomicLong(clock.nowMillis() shl 16)
    private var observationJob: Job? = null
    private var resourceJob: Job? = null
    private var openedDocumentId: DocumentId? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableState.update { current ->
                    current.copy(
                        inputMode = settings.inputMode,
                        colorSlots = settings.colorSlots,
                        activeColorSlot = settings.activeColorSlot,
                        widthStep = settings.widthStep,
                        activeBrushId = settings.activeBrushId,
                        pressureSensitivity = settings.pressureSensitivity,
                        sideButtonAction = settings.sideButtonAction,
                        backBehavior = settings.backBehavior,
                        pageSize = if (current.documentId == null) settings.defaultPageTemplate.size else current.pageSize,
                        background = if (current.documentId == null) settings.defaultBackground else current.background,
                    )
                }
            }
        }
    }

    fun openDocument(documentId: Long?) {
        if (documentId == null || openedDocumentId?.value == documentId) return
        openedDocumentId = DocumentId(documentId)
        history.clear()
        updateHistoryState()
        observeDocument(DocumentId(documentId))
    }

    fun setTool(tool: EditorTool) {
        mutableState.update { it.copy(tool = tool) }
    }

    fun setZoom(percent: Int) {
        mutableState.update { it.copy(zoomPercent = percent.coerceIn(100, 400)) }
    }

    fun selectColorSlot(index: Int) {
        val colors = mutableState.value.colorSlots
        if (index !in colors.indices) return
        mutableState.update { it.copy(activeColorSlot = index) }
        updateSettings { it.copy(activeColorSlot = index) }
    }

    fun setWidthStep(step: Int) {
        val value = step.coerceIn(1, 100)
        mutableState.update { it.copy(widthStep = value) }
        updateSettings { it.copy(widthStep = value) }
    }

    fun commitStroke(samples: List<StrokeSample>, onCompleted: () -> Unit = {}) {
        if (samples.isEmpty()) {
            onCompleted()
            return
        }
        viewModelScope.launch {
            writeMutex.withLock {
                val target = ensureDocument()
                if (target == null) {
                    onCompleted()
                    return@withLock
                }
                val current = mutableState.value
                val stroke = StrokeElement(
                    id = ElementId(nextElementId.incrementAndGet()),
                    pageId = target.second,
                    orderKey = (current.elements.maxOfOrNull(PageElement::orderKey) ?: 0L) + ORDER_STEP,
                    style = BrushStyle(
                        id = current.activeBrushId,
                        argb = current.activeColor,
                        width = current.activeWidth,
                        pressureSensitivity = current.pressureSensitivity,
                    ),
                    samples = samples,
                )
                commit(
                    DocumentCommand.ReplaceElements(target.first, target.second, emptyList(), listOf(stroke)),
                    recordHistory = true,
                )
                onCompleted()
            }
        }
    }

    fun eraseElements(ids: Set<ElementId>, onCompleted: () -> Unit = {}) {
        if (ids.isEmpty()) {
            onCompleted()
            return
        }
        viewModelScope.launch {
            writeMutex.withLock {
                val current = mutableState.value
                val documentId = current.documentId
                val pageId = current.pageId
                if (documentId == null || pageId == null) {
                    onCompleted()
                    return@withLock
                }
                val removed = current.elements.filter { it.id in ids }
                if (removed.isEmpty()) {
                    onCompleted()
                    return@withLock
                }
                commit(
                    DocumentCommand.ReplaceElements(documentId, pageId, removed, emptyList()),
                    recordHistory = true,
                )
                onCompleted()
            }
        }
    }

    fun clearPage() {
        val current = mutableState.value
        if (current.elements.isEmpty()) return
        eraseElements(current.elements.mapTo(linkedSetOf(), PageElement::id))
    }

    fun setBackground(background: PageBackground) {
        if (background == mutableState.value.background) return
        viewModelScope.launch {
            writeMutex.withLock {
                val target = ensureDocument() ?: return@withLock
                val before = mutableState.value.background
                commit(
                    DocumentCommand.UpdateBackground(target.first, target.second, before, background),
                    recordHistory = true,
                )
            }
        }
    }

    fun importBackground(mimeType: String, input: ResourceInput) {
        viewModelScope.launch {
            writeMutex.withLock {
                mutableState.update { it.copy(isSaving = true) }
                when (val imported = backgroundResources.import(mimeType, input)) {
                    is DomainResult.Success -> {
                        val target = ensureDocument() ?: return@withLock
                        val before = mutableState.value.background
                        val after = PageBackground.Asset(
                            resourceId = imported.value.id,
                            kind = if (imported.value.mimeType == PDF_MIME_TYPE) {
                                BackgroundAssetKind.PDF
                            } else {
                                BackgroundAssetKind.IMAGE
                            },
                            pdfPageIndex = if (imported.value.mimeType == PDF_MIME_TYPE) 0 else null,
                        )
                        if (commit(DocumentCommand.UpdateBackground(target.first, target.second, before, after), true)) {
                            mutableState.update { it.copy(backgroundResource = imported.value) }
                        }
                    }
                    is DomainResult.Failure -> {
                        mutableState.update { it.copy(isSaving = false) }
                        effectsChannel.send(EditorUiEffect.ShowMessage("无法导入背景文件"))
                    }
                }
            }
        }
    }

    fun undo() {
        val command = history.commandToUndo() ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                if (commit(command, recordHistory = false)) history.confirmUndo()
                updateHistoryState()
            }
        }
    }

    fun redo() {
        val command = history.commandToRedo() ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                if (commit(command, recordHistory = false)) history.confirmRedo()
                updateHistoryState()
            }
        }
    }

    fun toggleEraser() {
        mutableState.update { current ->
            current.copy(tool = if (current.tool == EditorTool.ERASER) EditorTool.PEN else EditorTool.ERASER)
        }
    }

    private suspend fun ensureDocument(): Pair<DocumentId, PageId>? {
        val current = mutableState.value
        if (current.documentId != null && current.pageId != null) return current.documentId to current.pageId

        mutableState.update { it.copy(isSaving = true) }
        val nameBase = "未命名 ${NAME_FORMAT.format(Instant.ofEpochMilli(clock.nowMillis()))}"
        repeat(100) { attempt ->
            val displayName = (DisplayName.create(if (attempt == 0) nameBase else "$nameBase ${attempt + 1}") as NameResult.Valid).name
            when (
                val created = documents.createDocument(
                    displayName,
                    current.pageSize,
                    current.background,
                    clock.nowMillis(),
                )
            ) {
                is DomainResult.Success -> {
                    val document = documents.observeDocument(created.value).filterNotNull().first()
                    openedDocumentId = created.value
                    mutableState.update {
                        it.copy(
                            documentId = created.value,
                            pageId = document.lastActivePageId,
                            documentName = document.name.value,
                            isSaving = false,
                        )
                    }
                    observeDocument(created.value)
                    return created.value to document.lastActivePageId
                }
                is DomainResult.Failure -> if (created.error != DomainFailure.NameConflict) {
                    showWriteFailure(created.error)
                    mutableState.update { it.copy(isSaving = false) }
                    return null
                }
            }
        }
        mutableState.update { it.copy(isSaving = false) }
        effectsChannel.send(EditorUiEffect.ShowMessage("无法生成唯一文档名称"))
        return null
    }

    private fun observeDocument(documentId: DocumentId) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            documents.observeDocument(documentId).filterNotNull().collect { document ->
                observePage(document)
            }
        }
    }

    private var observedPageId: PageId? = null
    private var pageJob: Job? = null

    private fun observePage(document: Document) {
        mutableState.update {
            it.copy(
                documentId = document.id,
                pageId = document.lastActivePageId,
                documentName = document.name.value,
            )
        }
        if (observedPageId == document.lastActivePageId) return
        observedPageId = document.lastActivePageId
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            documents.observePage(document.lastActivePageId).filterNotNull().collect { content ->
                mutableState.update {
                    it.copy(
                        pageId = content.page.id,
                        pageSize = content.page.size,
                        background = content.page.background,
                        elements = content.elements,
                        isSaving = false,
                    )
                }
                resolveBackgroundResource(content.page.background)
            }
        }
    }

    private fun resolveBackgroundResource(background: PageBackground) {
        val asset = background as? PageBackground.Asset
        if (asset == null) {
            resourceJob?.cancel()
            mutableState.update { it.copy(backgroundResource = null) }
            return
        }
        if (mutableState.value.backgroundResource?.id == asset.resourceId) return
        resourceJob?.cancel()
        resourceJob = viewModelScope.launch {
            when (val resource = backgroundResources.find(asset.resourceId)) {
                is DomainResult.Success -> {
                    if ((mutableState.value.background as? PageBackground.Asset)?.resourceId == asset.resourceId) {
                        mutableState.update { it.copy(backgroundResource = resource.value) }
                    }
                }
                is DomainResult.Failure -> effectsChannel.send(EditorUiEffect.ShowMessage("背景文件已丢失"))
            }
        }
    }

    private suspend fun commit(command: DocumentCommand, recordHistory: Boolean): Boolean {
        mutableState.update { it.copy(isSaving = true) }
        val result = commands.execute(PendingCommand(OperationId(UUID.randomUUID().toString()), command))
        return when (result) {
            is DomainResult.Success -> {
                applyLocally(command)
                if (recordHistory) history.recordCommitted(command)
                updateHistoryState()
                mutableState.update { it.copy(isSaving = false) }
                true
            }
            is DomainResult.Failure -> {
                mutableState.update { it.copy(isSaving = false) }
                showWriteFailure(result.error)
                false
            }
        }
    }

    private fun applyLocally(command: DocumentCommand) {
        mutableState.update { current ->
            when (command) {
                is DocumentCommand.ReplaceElements -> {
                    val removed = command.removed.mapTo(hashSetOf(), PageElement::id)
                    current.copy(elements = (current.elements.filterNot { it.id in removed } + command.added).sortedBy(PageElement::orderKey))
                }
                is DocumentCommand.UpdateBackground -> current.copy(background = command.after)
            }
        }
    }

    private fun updateHistoryState() {
        mutableState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private suspend fun showWriteFailure(failure: DomainFailure) {
        val message = when (failure) {
            DomainFailure.StorageFull -> "存储空间不足"
            DomainFailure.DocumentNotFound, DomainFailure.PageNotFound -> "文档已不存在"
            else -> "保存失败，操作将在下次启动时恢复"
        }
        effectsChannel.send(EditorUiEffect.ShowMessage(message))
    }

    private companion object {
        const val ORDER_STEP = 1_024L
        const val PDF_MIME_TYPE = "application/pdf"
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss.SSS")
            .withZone(ZoneId.systemDefault())

    }
}
