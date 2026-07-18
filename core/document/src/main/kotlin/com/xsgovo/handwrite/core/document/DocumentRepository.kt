package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.LogicalSize
import kotlinx.coroutines.flow.Flow

interface DocumentRepository : DocumentCommandStore {
    fun observeDocuments(): Flow<List<Document>>

    fun observeDocument(documentId: DocumentId): Flow<Document?>

    fun observePages(documentId: DocumentId): Flow<List<Page>>

    fun observePage(pageId: PageId): Flow<PageContent?>

    suspend fun loadSnapshot(documentId: DocumentId): DomainResult<DocumentSnapshot>

    suspend fun createDocument(
        name: DisplayName,
        size: LogicalSize,
        background: PageBackground,
        nowEpochMillis: Long,
    ): DomainResult<DocumentId>

    suspend fun renameDocument(
        documentId: DocumentId,
        name: DisplayName,
    ): DomainResult<Unit>

    suspend fun deleteDocument(documentId: DocumentId): DomainResult<Unit>

    suspend fun createPage(
        documentId: DocumentId,
        size: LogicalSize,
        background: PageBackground,
    ): DomainResult<PageId>

    suspend fun deletePage(pageId: PageId): DomainResult<Unit>

    suspend fun setLastActivePage(
        documentId: DocumentId,
        pageId: PageId,
    ): DomainResult<Unit>
}
