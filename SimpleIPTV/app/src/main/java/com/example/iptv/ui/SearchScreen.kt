package com.example.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.iptv.ui.components.ChannelRow
import com.example.iptv.viewmodel.MainViewModel

@Composable
fun SearchScreen(vm: MainViewModel, onPlay: () -> Unit) {
    val q by vm.query.collectAsState()
    val results by vm.searchResults.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val epg by vm.epg.collectAsState()
    val settings by vm.settings.collectAsState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.setQuery("")
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 40.dp)) {
        Text("Tìm kênh", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = q,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Nhập tên kênh…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { ch ->
                ChannelRow(
                    ch = ch,
                    isFav = ch.url in favorites,
                    epg = epg,
                    showEpg = settings.showEpg,
                    onPlay = { vm.play(ch, results); onPlay() },
                    onToggleFav = { vm.toggleFavorite(ch) }
                )
            }
        }
    }
}
