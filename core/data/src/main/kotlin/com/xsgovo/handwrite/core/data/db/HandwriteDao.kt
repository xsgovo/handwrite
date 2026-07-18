package com.xsgovo.handwrite.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HandwriteDao {
    @Insert
    suspend fun insertLibraryItem(item: LibraryItemEntity): Long

    @Insert
    suspend fun insertDocumentState(state: DocumentStateEntity)

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertElements(elements: List<PageElementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAppliedOperation(operation: AppliedOperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResource(resource: ResourceEntity): Long

    @Transaction
    @Query("SELECT * FROM library_items WHERE kind = 'DOCUMENT' ORDER BY normalizedName ASC")
    fun observeDocuments(): Flow<List<DocumentBundle>>

    @Transaction
    @Query("SELECT * FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    fun observeDocument(documentId: Long): Flow<DocumentBundle?>

    @Transaction
    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun observePage(pageId: Long): Flow<PageBundle?>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY orderKey ASC")
    fun observePages(documentId: Long): Flow<List<PageEntity>>

    @Transaction
    @Query("SELECT * FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun findDocument(documentId: Long): DocumentBundle?

    @Transaction
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY orderKey ASC")
    suspend fun findPageBundles(documentId: Long): List<PageBundle>

    @Query("SELECT COUNT(*) FROM applied_operations WHERE operationId = :operationId")
    suspend fun hasAppliedOperation(operationId: String): Int

    @Query("SELECT documentId FROM pages WHERE id = :pageId")
    suspend fun findDocumentIdForPage(pageId: Long): Long?

    @Query("SELECT COUNT(*) FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun hasDocument(documentId: Long): Int

    @Query("SELECT COALESCE(MAX(orderKey), 0) FROM pages WHERE documentId = :documentId")
    suspend fun maxPageOrderKey(documentId: Long): Long

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun countPages(documentId: Long): Int

    @Query("SELECT id FROM pages WHERE documentId = :documentId ORDER BY orderKey ASC LIMIT 1")
    suspend fun findFirstPageId(documentId: Long): Long?

    @Query("SELECT lastActivePageId FROM document_states WHERE documentId = :documentId")
    suspend fun findLastActivePageId(documentId: Long): Long?

    @Query("SELECT * FROM resources WHERE id = :resourceId")
    suspend fun findResource(resourceId: Long): ResourceEntity?

    @Query("SELECT * FROM resources WHERE sha256 = :sha256")
    suspend fun findResourceByHash(sha256: String): ResourceEntity?

    @Query("SELECT backgroundPayload FROM pages WHERE documentId = :documentId")
    suspend fun findBackgroundsForDocument(documentId: Long): List<ByteArray>

    @Query("SELECT backgroundPayload FROM pages WHERE id = :pageId")
    suspend fun findBackgroundForPage(pageId: Long): ByteArray?

    @Query("DELETE FROM page_elements WHERE pageId = :pageId AND id IN (:ids)")
    suspend fun deleteElements(pageId: Long, ids: List<Long>)

    @Query("DELETE FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun deleteDocument(documentId: Long): Int

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long): Int

    @Query("UPDATE pages SET backgroundPayload = :payload WHERE id = :pageId")
    suspend fun updateBackground(pageId: Long, payload: ByteArray): Int

    @Query("UPDATE library_items SET name = :name, normalizedName = :normalizedName, modifiedAtEpochMillis = :now WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun renameDocument(documentId: Long, name: String, normalizedName: String, now: Long): Int

    @Query("UPDATE library_items SET modifiedAtEpochMillis = :now WHERE id = (SELECT documentId FROM pages WHERE id = :pageId)")
    suspend fun touchDocumentForPage(pageId: Long, now: Long)

    @Query("UPDATE document_states SET lastActivePageId = :pageId WHERE documentId = :documentId")
    suspend fun updateLastActivePage(documentId: Long, pageId: Long): Int

    @Query("UPDATE resources SET referenceCount = referenceCount + :delta WHERE id = :resourceId AND referenceCount + :delta >= 0")
    suspend fun adjustResourceReferenceCount(resourceId: Long, delta: Long): Int

    @Query("SELECT * FROM resources WHERE referenceCount = 0")
    suspend fun findUnreferencedResources(): List<ResourceEntity>

    @Query("DELETE FROM resources WHERE id = :resourceId AND referenceCount = 0")
    suspend fun deleteUnreferencedResource(resourceId: Long): Int
}
