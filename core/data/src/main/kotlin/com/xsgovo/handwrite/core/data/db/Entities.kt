package com.xsgovo.handwrite.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "library_items",
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["kind"]),
    ],
)
data class LibraryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val normalizedName: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

object LibraryItemKinds {
    const val DOCUMENT = "DOCUMENT"
}

@Entity(
    tableName = "document_states",
    foreignKeys = [
        ForeignKey(
            entity = LibraryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DocumentStateEntity(
    @PrimaryKey val documentId: Long,
    val lastActivePageId: Long,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = LibraryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId", "orderKey"], unique = true),
        Index(value = ["documentId"]),
    ],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val orderKey: Long,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val backgroundPayload: ByteArray,
)

@Entity(
    tableName = "page_elements",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId", "orderKey"], unique = true),
        Index(value = ["pageId"]),
        Index(value = ["type"]),
    ],
)
data class PageElementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val orderKey: Long,
    val type: String,
    val payloadVersion: Int,
    val payload: ByteArray,
)

object PageElementTypes {
    const val STROKE = "STROKE"
}

@Entity(
    tableName = "resources",
    indices = [Index(value = ["sha256"], unique = true)],
)
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sha256: String,
    val mimeType: String,
    val relativePath: String,
    val byteSize: Long,
    val referenceCount: Long,
)
