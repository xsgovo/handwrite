package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.OperationId

interface DocumentCommandStore {
    suspend fun apply(
        command: DocumentCommand,
        operationId: OperationId,
    ): DomainResult<Unit>
}
