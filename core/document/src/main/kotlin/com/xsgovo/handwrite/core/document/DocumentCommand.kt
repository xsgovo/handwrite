package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId

sealed interface DocumentCommand {
    val documentId: DocumentId
    val pageId: PageId

    fun inverse(): DocumentCommand

    fun estimatedBytes(): Long

    data class ReplaceElements(
        override val documentId: DocumentId,
        override val pageId: PageId,
        val removed: List<PageElement>,
        val added: List<PageElement>,
    ) : DocumentCommand {
        init {
            require(removed.all { it.pageId == pageId })
            require(added.all { it.pageId == pageId })
            require(removed.map(PageElement::id).toSet().size == removed.size)
            require(added.map(PageElement::id).toSet().size == added.size)
        }

        override fun inverse(): DocumentCommand = copy(removed = added, added = removed)

        override fun estimatedBytes(): Long =
            (removed.sumOf(PageElement::estimatedBytes) + added.sumOf(PageElement::estimatedBytes)).toLong()
    }

    data class UpdateBackground(
        override val documentId: DocumentId,
        override val pageId: PageId,
        val before: PageBackground,
        val after: PageBackground,
    ) : DocumentCommand {
        override fun inverse(): DocumentCommand = copy(before = after, after = before)

        override fun estimatedBytes(): Long = 256
    }
}

private fun PageElement.estimatedBytes(): Int = when (this) {
    is com.xsgovo.handwrite.core.model.StrokeElement -> 96 + samples.size * 20
    else -> 256
}

fun DocumentCommand.removedElementIds(): Set<ElementId> = when (this) {
    is DocumentCommand.ReplaceElements -> removed.mapTo(linkedSetOf(), PageElement::id)
    is DocumentCommand.UpdateBackground -> emptySet()
}
