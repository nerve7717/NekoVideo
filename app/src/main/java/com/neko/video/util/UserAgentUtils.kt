package com.neko.video.util

import com.neko.video.BuildConfig
import com.neko.video.model.UserAgentPreset

object UserAgentUtils {
    const val APP_DEFAULT =
        "NekoVideo/${BuildConfig.VERSION_NAME} (Android; Media3)"

    const val ANDROID_DEFAULT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"

    const val CHROME_ANDROID =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun resolve(
        preset: UserAgentPreset,
        custom: String?,
        serverOverride: String? = null,
        mediaOverride: String? = null,
    ): String {
        mediaOverride?.takeIf { it.isNotBlank() }?.let { return it }
        serverOverride?.takeIf { it.isNotBlank() }?.let { return it }
        return when (preset) {
            UserAgentPreset.APP_DEFAULT -> APP_DEFAULT
            UserAgentPreset.ANDROID -> ANDROID_DEFAULT
            UserAgentPreset.CHROME_ANDROID -> CHROME_ANDROID
            UserAgentPreset.CUSTOM -> custom?.takeIf { it.isNotBlank() } ?: APP_DEFAULT
        }
    }
}
