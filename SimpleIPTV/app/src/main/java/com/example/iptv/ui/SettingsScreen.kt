package com.example.iptv.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iptv.model.PlaylistConfig
import com.example.iptv.model.SourceType
import com.example.iptv.ui.components.TvItem
import com.example.iptv.viewmodel.MainViewModel
import java.util.UUID

private fun onOff(b: Boolean) = if (b) "Bật" else "Tắt"

@Composable
private fun SettingRow(title: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvItem(modifier.fillMaxWidth(), onClick = onClick) {
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var section by remember { mutableStateOf("main") }
    val message by vm.message.collectAsState()

    BackHandler {
        when (section) {
            "main" -> onBack()
            "add" -> section = "playlists"
            else -> section = "main"
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 40.dp)) {
        Text(
            when (section) {
                "playlists", "add" -> "Playlist"
                "about" -> "Thông tin ứng dụng"
                else -> "Cài đặt"
            },
            style = MaterialTheme.typography.headlineMedium
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        when (section) {
            "main" -> MainSection(vm) { section = it }
            "playlists" -> PlaylistSection(vm) { section = it }
            "add" -> AddSection(vm) { section = "playlists" }
            else -> AboutSection()
        }
    }
}

@Composable
private fun MainSection(vm: MainViewModel, go: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SettingRow("Playlist", null, { go("playlists") }, Modifier.focusRequester(first)) }
        item {
            SettingRow("Giao diện (màu nhấn)", if (s.accent == "red") "Đỏ" else "Xanh dương",
                { vm.setAccent(if (s.accent == "red") "blue" else "red") })
        }
        item { SettingRow("Tự động phát khi mở ứng dụng", onOff(s.autoPlay), { vm.setAutoPlay(!s.autoPlay) }) }
        item { SettingRow("Tự động mở kênh cuối", onOff(s.resumeLast), { vm.setResumeLast(!s.resumeLast) }) }
        item { SettingRow("Hiển thị EPG", onOff(s.showEpg), { vm.setShowEpg(!s.showEpg) }) }
        item { SettingRow("Tự cập nhật playlist khi mở", onOff(s.autoUpdate), { vm.setAutoUpdate(!s.autoUpdate) }) }
        item { SettingRow("Xóa cache", null, { vm.clearCache() }) }
        item { SettingRow("Xuất cấu hình", null, { vm.exportConfig() }) }
        item { SettingRow("Nhập cấu hình", null, { vm.importConfig() }) }
        item { SettingRow("Thông tin ứng dụng", null, { go("about") }) }
    }
}

@Composable
private fun PlaylistSection(vm: MainViewModel, go: (String) -> Unit) {
    val playlists by vm.playlists.collectAsState()
    val s by vm.settings.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(playlists) { i, p ->
            val active = p.id == s.activePlaylistId || (s.activePlaylistId.isBlank() && i == 0)
            Column(Modifier.padding(vertical = 6.dp)) {
                SettingRow(
                    (if (active) "● " else "○ ") + p.name,
                    if (p.type == SourceType.XTREAM) "Xtream" else "M3U",
                    { vm.activate(p) },
                    if (i == 0) Modifier.focusRequester(first) else Modifier
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp)) {
                    TvItem(onClick = { vm.movePlaylist(p, -1) }) { Text("Lên", style = MaterialTheme.typography.bodyMedium) }
                    TvItem(onClick = { vm.movePlaylist(p, 1) }) { Text("Xuống", style = MaterialTheme.typography.bodyMedium) }
                    TvItem(onClick = { vm.activate(p, force = true) }) { Text("Làm mới", style = MaterialTheme.typography.bodyMedium) }
                    TvItem(onClick = { vm.removePlaylist(p) }) { Text("Xóa", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item {
            SettingRow("+ Thêm playlist", null, { go("add") },
                if (playlists.isEmpty()) Modifier.focusRequester(first) else Modifier)
        }
        item { SettingRow("Làm mới playlist hiện tại", null, { vm.refresh() }) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
}

@Composable
private fun AddSection(vm: MainViewModel, done: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SourceType.M3U) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SettingRow(
                "Loại nguồn",
                if (type == SourceType.M3U) "M3U / M3U8 URL" else "Xtream Codes",
                { type = if (type == SourceType.M3U) SourceType.XTREAM else SourceType.M3U },
                Modifier.focusRequester(first)
            )
        }
        item { Field("Tên playlist", name) { name = it } }
        item {
            Field(if (type == SourceType.M3U) "URL (M3U / M3U8)" else "URL server (http://host:port)", url) { url = it }
        }
        if (type == SourceType.XTREAM) {
            item { Field("Tài khoản", user) { user = it } }
            item { Field("Mật khẩu", pass) { pass = it } }
        }
        error?.let { e -> item { Text(e, color = Color(0xFFFF8A80)) } }
        item {
            SettingRow("Lưu playlist", null, {
                val u = url.trim()
                if (u.isBlank()) {
                    error = "Vui lòng nhập URL"
                } else if (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank())) {
                    error = "Cần nhập tài khoản và mật khẩu"
                } else {
                    vm.addPlaylist(
                        PlaylistConfig(
                            id = UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { "Playlist" },
                            type = type, url = u, username = user.trim(), password = pass.trim()
                        )
                    )
                    done()
                }
            })
        }
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("IPTV TV • phiên bản 1.0.0")
        Text("Trình phát: Media3 ExoPlayer (HLS, DASH, MPEG-TS, MP4)", style = MaterialTheme.typography.bodyMedium)
        Text("Nguồn: M3U / M3U8 / Xtream Codes • EPG: XMLTV", style = MaterialTheme.typography.bodyMedium)
        Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.bodyMedium)
        Text("Không quảng cáo • Không cần đăng nhập", style = MaterialTheme.typography.bodyMedium)
    }
}
