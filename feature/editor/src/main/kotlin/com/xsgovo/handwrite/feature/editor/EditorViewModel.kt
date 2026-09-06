package com.xsgovo.handwrite.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xsgovo.handwrite.core.document.CommandHistory
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.EpochClock
import com.xsgovo.handwrite.core.document.HistoryLimits
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    val pickerCandidates: List<Int> = AppSettings.DEFAULT_PICKER_CANDIDATES,
    val widthSteps: List<Int> = AppSettings.DEFAULT_WIDTH_STEPS,
    val activeWidthSlot: Int = 1,
    val screenRatioLocked: Boolean = false,
    val activeBrushId: BrushId = BrushId.MONOLINE,
    val pressureSensitivity: PressureSensitivity = PressureSensitivity.STANDARD,
    val sideButtonAction: SideButtonAction = SideButtonAction.TEMPORARY_ERASER,
    val zoomPercent: Int = 100,
    val zoomLocked: Boolean = false,
    val backBehavior: BackBehavior = BackBehavior.EXIT_APP,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
) {
    val strokes: List<StrokeElement> get() = elements.filterIsInstance<StrokeElement>()
    val activeColor: Int get() = colorSlots[activeColorSlot]
    val widthStep: Int get() = widthSteps[activeWidthSlot]
    val activeWidth: Int get() = 16 + widthStep * 8
}

sealed interface EditorUiEffect {
    data class ShowMessage(val message: String) : EditorUiEffect
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documents: DocumentRepository,
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
    private var colorSlotPersistJob: Job? = null
    private var toolStateRestored = false
    private var openedDocumentId: DocumentId? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                // 笔迹颜色槽位、候选缓存、笔宽档位只有编辑器自己会写入；设置流里的后续
                // 发射是写入回声，可能落后于用户刚做的修改，只在首次加载时采用一次。
                val restoreToolState = !toolStateRestored
                toolStateRestored = true
                mutableState.update { current ->
                    current.copy(
                        inputMode = settings.inputMode,
                        colorSlots = if (restoreToolState) settings.colorSlots else current.colorSlots,
                        activeColorSlot = if (restoreToolState) settings.activeColorSlot else current.activeColorSlot,
                        pickerCandidates = if (restoreToolState) settings.pickerCandidates else current.pickerCandidates,
                        widthSteps = if (restoreToolState) settings.widthSteps else current.widthSteps,
                        activeWidthSlot = if (restoreToolState) settings.activeWidthSlot else current.activeWidthSlot,
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
        // 画布锁定后忽略一切缩放请求，保持当前贴合屏幕的状态。
        if (mutableState.value.zoomLocked) return
        mutableState.update { it.copy(zoomPercent = percent.coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)) }
    }

    fun setZoomLocked(locked: Boolean) {
        mutableState.update { it.copy(zoomLocked = locked) }
    }

    fun selectColorSlot(index: Int) {
        val colors = mutableState.value.colorSlots
        if (index !in colors.indices) return
        mutableState.update { it.copy(activeColorSlot = index) }
        updateSettings { it.copy(activeColorSlot = index) }
    }

    fun setColorSlotValue(argb: Int) {
        // 取色器的色板/光谱/Hex 只发出 RGB；改色时保留槽位当前的透明度。
        updateActiveColorSlot { existing -> (existing and 0xFF000000.toInt()) or (argb and 0x00FFFFFF) }
    }

    fun setColorSlotOpacity(percent: Int) {
        updateActiveColorSlot { existing -> existing.withOpacityPercent(percent) }
    }

    private fun updateActiveColorSlot(transform: (Int) -> Int) {
        val current = mutableState.value
        val slot = current.activeColorSlot
        if (slot !in current.colorSlots.indices) return
        val colors = current.colorSlots.toMutableList().apply { this[slot] = transform(current.colorSlots[slot]) }
        mutableState.update { it.copy(colorSlots = colors) }
        // 取色器拖动会连续触发，状态立即生效，持久化做防抖。
        colorSlotPersistJob?.cancel()
        colorSlotPersistJob = viewModelScope.launch {
            delay(COLOR_SLOT_PERSIST_DELAY_MILLIS)
            updateSettings { it.copy(colorSlots = colors) }
        }
    }

    fun addPickerCandidate(argb: Int) {
        val candidate = argb or 0xFF000000.toInt()
        val current = mutableState.value.pickerCandidates
        // 新颜色从队列末尾追加；已存在或已满员时不做任何变动。
        if (candidate in current || current.size >= AppSettings.PICKER_CANDIDATE_COUNT) return
        val updated = current + candidate
        mutableState.update { it.copy(pickerCandidates = updated) }
        updateSettings { it.copy(pickerCandidates = updated) }
    }

    fun removePickerCandidate(argb: Int) {
        val updated = mutableState.value.pickerCandidates - argb
        mutableState.update { it.copy(pickerCandidates = updated) }
        updateSettings { it.copy(pickerCandidates = updated) }
    }

    fun setWidthStep(step: Int) {
        val value = step.coerceIn(1, 100)
        val current = mutableState.value
        val slot = current.activeWidthSlot
        val widths = current.widthSteps.toMutableList().apply { this[slot] = value }
        mutableState.update { it.copy(widthSteps = widths) }
        updateSettings { it.copy(widthSteps = widths, activeWidthSlot = slot) }
    }

    fun selectWidthSlot(index: Int) {
        if (index !in mutableState.value.widthSteps.indices) return
        mutableState.update { it.copy(activeWidthSlot = index) }
        updateSettings { it.copy(activeWidthSlot = index) }
    }

    fun commitStroke(samples: List<StrokeSample>, onCompleted: () -> Unit = {}) {
        commitStrokes(listOf(samples), onCompleted)
    }

    fun commitStrokes(strokeSamples: List<List<StrokeSample>>, onCompleted: () -> Unit = {}) {
        val nonEmptyStrokes = strokeSamples.filter { it.isNotEmpty() }
        if (nonEmptyStrokes.isEmpty()) {
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
                val firstOrderKey = (current.elements.maxOfOrNull(PageElement::orderKey) ?: 0L) + ORDER_STEP
                val strokes = nonEmptyStrokes.mapIndexed { index, samples ->
                    StrokeElement(
                        id = ElementId(nextElementId.incrementAndGet()),
                        pageId = target.second,
                        orderKey = firstOrderKey + index * ORDER_STEP,
                        style = BrushStyle(
                            id = current.activeBrushId,
                            argb = current.activeColor,
                            width = current.activeWidth,
                            pressureSensitivity = current.pressureSensitivity,
                        ),
                        samples = samples,
                    )
                }
                commit(
                    DocumentCommand.ReplaceElements(target.first, target.second, emptyList(), strokes),
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
                if (commit(
                        DocumentCommand.ReplaceElements(documentId, pageId, removed, emptyList()),
                        recordHistory = true,
                    )
                ) {
                    discardDocumentIfCanvasIsEmpty(documentId)
                }
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
        if (background !is PageBackground.Asset) {
            updateSettings { it.copy(defaultBackground = background) }
        }
        if (background == mutableState.value.background) return
        if (mutableState.value.documentId == null) {
            mutableState.update { it.copy(background = background) }
            return
        }
        viewModelScope.launch {
            writeMutex.withLock {
                val target = ensureDocument() ?: return@withLock
                val before = mutableState.value.background
                if (commit(
                    DocumentCommand.UpdateBackground(target.first, target.second, before, background),
                    recordHistory = true,
                )) {
                    discardDocumentIfCanvasIsEmpty(target.first)
                }
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
                if (commit(command, recordHistory = false)) {
                    history.confirmUndo()
                    discardDocumentIfCanvasIsEmpty(command.documentId)
                }
                updateHistoryState()
            }
        }
    }

    fun redo() {
        val command = history.commandToRedo() ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                if (commit(command, recordHistory = false)) {
                    history.confirmRedo()
                    discardDocumentIfCanvasIsEmpty(command.documentId)
                }
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
        val name = when (
            val result = DisplayName.create("未命名 ${NAME_FORMAT.format(Instant.ofEpochMilli(clock.nowMillis()))}")
        ) {
            is NameResult.Valid -> result.name
            is NameResult.Invalid -> error("Generated document name is invalid: ${result.problem}")
        }
        when (val created = documents.createDocument(name, current.pageSize, current.background, clock.nowMillis())) {
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
            is DomainResult.Failure -> {
                showWriteFailure(created.error)
                mutableState.update { it.copy(isSaving = false) }
                return null
            }
        }
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

    private suspend fun discardDocumentIfCanvasIsEmpty(documentId: DocumentId) {
        val snapshot = when (val loaded = documents.loadSnapshot(documentId)) {
            is DomainResult.Success -> loaded.value
            is DomainResult.Failure -> return
        }
        val hasCanvasContent = snapshot.pages.any { page ->
            page.elements.isNotEmpty() || page.page.background is PageBackground.Asset
        }
        if (hasCanvasContent) return

        when (documents.deleteDocument(documentId)) {
            is DomainResult.Success -> {
                observationJob?.cancel()
                pageJob?.cancel()
                resourceJob?.cancel()
                openedDocumentId = null
                observedPageId = null
                history.clear()
                mutableState.update { current ->
                    if (current.documentId == documentId) {
                        current.copy(
                            documentId = null,
                            pageId = null,
                            documentName = "新文档",
                            elements = emptyList(),
                            backgroundResource = null,
                            canUndo = false,
                            canRedo = false,
                            isSaving = false,
                        )
                    } else {
                        current
                    }
                }
            }
            is DomainResult.Failure -> effectsChannel.send(EditorUiEffect.ShowMessage("无法丢弃空白文档"))
        }
    }

    private suspend fun commit(command: DocumentCommand, recordHistory: Boolean): Boolean {
        mutableState.update { it.copy(isSaving = true) }
        return when (val result = documents.apply(command)) {
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
            else -> "保存失败，请重试"
        }
        effectsChannel.send(EditorUiEffect.ShowMessage(message))
    }

    private companion object {
        const val ORDER_STEP = 1_024L
        const val PDF_MIME_TYPE = "application/pdf"
        const val COLOR_SLOT_PERSIST_DELAY_MILLIS = 300L
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss.SSS")
            .withZone(ZoneId.systemDefault())

    }
}
