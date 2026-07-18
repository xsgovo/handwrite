package com.xsgovo.handwrite.core.data

import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageId
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePendingCommandJournalTest {
    @Test
    fun appendReadAndRemoveUseDurableOperationEntries() = runBlocking {
        val directory = Files.createTempDirectory("pending-command-test").toFile()
        try {
            val journal = FilePendingCommandJournal(directory, Dispatchers.IO, initialSequence = 0)
            val first = pending("first", PageBackground.Solid(), PageBackground.Transparent)
            val second = pending(
                "second",
                PageBackground.Transparent,
                PageBackground.Solid(0xFFEEEEEE.toInt()),
            )

            assertTrue(journal.append(first) is DomainResult.Success)
            assertTrue(journal.append(second) is DomainResult.Success)
            assertEquals(listOf(first, second), (journal.readAll() as DomainResult.Success).value)

            assertTrue(journal.remove(first.operationId) is DomainResult.Success)
            assertEquals(listOf(second), (journal.readAll() as DomainResult.Success).value)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun pending(
        operationId: String,
        before: PageBackground,
        after: PageBackground,
    ) = PendingCommand(
        OperationId(operationId),
        DocumentCommand.UpdateBackground(DocumentId(1), PageId(2), before, after),
    )
}
