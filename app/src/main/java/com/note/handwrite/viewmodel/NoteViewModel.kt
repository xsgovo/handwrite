package com.note.handwrite.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NoteOperation
import com.note.handwrite.model.RemovedStroke
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.ui.theme.PenBlack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoteViewModel : ViewModel() {
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes.asStateFlow()

    private val _currentTool = MutableStateFlow(Tool.PEN)
    val currentTool: StateFlow<Tool> = _currentTool.asStateFlow()

    private val _currentColor = MutableStateFlow(PenBlack)
    val currentColor: StateFlow<Color> = _currentColor.asStateFlow()

    private val _currentWidth = MutableStateFlow(8f)
    val currentWidth: StateFlow<Float> = _currentWidth.asStateFlow()

    private val _backgroundType = MutableStateFlow(BackgroundType.PLAIN)
    val backgroundType: StateFlow<BackgroundType> = _backgroundType.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val undoHistory = mutableListOf<NoteOperation>()

    fun addStroke(stroke: Stroke) {
        updateStrokes(_strokes.value + stroke, NoteOperation.StrokeAdded(stroke))
    }

    fun removeStrokes(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val removed = _strokes.value.mapIndexedNotNull { index, candidate ->
            if (strokes.any { it === candidate }) RemovedStroke(index, candidate) else null
        }
        if (removed.isEmpty()) return
        val removedIdentities = removed.map { it.stroke }.toSet()
        updateStrokes(
            _strokes.value.filter { it !in removedIdentities },
            NoteOperation.StrokesRemoved(removed)
        )
    }

    fun undo() {
        val operation = undoHistory.removeLastOrNull() ?: return
        _strokes.value = when (operation) {
            is NoteOperation.StrokeAdded -> {
                val index = _strokes.value.indexOfLast { it === operation.stroke }
                if (index < 0) _strokes.value else _strokes.value.toMutableList().also { it.removeAt(index) }
            }
            is NoteOperation.StrokesRemoved -> {
                _strokes.value.toMutableList().also { current ->
                    operation.entries.sortedBy { it.index }.forEach { entry ->
                        current.add(entry.index.coerceIn(0, current.size), entry.stroke)
                    }
                }
            }
        }
        _canUndo.value = undoHistory.isNotEmpty()
    }

    fun clearAll() {
        _strokes.value = emptyList()
        undoHistory.clear()
        _canUndo.value = false
    }

    fun switchTool(tool: Tool) {
        _currentTool.value = tool
    }

    fun switchColor(color: Color) {
        _currentColor.value = color
    }

    fun switchWidth(width: Float) {
        _currentWidth.value = width
    }

    fun switchBackground(type: BackgroundType) {
        _backgroundType.value = type
    }

    private fun updateStrokes(strokes: List<Stroke>, operation: NoteOperation) {
        _strokes.value = strokes
        undoHistory += operation
        if (undoHistory.size > 100) undoHistory.removeAt(0)
        _canUndo.value = true
    }
}
