package com.note.handwrite.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.note.handwrite.data.InputSettingsRepository
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.NoteOperation
import com.note.handwrite.model.InputMode
import com.note.handwrite.model.RemovedStroke
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.ui.theme.PenBlack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NoteViewModel(
    private val inputSettingsRepository: InputSettingsRepository? = null
) : ViewModel() {
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

    private val _inputMode = MutableStateFlow(InputMode.SPEN)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val undoHistory = mutableListOf<NoteOperation>()
    private val activeEraseEntries = mutableListOf<RemovedStroke>()
    private var eraseBaseline: List<Stroke> = emptyList()

    init {
        inputSettingsRepository?.settings?.onEach { settings ->
            _inputMode.value = settings.inputMode
            _currentTool.value = settings.tool
            _currentColor.value = settings.color
            _currentWidth.value = settings.width
            _backgroundType.value = settings.background
        }?.launchIn(viewModelScope)
    }

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

    fun beginErase() {
        activeEraseEntries.clear()
        eraseBaseline = _strokes.value
    }

    fun eraseStrokes(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val removed = eraseBaseline.mapIndexedNotNull { index, candidate ->
            if (strokes.any { it === candidate }) RemovedStroke(index, candidate) else null
        }
        if (removed.isEmpty()) return
        activeEraseEntries += removed.filter { entry ->
            activeEraseEntries.none { it.stroke === entry.stroke }
        }
        val removedIdentities = removed.map { it.stroke }.toSet()
        _strokes.value = _strokes.value.filter { it !in removedIdentities }
    }

    fun endErase() {
        if (activeEraseEntries.isNotEmpty()) {
            undoHistory += NoteOperation.StrokesRemoved(activeEraseEntries.sortedBy { it.index })
            if (undoHistory.size > 100) undoHistory.removeAt(0)
            _canUndo.value = true
        }
        activeEraseEntries.clear()
        eraseBaseline = emptyList()
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
        activeEraseEntries.clear()
        eraseBaseline = emptyList()
    }

    fun switchTool(tool: Tool) {
        _currentTool.value = tool
        viewModelScope.launch { inputSettingsRepository?.setTool(tool) }
    }

    fun switchColor(color: Color) {
        _currentColor.value = color
        viewModelScope.launch { inputSettingsRepository?.setColor(color) }
    }

    fun switchWidth(width: Float) {
        _currentWidth.value = width
        viewModelScope.launch { inputSettingsRepository?.setWidth(width) }
    }

    fun switchBackground(type: BackgroundType) {
        _backgroundType.value = type
        viewModelScope.launch { inputSettingsRepository?.setBackground(type) }
    }

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
        viewModelScope.launch {
            inputSettingsRepository?.setInputMode(mode)
        }
    }

    private fun updateStrokes(strokes: List<Stroke>, operation: NoteOperation) {
        _strokes.value = strokes
        undoHistory += operation
        if (undoHistory.size > 100) undoHistory.removeAt(0)
        _canUndo.value = true
    }
}
