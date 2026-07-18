package com.xsgovo.handwrite.core.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.xsgovo.handwrite.core.data.codec.PayloadCodec
import com.xsgovo.handwrite.core.data.db.AppliedOperationEntity
import com.xsgovo.handwrite.core.data.db.DocumentStateEntity
import com.xsgovo.handwrite.core.data.db.HandwriteDao
import com.xsgovo.handwrite.core.data.db.HandwriteDatabase
import com.xsgovo.handwrite.core.data.db.LibraryItemEntity
import com.xsgovo.handwrite.core.data.db.LibraryItemKinds
import com.xsgovo.handwrite.core.data.db.PageEntity
import com.xsgovo.handwrite.core.data.db.toDomain
import com.xsgovo.handwrite.core.data.db.toEntity
import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.EpochClock
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDocumentRepository(
    private val database: HandwriteDatabase,
    private val dao: HandwriteDao,
    private val clock: EpochClock,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeDocuments().map { documents -> documents.map { it.toDomain() } }

    override fun observeDocument(documentId: DocumentId): Flow<Document?> =
        dao.observeDocument(documentId.value).map { it?.toDomain() }

    override fun observePage(pageId: PageId): Flow<PageContent?> =
        dao.observePage(pageId.value).map { it?.toDomain() }

    override fun observePages(documentId: DocumentId): Flow<List<Page>> =
        dao.observePages(documentId.value).map { pages -> pages.map { it.toDomain() } }

    override suspend fun loadSnapshot(documentId: DocumentId): DomainResult<DocumentSnapshot> = guardedWrite {
        database.withTransaction {
            val document = dao.findDocument(documentId.value)?.toDomain() ?: throw MissingDocumentException()
            val pages = dao.findPageBundles(documentId.value).map { it.toDomain() }
            if (pages.isEmpty()) throw MissingPageException()
            DocumentSnapshot(document, pages)
        }
    }

    override suspend fun createDocument(
        name: DisplayName,
        size: LogicalSize,
        background: PageBackground,
        nowEpochMillis: Long,
    ): DomainResult<DocumentId> = guardedWrite(DomainFailure.NameConflict) {
        database.withTransaction {
            val documentId = dao.insertLibraryItem(
                LibraryItemEntity(
                    kind = LibraryItemKinds.DOCUMENT,
                    name = name.value,
                    normalizedName = name.normalizedKey,
                    parentFolderId = null,
                    depth = 0,
                    createdAtEpochMillis = nowEpochMillis,
                    modifiedAtEpochMillis = nowEpochMillis,
                    isFavorite = false,
                ),
            )
            val pageId = dao.insertPage(
                PageEntity(
                    documentId = documentId,
                    orderKey = ORDER_STEP,
                    logicalWidth = size.width,
                    logicalHeight = size.height,
                    backgroundPayload = PayloadCodec.encodeBackground(background),
                ),
            )
            background.resourceIdOrNull()?.let { requireResourceReference(it.value, 1) }
            dao.insertDocumentState(DocumentStateEntity(documentId, pageId))
            DocumentId(documentId)
        }
    }

    override suspend fun renameDocument(
        documentId: DocumentId,
        name: DisplayName,
    ): DomainResult<Unit> = guardedWrite(DomainFailure.NameConflict) {
        val changed = dao.renameDocument(documentId.value, name.value, name.normalizedKey, clock.nowMillis())
        if (changed == 0) throw MissingDocumentException()
    }

    override suspend fun deleteDocument(documentId: DocumentId): DomainResult<Unit> = guardedWrite {
        database.withTransaction {
            val backgrounds = dao.findBackgroundsForDocument(documentId.value).map(PayloadCodec::decodeBackground)
            if (dao.deleteDocument(documentId.value) == 0) throw MissingDocumentException()
            backgrounds.forEach { it.resourceIdOrNull()?.let { id -> requireResourceReference(id.value, -1) } }
        }
    }

    override suspend fun createPage(
        documentId: DocumentId,
        size: LogicalSize,
        background: PageBackground,
    ): DomainResult<PageId> = guardedWrite {
        database.withTransaction {
            if (dao.hasDocument(documentId.value) == 0) throw MissingDocumentException()
            val pageId = dao.insertPage(
                PageEntity(
                    documentId = documentId.value,
                    orderKey = dao.maxPageOrderKey(documentId.value) + ORDER_STEP,
                    logicalWidth = size.width,
                    logicalHeight = size.height,
                    backgroundPayload = PayloadCodec.encodeBackground(background),
                ),
            )
            background.resourceIdOrNull()?.let { requireResourceReference(it.value, 1) }
            dao.updateLastActivePage(documentId.value, pageId)
            dao.touchDocumentForPage(pageId, clock.nowMillis())
            PageId(pageId)
        }
    }

    override suspend fun deletePage(pageId: PageId): DomainResult<Unit> = guardedWrite {
        database.withTransaction {
            val documentId = dao.findDocumentIdForPage(pageId.value) ?: throw MissingPageException()
            if (dao.countPages(documentId) <= 1) throw LastPageException()
            val wasActivePage = dao.findLastActivePageId(documentId) == pageId.value
            val background = dao.findBackgroundForPage(pageId.value)?.let(PayloadCodec::decodeBackground)
                ?: throw MissingPageException()
            if (dao.deletePage(pageId.value) == 0) throw MissingPageException()
            background.resourceIdOrNull()?.let { requireResourceReference(it.value, -1) }
            val firstPageId = dao.findFirstPageId(documentId) ?: throw MissingPageException()
            if (wasActivePage) dao.updateLastActivePage(documentId, firstPageId)
            dao.touchDocumentForPage(firstPageId, clock.nowMillis())
        }
    }

    override suspend fun setLastActivePage(
        documentId: DocumentId,
        pageId: PageId,
    ): DomainResult<Unit> = guardedWrite {
        if (dao.findDocumentIdForPage(pageId.value) != documentId.value) throw MissingPageException()
        if (dao.updateLastActivePage(documentId.value, pageId.value) == 0) throw MissingDocumentException()
    }

    override suspend fun apply(
        command: DocumentCommand,
        operationId: OperationId,
    ): DomainResult<Unit> = guardedWrite {
        database.withTransaction {
            if (dao.hasAppliedOperation(operationId.value) != 0) return@withTransaction
            if (dao.findDocumentIdForPage(command.pageId.value) != command.documentId.value) {
                throw MissingPageException()
            }
            when (command) {
                is DocumentCommand.ReplaceElements -> {
                    val removedIds = command.removed.map { it.id.value }
                    if (removedIds.isNotEmpty()) dao.deleteElements(command.pageId.value, removedIds)
                    if (command.added.isNotEmpty()) dao.insertElements(command.added.map { it.toEntity() })
                }
                is DocumentCommand.UpdateBackground -> {
                    val beforeResource = command.before.resourceIdOrNull()
                    val afterResource = command.after.resourceIdOrNull()
                    if (afterResource != beforeResource) {
                        afterResource?.let { requireResourceReference(it.value, 1) }
                    }
                    if (dao.updateBackground(command.pageId.value, PayloadCodec.encodeBackground(command.after)) == 0) {
                        throw MissingPageException()
                    }
                    if (afterResource != beforeResource) {
                        beforeResource?.let { requireResourceReference(it.value, -1) }
                    }
                }
            }
            dao.touchDocumentForPage(command.pageId.value, clock.nowMillis())
            dao.insertAppliedOperation(AppliedOperationEntity(operationId.value, clock.nowMillis()))
        }
    }

    private suspend fun <T> guardedWrite(
        constraintFailure: DomainFailure = DomainFailure.DatabaseUnavailable,
        block: suspend () -> T,
    ): DomainResult<T> = try {
        DomainResult.Success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: SQLiteConstraintException) {
        DomainResult.Failure(constraintFailure)
    } catch (exception: SQLiteException) {
        DomainResult.Failure(DomainFailure.DatabaseUnavailable)
    } catch (exception: MissingDocumentException) {
        DomainResult.Failure(DomainFailure.DocumentNotFound)
    } catch (exception: MissingPageException) {
        DomainResult.Failure(DomainFailure.PageNotFound)
    } catch (exception: LastPageException) {
        DomainResult.Failure(DomainFailure.LastPageCannotBeDeleted)
    } catch (exception: MissingResourceException) {
        DomainResult.Failure(DomainFailure.ResourceNotFound)
    } catch (exception: IOException) {
        DomainResult.Failure(DomainFailure.DatabaseUnavailable)
    }

    private class MissingDocumentException : Exception()
    private class MissingPageException : Exception()
    private class LastPageException : Exception()

    private suspend fun requireResourceReference(resourceId: Long, delta: Long) {
        if (dao.adjustResourceReferenceCount(resourceId, delta) == 0) throw MissingResourceException()
    }

    private class MissingResourceException : Exception()

    private companion object {
        const val ORDER_STEP = 1_024L
    }
}

private fun PageBackground.resourceIdOrNull() = (this as? PageBackground.Asset)?.resourceId
