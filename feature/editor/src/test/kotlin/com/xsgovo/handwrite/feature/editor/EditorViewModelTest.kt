package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.DurableCommandExecutor
import com.xsgovo.handwrite.core.document.EpochClock
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.document.PendingCommandJournal
import com.xsgovo.handwrite.core.document.SettingsRepository
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.ResourceId
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.LogicalCanvas
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.StrokeSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun documentIsCreatedOnlyAfterFirstSubstantiveEdit() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(0, repository.createCount)
        assertEquals(null, viewModel.state.value.documentId)

        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200))))
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertNotNull(viewModel.state.value.documentId)
        assertEquals(1, viewModel.state.value.strokes.size)
        assertTrue(viewModel.state.value.canUndo)
    }

    @Test
    fun changingThePaperPatternDoesNotCreateAnEmptyDocument() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)

        viewModel.setBackground(PageBackground.Pattern(PatternType.GRID))
        advanceUntilIdle()

        assertEquals(0, repository.createCount)
        assertEquals(null, viewModel.state.value.documentId)
    }

    @Test
    fun strokeCompletionIsAcknowledgedAfterTheStrokeIsVisible() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        var strokeCountAtCompletion = 0

        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200)))) {
            strokeCountAtCompletion = viewModel.state.value.strokes.size
        }
        advanceUntilIdle()

        assertEquals(1, strokeCountAtCompletion)
    }

    @Test
    fun eraseCompletionIsAcknowledgedAfterTheStrokeIsRemoved() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200))))
        advanceUntilIdle()
        val strokeId = viewModel.state.value.strokes.single().id
        var strokeCountAtCompletion = -1

        viewModel.eraseElements(setOf(strokeId)) {
            strokeCountAtCompletion = viewModel.state.value.strokes.size
        }
        advanceUntilIdle()

        assertEquals(0, strokeCountAtCompletion)
    }

    @Test
    fun erasingTheLastStrokeDiscardsTheEmptyDocument() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200))))
        advanceUntilIdle()

        viewModel.eraseElements(setOf(viewModel.state.value.strokes.single().id))
        advanceUntilIdle()

        assertEquals(1, repository.deleteCount)
        assertEquals(null, viewModel.state.value.documentId)
    }

    @Test
    fun assetBackgroundKeepsTheDocumentWhenItsLastStrokeIsErased() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200))))
        advanceUntilIdle()
        viewModel.setBackground(
            PageBackground.Asset(
                resourceId = ResourceId(1),
                kind = BackgroundAssetKind.IMAGE,
            ),
        )
        advanceUntilIdle()

        viewModel.eraseElements(setOf(viewModel.state.value.strokes.single().id))
        advanceUntilIdle()

        assertEquals(0, repository.deleteCount)
        assertNotNull(viewModel.state.value.documentId)
    }

    @Test
    fun undoingTheLastStrokeDiscardsTheEmptyDocument() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val viewModel = createViewModel(repository)
        viewModel.commitStroke(listOf(StrokeSample(LogicalPoint(100, 200))))
        advanceUntilIdle()

        viewModel.undo()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.strokes.isEmpty())
        assertEquals(1, repository.deleteCount)
        assertEquals(null, viewModel.state.value.documentId)
        assertTrue(!viewModel.state.value.canUndo && !viewModel.state.value.canRedo)
    }

    @Test
    fun strokeStartingOutsideThePageIsPersistedFromItsEntryPoint() {
        val clipped = clipStrokeToPage(
            samples = listOf(sample(-100, 400), sample(400, 400)),
            pageSize = squarePage(),
        )

        assertEquals(1, clipped.size)
        assertEquals(LogicalPoint(0, 400), clipped.single().first().point)
        assertEquals(LogicalPoint(400, 400), clipped.single().last().point)
    }

    @Test
    fun leavingAndReenteringThePageSplitsTheStroke() {
        val clipped = clipStrokeToPage(
            samples = listOf(sample(100, 400), sample(70_000, 400), sample(100, 400)),
            pageSize = squarePage(),
        )

        assertEquals(2, clipped.size)
        assertEquals(LogicalPoint(LogicalCanvas.LONG_EDGE, 400), clipped.first().last().point)
        assertEquals(LogicalPoint(LogicalCanvas.LONG_EDGE, 400), clipped.last().first().point)
    }

    @Test
    fun onlyNonStylusPointersNavigateInStylusMode() {
        assertTrue(
            isSingleFingerNavigationPointer(
                inputMode = com.xsgovo.handwrite.core.model.InputMode.STYLUS,
                pointerType = PointerType.Touch,
                rawToolType = MotionEvent.TOOL_TYPE_FINGER,
            ),
        )
        assertFalse(
            isSingleFingerNavigationPointer(
                inputMode = com.xsgovo.handwrite.core.model.InputMode.STYLUS,
                pointerType = PointerType.Stylus,
                rawToolType = MotionEvent.TOOL_TYPE_STYLUS,
            ),
        )
    }

    @Test
    fun canvasPanClampsAtTheVisibleOverflow() {
        val transform = CanvasPageTransform.create(
            canvas = IntSize(400, 400),
            page = squarePage(),
            zoom = 2f,
            pan = Offset.Zero,
        )

        val clamped = transform.clampPan(Offset(10_000f, -10_000f))

        assertEquals(transform.panLimit.x, clamped.x, 0f)
        assertEquals(-transform.panLimit.y, clamped.y, 0f)
    }

    private fun sample(x: Int, y: Int): StrokeSample = StrokeSample(LogicalPoint(x, y))

    private fun squarePage(): LogicalSize = LogicalSize(LogicalCanvas.LONG_EDGE, LogicalCanvas.LONG_EDGE)

    private fun createViewModel(repository: FakeDocumentRepository): EditorViewModel = EditorViewModel(
        documents = repository,
        commands = DurableCommandExecutor(repository, InMemoryJournal()),
        settingsRepository = FakeSettingsRepository(),
        backgroundResources = FakeBackgroundResources(),
        clock = EpochClock { 1_700_000_000_000 },
    )

    private class FakeBackgroundResources : BackgroundResourceRepository {
        override suspend fun import(mimeType: String, input: ResourceInput): DomainResult<StoredResource> =
            DomainResult.Failure(DomainFailure.InvalidResource)

        override suspend fun find(resourceId: ResourceId): DomainResult<StoredResource> =
            DomainResult.Failure(DomainFailure.ResourceNotFound)

        override suspend fun pruneUnreferenced(): DomainResult<Unit> = DomainResult.Success(Unit)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val mutableSettings = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = mutableSettings

        override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> {
            mutableSettings.value = transform(mutableSettings.value)
            return DomainResult.Success(Unit)
        }
    }

    private class InMemoryJournal : PendingCommandJournal {
        private val entries = mutableListOf<PendingCommand>()

        override suspend fun append(command: PendingCommand): DomainResult<Unit> {
            entries += command
            return DomainResult.Success(Unit)
        }

        override suspend fun readAll(): DomainResult<List<PendingCommand>> = DomainResult.Success(entries.toList())

        override suspend fun remove(operationId: OperationId): DomainResult<Unit> {
            entries.removeAll { it.operationId == operationId }
            return DomainResult.Success(Unit)
        }
    }

    private class FakeDocumentRepository : DocumentRepository {
        private val document = MutableStateFlow<Document?>(null)
        private val page = MutableStateFlow<PageContent?>(null)
        var createCount = 0
            private set
        var deleteCount = 0
            private set

        override fun observeDocuments(): Flow<List<Document>> = MutableStateFlow(emptyList())
        override fun observeDocument(documentId: DocumentId): Flow<Document?> = document
        override fun observePages(documentId: DocumentId): Flow<List<Page>> = MutableStateFlow(listOfNotNull(page.value?.page))
        override fun observePage(pageId: PageId): Flow<PageContent?> = page

        override suspend fun loadSnapshot(documentId: DocumentId): DomainResult<DocumentSnapshot> {
            val currentDocument = document.value ?: return DomainResult.Failure(DomainFailure.DocumentNotFound)
            val currentPage = page.value ?: return DomainResult.Failure(DomainFailure.PageNotFound)
            return DomainResult.Success(DocumentSnapshot(currentDocument, listOf(currentPage)))
        }

        override suspend fun createDocument(
            name: DisplayName,
            size: LogicalSize,
            background: PageBackground,
            nowEpochMillis: Long,
        ): DomainResult<DocumentId> {
            createCount++
            val documentId = DocumentId(1)
            val pageId = PageId(2)
            document.value = Document(documentId, name, null, nowEpochMillis, nowEpochMillis, lastActivePageId = pageId)
            page.value = PageContent(Page(pageId, documentId, 1_024, size, background), emptyList())
            return DomainResult.Success(documentId)
        }

        override suspend fun renameDocument(documentId: DocumentId, name: DisplayName): DomainResult<Unit> =
            DomainResult.Success(Unit)

        override suspend fun deleteDocument(documentId: DocumentId): DomainResult<Unit> {
            deleteCount++
            document.value = null
            page.value = null
            return DomainResult.Success(Unit)
        }

        override suspend fun createPage(
            documentId: DocumentId,
            size: LogicalSize,
            background: PageBackground,
        ): DomainResult<PageId> = DomainResult.Success(PageId(3))

        override suspend fun deletePage(pageId: PageId): DomainResult<Unit> = DomainResult.Success(Unit)

        override suspend fun setLastActivePage(documentId: DocumentId, pageId: PageId): DomainResult<Unit> =
            DomainResult.Success(Unit)

        override suspend fun apply(command: DocumentCommand, operationId: OperationId): DomainResult<Unit> {
            val current = page.value ?: return DomainResult.Success(Unit)
            page.value = when (command) {
                is DocumentCommand.ReplaceElements -> {
                    val removed = command.removed.mapTo(hashSetOf()) { it.id }
                    current.copy(elements = (current.elements.filterNot { it.id in removed } + command.added).sortedBy { it.orderKey })
                }
                is DocumentCommand.UpdateBackground -> current.copy(page = current.page.copy(background = command.after))
            }
            return DomainResult.Success(Unit)
        }
    }
}
