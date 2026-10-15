package com.example.iptv.data

import android.content.Context
import com.example.iptv.model.EpgData
import com.example.iptv.model.ParsedPlaylist
import com.example.iptv.model.PlaylistConfig
import com.example.iptv.model.SourceType
import com.example.iptv.model.TvChannel
import com.example.iptv.network.XtreamApi
import com.example.iptv.util.M3uParser
import com.example.iptv.util.XmltvParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient
) {
    private fun cacheFile(cfg: PlaylistConfig) = File(ctx.cacheDir, "playlist_${cfg.id}.json")

    /** Đọc cache để mở app gần như tức thì. */
    fun readCache(cfg: PlaylistConfig): ParsedPlaylist? = runCatching {
        val f = cacheFile(cfg)
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("ch")
        val list = ArrayList<TvChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            list += TvChannel(a.getString(0), a.getString(1), a.getString(2),
                a.getString(3).ifBlank { null }, a.getString(4).ifBlank { null })
        }
        ParsedPlaylist(list, o.optString("epg").ifBlank { null })
    }.getOrNull()

    private fun writeCache(cfg: PlaylistConfig, p: ParsedPlaylist) {
        runCatching {
            val arr = JSONArray()
            p.channels.forEach {
                arr.put(JSONArray().put(it.name).put(it.url).put(it.group).put(it.logo ?: "").put(it.epgId ?: ""))
            }
            cacheFile(cfg).writeText(JSONObject().put("epg", p.epgUrl ?: "").put("ch", arr).toString())
        }
    }

    suspend fun download(cfg: PlaylistConfig): ParsedPlaylist = withContext(Dispatchers.IO) {
        val parsed = when (cfg.type) {
            SourceType.M3U -> client.newCall(Request.Builder().url(cfg.url.trim()).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                M3uParser.parse(resp.body!!.charStream().buffered())
            }
            SourceType.XTREAM -> XtreamApi.load(client, cfg)
        }
        if (parsed.channels.isEmpty()) throw IOException("Playlist không có kênh nào")
        writeCache(cfg, parsed)
        parsed
    }

    suspend fun loadEpg(url: String, channels: List<TvChannel>): EpgData = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val raw = BufferedInputStream(resp.body!!.byteStream(), 64 * 1024)
            raw.mark(2)
            val b1 = raw.read()
            val b2 = raw.read()
            raw.reset()
            val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
            XmltvParser.parse(input, channels, System.currentTimeMillis())
        }
    }

    fun deleteCache(cfg: PlaylistConfig) { cacheFile(cfg).delete() }

    fun clearCache() {
        ctx.cacheDir.listFiles()?.filter { it.name.startsWith("playlist_") }?.forEach { it.delete() }
    }
}
