@file:OptIn(UnstableApi::class)

package com.example.iptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class Quality(val label: String, val width: Int, val height: Int) {
    AUTO("Tự động", 0, 0),
    Q1080("1080p", 1920, 1080),
    Q720("720p", 1280, 720),
    Q480("480p", 854, 480)
}

data class PlayerUiState(
    val isBuffering: Boolean = true,
    val isPlaying: Boolean = false,
    val error: Boolean = false,
    val multiQuality: Boolean = false,
    val quality: Quality = Quality.AUTO
)

/**
 * Giữ một ExoPlayer duy nhất, chỉ tạo khi vào màn hình xem và giải phóng khi thoát (tiết kiệm RAM).
 * Hỗ trợ HLS, DASH, MPEG-TS, MP4; tự kết nối lại khi lỗi mạng.
 */
@Singleton
class PlayerManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    private var _player: ExoPlayer? = null
    val player: ExoPlayer get() = _player ?: create().also { _player = it }

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var currentUrl: String? = null
    private var retry = 0
    private var forceHls = false
    private var quality = Quality.AUTO

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    retry = 0
                    _state.update { it.copy(isBuffering = false, error = false) }
                }
                Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            scheduleRetry(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            val heights = HashSet<Int>()
            tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { g ->
                for (i in 0 until g.length) {
                    val h = g.getTrackFormat(i).height
                    if (h > 0) heights.add(h)
                }
            }
            _state.update { it.copy(multiQuality = heights.size > 1) }
        }
    }

    private fun create(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android TV) SimpleIPTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), DefaultExtractorsFactory())

        // Buffer nhỏ vừa đủ: vào kênh nhanh, ít tốn RAM trên box cấu hình thấp
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .setTargetBufferBytes(16 * 1024 * 1024)
            .build()

        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)

        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .also { p ->
                p.addListener(listener)
                p.playWhenReady = true
                applyQuality(p)
            }
    }

    fun play(url: String) {
        currentUrl = url
        retry = 0
        forceHls = false
        handler.removeCallbacksAndMessages(null)
        _state.update { it.copy(isBuffering = true, error = false) }
        prepareCurrent()
    }

    private fun prepareCurrent() {
        val url = currentUrl ?: return
        val p = player
        val item = MediaItem.Builder().setUri(url).apply {
            if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
    }

    private fun scheduleRetry(e: PlaybackException) {
        retry++
        if (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        ) forceHls = true
        _state.update { it.copy(error = true, isBuffering = true) }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ prepareCurrent() }, (retry * 1000L).coerceAtMost(8000L))
    }

    fun togglePlay() {
        _player?.let { if (it.playWhenReady) it.pause() else it.play() }
    }

    fun setQuality(q: Quality) {
        quality = q
        _state.update { it.copy(quality = q) }
        _player?.let { applyQuality(it) }
    }

    private fun applyQuality(p: ExoPlayer) {
        val b = p.trackSelectionParameters.buildUpon()
        if (quality == Quality.AUTO) b.clearVideoSizeConstraints() else b.setMaxVideoSize(quality.width, quality.height)
        p.trackSelectionParameters = b.build()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        _player?.release()
        _player = null
        currentUrl = null
        _state.value = PlayerUiState(quality = quality)
    }
}
