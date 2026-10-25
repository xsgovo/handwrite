package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageElement
import com.xsgovo.handwrite.core.model.PageId

/** 可逆的文档修改命令；元素与背景的修改以命令表达，供撤销/重做重放。 */
sealed interface DocumentCommand {
    val documentId: DocumentId
    val pageId: PageId

    /** 返回撤销本命令所需的重放命令；ReplaceElements 的逆是 removed 与 added 互换。 */
    fun inverse(): DocumentCommand

    /** 仅供历史容量裁剪时的粗略估算，不是精确字节数。 */
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
