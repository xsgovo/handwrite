package com.note.handwrite.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.ui.theme.PenBlack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun addStroke(stroke: Stroke) {
        _strokes.update { it + stroke }
    }

    fun removeStroke(stroke: Stroke) {
        _strokes.update { it.filter { candidate -> candidate !== stroke } }
    }

    fun undo() {
        _strokes.update { it.dropLast(1) }
    }

    fun clearAll() {
        _strokes.value = emptyList()
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
}
