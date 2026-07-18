package com.xsgovo.handwrite.core.model

data class Folder(
    val id: FolderId,
    val name: DisplayName,
    val parentId: FolderId?,
    val depth: Int,
) {
    init {
        require(depth in 1..MAX_FOLDER_DEPTH)
    }

    companion object {
        const val MAX_FOLDER_DEPTH = 10
    }
}

data class Document(
    val id: DocumentId,
    val name: DisplayName,
    val folderId: FolderId?,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val isFavorite: Boolean = false,
    val lastActivePageId: PageId,
) {
    init {
        require(createdAtEpochMillis >= 0)
        require(modifiedAtEpochMillis >= createdAtEpochMillis)
    }
}

data class Page(
    val id: PageId,
    val documentId: DocumentId,
    val orderKey: Long,
    val size: LogicalSize,
    val background: PageBackground,
)

data class PageContent(
    val page: Page,
    val elements: List<PageElement>,
) {
    init {
        require(elements.all { it.pageId == page.id })
        require(elements.zipWithNext().all { (left, right) -> left.orderKey <= right.orderKey })
    }
}

data class DocumentSnapshot(
    val document: Document,
    val pages: List<PageContent>,
) {
    init {
        require(pages.isNotEmpty())
        require(pages.all { it.page.documentId == document.id })
        require(pages.zipWithNext().all { (left, right) -> left.page.orderKey <= right.page.orderKey })
    }
}
