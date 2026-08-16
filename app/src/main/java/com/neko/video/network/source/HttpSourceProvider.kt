package com.neko.video.network.source

import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import java.util.UUID

class HttpSourceProvider : MediaSourceProvider {
    override val sourceType: SourceType = SourceType.HTTP

    override suspend fun list(path: String): List<MediaItemInfo> = emptyList()

    fun createMediaItem(
        url: String,
        title: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): MediaItemInfo {
        val name = title?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/')
                .substringBefore('?')
                .ifBlank { url }
        return MediaItemInfo(
            id = "http_${UUID.nameUUIDFromBytes(url.toByteArray())}",
            name = name,
            uri = url,
            path = url,
            sourceType = SourceType.HTTP,
            mimeType = guessMime(url),
            headers = headers,
        )
    }

    private fun guessMime(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "application/x-mpegURL"
            lower.contains(".mpd") -> "application/dash+xml"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            else -> null
        }
    }
}
