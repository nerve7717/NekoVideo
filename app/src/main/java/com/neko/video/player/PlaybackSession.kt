package com.neko.video.player

import com.neko.video.model.MediaItemInfo

/**
 * In-memory playlist / media extras that cannot fit in navigation routes.
 * Set before navigating to the player; consumed in [com.neko.video.ui.player.PlayerViewModel.prepare].
 */
object PlaybackSession {
    @Volatile
    var playlist: List<MediaItemInfo> = emptyList()
        private set

    @Volatile
    var startIndex: Int = 0
        private set

    fun begin(items: List<MediaItemInfo>, startIndex: Int = 0) {
        playlist = items
        this.startIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    fun beginSingle(item: MediaItemInfo) {
        begin(listOf(item), 0)
    }

    /** Atomically take the pending playlist so a dying PlayerViewModel cannot wipe a new begin(). */
    @Synchronized
    fun consume(): Pair<List<MediaItemInfo>, Int>? {
        val items = playlist
        if (items.isEmpty()) return null
        val index = startIndex
        playlist = emptyList()
        startIndex = 0
        return items to index
    }

    fun clear() {
        playlist = emptyList()
        startIndex = 0
    }
}
