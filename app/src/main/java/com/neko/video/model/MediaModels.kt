package com.neko.video.model

import android.net.Uri

enum class SourceType {
    LOCAL,
    HTTP,
    WEBDAV,
    SMB,
    SFTP,
    FTP,
}

data class MediaItemInfo(
    val id: String,
    val name: String,
    val uri: String,
    val path: String? = null,
    val sourceType: SourceType,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
    val mimeType: String? = null,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUri: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitleCandidates: List<SubtitleCandidate> = emptyList(),
    val isDirectory: Boolean = false,
    val serverId: String? = null,
    val playbackPosition: Long = 0L,
    val lastPlayedAt: Long = 0L,
) {
    fun toUri(): Uri = Uri.parse(uri)

    val displayResolution: String?
        get() = if (width > 0 && height > 0) "${width}x$height" else null
}

data class SubtitleCandidate(
    val name: String,
    val uri: String,
    val language: String? = null,
    val mimeType: String? = null,
)

data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val videoCount: Int,
    val thumbnailUri: String? = null,
)

data class PlaybackRequest(
    val media: MediaItemInfo,
    val playlist: List<MediaItemInfo> = listOf(media),
    val startIndex: Int = 0,
    val startPositionMs: Long? = null,
)

enum class AspectRatioMode {
    FIT,
    FILL,
    CROP,
    STRETCH,
    RATIO_16_9,
    RATIO_4_3,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class UserAgentPreset {
    APP_DEFAULT,
    ANDROID,
    CHROME_ANDROID,
    CUSTOM,
}

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String?,
    val codecs: String?,
    val channelCount: Int?,
    val selected: Boolean,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String?,
    val selected: Boolean,
)
