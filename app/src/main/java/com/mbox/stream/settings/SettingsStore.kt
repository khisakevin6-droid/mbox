package com.mbox.stream.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** theme: "dark" | "light" | "system". catalogMode: "sample" | "remote" | "tmdb". subLang: ISO code or "off". */
data class Prefs(
    val theme: String = "dark",
    val speed: Float = 1f,
    val subLang: String = "en",
    val catalogMode: String = "sample",
    val catalogUrl: String = "",
    val tmdbKey: String = "",
    val sourceApiTemplate: String = "",
    val magnetEnabled: Boolean = false
)

class SettingsStore(private val context: Context) {
    private object K {
        val THEME = stringPreferencesKey("theme")
        val SPEED = floatPreferencesKey("speed")
        val SUB_LANG = stringPreferencesKey("sub_lang")
        val MODE = stringPreferencesKey("catalog_mode")
        val URL = stringPreferencesKey("catalog_url")
        val TMDB = stringPreferencesKey("tmdb_key")
        val SOURCE_API = stringPreferencesKey("source_api")
        val MAGNET = booleanPreferencesKey("magnet_enabled")
    }

    val prefs: Flow<Prefs> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p: Preferences ->
            Prefs(
                theme = p[K.THEME] ?: "dark",
                speed = p[K.SPEED] ?: 1f,
                subLang = p[K.SUB_LANG] ?: "en",
                catalogMode = p[K.MODE] ?: "sample",
                catalogUrl = p[K.URL] ?: "",
                tmdbKey = p[K.TMDB] ?: "",
                sourceApiTemplate = p[K.SOURCE_API] ?: "",
                magnetEnabled = p[K.MAGNET] ?: false
            )
        }

    suspend fun setTheme(v: String) { context.settingsDataStore.edit { it[K.THEME] = v } }
    suspend fun setSpeed(v: Float) { context.settingsDataStore.edit { it[K.SPEED] = v } }
    suspend fun setSubLang(v: String) { context.settingsDataStore.edit { it[K.SUB_LANG] = v } }
    suspend fun setMagnetEnabled(v: Boolean) { context.settingsDataStore.edit { it[K.MAGNET] = v } }
    suspend fun setCatalogMode(v: String) { context.settingsDataStore.edit { it[K.MODE] = v } }
    suspend fun saveConnections(url: String, tmdbKey: String, sourceApi: String) {
        context.settingsDataStore.edit {
            it[K.URL] = url.trim()
            it[K.TMDB] = tmdbKey.trim()
            it[K.SOURCE_API] = sourceApi.trim()
        }
    }
}
