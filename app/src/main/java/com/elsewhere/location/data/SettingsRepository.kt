package com.elsewhere.location.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elsewhere.location.BuildConfig
import com.elsewhere.location.core.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.elsewhereDataStore by preferencesDataStore(name = "elsewhere_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val key = stringPreferencesKey("settings_json")

    private val defaults = AppSettings(updateRepo = BuildConfig.UPDATE_REPO)

    val settings: Flow<AppSettings> = context.elsewhereDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { preferences -> decode(preferences) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var result = defaults
        context.elsewhereDataStore.edit { preferences ->
            val current = decode(preferences)
            result = transform(current)
            preferences[key] = json.encodeToString(AppSettings.serializer(), result)
        }
        return result
    }

    suspend fun set(settings: AppSettings) {
        context.elsewhereDataStore.edit { preferences ->
            preferences[key] = json.encodeToString(AppSettings.serializer(), settings)
        }
    }

    private fun decode(preferences: Preferences): AppSettings {
        val raw = preferences[key] ?: return defaults
        return runCatching {
            json.decodeFromString(AppSettings.serializer(), raw)
        }.getOrElse { defaults }
    }
}
