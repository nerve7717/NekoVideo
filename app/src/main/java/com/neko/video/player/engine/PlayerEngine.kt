package com.neko.video.player.engine

import android.view.View
import androidx.media3.common.Player
import com.neko.video.model.AspectRatioMode
import com.neko.video.model.AudioTrackInfo
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SubtitleTrackInfo
import kotlinx.coroutines.flow.StateFlow

data class PlayerEngineState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val speed: Float = 1.0f,
    val title: String = "",
    val errorMessage: String? = null,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

interface PlayerEngine {
    val state: StateFlow<PlayerEngineState>

    /** Media3 Player for UI binding when available; null for non-Media3 engines. */
    val media3Player: Player?

    fun prepare(
        playlist: List<MediaItemInfo>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        headersByMediaId: Map<String, Map<String, String>> = emptyMap(),
        playWhenReady: Boolean = true,
    )

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)
    fun setAspectRatio(mode: AspectRatioMode)
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int)
    fun disableSubtitles()
    fun addSubtitle(uri: String, mimeType: String? = null, language: String? = null)
    fun setPreferredLanguages(audioLanguage: String, subtitleLanguage: String)
    fun playNext()
    fun playPrevious()
    fun release()

    fun attachVideoView(view: View)
    fun detachVideoView()
}
