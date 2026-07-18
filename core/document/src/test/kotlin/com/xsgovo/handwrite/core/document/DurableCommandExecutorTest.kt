package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableCommandExecutorTest {
    @Test
    fun executeWritesJournalBeforeStoreAndThenRemovesIt() = runBlocking {
        val events = mutableListOf<String>()
        val journal = FakeJournal(events)
        val store = FakeStore(events)
        val executor = DurableCommandExecutor(store, journal)

        assertEquals(DomainResult.Success(Unit), executor.execute(pending("one")))

        assertEquals(listOf("append:one", "apply:one", "remove:one"), events)
    }

    @Test
    fun failedStoreWriteLeavesJournalForStartupRecovery() = runBlocking {
        val events = mutableListOf<String>()
        val journal = FakeJournal(events)
        val store = FakeStore(events, failOperation = OperationId("one"))
        val executor = DurableCommandExecutor(store, journal)

        assertEquals(
            DomainResult.Failure(DomainFailure.DatabaseUnavailable),
            executor.execute(pending("one")),
        )
        assertEquals(listOf("append:one", "apply:one"), events)
        assertEquals(listOf(pending("one")), journal.entries)
    }

    @Test
    fun recoveryReplaysEntriesInJournalOrder() = runBlocking {
        val events = mutableListOf<String>()
        val journal = FakeJournal(events).apply {
            entries += pending("one")
            entries += pending("two")
        }
        val executor = DurableCommandExecutor(FakeStore(events), journal)

        assertEquals(DomainResult.Success(2), executor.recover())
        assertEquals(
            listOf("read", "apply:one", "remove:one", "apply:two", "remove:two"),
            events,
        )
        assertTrue(journal.entries.isEmpty())
    }

    private fun pending(id: String) = PendingCommand(
        operationId = OperationId(id),
        command = DocumentCommand.UpdateBackground(
            documentId = DocumentId(1),
            pageId = PageId(2),
            before = PageBackground.Solid(),
            after = PageBackground.Transparent,
        ),
    )

    private class FakeStore(
        private val events: MutableList<String>,
        private val failOperation: OperationId? = null,
    ) : DocumentCommandStore {
        override suspend fun apply(
            command: DocumentCommand,
            operationId: OperationId,
        ): DomainResult<Unit> {
            events += "apply:${operationId.value}"
            return if (operationId == failOperation) {
                DomainResult.Failure(DomainFailure.DatabaseUnavailable)
            } else {
                DomainResult.Success(Unit)
            }
        }
    }

    private class FakeJournal(
        private val events: MutableList<String>,
    ) : PendingCommandJournal {
        val entries = mutableListOf<PendingCommand>()

        override suspend fun append(command: PendingCommand): DomainResult<Unit> {
            events += "append:${command.operationId.value}"
            entries += command
            return DomainResult.Success(Unit)
        }

        override suspend fun readAll(): DomainResult<List<PendingCommand>> {
            events += "read"
            return DomainResult.Success(entries.toList())
        }

        override suspend fun remove(operationId: OperationId): DomainResult<Unit> {
            events += "remove:${operationId.value}"
            entries.removeAll { it.operationId == operationId }
            return DomainResult.Success(Unit)
        }
    }
}
