package com.neko.video.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getById(mediaId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun observeIsFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface WebDavServerDao {
    @Query("SELECT * FROM webdav_servers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WebDavServerEntity>>

    @Query("SELECT * FROM webdav_servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebDavServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WebDavServerEntity)

    @Query("DELETE FROM webdav_servers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecentUrlDao {
    @Query("SELECT * FROM recent_urls ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecentUrlEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentUrlEntity)

    @Query("DELETE FROM recent_urls WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface RemoteServerDao {
    @Query("SELECT * FROM remote_servers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RemoteServerEntity>>

    @Query("SELECT * FROM remote_servers WHERE protocol = :protocol ORDER BY updatedAt DESC")
    fun observeByProtocol(protocol: String): Flow<List<RemoteServerEntity>>

    @Query("SELECT * FROM remote_servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RemoteServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemoteServerEntity)

    @Query("DELETE FROM remote_servers WHERE id = :id")
    suspend fun delete(id: String)
}
