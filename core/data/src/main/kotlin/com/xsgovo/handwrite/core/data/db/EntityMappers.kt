package com.xsgovo.handwrite.core.data.db

import com.xsgovo.handwrite.core.data.codec.PayloadCodec
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.FolderId
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement

internal fun DocumentBundle.toDomain(): Document {
    val validName = DisplayName.create(item.name) as? NameResult.Valid
        ?: error("Invalid persisted document name")
    return Document(
        id = DocumentId(item.id),
        name = validName.name,
        folderId = item.parentFolderId?.let(::FolderId),
        createdAtEpochMillis = item.createdAtEpochMillis,
        modifiedAtEpochMillis = item.modifiedAtEpochMillis,
        isFavorite = item.isFavorite,
        lastActivePageId = PageId(state.lastActivePageId),
    )
}

internal fun PageBundle.toDomain(): PageContent {
    val domainPage = page.toDomain()
    return PageContent(
        page = domainPage,
        elements = elements.sortedBy(PageElementEntity::orderKey).map(PageElementEntity::toDomain),
    )
}

internal fun PageEntity.toDomain(): Page = Page(
    id = PageId(id),
    documentId = DocumentId(documentId),
    orderKey = orderKey,
    size = LogicalSize(logicalWidth, logicalHeight),
    background = PayloadCodec.decodeBackground(backgroundPayload),
)

internal fun PageElementEntity.toDomain(): PageElement = when (type) {
    PageElementTypes.STROKE -> {
        require(payloadVersion == PayloadCodec.STROKE_PAYLOAD_VERSION)
        val (style, samples) = PayloadCodec.decodeStroke(payload)
        StrokeElement(ElementId(id), PageId(pageId), orderKey, style, samples)
    }
    else -> error("Unsupported element type: $type")
}

internal fun PageElement.toEntity(): PageElementEntity = when (this) {
    is StrokeElement -> PageElementEntity(
        id = id.value,
        pageId = pageId.value,
        orderKey = orderKey,
        type = PageElementTypes.STROKE,
        payloadVersion = PayloadCodec.STROKE_PAYLOAD_VERSION,
        payload = PayloadCodec.encodeStroke(style, samples),
    )
    else -> error("Unsupported page element: ${this::class.qualifiedName}")
}
