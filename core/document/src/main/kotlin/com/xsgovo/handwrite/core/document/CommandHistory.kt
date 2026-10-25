package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.PageId

data class HistoryLimits(
    val maxCommands: Int,
    val maxEstimatedBytes: Long,
) {
    init {
        require(maxCommands > 0)
        require(maxEstimatedBytes > 0)
    }
}

/**
 * 进程内撤销/重做栈。采用预览-确认两步协议：commandToUndo/commandToRedo 只读栈并返回待重放命令，
 * confirm* 在写库成功后才真正搬动栈，撤销失败时栈保持原状。
 */
class CommandHistory(
    private val limits: HistoryLimits,
) {
    private val undo = ArrayDeque<DocumentCommand>()
    private val redo = ArrayDeque<DocumentCommand>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    val undoCount: Int get() = undo.size

    fun recordCommitted(command: DocumentCommand) {
        undo.addLast(command)
        redo.clear()
        trimToLimits()
    }

    fun commandToUndo(): DocumentCommand? = undo.lastOrNull()?.inverse()

    fun confirmUndo() {
        val command = undo.removeLastOrNull() ?: return
        redo.addLast(command)
        trimToLimits()
    }

    fun commandToRedo(): DocumentCommand? = redo.lastOrNull()

    fun confirmRedo() {
        val command = redo.removeLastOrNull() ?: return
        undo.addLast(command)
        trimToLimits()
    }

    // 页面删除不可撤销，但历史里可能残留指向已删页面的笔迹命令；
    // 不清理的话，后续撤销会撞上缺失页面并反复报错。
    fun dropCommandsForPage(pageId: PageId) {
        undo.removeAll { it.pageId == pageId }
        redo.removeAll { it.pageId == pageId }
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    // 超出上限时优先淘汰最旧的 undo 命令，undo 空后才动 redo。
    private fun trimToLimits() {
        while (undo.size + redo.size > limits.maxCommands || estimatedBytes() > limits.maxEstimatedBytes) {
            when {
                undo.isNotEmpty() -> undo.removeFirst()
                redo.isNotEmpty() -> redo.removeFirst()
                else -> return
            }
        }
    }

    private fun estimatedBytes(): Long =
        undo.sumOf(DocumentCommand::estimatedBytes) + redo.sumOf(DocumentCommand::estimatedBytes)
}
