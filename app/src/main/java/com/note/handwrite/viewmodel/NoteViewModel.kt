package com.note.handwrite.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.note.handwrite.data.InputSettingsRepository
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.DefaultColorSlots
import com.note.handwrite.model.NoteOperation
import com.note.handwrite.model.InputMode
import com.note.handwrite.model.RemovedStroke
import com.note.handwrite.model.Stroke
import com.note.handwrite.model.Tool
import com.note.handwrite.model.NotePage
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

    private val _colorSlots = MutableStateFlow(DefaultColorSlots)
    val colorSlots: StateFlow<List<Color>> = _colorSlots.asStateFlow()

    private val _activeColorSlot = MutableStateFlow(0)
    val activeColorSlot: StateFlow<Int> = _activeColorSlot.asStateFlow()

    private val _currentColor = MutableStateFlow(DefaultColorSlots.first())
    val currentColor: StateFlow<Color> = _currentColor.asStateFlow()

    private val _currentWidthStep = MutableStateFlow(50)
    val currentWidthStep: StateFlow<Int> = _currentWidthStep.asStateFlow()

    private val _currentWidth = MutableStateFlow(NotePage.widthForStep(50))
    val currentWidth: StateFlow<Float> = _currentWidth.asStateFlow()

    private val _backgroundType = MutableStateFlow(BackgroundType.PLAIN)
    val backgroundType: StateFlow<BackgroundType> = _backgroundType.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _inputMode = MutableStateFlow(InputMode.SPEN)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _zoomPercent = MutableStateFlow(100)
    val zoomPercent: StateFlow<Int> = _zoomPercent.asStateFlow()

    private val undoHistory = mutableListOf<NoteOperation>()
    private val activeEraseEntries = mutableListOf<RemovedStroke>()
    private var eraseBaseline: List<Stroke> = emptyList()
    private var lastOrientation: Int? = null

    init {
        inputSettingsRepository?.settings?.onEach { settings ->
            _inputMode.value = settings.inputMode
            _currentTool.value = settings.tool
            _colorSlots.value = settings.colorSlots
            _activeColorSlot.value = settings.activeColorSlot
            _currentColor.value = settings.color
            _currentWidthStep.value = settings.widthStep
            _currentWidth.value = NotePage.widthForStep(settings.widthStep)
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

    fun selectColorSlot(index: Int) {
        if (index !in _colorSlots.value.indices) return
        _activeColorSlot.value = index
        _currentColor.value = _colorSlots.value[index]
    }

    fun updateActiveColor(color: Color) {
        val activeSlot = _activeColorSlot.value
        _colorSlots.value = _colorSlots.value.toMutableList().also { it[activeSlot] = color }
        _currentColor.value = color
    }

    fun switchColor(color: Color) {
        updateActiveColor(color)
        saveColorSlots()
    }

    fun restoreColorSlots(colors: List<Color>, activeSlot: Int) {
        if (colors.size != DefaultColorSlots.size || activeSlot !in colors.indices) return
        _colorSlots.value = colors
        _activeColorSlot.value = activeSlot
        _currentColor.value = colors[activeSlot]
    }

    fun saveColorSlots() {
        val colors = _colorSlots.value
        val activeSlot = _activeColorSlot.value
        viewModelScope.launch {
            inputSettingsRepository?.setColorSlots(colors, activeSlot)
        }
    }

    fun switchWidthStep(step: Int) {
        val clampedStep = step.coerceIn(1, 100)
        _currentWidthStep.value = clampedStep
        _currentWidth.value = NotePage.widthForStep(clampedStep)
        viewModelScope.launch { inputSettingsRepository?.setWidthStep(clampedStep) }
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

    fun decreaseZoom() {
        _zoomPercent.value = (_zoomPercent.value - 5).coerceAtLeast(100)
    }

    fun increaseZoom() {
        _zoomPercent.value = (_zoomPercent.value + 5).coerceAtMost(400)
    }

    fun setZoomPercent(percent: Float) {
        _zoomPercent.value = percent.toInt().coerceIn(100, 400)
    }

    fun resetZoom(percent: Int = 100) {
        _zoomPercent.value = percent.coerceIn(100, 400)
    }

    fun resetZoomForOrientation(orientation: Int): Boolean {
        val portraitToLandscape = lastOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        resetZoom(if (portraitToLandscape) 200 else 100)
        lastOrientation = orientation
        return portraitToLandscape
    }

    private fun updateStrokes(strokes: List<Stroke>, operation: NoteOperation) {
        _strokes.value = strokes
        undoHistory += operation
        if (undoHistory.size > 100) undoHistory.removeAt(0)
        _canUndo.value = true
    }
}
