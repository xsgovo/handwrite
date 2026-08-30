package com.xsgovo.handwrite.core.data.db

import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import com.xsgovo.handwrite.core.data.codec.PayloadCodec
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement

internal fun DocumentBundle.toDomain(): Document = Document(
    id = DocumentId(item.id),
    name = persistedName(item.name),
    createdAtEpochMillis = item.createdAtEpochMillis,
    modifiedAtEpochMillis = item.modifiedAtEpochMillis,
    lastActivePageId = PageId(state.lastActivePageId),
)

internal fun PageBundle.toDomain(): PageContent {
    val domainPage = page.toDomain()
    return PageContent(
        page = domainPage,
        elements = elements.sortedBy(PageElementEntity::orderKey).mapNotNull(PageElementEntity::toDomain),
    )
}

internal fun PageEntity.toDomain(): Page = Page(
    id = PageId(id),
    documentId = DocumentId(documentId),
    orderKey = orderKey,
    size = LogicalSize(logicalWidth, logicalHeight),
    background = persistedBackground(backgroundPayload),
)

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

// One unreadable row must not break the whole library or page observation flow.
private fun persistedName(raw: String): DisplayName = when (val result = DisplayName.create(raw)) {
    is NameResult.Valid -> result.name
    is NameResult.Invalid -> {
        Log.w(LOG_TAG, "Falling back to default name after invalid persisted name (${result.problem})")
        (DisplayName.create(DEFAULT_DOCUMENT_NAME) as NameResult.Valid).name
    }
}

private fun persistedBackground(payload: ByteArray): PageBackground = try {
    PayloadCodec.decodeBackground(payload)
} catch (exception: InvalidProtocolBufferException) {
    Log.w(LOG_TAG, "Falling back to solid background after unreadable background payload", exception)
    PageBackground.Solid()
}

private fun PageElementEntity.toDomain(): PageElement? {
    if (type != PageElementTypes.STROKE) {
        Log.w(LOG_TAG, "Skipping element with unsupported type: $type")
        return null
    }
    if (payloadVersion != PayloadCodec.STROKE_PAYLOAD_VERSION) {
        Log.w(LOG_TAG, "Skipping stroke with unsupported payload version: $payloadVersion")
        return null
    }
    return try {
        val (style, samples) = PayloadCodec.decodeStroke(payload)
        StrokeElement(ElementId(id), PageId(pageId), orderKey, style, samples)
    } catch (exception: InvalidProtocolBufferException) {
        Log.w(LOG_TAG, "Skipping stroke with unreadable payload", exception)
        null
    } catch (exception: IllegalArgumentException) {
        Log.w(LOG_TAG, "Skipping stroke with out-of-range payload values", exception)
        null
    }
}

private const val LOG_TAG = "EntityMappers"
private const val DEFAULT_DOCUMENT_NAME = "未命名文档"
