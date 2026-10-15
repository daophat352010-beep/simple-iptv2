package com.example.iptv.network

import com.example.iptv.model.ParsedPlaylist
import com.example.iptv.model.PlaylistConfig
import com.example.iptv.model.TvChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.URLEncoder

object XtreamApi {
    fun load(client: OkHttpClient, cfg: PlaylistConfig): ParsedPlaylist {
        val base = cfg.url.trim().trimEnd('/').removeSuffix("/player_api.php").removeSuffix("/get.php")
        val auth = "username=${URLEncoder.encode(cfg.username, "UTF-8")}&password=${URLEncoder.encode(cfg.password, "UTF-8")}"

        val cats = HashMap<String, String>()
        try {
            val arr = JSONArray(get(client, "$base/player_api.php?$auth&action=get_live_categories"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cats[o.optString("category_id")] = o.optString("category_name")
            }
        } catch (e: JSONException) { /* danh mục là tùy chọn */ }

        val streams = try {
            JSONArray(get(client, "$base/player_api.php?$auth&action=get_live_streams"))
        } catch (e: JSONException) {
            throw IOException("Sai tài khoản/mật khẩu hoặc server không hỗ trợ Xtream Codes")
        }

        val channels = ArrayList<TvChannel>(streams.length())
        for (i in 0 until streams.length()) {
            val o = streams.getJSONObject(i)
            val id = o.optString("stream_id")
            if (id.isBlank()) continue
            channels += TvChannel(
                name = o.optString("name").ifBlank { "Kênh $id" },
                url = "$base/live/${cfg.username}/${cfg.password}/$id.m3u8",
                group = cats[o.optString("category_id")]?.ifBlank { null } ?: "Khác",
                logo = o.optString("stream_icon").ifBlank { null },
                epgId = o.optString("epg_channel_id").ifBlank { null }
            )
        }
        return ParsedPlaylist(channels, "$base/xmltv.php?$auth")
    }

    private fun get(client: OkHttpClient, url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body!!.string()
        }
}
