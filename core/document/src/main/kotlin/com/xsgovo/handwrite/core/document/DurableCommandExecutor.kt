package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DomainResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DurableCommandExecutor(
    private val store: DocumentCommandStore,
    private val journal: PendingCommandJournal,
) {
    private val mutex = Mutex()

    suspend fun execute(pending: PendingCommand): DomainResult<Unit> = mutex.withLock {
        val appended = journal.append(pending)
        if (appended is DomainResult.Failure) return appended

        val applied = store.apply(pending.command, pending.operationId)
        if (applied is DomainResult.Failure) return applied

        return journal.remove(pending.operationId)
    }

    suspend fun recover(): DomainResult<Int> = mutex.withLock {
        val entries = journal.readAll()
        if (entries is DomainResult.Failure) return entries

        var recovered = 0
        for (pending in (entries as DomainResult.Success).value) {
            val applied = store.apply(pending.command, pending.operationId)
            if (applied is DomainResult.Failure) return applied

            val removed = journal.remove(pending.operationId)
            if (removed is DomainResult.Failure) return removed
            recovered++
        }
        return DomainResult.Success(recovered)
    }
}
