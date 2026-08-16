package com.neko.video.player.service

import androidx.media3.common.Player

/** Shared player for [PlaybackService] media notifications / background controls. */
object PlayerBridge {
    @Volatile
    var player: Player? = null

    /** Invoked by the service so the UI can release MediaSession before ExoPlayer. */
    @Volatile
    var releaseSession: (() -> Unit)? = null
}
