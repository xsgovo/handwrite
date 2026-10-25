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

/** 文档、页面与元素数据的读写契约；失败统一以 [DomainResult] 报告。 */
interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    fun observeDocument(documentId: DocumentId): Flow<Document?>

    fun observePage(pageId: PageId): Flow<PageContent?>

    /** 观察文档的全部页面，每项含该页元素；与 [observePage] 的区别是覆盖整个文档而非单页。 */
    fun observePageContents(documentId: DocumentId): Flow<List<PageContent>>

    suspend fun loadSnapshot(documentId: DocumentId): DomainResult<DocumentSnapshot>

    suspend fun createDocument(
        name: DisplayName,
        size: LogicalSize,
        background: PageBackground,
        nowEpochMillis: Long,
    ): DomainResult<DocumentId>

    suspend fun deleteDocument(documentId: DocumentId): DomainResult<Unit>

    suspend fun addPage(
        documentId: DocumentId,
        size: LogicalSize,
        background: PageBackground,
        nowEpochMillis: Long,
    ): DomainResult<Page>

    suspend fun deletePage(documentId: DocumentId, pageId: PageId): DomainResult<Unit>

    // 页面移动以目标位置索引表达：页面以 orderKey 排序，不以数组位置作为身份。
    suspend fun movePage(documentId: DocumentId, pageId: PageId, toIndex: Int): DomainResult<Unit>

    suspend fun setPageBookmarked(
        documentId: DocumentId,
        pageId: PageId,
        bookmarked: Boolean,
    ): DomainResult<Unit>

    suspend fun updateLastActivePage(documentId: DocumentId, pageId: PageId): DomainResult<Unit>

    /** 应用命令式修改（元素增删与背景变更），调用方据此维护撤销/重做历史；页面级操作不经此入口。 */
    suspend fun apply(command: DocumentCommand): DomainResult<Unit>
}
