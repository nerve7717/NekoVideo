package com.neko.video

import android.content.Context
import com.neko.video.database.AppDatabase
import com.neko.video.repository.MediaRepository
import com.neko.video.settings.AppSettings
import com.neko.video.util.SecureStorage

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.get(appContext)
    val secureStorage: SecureStorage = SecureStorage.get(appContext)
    val settings: AppSettings = AppSettings(appContext)
    val mediaRepository: MediaRepository = MediaRepository(appContext, database, secureStorage)
}
