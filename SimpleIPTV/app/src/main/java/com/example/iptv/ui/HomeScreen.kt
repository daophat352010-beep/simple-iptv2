package com.example.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iptv.ui.components.ChannelRow
import com.example.iptv.ui.components.TvItem
import com.example.iptv.viewmodel.GROUP_ALL
import com.example.iptv.viewmodel.GROUP_FAV
import com.example.iptv.viewmodel.MainViewModel

private fun groupLabel(g: String) = when (g) {
    GROUP_ALL -> "Tất cả kênh"
    GROUP_FAV -> "★ Favorites"
    else -> g
}

@Composable
fun HomeScreen(vm: MainViewModel, onPlay: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val groups by vm.groups.collectAsState()
    val selected by vm.selectedGroup.collectAsState()
    val channels by vm.visibleChannels.collectAsState()
    val all by vm.channels.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val epg by vm.epg.collectAsState()
    val settings by vm.settings.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()

    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val groupItems = remember(groups) { listOf(GROUP_ALL, GROUP_FAV) + groups }

    LaunchedEffect(selected) { listState.scrollToItem(0) }
    LaunchedEffect(Unit) { runCatching { selFocus.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        // ---------- Cột trái: nhóm kênh ----------
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text("IPTV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    TvItem(Modifier.fillMaxWidth(), onClick = onSearch) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(12.dp)); Text("Tìm kiếm")
                    }
                }
                item {
                    TvItem(Modifier.fillMaxWidth(), onClick = onSettings) {
                        Icon(Icons.Default.Settings, null); Spacer(Modifier.width(12.dp)); Text("Cài đặt")
                    }
                }
                item {
                    TvItem(Modifier.fillMaxWidth(), onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(12.dp)); Text("Làm mới")
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
                items(groupItems) { g ->
                    TvItem(
                        Modifier.fillMaxWidth().then(if (g == selected) Modifier.focusRequester(selFocus) else Modifier),
                        selected = g == selected,
                        onFocused = { vm.selectGroup(g) },
                        onClick = { focusManager.moveFocus(FocusDirection.Right) }
                    ) {
                        Text(groupLabel(g), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.width(32.dp))

        // ---------- Cột phải: danh sách kênh ----------
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${groupLabel(selected)} • ${channels.size}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (loading) Text("Đang cập nhật…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80)) }
            Spacer(Modifier.height(8.dp))

            if (channels.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            loading -> "Đang tải danh sách kênh…"
                            selected == GROUP_FAV && all.isNotEmpty() ->
                                "Chưa có kênh yêu thích.\nGiữ phím OK (hoặc bấm Menu) trên một kênh để thêm."
                            all.isEmpty() -> "Chưa có kênh nào.\nVào Cài đặt → Playlist để thêm danh sách."
                            else -> "Không có kênh trong nhóm này."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(channels) { ch ->
                        ChannelRow(
                            ch = ch,
                            isFav = ch.url in favorites,
                            epg = epg,
                            showEpg = settings.showEpg,
                            onPlay = { vm.play(ch, channels); onPlay() },
                            onToggleFav = { vm.toggleFavorite(ch) }
                        )
                    }
                }
            }
            Text(
                "OK: Xem   •   Giữ OK / Menu: Yêu thích",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
