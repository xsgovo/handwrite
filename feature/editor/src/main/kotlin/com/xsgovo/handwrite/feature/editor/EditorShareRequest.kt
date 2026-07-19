package com.xsgovo.handwrite.feature.editor

import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId

data class EditorShareRequest(
    val documentName: String,
    val pageContent: PageContent,
)

internal fun EditorUiState.toShareRequest(): EditorShareRequest {
    val activePageId = pageId ?: elements.firstOrNull()?.pageId ?: PageId(0)
    return EditorShareRequest(
        documentName = documentName,
        pageContent = PageContent(
            page = Page(
                id = activePageId,
                documentId = documentId ?: DocumentId(0),
                orderKey = 0,
                size = pageSize,
                background = background,
            ),
            elements = elements,
        ),
    )
}
