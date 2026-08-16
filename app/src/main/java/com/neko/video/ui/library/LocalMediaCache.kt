package com.neko.video.ui.library

import com.neko.video.model.MediaFolder
import com.neko.video.model.MediaItemInfo

/**
 * Process-lifetime cache for local library scans. Cleared automatically when the
 * app process dies; not persisted to disk.
 */
object LocalMediaCache {
    @Volatile
    var videos: List<MediaItemInfo>? = null
        private set

    @Volatile
    var folders: List<MediaFolder>? = null
        private set

    val isReady: Boolean
        get() = videos != null

    fun update(videos: List<MediaItemInfo>, folders: List<MediaFolder>) {
        this.videos = videos
        this.folders = folders
    }

    fun clear() {
        videos = null
        folders = null
    }
}
