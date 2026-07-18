package com.xsgovo.handwrite.core.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class DocumentBundle(
    @Embedded val item: LibraryItemEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val state: DocumentStateEntity,
)

data class PageBundle(
    @Embedded val page: PageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "pageId",
    )
    val elements: List<PageElementEntity>,
)
