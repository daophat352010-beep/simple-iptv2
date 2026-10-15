package com.example.iptv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.iptv.model.EpgData
import com.example.iptv.model.TvChannel

/**
 * Phần tử điều hướng bằng remote: phóng to nhẹ + viền trắng + nền màu nhấn khi focus.
 * onLongPress: giữ OK hoặc bấm Menu.
 */
@Composable
fun TvItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(100), label = "scale")
    val shape = RoundedCornerShape(12.dp)
    val cs = MaterialTheme.colorScheme

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) cs.primary else if (selected) cs.surfaceVariant else Color.Transparent)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .onPreviewKeyEvent { e ->
                if (onLongPress == null) {
                    false
                } else if (e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) onLongPress()
                    true
                } else if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1) {
                        longDone = true
                        onLongPress()
                        true
                    } else if (e.type == KeyEventType.KeyUp && longDone) {
                        longDone = false
                        true // nuốt KeyUp để không kích hoạt onClick sau khi giữ phím
                    } else false
                } else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun ChannelRow(
    ch: TvChannel,
    isFav: Boolean,
    epg: EpgData,
    showEpg: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = if (showEpg) epg.lookup(ch, System.currentTimeMillis()).first else null
    TvItem(modifier.fillMaxWidth(), onClick = onPlay, onLongPress = onToggleFav) {
        ChannelLogo(ch.logo, 64)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (cur != null && cur.title.isNotBlank()) {
                Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFDDDDDD))
            }
        }
        if (isFav) Icon(Icons.Default.Star, contentDescription = "Yêu thích", tint = Color(0xFFFFC107))
    }
}

@Composable
fun ChannelLogo(url: String?, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(4.dp)
        )
    }
}
