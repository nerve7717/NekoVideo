package com.neko.video.player.engine

import android.content.Context
import android.view.View
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.neko.video.model.AspectRatioMode
import com.neko.video.model.AudioTrackInfo
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SubtitleCandidate
import com.neko.video.model.SubtitleTrackInfo
import com.neko.video.player.cache.MediaCache
import com.neko.video.remote.RemoteFileDataSource
import com.neko.video.remote.RemoteServerConfig
import com.neko.video.remote.RoutingDataSourceFactory
import com.neko.video.util.UserAgentUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class Media3PlayerEngine(
    context: Context,
    val handleAudioFocus: Boolean = true,
    val timeoutSeconds: Int = 20,
    private val remoteServerResolver: ((String) -> RemoteServerConfig?)? = null,
) : PlayerEngine {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlayerEngineState())
    override val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    private var playerView: PlayerView? = null
    private var playlist: List<MediaItemInfo> = emptyList()
    private var headersByMediaId: Map<String, Map<String, String>> = emptyMap()

    /** Applied to OkHttp requests for the current media origin host. */
    private val playbackHeaders = AtomicReference<Map<String, String>>(emptyMap())
    /** Host that may receive Authorization / custom auth headers (redirect-safe). */
    private val playbackOriginHost = AtomicReference<String?>(null)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds.coerceIn(5, 120).toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.coerceIn(5, 120).toLong() + 10L, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = playbackHeaders.get()
            if (headers.isEmpty()) {
                return@addInterceptor chain.proceed(request)
            }
            val originHost = playbackOriginHost.get()
            val sameOrigin = originHost != null &&
                request.url.host.equals(originHost, ignoreCase = true)
            val builder = request.newBuilder()
            headers.forEach { (key, value) ->
                val isAuth = key.equals("Authorization", ignoreCase = true) ||
                    key.equals("Cookie", ignoreCase = true)
                if (!isAuth || sameOrigin) {
                    builder.header(key, value)
                }
            }
            chain.proceed(builder.build())
        }
        .build()

    private val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent(UserAgentUtils.APP_DEFAULT)

    private val player: ExoPlayer = createPlayer()

    override val media3Player: Player get() = player

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = player.duration.coerceAtLeast(0L),
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                val current = playlist.getOrNull(index)
                if (current != null) {
                    applyHeadersFor(current)
                }
                val title = mediaItem?.mediaMetadata?.title?.toString()
                    ?: current?.name.orEmpty()
                _state.update {
                    it.copy(
                        title = title,
                        hasNext = player.hasNextMediaItem(),
                        hasPrevious = player.hasPreviousMediaItem(),
                        errorMessage = null,
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                _state.update {
                    it.copy(
                        audioTracks = extractAudioTracks(tracks),
                        subtitleTracks = extractSubtitleTracks(tracks),
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(errorMessage = error.localizedMessage ?: error.errorCodeName)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _state.update { it.copy(speed = playbackParameters.speed) }
            }
        })
    }

    private fun createPlayer(): ExoPlayer {
        val httpUpstream = DefaultDataSource.Factory(appContext, httpFactory)
        val upstreamFactory = if (remoteServerResolver != null) {
            RoutingDataSourceFactory(
                fallback = httpUpstream,
                remote = RemoteFileDataSource.Factory(remoteServerResolver),
            )
        } else {
            httpUpstream
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(MediaCache.get(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(cacheFactory)

        val builder = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
        if (handleAudioFocus) {
            builder.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            builder.setHandleAudioBecomingNoisy(true)
        }
        return builder.build()
    }

    override fun prepare(
        playlist: List<MediaItemInfo>,
        startIndex: Int,
        startPositionMs: Long,
        headersByMediaId: Map<String, Map<String, String>>,
        playWhenReady: Boolean,
    ) {
        this.playlist = playlist
        this.headersByMediaId = headersByMediaId
        val safeIndex = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        playlist.getOrNull(safeIndex)?.let { applyHeadersFor(it) }

        val mediaItems = playlist.map { info ->
            buildMediaItem(info)
        }
        player.setMediaItems(mediaItems, safeIndex, startPositionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        _state.update {
            it.copy(
                title = playlist.getOrNull(safeIndex)?.name.orEmpty(),
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem(),
                errorMessage = null,
            )
        }
    }

    private fun applyHeadersFor(info: MediaItemInfo) {
        val headers = headersByMediaId[info.id] ?: info.headers
        playbackHeaders.set(headers)
        playbackOriginHost.set(
            runCatching { android.net.Uri.parse(info.uri).host }.getOrNull(),
        )
        val ua = headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?: UserAgentUtils.APP_DEFAULT
        httpFactory.setUserAgent(ua)
    }

    private fun buildMediaItem(info: MediaItemInfo): MediaItem {
        val subtitleConfigs = info.subtitleCandidates.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.uri))
                .setMimeType(sub.mimeType ?: guessSubtitleMime(sub.name))
                .setLanguage(sub.language)
                .setLabel(sub.name)
                .build()
        }
        return MediaItem.Builder()
            .setUri(info.toUri())
            .setMediaId(info.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(info.name)
                    .build(),
            )
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    override fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val max = if (duration > 0L) duration else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        val resizeMode = when (mode) {
            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL, AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView?.resizeMode = resizeMode
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = player.currentTracks
        if (groupIndex !in tracks.groups.indices) return
        val group = tracks.groups[groupIndex]
        if (group.type != C.TRACK_TYPE_AUDIO) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val tracks = player.currentTracks
        if (groupIndex !in tracks.groups.indices) return
        val group = tracks.groups[groupIndex]
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    override fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun setPreferredLanguages(audioLanguage: String, subtitleLanguage: String) {
        val audioCode = preferredLanguageCode(audioLanguage)
        val textCode = preferredLanguageCode(subtitleLanguage)
        val builder = player.trackSelectionParameters.buildUpon()
        if (audioCode == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            builder.setPreferredAudioLanguage(audioCode)
        }
        when {
            subtitleLanguage.equals("none", ignoreCase = true) -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            textCode == null -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            }
            else -> {
                builder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(textCode)
            }
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun preferredLanguageCode(value: String): String? {
        return when (value.lowercase()) {
            "", "system" -> java.util.Locale.getDefault().language.takeIf { it.isNotBlank() }
            "none" -> null
            else -> value
        }
    }

    override fun addSubtitle(uri: String, mimeType: String?, language: String?) {
        val index = player.currentMediaItemIndex
        val current = playlist.getOrNull(index) ?: return
        val updated = current.copy(
            subtitleCandidates = current.subtitleCandidates + SubtitleCandidate(
                name = uri.substringAfterLast('/'),
                uri = uri,
                language = language,
                mimeType = mimeType,
            ),
        )
        playlist = playlist.toMutableList().also { it[index] = updated }
        val mediaItem = buildMediaItem(updated)
        val position = player.currentPosition
        player.replaceMediaItem(index, mediaItem)
        player.seekTo(position)
    }

    override fun playNext() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    override fun playPrevious() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    override fun release() {
        clearVideoOutput()
        playerView?.player = null
        playerView = null
        playbackHeaders.set(emptyMap())
        playbackOriginHost.set(null)
        player.release()
    }

    override fun attachVideoView(view: View) {
        val pv = view as? PlayerView ?: return
        playerView = pv
        pv.setKeepContentOnPlayerReset(false)
        pv.player = player
    }

    override fun detachVideoView() {
        val pv = playerView
        playerView = null
        // Drop the surface immediately so the last frame cannot linger after popBackStack.
        clearVideoOutput()
        if (pv != null) {
            pv.player = null
            pv.visibility = View.GONE
        }
    }

    /** Pause and strip the video output before navigating away from the player UI. */
    fun prepareForExit() {
        runCatching { player.pause() }
        clearVideoOutput()
        val pv = playerView
        if (pv != null) {
            pv.player = null
            pv.visibility = View.GONE
        }
        playerView = null
    }

    private fun clearVideoOutput() {
        runCatching { player.clearVideoSurface() }
    }

    fun pollProgress() {
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                bufferedPercent = player.bufferedPercentage.coerceIn(0, 100),
            )
        }
    }

    private fun extractAudioTracks(tracks: Tracks): List<AudioTrackInfo> {
        val result = mutableListOf<AudioTrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result += AudioTrackInfo(
                    groupIndex = groupIndex,
                    trackIndex = i,
                    name = format.label
                        ?: format.language
                        ?: "Audio ${result.size + 1}",
                    language = format.language,
                    codecs = format.codecs,
                    channelCount = format.channelCount.takeIf { it > 0 },
                    selected = group.isTrackSelected(i),
                )
            }
        }
        return result
    }

    private fun extractSubtitleTracks(tracks: Tracks): List<SubtitleTrackInfo> {
        val result = mutableListOf<SubtitleTrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result += SubtitleTrackInfo(
                    groupIndex = groupIndex,
                    trackIndex = i,
                    name = format.label
                        ?: format.language
                        ?: "Subtitle ${result.size + 1}",
                    language = format.language,
                    selected = group.isTrackSelected(i),
                )
            }
        }
        return result
    }

    private fun guessSubtitleMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
