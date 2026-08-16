package com.neko.video.network

import com.neko.video.settings.SettingsState
import com.neko.video.util.FormatUtils
import com.neko.video.util.UserAgentUtils

/**
 * Header priority:
 * media-specific > server-specific > global settings
 *
 * User-Agent is always normalized via [UserAgentUtils.resolve] so the settings
 * custom UA is applied unless media/server overrides it.
 */
object HttpHeaderResolver {
    fun resolve(
        settings: SettingsState,
        mediaHeaders: Map<String, String> = emptyMap(),
        serverUserAgent: String? = null,
        serverHeaders: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val globalHeaders = FormatUtils.parseHeaders(settings.defaultHeadersText)
        val merged = linkedMapOf<String, String>()
        merged.putAll(globalHeaders)
        merged.putAll(serverHeaders)
        merged.putAll(mediaHeaders)

        val mediaUa = mediaHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
        val userAgent = UserAgentUtils.resolve(
            preset = settings.userAgentPreset,
            custom = settings.customUserAgent,
            serverOverride = serverUserAgent,
            mediaOverride = mediaUa,
        )
        merged.keys.filter { it.equals("User-Agent", ignoreCase = true) }
            .toList()
            .forEach { merged.remove(it) }
        merged["User-Agent"] = userAgent
        return merged
    }
}
