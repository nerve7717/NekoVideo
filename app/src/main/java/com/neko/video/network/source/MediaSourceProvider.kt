package com.neko.video.network.source

import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType

interface MediaSourceProvider {
    val sourceType: SourceType

    suspend fun list(path: String = "/"): List<MediaItemInfo>

    suspend fun resolvePlaybackHeaders(item: MediaItemInfo): Map<String, String> = item.headers
}
