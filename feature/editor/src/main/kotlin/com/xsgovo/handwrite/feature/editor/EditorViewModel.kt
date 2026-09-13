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
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageContent
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

// 展示面：真实页面，或紧随最后一页的待添加虚拟页。虚拟页仅存在于会话状态，
// 不落库、不参与导出与页数统计，作为普通翻页目标参与切换。
sealed interface EditorSurface {
    data class Page(val pageId: PageId) : EditorSurface
    data object PendingAdd : EditorSurface
}

// 拖拽方向上的相邻展示面：真实页携带内容供画布预览渲染；
// 待添加虚拟页以哨兵表示，其内容由界面槽位图层提供。
sealed interface EditorNeighbor {
    data class Page(val content: PageContent) : EditorNeighbor
    data object PendingAdd : EditorNeighbor
}

// 橡皮擦的工作方式：STROKE 划过的整条笔画被删除；PIXEL 只擦除橡皮圆盘覆盖的轨迹，
// 笔画在擦除处断开并拆分为多条存留笔画。
enum class EraserMode {
    STROKE,
    PIXEL,
}

data class EditorUiState(
    val documentId: DocumentId? = null,
    val surface: EditorSurface? = null,
    val pages: List<PageContent> = emptyList(),
    val pendingAddBackground: PageBackground = PageBackground.Solid(),
    val documentName: String = "新文档",
    val pageSize: LogicalSize = PageTemplate.LEGACY_PORTRAIT.size,
    val background: PageBackground = PageBackground.Solid(),
    val backgroundResource: StoredResource? = null,
    val elements: List<PageElement> = emptyList(),
    val tool: EditorTool = EditorTool.PEN,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserSize: Int = DEFAULT_ERASER_SIZE,
    val inputMode: InputMode = InputMode.FINGER,
    val colorSlots: List<Int> = AppSettings.DEFAULT_COLOR_SLOTS,
    val activeColorSlot: Int = 0,
    val pickerCandidates: List<Int> = AppSettings.DEFAULT_PICKER_CANDIDATES,
    val widthSteps: List<Int> = AppSettings.DEFAULT_WIDTH_STEPS,
    val activeWidthSlot: Int = 1,
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
    val pageId: PageId? get() = (surface as? EditorSurface.Page)?.pageId
    val onPendingAddPage: Boolean get() = surface is EditorSurface.PendingAdd
    val pageCount: Int get() = pages.size
    val pageIndex: Int? get() = pageId?.let { id -> pages.indexOfFirst { it.page.id == id }.takeIf { it >= 0 } }
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
    private var pagesJob: Job? = null
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

    fun setEraserMode(mode: EraserMode) {
        mutableState.update { it.copy(eraserMode = mode) }
    }

    fun setEraserSize(size: Int) {
        mutableState.update { it.copy(eraserSize = size.coerceIn(MIN_ERASER_SIZE, MAX_ERASER_SIZE)) }
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
        // 目标页与工具样式在手势发生的页面锁定：等待写入锁期间切换页面，
        // 笔迹也不会落进别的页面，样式与用户落笔时所见一致。
        val snapshot = mutableState.value
        val requestedPageId = snapshot.pageId
        viewModelScope.launch {
            writeMutex.withLock {
                val target = requestedPageId
                    ?.let { pageId -> snapshot.documentId?.let { documentId -> documentId to pageId } }
                    ?: ensureDocument()
                if (target == null) {
                    onCompleted()
                    return@withLock
                }
                val firstOrderKey = (snapshot.elements.maxOfOrNull(PageElement::orderKey) ?: 0L) + ORDER_STEP
                val strokes = nonEmptyStrokes.mapIndexed { index, samples ->
                    StrokeElement(
                        id = ElementId(nextElementId.incrementAndGet()),
                        pageId = target.second,
                        orderKey = firstOrderKey + index * ORDER_STEP,
                        style = BrushStyle(
                            id = snapshot.activeBrushId,
                            argb = snapshot.activeColor,
                            width = snapshot.activeWidth,
                            pressureSensitivity = snapshot.pressureSensitivity,
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
        // 擦除目标在手势发生的页面锁定，避免等待写入锁期间页面切换后误删或丢失内容。
        val snapshot = mutableState.value
        val documentId = snapshot.documentId
        val pageId = snapshot.pageId
        viewModelScope.launch {
            writeMutex.withLock {
                if (documentId == null || pageId == null) {
                    onCompleted()
                    return@withLock
                }
                val removed = snapshot.elements.filter { it.id in ids }
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

    // 像素橡皮的提交：原笔画整体移除，未被擦除的样本片段按原笔画的样式重新落为新笔画；
    // 单个 ReplaceElements 命令保证撤销/重做一步完成。
    fun eraseByPixels(
        removedIds: Set<ElementId>,
        piecesByOriginalId: Map<ElementId, List<List<StrokeSample>>>,
        onCompleted: () -> Unit = {},
    ) {
        if (removedIds.isEmpty()) {
            onCompleted()
            return
        }
        val snapshot = mutableState.value
        val documentId = snapshot.documentId
        val pageId = snapshot.pageId
        viewModelScope.launch {
            writeMutex.withLock {
                if (documentId == null || pageId == null) {
                    onCompleted()
                    return@withLock
                }
                val originals = snapshot.elements.associateBy(PageElement::id)
                val removed = removedIds.mapNotNull(originals::get)
                if (removed.isEmpty()) {
                    onCompleted()
                    return@withLock
                }
                var orderKey = (snapshot.elements.maxOfOrNull(PageElement::orderKey) ?: 0L) + ORDER_STEP
                val added = removed.flatMap { original ->
                    val stroke = original as? StrokeElement
                    if (stroke == null) {
                        emptyList()
                    } else {
                        piecesByOriginalId[stroke.id].orEmpty()
                            .filter { it.size >= MIN_PIECE_SAMPLES }
                            .map { pieceSamples ->
                                StrokeElement(
                                    id = ElementId(nextElementId.incrementAndGet()),
                                    pageId = pageId,
                                    orderKey = orderKey.also { orderKey += ORDER_STEP },
                                    style = stroke.style,
                                    samples = pieceSamples,
                                )
                            }
                    }
                }
                if (commit(
                        DocumentCommand.ReplaceElements(documentId, pageId, removed, added),
                        recordHistory = true,
                    )
                ) {
                    discardDocumentIfCanvasIsEmpty(documentId)
                }
                onCompleted()
            }
        }
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
        // 背景替换只作用于手势发生时的页面；写入前页面被切走则由 applyLocally 丢弃本地合并。
        val snapshot = mutableState.value
        val documentId = snapshot.documentId ?: return
        val pageId = snapshot.pageId ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                if (commit(
                    DocumentCommand.UpdateBackground(documentId, pageId, snapshot.background, background),
                    recordHistory = true,
                )) {
                    discardDocumentIfCanvasIsEmpty(documentId)
                }
            }
        }
    }

    fun importBackground(mimeType: String, input: ResourceInput) {
        val snapshot = mutableState.value
        viewModelScope.launch {
            writeMutex.withLock {
                mutableState.update { it.copy(isSaving = true) }
                when (val imported = backgroundResources.import(mimeType, input)) {
                    is DomainResult.Success -> {
                        val target = snapshot.documentId?.let { documentId ->
                            snapshot.pageId?.let { pageId -> documentId to pageId }
                        } ?: ensureDocument() ?: return@withLock
                        val before = snapshot.background
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

    fun selectPage(pageId: PageId) {
        val current = mutableState.value
        if (current.surface == EditorSurface.Page(pageId)) return
        if (current.pages.none { it.page.id == pageId }) return
        switchToPage(EditorSurface.Page(pageId))
        current.documentId?.let { documentId ->
            viewModelScope.launch { documents.updateLastActivePage(documentId, pageId) }
        }
    }

    // 待添加虚拟页仅在最后一页可进入；等待期间可先自定义新页背景。
    fun selectPendingAddPage() {
        val current = mutableState.value
        if (current.onPendingAddPage) return
        val onLastPage = current.pageIndex == null || current.pageIndex == current.pageCount - 1
        if (!onLastPage) return
        val inherited = current.background.takeUnless { it is PageBackground.Asset }
        mutableState.update {
            it.copy(
                surface = EditorSurface.PendingAdd,
                pendingAddBackground = inherited ?: it.pendingAddBackground,
            )
        }
        if (inherited == null) {
            viewModelScope.launch {
                val fallback = settingsRepository.settings.first().defaultBackground
                mutableState.update { if (it.onPendingAddPage) it.copy(pendingAddBackground = fallback) else it }
            }
        }
    }

    // 离开待添加虚拟页即回到最后一页（虚拟页总是紧随最后一页存在）。
    fun exitPendingAddPage() {
        val current = mutableState.value
        if (!current.onPendingAddPage) return
        val lastPage = current.pages.lastOrNull()?.page?.id
        if (lastPage == null) {
            mutableState.update { it.copy(surface = null) }
        } else {
            switchToPage(EditorSurface.Page(lastPage))
        }
    }

    fun setPendingAddBackground(background: PageBackground) {
        mutableState.update { it.copy(pendingAddBackground = background) }
    }

    // 新页沿用当前页面尺寸；背景默认沿用当前纸面，图片/PDF 背景不复制到新页，回退到默认背景。
    fun addPage() {
        viewModelScope.launch {
            writeMutex.withLock {
                // 空白临时会话首次物化即带首页：落在首页即可，不再额外追加一页。
                val wasUnmaterialized = mutableState.value.documentId == null
                val target = ensureDocument() ?: return@withLock
                if (wasUnmaterialized) {
                    documents.updateLastActivePage(target.first, target.second)
                    return@withLock
                }
                val current = mutableState.value
                val background = if (current.onPendingAddPage) {
                    current.pendingAddBackground
                } else {
                    current.background.takeUnless { it is PageBackground.Asset }
                        ?: settingsRepository.settings.first().defaultBackground
                }
                when (val result = documents.addPage(target.first, current.pageSize, background, clock.nowMillis())) {
                    is DomainResult.Success -> {
                        switchToPage(EditorSurface.Page(result.value.id))
                        documents.updateLastActivePage(target.first, result.value.id)
                    }
                    is DomainResult.Failure -> showWriteFailure(result.error)
                }
            }
        }
    }

    fun deleteCurrentPage() {
        val pageId = mutableState.value.pageId ?: return
        deletePage(pageId)
    }

    fun deletePage(pageId: PageId) {
        viewModelScope.launch {
            writeMutex.withLock {
                val current = mutableState.value
                val documentId = current.documentId ?: return@withLock
                when (val result = documents.deletePage(documentId, pageId)) {
                    is DomainResult.Success -> {
                        // 页面删除不可撤销；历史中指向该页的笔迹命令一并清除，避免撤销撞上缺失页面。
                        history.dropCommandsForPage(pageId)
                        updateHistoryState()
                        discardDocumentIfCanvasIsEmpty(documentId)
                    }
                    is DomainResult.Failure -> showWriteFailure(result.error)
                }
            }
        }
    }

    fun togglePageBookmark(pageId: PageId) {
        viewModelScope.launch {
            val current = mutableState.value
            val documentId = current.documentId ?: return@launch
            val target = current.pages.firstOrNull { it.page.id == pageId } ?: return@launch
            documents.setPageBookmarked(documentId, pageId, !target.page.bookmarked)
        }
    }

    fun movePage(pageId: PageId, delta: Int) {
        viewModelScope.launch {
            writeMutex.withLock {
                val documentId = mutableState.value.documentId ?: return@withLock
                // 越界移动由界面禁用，这里对失败静默处理即可。
                documents.movePage(documentId, pageId, delta)
            }
        }
    }

    private suspend fun ensureDocument(): Pair<DocumentId, PageId>? {
        val current = mutableState.value
        // 待添加虚拟页不代表文档缺失：写目标回落到最后一页，不触发重复建档。
        val activePageId = when (val currentSurface = current.surface) {
            is EditorSurface.Page -> currentSurface.pageId
            EditorSurface.PendingAdd -> current.pages.lastOrNull()?.page?.id
            null -> null
        }
        if (current.documentId != null && activePageId != null) {
            return current.documentId to activePageId
        }

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
                        documentName = document.name.value,
                        isSaving = false,
                    )
                }
                observeDocument(created.value)
                switchToPage(EditorSurface.Page(document.lastActivePageId))
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
                mutableState.update {
                    it.copy(
                        documentId = document.id,
                        documentName = document.name.value,
                    )
                }
                // 只有尚未确定展示面时才跟随持久化的最后活跃页；切换页面后的文档回声不再抢占。
                if (mutableState.value.surface == null) {
                    switchToPage(EditorSurface.Page(document.lastActivePageId))
                }
            }
        }
        pagesJob?.cancel()
        pagesJob = viewModelScope.launch {
            documents.observePageContents(documentId).collect { pages ->
                val previousIds = mutableState.value.pages.map(PageContent::page).map(Page::id)
                mutableState.update { it.copy(pages = pages) }
                val activeId = mutableState.value.pageId ?: return@collect
                if (activeId in previousIds && pages.isNotEmpty() && pages.none { it.page.id == activeId }) {
                    // 展示页在会话外被删除：回退到原位置最近的相邻页并持久化。
                    val fallback = pages[previousIds.indexOf(activeId).coerceAtMost(pages.size - 1)].page.id
                    switchToPage(EditorSurface.Page(fallback))
                    documents.updateLastActivePage(documentId, fallback)
                }
            }
        }
    }

    private var observedPageId: PageId? = null
    private var pageJob: Job? = null

    private fun switchToPage(surface: EditorSurface) {
        // 翻页过渡的第一帧就要呈现目标页的完整内容：优先用已观察到的页面列表同步填充，
        // 避免清空元素后等待内容流，新页面先空白再闪现笔迹；页面内容流随后校准。
        // 填充的是目标页自己的内容，不会让上一页的笔迹闪现在新页面上。
        // 待添加虚拟页没有落库内容：保持空元素并沿用当前背景，等待期间可自定义。
        val pageId = (surface as? EditorSurface.Page)?.pageId
        val seeded = pageId?.let { id -> mutableState.value.pages.firstOrNull { it.page.id == id } }
        mutableState.update {
            it.copy(
                surface = surface,
                elements = seeded?.elements ?: emptyList(),
                background = seeded?.page?.background ?: it.background,
                pageSize = seeded?.page?.size ?: it.pageSize,
                isSaving = false,
            )
        }
        if (pageId == null || observedPageId == pageId) return
        observedPageId = pageId
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            documents.observePage(pageId).filterNotNull().collect { content ->
                mutableState.update {
                    it.copy(
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
                pagesJob?.cancel()
                pageJob?.cancel()
                resourceJob?.cancel()
                openedDocumentId = null
                observedPageId = null
                history.clear()
                mutableState.update { current ->
                    if (current.documentId == documentId) {
                        current.copy(
                            documentId = null,
                            surface = null,
                            pages = emptyList(),
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
            // 命令可能属于已切走的页面：只合并当前展示页的本地状态，其余由页面流重新读取。
            if (current.pageId != command.pageId) return@update current
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
            DomainFailure.LastPageRemaining -> "文档至少保留一页"
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
