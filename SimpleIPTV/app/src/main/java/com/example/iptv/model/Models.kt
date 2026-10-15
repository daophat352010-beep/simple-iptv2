package com.example.iptv.model

import com.example.iptv.util.foldKey
import org.json.JSONObject

data class TvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val epgId: String?
) {
    val nameKey: String = name.foldKey()
}

data class ParsedPlaylist(val channels: List<TvChannel>, val epgUrl: String?)

data class Programme(val start: Long, val stop: Long, val title: String)

enum class SourceType { M3U, XTREAM }

data class PlaylistConfig(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String = "",
    val password: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type.name)
        .put("url", url).put("username", username).put("password", password)

    companion object {
        fun fromJson(o: JSONObject) = PlaylistConfig(
            id = o.getString("id"),
            name = o.getString("name"),
            type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.M3U),
            url = o.getString("url"),
            username = o.optString("username"),
            password = o.optString("password")
        )
    }
}

data class AppSettings(
    val autoPlay: Boolean = true,
    val resumeLast: Boolean = true,
    val showEpg: Boolean = true,
    val autoUpdate: Boolean = true,
    val accent: String = "blue",
    val lastChannelUrl: String = "",
    val activePlaylistId: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("autoPlay", autoPlay).put("resumeLast", resumeLast).put("showEpg", showEpg)
        .put("autoUpdate", autoUpdate).put("accent", accent)
        .put("lastChannelUrl", lastChannelUrl).put("activePlaylistId", activePlaylistId)

    companion object {
        fun fromJson(o: JSONObject) = AppSettings(
            autoPlay = o.optBoolean("autoPlay", true),
            resumeLast = o.optBoolean("resumeLast", true),
            showEpg = o.optBoolean("showEpg", true),
            autoUpdate = o.optBoolean("autoUpdate", true),
            accent = o.optString("accent", "blue"),
            lastChannelUrl = o.optString("lastChannelUrl"),
            activePlaylistId = o.optString("activePlaylistId")
        )
    }
}

/** Dữ liệu EPG đã lọc (chỉ các kênh có trong playlist, 24h tới). */
class EpgData(
    private val programmes: Map<String, List<Programme>>,
    private val nameToId: Map<String, String>
) {
    fun lookup(ch: TvChannel, now: Long): Pair<Programme?, Programme?> {
        if (programmes.isEmpty()) return null to null
        val id = ch.epgId?.lowercase()?.takeIf { programmes.containsKey(it) } ?: nameToId[ch.nameKey]
        val list = id?.let { programmes[it] } ?: return null to null
        val cur = list.firstOrNull { now >= it.start && now < it.stop }
        val next = list.firstOrNull { it.start >= (cur?.stop ?: now) }
        return cur to next
    }

    companion object {
        val EMPTY = EpgData(emptyMap(), emptyMap())
    }
}
