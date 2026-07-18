package com.xsgovo.handwrite.core.document

data class HistoryLimits(
    val maxCommands: Int,
    val maxEstimatedBytes: Long,
) {
    init {
        require(maxCommands > 0)
        require(maxEstimatedBytes > 0)
    }
}

class CommandHistory(
    private val limits: HistoryLimits,
) {
    private val undo = ArrayDeque<DocumentCommand>()
    private val redo = ArrayDeque<DocumentCommand>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    val undoCount: Int get() = undo.size
    val redoCount: Int get() = redo.size

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

    fun clear() {
        undo.clear()
        redo.clear()
    }

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
