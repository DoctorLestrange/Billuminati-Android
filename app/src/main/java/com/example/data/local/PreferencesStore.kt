package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.billuminatiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "billuminati_prefs"
)

data class AppPreferences(
    val isDarkTheme: Boolean = false,
    val welcomeSeen: Boolean = false
)

class PreferencesStore(private val context: Context) {
    private val keyDarkTheme = booleanPreferencesKey("is_dark_theme")
    private val keyWelcomeSeen = booleanPreferencesKey("welcome_seen")

    val preferencesFlow: Flow<AppPreferences> = context.billuminatiDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppPreferences(
                isDarkTheme = prefs[keyDarkTheme] ?: false,
                welcomeSeen = prefs[keyWelcomeSeen] ?: false
            )
        }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.billuminatiDataStore.edit { prefs ->
            prefs[keyDarkTheme] = isDark
        }
    }

    suspend fun markWelcomeSeen() {
        context.billuminatiDataStore.edit { prefs ->
            prefs[keyWelcomeSeen] = true
        }
    }
}
