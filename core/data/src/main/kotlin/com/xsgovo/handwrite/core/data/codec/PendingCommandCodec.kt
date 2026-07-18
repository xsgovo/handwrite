package com.xsgovo.handwrite.core.data.codec

import com.xsgovo.handwrite.core.data.proto.ElementEnvelope
import com.xsgovo.handwrite.core.data.proto.ElementType
import com.xsgovo.handwrite.core.data.proto.PendingCommandPayload
import com.xsgovo.handwrite.core.data.proto.ReplaceElementsPayload
import com.xsgovo.handwrite.core.data.proto.UpdateBackgroundPayload
import com.xsgovo.handwrite.core.document.DocumentCommand
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.OperationId
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement

object PendingCommandCodec {
    fun encode(pending: PendingCommand, sequence: Long): ByteArray {
        val builder = PendingCommandPayload.newBuilder()
            .setOperationId(pending.operationId.value)
            .setDocumentId(pending.command.documentId.value)
            .setPageId(pending.command.pageId.value)
            .setSequence(sequence)
        when (val command = pending.command) {
            is DocumentCommand.ReplaceElements -> builder.replaceElements = ReplaceElementsPayload.newBuilder()
                .addAllRemoved(command.removed.map(PageElement::toEnvelope))
                .addAllAdded(command.added.map(PageElement::toEnvelope))
                .build()
            is DocumentCommand.UpdateBackground -> builder.updateBackground = UpdateBackgroundPayload.newBuilder()
                .setBefore(command.before.toProto())
                .setAfter(command.after.toProto())
                .build()
        }
        return builder.build().toByteArray()
    }

    fun decode(bytes: ByteArray): DecodedPendingCommand {
        val payload = PendingCommandPayload.parseFrom(bytes)
        val documentId = DocumentId(payload.documentId)
        val pageId = PageId(payload.pageId)
        val command = when (payload.commandCase) {
            PendingCommandPayload.CommandCase.REPLACE_ELEMENTS -> DocumentCommand.ReplaceElements(
                documentId = documentId,
                pageId = pageId,
                removed = payload.replaceElements.removedList.map(ElementEnvelope::toDomain),
                added = payload.replaceElements.addedList.map(ElementEnvelope::toDomain),
            )
            PendingCommandPayload.CommandCase.UPDATE_BACKGROUND -> DocumentCommand.UpdateBackground(
                documentId = documentId,
                pageId = pageId,
                before = payload.updateBackground.before.toDomain(),
                after = payload.updateBackground.after.toDomain(),
            )
            else -> error("Pending command has no payload")
        }
        return DecodedPendingCommand(
            sequence = payload.sequence,
            command = PendingCommand(OperationId(payload.operationId), command),
        )
    }
}

data class DecodedPendingCommand(
    val sequence: Long,
    val command: PendingCommand,
)

private fun PageElement.toEnvelope(): ElementEnvelope = when (this) {
    is StrokeElement -> ElementEnvelope.newBuilder()
        .setId(id.value)
        .setPageId(pageId.value)
        .setOrderKey(orderKey)
        .setType(ElementType.ELEMENT_TYPE_STROKE)
        .setPayload(com.google.protobuf.ByteString.copyFrom(PayloadCodec.encodeStroke(style, samples)))
        .build()
    else -> error("Unsupported pending element: ${this::class.qualifiedName}")
}

private fun ElementEnvelope.toDomain(): PageElement = when (type) {
    ElementType.ELEMENT_TYPE_STROKE -> {
        val (style, samples) = PayloadCodec.decodeStroke(payload.toByteArray())
        StrokeElement(ElementId(id), PageId(pageId), orderKey, style, samples)
    }
    else -> error("Unsupported pending element type: $type")
}
