@file:OptIn(UnstableApi::class)

package com.example.iptv.ui

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.iptv.R
import com.example.iptv.model.EpgData
import com.example.iptv.model.TvChannel
import com.example.iptv.player.Quality
import com.example.iptv.ui.components.ChannelLogo
import com.example.iptv.ui.components.TvItem
import com.example.iptv.util.fmtTime
import com.example.iptv.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val pm = vm.player
    val current by vm.current.collectAsState()
    val state by pm.state.collectAsState()
    val epg by vm.epg.collectAsState()
    val settings by vm.settings.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(10_000) }
    }

    var overlay by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var okLong by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }

    fun showOverlay() { overlay = true; tick++ }

    DisposableEffect(Unit) { onDispose { pm.release() } }

    LaunchedEffect(current?.url) {
        current?.let { pm.play(it.url) }
        showOverlay()
    }
    // Overlay tự ẩn sau vài giây
    LaunchedEffect(overlay, tick) {
        if (overlay) { delay(4000); overlay = false }
    }
    LaunchedEffect(menu) {
        if (menu) runCatching { menuFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }

    BackHandler { if (menu) menu = false else onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (menu) return@onPreviewKeyEvent false
                val down = e.type == KeyEventType.KeyDown
                val up = e.type == KeyEventType.KeyUp
                val first = down && e.nativeKeyEvent.repeatCount == 0
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (down && e.nativeKeyEvent.repeatCount == 1) okLong = true
                        else if (up) {
                            if (okLong) { okLong = false; menu = true } else showOverlay()
                        }
                        true
                    }
                    Key.DirectionUp, Key.ChannelUp -> { if (first) vm.nextChannel(); true }
                    Key.DirectionDown, Key.ChannelDown -> { if (first) vm.prevChannel(); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (up) pm.togglePlay(); true }
                    Key.Menu -> { if (up) menu = true; true }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView },
            update = { it.player = pm.player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        val ch = current
        if (ch != null) {
            AnimatedVisibility(
                visible = overlay && !menu,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                OverlayBar(ch, vm.numberOf(ch), epg, settings.showEpg, now)
            }

            if (menu) {
                val isFav = ch.url in favorites
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(48.dp)
                        .width(440.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF0181818))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TvItem(Modifier.fillMaxWidth().focusRequester(menuFocus), onClick = { vm.toggleFavorite(ch) }) {
                        Icon(Icons.Default.Star, null, tint = if (isFav) Color(0xFFFFC107) else Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text(if (isFav) "Bỏ khỏi yêu thích" else "Thêm vào yêu thích")
                    }
                    Text("Chất lượng", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.multiQuality) {
                        Quality.values().forEach { q ->
                            TvItem(Modifier.fillMaxWidth(), selected = q == state.quality, onClick = { pm.setQuality(q); menu = false }) {
                                Text(q.label, Modifier.weight(1f))
                                if (q == state.quality) Text("✓")
                            }
                        }
                    } else {
                        Text("Luồng này chỉ có một độ phân giải", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TvItem(Modifier.fillMaxWidth(), onClick = { menu = false }) { Text("Đóng") }
                }
            }
        }

        if (state.isBuffering && !menu) {
            CircularProgressIndicator(
                Modifier.align(Alignment.TopEnd).padding(32.dp).size(40.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (state.error && !menu) {
            Text(
                "Mất kết nối – đang thử lại…",
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(20.dp)
            )
        }
    }
}

@Composable
private fun OverlayBar(ch: TvChannel, number: Int, epg: EpgData, showEpg: Boolean, now: Long) {
    val (cur, next) = if (showEpg) epg.lookup(ch, now) else (null to null)
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(horizontal = 56.dp, vertical = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch.logo, 88)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text("$number   ${ch.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cur != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    val total = (cur.stop - cur.start).coerceAtLeast(1L)
                    val p = ((now - cur.start).toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(0.7f).height(6.dp))
                    Text("${fmtTime(cur.start)} – ${fmtTime(cur.stop)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCCCCCC))
                }
                if (next != null && next.title.isNotBlank()) {
                    Text("Tiếp theo: ${next.title}", style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(fmtTime(now), style = MaterialTheme.typography.titleLarge)
        }
    }
}
