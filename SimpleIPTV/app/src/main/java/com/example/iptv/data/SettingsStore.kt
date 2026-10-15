package com.example.iptv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.iptv.model.AppSettings
import com.example.iptv.model.PlaylistConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iptv_settings")

private object K {
    val PLAYLISTS = stringPreferencesKey("playlists")
    val FAVS = stringSetPreferencesKey("favorites")
    val AUTOPLAY = booleanPreferencesKey("auto_play")
    val RESUME = booleanPreferencesKey("resume_last")
    val EPG = booleanPreferencesKey("show_epg")
    val AUTOUPDATE = booleanPreferencesKey("auto_update")
    val ACCENT = stringPreferencesKey("accent")
    val LAST = stringPreferencesKey("last_channel")
    val ACTIVE = stringPreferencesKey("active_playlist")
}

private fun Preferences.toSettings() = AppSettings(
    autoPlay = this[K.AUTOPLAY] ?: true,
    resumeLast = this[K.RESUME] ?: true,
    showEpg = this[K.EPG] ?: true,
    autoUpdate = this[K.AUTOUPDATE] ?: true,
    accent = this[K.ACCENT] ?: "blue",
    lastChannelUrl = this[K.LAST] ?: "",
    activePlaylistId = this[K.ACTIVE] ?: ""
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { it.toSettings() }

    val playlists: Flow<List<PlaylistConfig>> = ctx.dataStore.data.map { p ->
        runCatching {
            val a = JSONArray(p[K.PLAYLISTS] ?: "[]")
            (0 until a.length()).map { PlaylistConfig.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    val favorites: Flow<Set<String>> = ctx.dataStore.data.map { it[K.FAVS] ?: emptySet() }

    suspend fun updateSettings(f: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p ->
            val n = f(p.toSettings())
            p[K.AUTOPLAY] = n.autoPlay
            p[K.RESUME] = n.resumeLast
            p[K.EPG] = n.showEpg
            p[K.AUTOUPDATE] = n.autoUpdate
            p[K.ACCENT] = n.accent
            p[K.LAST] = n.lastChannelUrl
            p[K.ACTIVE] = n.activePlaylistId
        }
    }

    suspend fun savePlaylists(list: List<PlaylistConfig>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        ctx.dataStore.edit { it[K.PLAYLISTS] = a.toString() }
    }

    suspend fun toggleFavorite(url: String) {
        ctx.dataStore.edit {
            val cur = it[K.FAVS] ?: emptySet()
            it[K.FAVS] = if (url in cur) cur - url else cur + url
        }
    }

    suspend fun setFavorites(set: Set<String>) {
        ctx.dataStore.edit { it[K.FAVS] = set }
    }
}
