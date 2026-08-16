package com.neko.video.repository

import android.content.Context
import android.net.Uri
import com.neko.video.database.AppDatabase
import com.neko.video.database.FavoriteEntity
import com.neko.video.database.PlaybackHistoryEntity
import com.neko.video.database.RecentUrlEntity
import com.neko.video.database.WebDavServerEntity
import com.neko.video.model.MediaFolder
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.network.source.HttpSourceProvider
import com.neko.video.network.source.LocalSourceProvider
import com.neko.video.util.FormatUtils
import com.neko.video.util.SecureStorage
import com.neko.video.database.RemoteServerEntity
import com.neko.video.remote.RemoteClients
import com.neko.video.remote.RemoteProtocol
import com.neko.video.remote.RemoteServerConfig
import com.neko.video.webdav.WebDavClient
import com.neko.video.webdav.WebDavServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

class MediaRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.get(context),
    private val secureStorage: SecureStorage = SecureStorage.get(context),
) {
    private val localSource = LocalSourceProvider(context)
    private val httpSource = HttpSourceProvider()
    private fun webDavClient(timeoutSeconds: Int = 20) = WebDavClient(timeoutSeconds)

    val historyFlow: Flow<List<MediaItemInfo>> =
        database.playbackHistoryDao().observeAll().map { list ->
            list.map { it.toMediaItem() }
        }

    val recentHistoryFlow: Flow<List<MediaItemInfo>> =
        database.playbackHistoryDao().observeRecent(20).map { list ->
            list.map { it.toMediaItem() }
        }

    val favoritesFlow: Flow<List<MediaItemInfo>> =
        database.favoriteDao().observeAll().map { list ->
            list.map { it.toMediaItem() }
        }

    val webDavServersFlow: Flow<List<WebDavServerConfig>> =
        database.webDavServerDao().observeAll().map { list ->
            list.map { it.toConfig() }
        }

    val recentUrlsFlow: Flow<List<RecentUrlEntity>> =
        database.recentUrlDao().observeRecent()

    val remoteServersFlow: Flow<List<RemoteServerConfig>> =
        database.remoteServerDao().observeAll().map { list -> list.map { it.toRemoteConfig() } }

    fun observeRemoteServers(protocol: RemoteProtocol): Flow<List<RemoteServerConfig>> =
        database.remoteServerDao().observeByProtocol(protocol.name).map { list ->
            list.map { it.toRemoteConfig() }
        }

    suspend fun loadLocalVideos(): List<MediaItemInfo> = localSource.loadAllVideos()

    suspend fun loadLocalFolders(videos: List<MediaItemInfo>): List<MediaFolder> =
        localSource.loadFolders(videos)

    suspend fun loadFromTreeUri(uri: Uri): List<MediaItemInfo> =
        localSource.loadFromTreeUri(uri)

    fun createHttpMedia(
        url: String,
        title: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): MediaItemInfo = httpSource.createMediaItem(url, title, headers)

    suspend fun saveRecentUrl(url: String, title: String, headers: Map<String, String>) {
        database.recentUrlDao().upsert(
            RecentUrlEntity(
                url = url,
                title = title,
                headersJson = headersToJson(headers),
                openedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun getHistory(mediaId: String): PlaybackHistoryEntity? =
        database.playbackHistoryDao().getById(mediaId)

    suspend fun upsertHistory(
        media: MediaItemInfo,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) {
        database.playbackHistoryDao().upsert(
            PlaybackHistoryEntity(
                mediaId = media.id,
                name = media.name,
                uri = media.uri,
                sourceType = media.sourceType.name,
                positionMs = positionMs,
                durationMs = durationMs,
                completed = completed,
                lastPlayedAt = System.currentTimeMillis(),
                thumbnailUri = media.thumbnailUri,
                serverId = media.serverId,
                size = media.size,
            ),
        )
    }

    suspend fun clearHistory() = database.playbackHistoryDao().clearAll()

    suspend fun toggleFavorite(media: MediaItemInfo): Boolean {
        val dao = database.favoriteDao()
        return if (dao.isFavorite(media.id)) {
            dao.delete(media.id)
            false
        } else {
            dao.upsert(
                FavoriteEntity(
                    id = media.id,
                    name = media.name,
                    uri = media.uri,
                    sourceType = media.sourceType.name,
                    isDirectory = media.isDirectory,
                    thumbnailUri = media.thumbnailUri,
                    serverId = media.serverId,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            true
        }
    }

    fun observeIsFavorite(id: String): Flow<Boolean> =
        database.favoriteDao().observeIsFavorite(id)

    suspend fun upsertWebDavServer(
        id: String?,
        name: String,
        url: String,
        username: String,
        password: String,
        userAgent: String?,
        headers: Map<String, String>,
    ): WebDavServerConfig {
        val now = System.currentTimeMillis()
        val serverId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { database.webDavServerDao().getById(it) }
        val encryptedPassword = if (password.isBlank() && existing != null) {
            existing.encryptedPassword
        } else {
            secureStorage.encrypt(password)
        }
        val entity = WebDavServerEntity(
            id = serverId,
            name = name,
            url = url.trim(),
            username = username,
            encryptedPassword = encryptedPassword,
            userAgent = userAgent,
            headersJson = headersToJson(headers),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        database.webDavServerDao().upsert(entity)
        return entity.toConfig()
    }

    suspend fun deleteWebDavServer(id: String) {
        database.webDavServerDao().delete(id)
    }

    suspend fun getWebDavServer(id: String): WebDavServerConfig? =
        database.webDavServerDao().getById(id)?.toConfig()

    suspend fun testWebDav(config: WebDavServerConfig, timeoutSeconds: Int = 20) =
        webDavClient(timeoutSeconds).testConnection(config)

    suspend fun listWebDav(
        config: WebDavServerConfig,
        path: String,
        videosOnly: Boolean,
        timeoutSeconds: Int = 20,
    ) = webDavClient(timeoutSeconds).listDirectory(config, path, videosOnly)

    fun playbackHeadersForWebDav(config: WebDavServerConfig): Map<String, String> =
        webDavClient().buildPlaybackHeaders(config)

    suspend fun upsertRemoteServer(
        id: String?,
        protocol: RemoteProtocol,
        name: String,
        host: String,
        port: Int,
        share: String,
        initialPath: String,
        username: String,
        password: String,
        domain: String,
    ): RemoteServerConfig {
        val now = System.currentTimeMillis()
        val serverId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { database.remoteServerDao().getById(it) }
        val encryptedPassword = if (password.isBlank() && existing != null) {
            existing.encryptedPassword
        } else {
            secureStorage.encrypt(password)
        }
        val entity = RemoteServerEntity(
            id = serverId,
            protocol = protocol.name,
            name = name.trim(),
            host = host.trim(),
            port = port,
            share = share.trim(),
            initialPath = initialPath.trim().ifBlank { "/" },
            username = username.trim(),
            encryptedPassword = encryptedPassword,
            domain = domain.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        database.remoteServerDao().upsert(entity)
        return entity.toRemoteConfig()
    }

    suspend fun deleteRemoteServer(id: String) {
        database.remoteServerDao().delete(id)
    }

    suspend fun getRemoteServer(id: String): RemoteServerConfig? =
        database.remoteServerDao().getById(id)?.toRemoteConfig()

    fun getRemoteServerBlocking(id: String): RemoteServerConfig? = runBlocking {
        getRemoteServer(id)
    }

    suspend fun testRemoteServer(config: RemoteServerConfig) = RemoteClients.test(config)

    suspend fun listRemote(
        config: RemoteServerConfig,
        path: String,
        videosOnly: Boolean,
    ) = RemoteClients.list(config, path, videosOnly)

    private fun RemoteServerEntity.toRemoteConfig(): RemoteServerConfig {
        val protocol = runCatching { RemoteProtocol.valueOf(protocol) }.getOrDefault(RemoteProtocol.SMB)
        return RemoteServerConfig(
            id = id,
            protocol = protocol,
            name = name,
            host = host,
            port = port,
            share = share,
            initialPath = initialPath,
            username = username,
            password = secureStorage.decrypt(encryptedPassword),
            domain = domain,
        )
    }

    private fun WebDavServerEntity.toConfig(): WebDavServerConfig {
        return WebDavServerConfig(
            id = id,
            name = name,
            url = url,
            username = username,
            password = secureStorage.decrypt(encryptedPassword),
            userAgent = userAgent,
            headers = jsonToHeaders(headersJson),
        )
    }

    private fun PlaybackHistoryEntity.toMediaItem(): MediaItemInfo {
        return MediaItemInfo(
            id = mediaId,
            name = name,
            uri = uri,
            sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.LOCAL),
            size = size,
            duration = durationMs,
            thumbnailUri = thumbnailUri,
            serverId = serverId,
            playbackPosition = positionMs,
            lastPlayedAt = lastPlayedAt,
        )
    }

    private fun FavoriteEntity.toMediaItem(): MediaItemInfo {
        return MediaItemInfo(
            id = id,
            name = name,
            uri = uri,
            sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.LOCAL),
            thumbnailUri = thumbnailUri,
            serverId = serverId,
            isDirectory = isDirectory,
        )
    }

    private fun headersToJson(headers: Map<String, String>): String {
        val json = JSONObject()
        headers.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun jsonToHeaders(text: String): Map<String, String> {
        return runCatching {
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrElse {
            FormatUtils.parseHeaders(text)
        }
    }
}
