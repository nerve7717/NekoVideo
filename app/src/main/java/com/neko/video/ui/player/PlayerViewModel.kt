package com.neko.video.ui.player

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.neko.video.asNekoVideoApp
import com.neko.video.model.AspectRatioMode
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.network.HttpHeaderResolver
import com.neko.video.player.PlaybackSession
import com.neko.video.player.engine.Media3PlayerEngine
import com.neko.video.player.engine.PlayerEngine
import com.neko.video.player.engine.PlayerEngineState
import com.neko.video.player.service.PlaybackService
import com.neko.video.player.service.PlayerBridge
import com.neko.video.settings.SettingsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ResumePrompt(
    val positionMs: Long,
    val durationMs: Long,
)

data class PlayerUiState(
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val resumePrompt: ResumePrompt? = null,
    val isFavorite: Boolean = false,
    val gestureLabel: String? = null,
    val hasPlaylist: Boolean = false,
)

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.asNekoVideoApp().container
    private val repository = container.mediaRepository

    val settings: StateFlow<SettingsState> = container.settings.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsState(),
    )

    private var engine: Media3PlayerEngine = createEngine(settings.value)
    val playerEngine: PlayerEngine get() = engine
    private val _engineState = MutableStateFlow(engine.state.value)
    val engineState: StateFlow<PlayerEngineState> = _engineState.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var hideControlsJob: Job? = null
    private var progressJob: Job? = null
    private var historyJob: Job? = null
    private var engineCollectJob: Job? = null
    private var favoriteJob: Job? = null
    private var prepareJob: Job? = null
    private var currentMedia: MediaItemInfo? = null
    private var playlist: List<MediaItemInfo> = emptyList()
    private var prepared = false
    private var preparedKey: String? = null

    init {
        bindEngineState()
    }

    private fun createEngine(settingsState: SettingsState): Media3PlayerEngine {
        return Media3PlayerEngine(
            context = getApplication(),
            handleAudioFocus = settingsState.audioFocusEnabled,
            timeoutSeconds = settingsState.networkTimeoutSeconds,
            remoteServerResolver = { id -> repository.getRemoteServerBlocking(id) },
        )
    }

    private fun bindEngineState() {
        engineCollectJob?.cancel()
        engineCollectJob = viewModelScope.launch {
            engine.state.collect { state -> _engineState.value = state }
        }
        engine.media3Player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && settings.value.autoPlayNext) {
                    playNextIfAvailable()
                }
            }
        })
    }

    fun prepare(
        uri: String,
        title: String,
        sourceType: String,
        mediaId: String?,
        serverId: String?,
    ) {
        val resolvedMediaId = mediaId?.takeIf { it.isNotBlank() }
        val key = listOf(uri, resolvedMediaId.orEmpty(), serverId.orEmpty(), sourceType).joinToString("|")
        if (prepared && preparedKey == key) return
        prepared = true
        preparedKey = key
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            val settingsState = container.settings.settingsFlow.first()
            ensureEngineMatches(settingsState)
            _uiState.update {
                it.copy(
                    aspectRatio = settingsState.defaultAspectRatio,
                    resumePrompt = null,
                )
            }

            val session = PlaybackSession.consume()
            val type = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.LOCAL)

            playlist = session?.first?.takeIf { it.isNotEmpty() } ?: listOf(
                MediaItemInfo(
                    id = resolvedMediaId ?: "play_${uri.hashCode()}",
                    name = title.ifBlank { uri.substringAfterLast('/') },
                    uri = uri,
                    sourceType = type,
                    serverId = serverId,
                ),
            )
            val startIndex = session?.second ?: 0
            if (playlist.getOrNull(startIndex) == null) return@launch
            ensureActive()

            val headersById = linkedMapOf<String, Map<String, String>>()
            val resolvedPlaylist = playlist.map { item ->
                val headers = resolveHeaders(settingsState, item)
                headersById[item.id] = headers
                item.copy(headers = headers)
            }
            playlist = resolvedPlaylist
            currentMedia = resolvedPlaylist[startIndex]
            _uiState.update { it.copy(hasPlaylist = resolvedPlaylist.size > 1) }

            val media = resolvedPlaylist[startIndex]
            val history = repository.getHistory(media.id)
            val threshold = settingsState.completionThresholdPercent / 100f
            val shouldResume = settingsState.autoResume &&
                history != null &&
                !history.completed &&
                history.positionMs > 3_000 &&
                history.durationMs > 0 &&
                history.positionMs < history.durationMs * (1f - threshold)

            if (shouldResume) {
                val resumeHistory = history!!
                _uiState.update {
                    it.copy(
                        resumePrompt = ResumePrompt(
                            resumeHistory.positionMs,
                            resumeHistory.durationMs,
                        ),
                    )
                }
            }
            ensureActive()
            engine.prepare(
                playlist = resolvedPlaylist,
                startIndex = startIndex,
                startPositionMs = 0L,
                headersByMediaId = headersById,
                playWhenReady = !shouldResume,
            )

            engine.setSpeed(settingsState.defaultSpeed)
            engine.setAspectRatio(settingsState.defaultAspectRatio)
            engine.setPreferredLanguages(
                audioLanguage = settingsState.audioLanguage,
                subtitleLanguage = settingsState.subtitleLanguage,
            )
            attachMediaSession(settingsState.backgroundPlay)
            startProgressLoop()
            startHistoryLoop()
            showControlsTemporarily()
            observeFavorite(media.id)
        }
    }

    private fun ensureEngineMatches(settingsState: SettingsState) {
        val current = engine
        val needsRebuild = current.timeoutSeconds != settingsState.networkTimeoutSeconds ||
            current.handleAudioFocus != settingsState.audioFocusEnabled
        if (!needsRebuild) return
        engineCollectJob?.cancel()
        runCatching { current.release() }
        engine = createEngine(settingsState)
        bindEngineState()
    }

    private fun observeFavorite(mediaId: String) {
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            repository.observeIsFavorite(mediaId).collect { fav ->
                _uiState.update { it.copy(isFavorite = fav) }
            }
        }
    }

    private suspend fun resolveHeaders(
        settingsState: SettingsState,
        item: MediaItemInfo,
    ): Map<String, String> {
        return when (item.sourceType) {
            SourceType.WEBDAV -> {
                val server = item.serverId?.let { repository.getWebDavServer(it) }
                HttpHeaderResolver.resolve(
                    settings = settingsState,
                    mediaHeaders = item.headers,
                    serverUserAgent = server?.userAgent,
                    serverHeaders = server?.let { repository.playbackHeadersForWebDav(it) }.orEmpty(),
                )
            }
            SourceType.HTTP -> HttpHeaderResolver.resolve(
                settings = settingsState,
                mediaHeaders = item.headers,
            )
            SourceType.LOCAL,
            SourceType.SMB,
            SourceType.SFTP,
            SourceType.FTP,
            -> emptyMap()
        }
    }

    private fun attachMediaSession(backgroundPlay: Boolean) {
        val player = engine.media3Player ?: return
        PlayerBridge.player = player
        if (backgroundPlay) {
            runCatching {
                ContextCompat.startForegroundService(
                    getApplication(),
                    Intent(getApplication(), PlaybackService::class.java),
                )
            }
        }
    }

    fun onAppBackgrounded(inPictureInPicture: Boolean = false) {
        if (inPictureInPicture || settings.value.backgroundPlay) return
        engine.pause()
    }

    /** Call before popBackStack so Surface/Texture frames do not linger on the previous screen. */
    fun prepareForExit() {
        engine.prepareForExit()
    }

    fun playNextIfAvailable() {
        val player = engine.media3Player ?: return
        if (!player.hasNextMediaItem()) return
        engine.playNext()
        syncCurrentFromPlayer()
    }

    fun playPreviousIfAvailable() {
        val player = engine.media3Player ?: return
        if (!player.hasPreviousMediaItem()) return
        engine.playPrevious()
        syncCurrentFromPlayer()
    }

    private fun syncCurrentFromPlayer() {
        val index = engine.media3Player?.currentMediaItemIndex ?: return
        currentMedia = playlist.getOrNull(index)
        currentMedia?.let { media -> observeFavorite(media.id) }
        showControlsTemporarily()
    }

    fun addExternalSubtitle(uri: String, mimeType: String? = null) {
        engine.addSubtitle(uri, mimeType)
        showControlsTemporarily()
    }

    fun resumeFromPrompt() {
        val prompt = _uiState.value.resumePrompt ?: return
        engine.seekTo(prompt.positionMs)
        engine.play()
        _uiState.update { it.copy(resumePrompt = null) }
        showControlsTemporarily()
    }

    fun restartFromPrompt() {
        engine.seekTo(0L)
        engine.play()
        _uiState.update { it.copy(resumePrompt = null) }
        showControlsTemporarily()
    }

    fun toggleControls() {
        if (_uiState.value.locked) return
        if (_uiState.value.controlsVisible) {
            _uiState.update { it.copy(controlsVisible = false) }
            hideControlsJob?.cancel()
        } else {
            showControlsTemporarily()
        }
    }

    fun showControlsTemporarily() {
        if (_uiState.value.locked) return
        _uiState.update { it.copy(controlsVisible = true) }
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(4_000)
            if (!_uiState.value.locked) {
                _uiState.update { it.copy(controlsVisible = false) }
            }
        }
    }

    fun toggleLock() {
        val locked = !_uiState.value.locked
        _uiState.update { it.copy(locked = locked, controlsVisible = true) }
        if (!locked) showControlsTemporarily()
    }

    fun toggleFavorite() {
        val media = currentMedia ?: return
        viewModelScope.launch {
            val added = repository.toggleFavorite(media)
            _uiState.update { it.copy(isFavorite = added) }
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        engine.setAspectRatio(mode)
        _uiState.update { it.copy(aspectRatio = mode) }
        showControlsTemporarily()
    }

    fun setGestureLabel(label: String?) {
        _uiState.update { it.copy(gestureLabel = label) }
    }

    fun seekSeconds(): Int = settings.value.seekSeconds

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                engine.pollProgress()
                delay(500)
            }
        }
    }

    private fun startHistoryLoop() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                persistHistory()
            }
        }
    }

    private suspend fun persistHistory() {
        // Don't overwrite real resume position with 0 while the prompt is showing.
        if (_uiState.value.resumePrompt != null) return
        val media = currentMedia ?: return
        val state = engine.state.value
        if (state.durationMs <= 0L) return
        val settingsState = container.settings.settingsFlow.first()
        val remainRatio = 1f - (state.positionMs.toFloat() / state.durationMs.toFloat())
        val completed = remainRatio <= settingsState.completionThresholdPercent / 100f
        repository.upsertHistory(
            media = media,
            positionMs = if (completed) 0L else state.positionMs,
            durationMs = state.durationMs,
            completed = completed,
        )
    }

    override fun onCleared() {
        prepareJob?.cancel()
        progressJob?.cancel()
        historyJob?.cancel()
        hideControlsJob?.cancel()
        engineCollectJob?.cancel()
        favoriteJob?.cancel()
        val app = getApplication<Application>().asNekoVideoApp()
        app.applicationScope.launch {
            runCatching { persistHistory() }
        }
        val ownedPlayer = PlayerBridge.player === engine.media3Player
        if (ownedPlayer) {
            // Release MediaSession while ExoPlayer is still alive, then clear the bridge.
            // Do NOT clear PlaybackSession here — a newer playMedia() may have already begin()'d.
            runCatching { PlayerBridge.releaseSession?.invoke() }
            PlayerBridge.releaseSession = null
            PlayerBridge.player = null
            runCatching {
                getApplication<Application>().stopService(
                    Intent(getApplication(), PlaybackService::class.java),
                )
            }
        }
        engine.release()
        super.onCleared()
    }
}
