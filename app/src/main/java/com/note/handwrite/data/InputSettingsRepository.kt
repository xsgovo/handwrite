package com.note.handwrite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.toArgb
import com.note.handwrite.model.BackgroundType
import com.note.handwrite.model.DefaultColorSlots
import com.note.handwrite.model.InputMode
import com.note.handwrite.model.NoteSettings
import com.note.handwrite.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inputSettingsDataStore by preferencesDataStore(name = "input_settings")

class InputSettingsRepository(private val context: Context) {
    private val useSpenModeKey = booleanPreferencesKey("useSpenMode")
    private val toolKey = stringPreferencesKey("tool")
    private val colorSlotKeys = List(DefaultColorSlots.size) { index ->
        stringPreferencesKey("color_slot_$index")
    }
    private val activeColorSlotKey = intPreferencesKey("active_color_slot")
    private val widthStepKey = stringPreferencesKey("width_step")
    private val backgroundKey = stringPreferencesKey("background")

    val settings: Flow<NoteSettings> = context.inputSettingsDataStore.data.map { preferences ->
        NoteSettings(
            inputMode = if (preferences[useSpenModeKey] ?: true) InputMode.SPEN else InputMode.FINGER,
            tool = preferences[toolKey].toToolOrDefault(),
            colorSlots = colorSlotKeys.mapIndexed { index, key ->
                preferences[key].toColorOrDefault(DefaultColorSlots[index])
            },
            activeColorSlot = preferences[activeColorSlotKey]
                ?.takeIf { it in DefaultColorSlots.indices }
                ?: 0,
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

    suspend fun setColorSlots(colors: List<androidx.compose.ui.graphics.Color>, activeSlot: Int) {
        context.inputSettingsDataStore.edit { preferences ->
            colorSlotKeys.forEachIndexed { index, key ->
                preferences[key] = colors.getOrElse(index) { DefaultColorSlots[index] }.toColorKey()
            }
            preferences[activeColorSlotKey] = activeSlot.coerceIn(DefaultColorSlots.indices)
        }
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

private fun String?.toColorOrDefault(default: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
    val value = this ?: return default
    if (!COLOR_PATTERN.matches(value)) return default
    return androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value))
}

private fun androidx.compose.ui.graphics.Color.toColorKey(): String =
    "#%06X".format(toArgb() and 0xFFFFFF)

private val COLOR_PATTERN = Regex("#[0-9A-Fa-f]{6}")
