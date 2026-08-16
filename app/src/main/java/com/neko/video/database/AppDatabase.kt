package com.neko.video.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        FavoriteEntity::class,
        WebDavServerEntity::class,
        RecentUrlEntity::class,
        RemoteServerEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun webDavServerDao(): WebDavServerDao
    abstract fun recentUrlDao(): RecentUrlDao
    abstract fun remoteServerDao(): RemoteServerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "neko_video.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
