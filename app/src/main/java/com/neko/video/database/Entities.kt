package com.neko.video.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaId: String,
    val name: String,
    val uri: String,
    val sourceType: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAt: Long,
    val thumbnailUri: String? = null,
    val serverId: String? = null,
    val size: Long = 0L,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val sourceType: String,
    val isDirectory: Boolean = false,
    val thumbnailUri: String? = null,
    val serverId: String? = null,
    val addedAt: Long,
)

@Entity(tableName = "webdav_servers")
data class WebDavServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val username: String,
    val encryptedPassword: String,
    val userAgent: String? = null,
    val headersJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "recent_urls")
data class RecentUrlEntity(
    @PrimaryKey val url: String,
    val title: String,
    val headersJson: String = "{}",
    val openedAt: Long,
)

@Entity(tableName = "remote_servers")
data class RemoteServerEntity(
    @PrimaryKey val id: String,
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int = 0,
    val share: String = "",
    val initialPath: String = "/",
    val username: String = "",
    val encryptedPassword: String = "",
    val domain: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
