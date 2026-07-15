package com.note.handwrite.model

sealed interface NoteOperation {
    data class StrokeAdded(val stroke: Stroke) : NoteOperation

    data class StrokesRemoved(val entries: List<RemovedStroke>) : NoteOperation
}

data class RemovedStroke(
    val index: Int,
    val stroke: Stroke
)
