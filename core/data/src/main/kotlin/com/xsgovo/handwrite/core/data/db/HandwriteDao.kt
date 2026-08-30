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

    @Transaction
    @Query("SELECT * FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun findDocument(documentId: Long): DocumentBundle?

    @Transaction
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY orderKey ASC")
    suspend fun findPageBundles(documentId: Long): List<PageBundle>

    @Query("SELECT documentId FROM pages WHERE id = :pageId")
    suspend fun findDocumentIdForPage(pageId: Long): Long?

    @Query("SELECT * FROM resources WHERE id = :resourceId")
    suspend fun findResource(resourceId: Long): ResourceEntity?

    @Query("SELECT * FROM resources WHERE sha256 = :sha256")
    suspend fun findResourceByHash(sha256: String): ResourceEntity?

    @Query("SELECT backgroundPayload FROM pages WHERE documentId = :documentId")
    suspend fun findBackgroundsForDocument(documentId: Long): List<ByteArray>

    @Query("DELETE FROM page_elements WHERE pageId = :pageId AND id IN (:ids)")
    suspend fun deleteElements(pageId: Long, ids: List<Long>)

    @Query("DELETE FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun deleteDocument(documentId: Long): Int

    @Query("UPDATE pages SET backgroundPayload = :payload WHERE id = :pageId")
    suspend fun updateBackground(pageId: Long, payload: ByteArray): Int

    @Query("UPDATE library_items SET modifiedAtEpochMillis = :now WHERE id = (SELECT documentId FROM pages WHERE id = :pageId)")
    suspend fun touchDocumentForPage(pageId: Long, now: Long)

    @Query("UPDATE resources SET referenceCount = referenceCount + :delta WHERE id = :resourceId AND referenceCount + :delta >= 0")
    suspend fun adjustResourceReferenceCount(resourceId: Long, delta: Long): Int

    @Query("SELECT * FROM resources WHERE referenceCount = 0")
    suspend fun findUnreferencedResources(): List<ResourceEntity>

    @Query("DELETE FROM resources WHERE id = :resourceId AND referenceCount = 0")
    suspend fun deleteUnreferencedResource(resourceId: Long): Int
}
