package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_ultra_prefs")

class SettingsPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.jarvisDataStore

    val voiceReplyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VOICE_REPLY] ?: true
    }

    val groqApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GROQ_API_KEY] ?: ""
    }

    suspend fun setVoiceReplyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_VOICE_REPLY] = enabled
        }
    }

    suspend fun setGroqApiKey(apiKey: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GROQ_API_KEY] = apiKey.trim()
        }
    }

    private companion object {
        val KEY_VOICE_REPLY = booleanPreferencesKey("voice_reply_enabled")
        val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    }
}
