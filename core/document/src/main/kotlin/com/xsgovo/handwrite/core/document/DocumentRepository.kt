package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import kotlinx.coroutines.flow.Flow

interface DocumentRepository : DocumentCommandStore {
    fun observeDocument(documentId: DocumentId): Flow<Document?>

    fun observePage(pageId: PageId): Flow<PageContent?>

    suspend fun renameDocument(
        documentId: DocumentId,
        name: DisplayName,
    ): DomainResult<Unit>
}
