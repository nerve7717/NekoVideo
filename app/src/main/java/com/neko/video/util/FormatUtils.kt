package com.neko.video.util

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow

object FormatUtils {
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    fun parseHeaders(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (key.isEmpty()) null else key to value
            }
            .toMap()
    }

    fun headersToText(headers: Map<String, String>): String {
        return headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    fun isVideoFile(name: String, mimeType: String? = null): Boolean {
        if (mimeType?.startsWith("video/") == true) return true
        val lower = name.lowercase(Locale.US)
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isSubtitleFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return SUBTITLE_EXTENSIONS.any { lower.endsWith(it) }
    }

    private val VIDEO_EXTENSIONS = listOf(
        ".mp4", ".mkv", ".webm", ".mov", ".avi", ".ts", ".m4v",
        ".flv", ".wmv", ".m3u8", ".mpd", ".3gp", ".mpeg", ".mpg",
    )

    private val SUBTITLE_EXTENSIONS = listOf(
        ".srt", ".ass", ".ssa", ".vtt", ".ttml", ".dfxp", ".sub",
    )
}
