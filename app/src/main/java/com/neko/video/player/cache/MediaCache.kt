package com.neko.video.player.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCache {
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L // 512 MB

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val dir = File(context.cacheDir, "media3_cache")
        val simpleCache = SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
        cache = simpleCache
        return simpleCache
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching {
            get(context).keys.forEach { key ->
                get(context).removeResource(key)
            }
        }
    }
}
