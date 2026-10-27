package com.neko.video.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.model.SubtitleCandidate
import org.json.JSONArray
import org.json.JSONObject

/**
 * One scanned local video, persisted so the library can be shown before `MediaStore` is queried.
 *
 * This is a cache: if it is ever wrong or stale, deleting the database file just forces a full
 * rescan. It deliberately lives in its own database file — the main database holds user data
 * (history, favourites, servers) and is opened with `fallbackToDestructiveMigration`, so adding a
 * table there would risk wiping that data on an upgrade.
 */
@Entity(tableName = "local_media")
data class IndexedMediaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val path: String?,
    val size: Long,
    val modifiedTime: Long,
    val mimeType: String?,
    val duration: Long,
    val width: Int,
    val height: Int,
    val thumbnailUri: String?,
    /** Serialised [SubtitleCandidate] list; empty array when the video has no siblings. */
    val subtitlesJson: String,
    val indexedAt: Long = 0L,
)

/** Id + modified time only: enough to decide what changed without reading the whole table. */
data class MediaStamp(
    val id: String,
    val modifiedTime: Long,
)

@Dao
interface IndexedMediaDao {
    @Query("SELECT * FROM local_media")
    suspend fun getAll(): List<IndexedMediaEntity>

    @Query("SELECT id, modifiedTime FROM local_media")
    suspend fun getStamps(): List<MediaStamp>

    @Query("SELECT COUNT(*) FROM local_media")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<IndexedMediaEntity>)

    @Query("DELETE FROM local_media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM local_media")
    suspend fun clear()
}

@Database(entities = [IndexedMediaEntity::class], version = 1, exportSchema = false)
abstract class MediaIndexDatabase : RoomDatabase() {
    abstract fun indexedMediaDao(): IndexedMediaDao

    companion object {
        @Volatile
        private var instance: MediaIndexDatabase? = null

        fun get(context: Context): MediaIndexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaIndexDatabase::class.java,
                    "neko_media_index.db",
                ).build().also { instance = it }
            }
        }
    }
}

internal fun IndexedMediaEntity.toMediaItem(): MediaItemInfo = MediaItemInfo(
    id = id,
    name = name,
    uri = uri,
    path = path,
    sourceType = SourceType.LOCAL,
    size = size,
    modifiedTime = modifiedTime,
    mimeType = mimeType,
    duration = duration,
    width = width,
    height = height,
    thumbnailUri = thumbnailUri,
    subtitleCandidates = subtitlesFromJson(subtitlesJson),
)

internal fun MediaItemInfo.toIndexedEntity(now: Long = System.currentTimeMillis()): IndexedMediaEntity =
    IndexedMediaEntity(
        id = id,
        name = name,
        uri = uri,
        path = path,
        size = size,
        modifiedTime = modifiedTime,
        mimeType = mimeType,
        duration = duration,
        width = width,
        height = height,
        thumbnailUri = thumbnailUri,
        subtitlesJson = subtitlesToJson(subtitleCandidates),
        indexedAt = now,
    )

private fun subtitlesToJson(items: List<SubtitleCandidate>): String {
    if (items.isEmpty()) return "[]"
    val array = JSONArray()
    items.forEach { candidate ->
        array.put(
            JSONObject().apply {
                put("name", candidate.name)
                put("uri", candidate.uri)
                candidate.language?.let { put("language", it) }
                candidate.mimeType?.let { put("mimeType", it) }
            },
        )
    }
    return array.toString()
}

private fun subtitlesFromJson(text: String): List<SubtitleCandidate> {
    if (text.isBlank() || text == "[]") return emptyList()
    return runCatching {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SubtitleCandidate(
                name = item.optString("name"),
                uri = item.optString("uri"),
                language = item.optString("language").takeIf { it.isNotBlank() },
                mimeType = item.optString("mimeType").takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())
}
