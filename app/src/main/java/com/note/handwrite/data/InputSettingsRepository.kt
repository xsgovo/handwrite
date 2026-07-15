package com.note.handwrite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.note.handwrite.model.InputMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inputSettingsDataStore by preferencesDataStore(name = "input_settings")

class InputSettingsRepository(private val context: Context) {
    private val useSpenModeKey = booleanPreferencesKey("useSpenMode")

    val inputMode: Flow<InputMode> = context.inputSettingsDataStore.data.map { preferences ->
        if (preferences[useSpenModeKey] ?: true) InputMode.SPEN else InputMode.FINGER
    }

    suspend fun setInputMode(mode: InputMode) {
        context.inputSettingsDataStore.edit { preferences ->
            preferences[useSpenModeKey] = mode == InputMode.SPEN
        }
    }
}
