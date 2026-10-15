package com.example.iptv.util

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
private val nonAlnum = Regex("[^a-z0-9]")

/** Bỏ dấu tiếng Việt, khoảng trắng, ký tự đặc biệt: "VTV 3 HD" -> "vtv3hd". Dùng cho tìm kiếm và ghép EPG. */
fun String.foldKey(): String =
    Normalizer.normalize(lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
        .replace(diacritics, "")
        .replace(nonAlnum, "")

fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
