package com.neko.video.network.source

import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.webdav.WebDavClient
import com.neko.video.webdav.WebDavServerConfig

class WebDavSourceProvider(
    private val config: WebDavServerConfig,
    private val client: WebDavClient,
    private val videosOnly: Boolean = true,
) : MediaSourceProvider {
    override val sourceType: SourceType = SourceType.WEBDAV

    override suspend fun list(path: String): List<MediaItemInfo> {
        return client.listDirectory(config, path, videosOnly).getOrThrow()
    }

    override suspend fun resolvePlaybackHeaders(item: MediaItemInfo): Map<String, String> {
        return client.buildPlaybackHeaders(config) + item.headers
    }
}
