package com.example.iptv.util

import com.example.iptv.model.ParsedPlaylist
import com.example.iptv.model.TvChannel
import java.io.BufferedReader

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    /** Đọc theo dòng (streaming) để tiết kiệm RAM với playlist lớn. */
    fun parse(reader: BufferedReader): ParsedPlaylist {
        val channels = ArrayList<TvChannel>()
        var epgUrl: String? = null
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var tvgId: String? = null
        var extGroup: String? = null

        reader.forEachLine { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U") -> {
                    val a = attrs(line)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.split(',')?.firstOrNull()?.trim()?.ifBlank { null }
                }
                line.startsWith("#EXTINF") -> {
                    val a = attrs(line)
                    val lastQuote = line.lastIndexOf('"')
                    val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else null
                    if (name.isNullOrBlank()) name = a["tvg-name"]
                    logo = a["tvg-logo"]?.ifBlank { null }
                    group = a["group-title"]?.ifBlank { null }
                    tvgId = a["tvg-id"]?.ifBlank { null }
                }
                line.startsWith("#EXTGRP:") -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                line.contains("://") -> {
                    channels += TvChannel(
                        name = name?.ifBlank { null } ?: line,
                        url = line,
                        group = group ?: extGroup?.ifBlank { null } ?: "Khác",
                        logo = logo,
                        epgId = tvgId
                    )
                    name = null; logo = null; group = null; tvgId = null; extGroup = null
                }
            }
        }
        return ParsedPlaylist(channels, epgUrl)
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
}
