package com.example.stopwatchapp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "trackpace_prefs")

class DataStoreManager(private val context: Context) {
    private val sessionKey = stringPreferencesKey("session_state")

    val sessionState: Flow<SessionState> = context.dataStore.data.map { preferences ->
        val json = preferences[sessionKey]
        if (json != null) {
            Json.decodeFromString<SessionState>(json)
        } else {
            SessionState()
        }
    }

    suspend fun saveSessionState(state: SessionState) {
        context.dataStore.edit { preferences ->
            preferences[sessionKey] = Json.encodeToString(state)
        }
    }
}
