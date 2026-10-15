package com.example.iptv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.example.iptv.data.PlaylistRepository
import com.example.iptv.data.SettingsStore
import com.example.iptv.model.AppSettings
import com.example.iptv.model.EpgData
import com.example.iptv.model.PlaylistConfig
import com.example.iptv.model.ParsedPlaylist
import com.example.iptv.model.TvChannel
import com.example.iptv.player.PlayerManager
import com.example.iptv.util.foldKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

const val GROUP_ALL = "__all__"
const val GROUP_FAV = "__fav__"

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: PlaylistRepository,
    private val store: SettingsStore,
    val player: PlayerManager
) : ViewModel() {

    // ---- Dữ liệu từ DataStore ----
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playlists: StateFlow<List<PlaylistConfig>> = store.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites: StateFlow<Set<String>> = store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---- Kênh / nhóm ----
    private val _channels = MutableStateFlow<List<TvChannel>>(emptyList())
    val channels: StateFlow<List<TvChannel>> = _channels.asStateFlow()

    val groups: StateFlow<List<String>> = _channels
        .map { list -> list.map { it.group }.distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedGroup = MutableStateFlow(GROUP_ALL)

    val visibleChannels: StateFlow<List<TvChannel>> =
        combine(_channels, selectedGroup, favorites) { all, g, fav ->
            when (g) {
                GROUP_ALL -> all
                GROUP_FAV -> all.filter { it.url in fav }
                else -> all.filter { it.group == g }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Tìm kiếm ----
    val query = MutableStateFlow("")
    val searchResults: StateFlow<List<TvChannel>> = combine(_channels, query) { all, q ->
        val k = q.foldKey()
        if (k.isEmpty()) emptyList() else all.filter { it.nameKey.contains(k) }.take(300)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Trạng thái chung ----
    private val _epg = MutableStateFlow(EpgData.EMPTY)
    val epg: StateFlow<EpgData> = _epg.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _current = MutableStateFlow<TvChannel?>(null)
    val current: StateFlow<TvChannel?> = _current.asStateFlow()

    private var playingList: List<TvChannel> = emptyList()
    private var epgUrl: String? = null
    private var loadJob: Job? = null
    private var epgJob: Job? = null
    private var started = false
    private val resume = Channel<Unit>(Channel.CONFLATED)
    val resumeEvents = resume.receiveAsFlow()

    init {
        viewModelScope.launch {
            val active = activeConfig()
            if (active != null) load(active, force = false, firstLaunch = true)
        }
    }

    // ---- Tải playlist ----
    private suspend fun activeConfig(): PlaylistConfig? {
        val pls = store.playlists.first()
        val id = store.settings.first().activePlaylistId
        return pls.firstOrNull { it.id == id } ?: pls.firstOrNull()
    }

    private fun load(cfg: PlaylistConfig, force: Boolean, firstLaunch: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            val s = store.settings.first()
            val cached = if (force) null else withContext(Dispatchers.IO) { repo.readCache(cfg) }
            if (cached != null) {
                applyPlaylist(cached)
                if (firstLaunch) maybeAutoPlay()
            }
            if (cached == null || force || s.autoUpdate) {
                try {
                    val fresh = withContext(Dispatchers.IO) { repo.download(cfg) }
                    applyPlaylist(fresh)
                    if (firstLaunch) maybeAutoPlay()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast("Không tải được playlist: ${e.message ?: "lỗi mạng"}")
                }
            }
            _loading.value = false
            reloadEpg()
        }
    }

    private fun applyPlaylist(p: ParsedPlaylist) {
        _channels.value = p.channels
        epgUrl = p.epgUrl
        // nếu nhóm đang chọn không còn tồn tại thì quay về "Tất cả"
        val g = selectedGroup.value
        if (g != GROUP_ALL && g != GROUP_FAV && p.channels.none { it.group == g }) selectedGroup.value = GROUP_ALL
    }

    private suspend fun maybeAutoPlay() {
        if (started) return
        val list = _channels.value
        if (list.isEmpty()) return
        started = true
        val s = store.settings.first()
        if (!s.autoPlay) return
        val ch = (if (s.resumeLast) list.firstOrNull { it.url == s.lastChannelUrl } else list.first()) ?: return
        playingList = list
        _current.value = ch
        selectedGroup.value = ch.group
        resume.trySend(Unit)
    }

    private fun reloadEpg() {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            val url = epgUrl
            if (!store.settings.first().showEpg || url.isNullOrBlank()) {
                _epg.value = EpgData.EMPTY
                return@launch
            }
            try {
                _epg.value = repo.loadEpg(url, _channels.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // EPG là tùy chọn: lỗi thì bỏ qua, ứng dụng vẫn hoạt động bình thường
            }
        }
    }

    private fun toast(msg: String) {
        _message.value = msg
        viewModelScope.launch {
            delay(6000)
            if (_message.value == msg) _message.value = null
        }
    }

    // ---- Thao tác giao diện ----
    fun selectGroup(g: String) { selectedGroup.value = g }
    fun setQuery(q: String) { query.value = q }

    fun play(ch: TvChannel, list: List<TvChannel>) {
        playingList = list
        setCurrent(ch)
    }

    fun nextChannel() = step(1)
    fun prevChannel() = step(-1)

    private fun step(d: Int) {
        val l = playingList
        if (l.isEmpty()) return
        val i = l.indexOfFirst { it.url == _current.value?.url }.let { if (it < 0) 0 else it }
        setCurrent(l[(i + d + l.size) % l.size])
    }

    private fun setCurrent(ch: TvChannel) {
        _current.value = ch
        viewModelScope.launch { store.updateSettings { it.copy(lastChannelUrl = ch.url) } }
    }

    fun numberOf(ch: TvChannel): Int = playingList.indexOfFirst { it.url == ch.url } + 1

    fun toggleFavorite(ch: TvChannel) {
        viewModelScope.launch { store.toggleFavorite(ch.url) }
    }

    // ---- Cài đặt ----
    fun setAutoPlay(v: Boolean) = update { it.copy(autoPlay = v) }
    fun setResumeLast(v: Boolean) = update { it.copy(resumeLast = v) }
    fun setAutoUpdate(v: Boolean) = update { it.copy(autoUpdate = v) }
    fun setAccent(v: String) = update { it.copy(accent = v) }
    fun setShowEpg(v: Boolean) {
        viewModelScope.launch {
            store.updateSettings { it.copy(showEpg = v) }
            reloadEpg()
        }
    }

    private fun update(f: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.updateSettings(f) }
    }

    // ---- Quản lý playlist ----
    fun addPlaylist(cfg: PlaylistConfig) {
        viewModelScope.launch {
            store.savePlaylists(store.playlists.first() + cfg)
            activate(cfg)
        }
    }

    fun activate(cfg: PlaylistConfig, force: Boolean = false) {
        viewModelScope.launch {
            store.updateSettings { it.copy(activePlaylistId = cfg.id) }
            if (!force) {
                _channels.value = emptyList()
                _epg.value = EpgData.EMPTY
                selectedGroup.value = GROUP_ALL
            }
            load(cfg, force)
        }
    }

    fun removePlaylist(cfg: PlaylistConfig) {
        viewModelScope.launch {
            val rest = store.playlists.first().filter { it.id != cfg.id }
            store.savePlaylists(rest)
            repo.deleteCache(cfg)
            if (store.settings.first().activePlaylistId == cfg.id) {
                val next = rest.firstOrNull()
                if (next != null) activate(next) else {
                    _channels.value = emptyList()
                    _epg.value = EpgData.EMPTY
                    store.updateSettings { it.copy(activePlaylistId = "") }
                }
            }
        }
    }

    fun movePlaylist(cfg: PlaylistConfig, delta: Int) {
        viewModelScope.launch {
            val list = store.playlists.first().toMutableList()
            val i = list.indexOfFirst { it.id == cfg.id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return@launch
            list[i] = list[j].also { list[j] = list[i] }
            store.savePlaylists(list)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val a = activeConfig()
            if (a == null) toast("Chưa có playlist. Vào Cài đặt → Playlist để thêm.") else load(a, force = true)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.clearCache()
                ctx.imageLoader.memoryCache?.clear()
                ctx.imageLoader.diskCache?.clear()
            }
            toast("Đã xóa cache")
        }
    }

    // ---- Xuất / nhập cấu hình ----
    private fun configFile() = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "iptv_config.json")

    fun exportConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val o = JSONObject()
                o.put("playlists", JSONArray().also { a -> store.playlists.first().forEach { a.put(it.toJson()) } })
                o.put("favorites", JSONArray(store.favorites.first().toList()))
                o.put("settings", store.settings.first().toJson())
                val f = configFile()
                f.writeText(o.toString(2))
                toast("Đã xuất cấu hình: ${f.absolutePath}")
            } catch (e: Exception) {
                toast("Xuất cấu hình thất bại: ${e.message}")
            }
        }
    }

    fun importConfig() {
        viewModelScope.launch {
            try {
                val f = configFile()
                if (!f.exists()) { toast("Không thấy tệp: ${f.absolutePath}"); return@launch }
                val o = JSONObject(withContext(Dispatchers.IO) { f.readText() })
                val pa = o.optJSONArray("playlists") ?: JSONArray()
                store.savePlaylists((0 until pa.length()).map { PlaylistConfig.fromJson(pa.getJSONObject(it)) })
                val fa = o.optJSONArray("favorites") ?: JSONArray()
                store.setFavorites((0 until fa.length()).map { fa.getString(it) }.toSet())
                o.optJSONObject("settings")?.let { s -> store.updateSettings { AppSettings.fromJson(s) } }
                toast("Đã nhập cấu hình")
                activeConfig()?.let { load(it, force = false) }
            } catch (e: Exception) {
                toast("Nhập cấu hình thất bại: ${e.message}")
            }
        }
    }
}
