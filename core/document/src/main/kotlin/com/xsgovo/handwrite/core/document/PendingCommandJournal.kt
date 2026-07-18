package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.OperationId

data class PendingCommand(
    val operationId: OperationId,
    val command: DocumentCommand,
)

interface PendingCommandJournal {
    suspend fun append(command: PendingCommand): DomainResult<Unit>

    suspend fun readAll(): DomainResult<List<PendingCommand>>

    suspend fun remove(operationId: OperationId): DomainResult<Unit>
}
