package com.example.iptv.util

import android.util.Xml
import com.example.iptv.model.EpgData
import com.example.iptv.model.Programme
import com.example.iptv.model.TvChannel
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmltvParser {
    /**
     * Đọc XMLTV dạng stream, chỉ giữ chương trình của kênh có trong playlist và trong 24h tới
     * -> RAM thấp dù file EPG rất lớn.
     */
    fun parse(input: InputStream, channels: List<TvChannel>, now: Long): EpgData {
        val wantedIds = HashSet<String>()
        val wantedNames = HashSet<String>()
        channels.forEach { c ->
            c.epgId?.let { wantedIds.add(it.lowercase()) }
            wantedNames.add(c.nameKey)
        }

        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val nameToId = HashMap<String, String>()
        val aliasIds = HashSet<String>()
        val progs = HashMap<String, MutableList<Programme>>()
        val limit = now + 24L * 3600 * 1000

        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun time(s: String?): Long {
            if (s.isNullOrBlank()) return 0L
            var t = s.trim()
            if (t.length > 14 && t[14] != ' ') t = t.substring(0, 14) + " " + t.substring(14)
            return try {
                if (t.length > 14) fmt.parse(t)!!.time else fmtNoTz.parse(t.take(14))!!.time
            } catch (e: Exception) { 0L }
        }

        var curId: String? = null
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> curId = parser.getAttributeValue(null, "id")?.lowercase()
                    "display-name" -> {
                        val id = curId
                        val n = parser.nextText().foldKey()
                        if (id != null && n in wantedNames && !nameToId.containsKey(n)) {
                            nameToId[n] = id
                            aliasIds.add(id)
                        }
                    }
                    "programme" -> {
                        val ch = parser.getAttributeValue(null, "channel")?.lowercase()
                        val start = time(parser.getAttributeValue(null, "start"))
                        val stop = time(parser.getAttributeValue(null, "stop"))
                        var title = ""
                        while (true) {
                            val e = parser.next()
                            if (e == XmlPullParser.END_DOCUMENT) break
                            if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                            if (e == XmlPullParser.START_TAG && parser.name == "title" && title.isEmpty()) {
                                title = parser.nextText()
                            }
                        }
                        if (ch != null && stop > now && start < limit && (ch in wantedIds || ch in aliasIds)) {
                            progs.getOrPut(ch) { ArrayList() }.add(Programme(start, stop, title.trim()))
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && parser.name == "channel") {
                curId = null
            }
            ev = parser.next()
        }
        progs.values.forEach { l -> l.sortBy { it.start } }
        return EpgData(progs, nameToId)
    }
}
