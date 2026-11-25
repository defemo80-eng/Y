package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chatdep_prefs")

data class UserPreferences(
    val activeUserId: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoSpeakResponses: Boolean = false,
    val speechRate: Float = 1.0f
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak_responses")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == modeName } ?: ThemeMode.DARK
        UserPreferences(
            activeUserId = prefs[Keys.ACTIVE_USER_ID],
            themeMode = themeMode,
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: false,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f
        )
    }

    suspend fun setActiveUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) {
                prefs.remove(Keys.ACTIVE_USER_ID)
            } else {
                prefs[Keys.ACTIVE_USER_ID] = userId
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAutoSpeakResponses(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SPEAK] = enabled
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f)
        }
    }
}
