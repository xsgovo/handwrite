package com.note.handwrite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.InputMode
import com.note.handwrite.model.NoteSettings
import com.note.handwrite.model.Tool
import com.note.handwrite.ui.theme.PenBlack
import com.note.handwrite.ui.theme.PenGreen
import com.note.handwrite.ui.theme.PenRed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inputSettingsDataStore by preferencesDataStore(name = "input_settings")

class InputSettingsRepository(private val context: Context) {
    private val useSpenModeKey = booleanPreferencesKey("useSpenMode")
    private val toolKey = stringPreferencesKey("tool")
    private val colorKey = stringPreferencesKey("color")
    private val widthStepKey = stringPreferencesKey("width_step")
    private val backgroundKey = stringPreferencesKey("background")

    val settings: Flow<NoteSettings> = context.inputSettingsDataStore.data.map { preferences ->
        NoteSettings(
            inputMode = if (preferences[useSpenModeKey] ?: true) InputMode.SPEN else InputMode.FINGER,
            tool = preferences[toolKey].toToolOrDefault(),
            color = preferences[colorKey].toColorOrDefault(),
            widthStep = preferences[widthStepKey]?.toIntOrNull()?.coerceIn(1, 100) ?: 50,
            background = preferences[backgroundKey].toBackgroundOrDefault()
        )
    }

    suspend fun setInputMode(mode: InputMode) {
        context.inputSettingsDataStore.edit { preferences ->
            preferences[useSpenModeKey] = mode == InputMode.SPEN
        }
    }

    suspend fun setTool(tool: Tool) {
        context.inputSettingsDataStore.edit { it[toolKey] = tool.name }
    }

    suspend fun setColor(color: androidx.compose.ui.graphics.Color) {
        context.inputSettingsDataStore.edit { it[colorKey] = color.toColorKey() }
    }

    suspend fun setWidthStep(step: Int) {
        context.inputSettingsDataStore.edit { it[widthStepKey] = step.coerceIn(1, 100).toString() }
    }

    suspend fun setBackground(background: BackgroundType) {
        context.inputSettingsDataStore.edit { it[backgroundKey] = background.name }
    }
}

private fun String?.toToolOrDefault(): Tool =
    runCatching { Tool.valueOf(this ?: "") }.getOrDefault(Tool.PEN)

private fun String?.toBackgroundOrDefault(): BackgroundType =
    runCatching { BackgroundType.valueOf(this ?: "") }.getOrDefault(BackgroundType.PLAIN)

private fun String?.toColorOrDefault(): androidx.compose.ui.graphics.Color = when (this) {
    "red" -> PenRed
    "green" -> PenGreen
    else -> PenBlack
}

private fun androidx.compose.ui.graphics.Color.toColorKey(): String = when (this) {
    PenRed -> "red"
    PenGreen -> "green"
    else -> "black"
}
