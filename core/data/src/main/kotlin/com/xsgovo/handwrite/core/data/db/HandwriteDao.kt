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

    @Transaction
    @Query("SELECT * FROM library_items WHERE id = :documentId AND kind = 'DOCUMENT'")
    fun observeDocument(documentId: Long): Flow<DocumentBundle?>

    @Transaction
    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun observePage(pageId: Long): Flow<PageBundle?>

    @Query("SELECT COUNT(*) FROM applied_operations WHERE operationId = :operationId")
    suspend fun hasAppliedOperation(operationId: String): Int

    @Query("SELECT documentId FROM pages WHERE id = :pageId")
    suspend fun findDocumentIdForPage(pageId: Long): Long?

    @Query("DELETE FROM page_elements WHERE pageId = :pageId AND id IN (:ids)")
    suspend fun deleteElements(pageId: Long, ids: List<Long>)

    @Query("UPDATE pages SET backgroundPayload = :payload WHERE id = :pageId")
    suspend fun updateBackground(pageId: Long, payload: ByteArray): Int

    @Query("UPDATE library_items SET name = :name, normalizedName = :normalizedName, modifiedAtEpochMillis = :now WHERE id = :documentId AND kind = 'DOCUMENT'")
    suspend fun renameDocument(documentId: Long, name: String, normalizedName: String, now: Long): Int

    @Query("UPDATE library_items SET modifiedAtEpochMillis = :now WHERE id = (SELECT documentId FROM pages WHERE id = :pageId)")
    suspend fun touchDocumentForPage(pageId: Long, now: Long)
}
