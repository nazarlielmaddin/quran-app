package com.quransanctuary.app.di

import android.content.Context
import androidx.room.Room
import com.quransanctuary.app.data.audio.AudioEngine
import com.quransanctuary.app.data.download.SurahDownloader
import com.quransanctuary.app.data.local.AssetLoader
import com.quransanctuary.app.data.local.LibraryDatabase
import com.quransanctuary.app.data.local.QuranRepository
import com.quransanctuary.app.data.local.SettingsRepository
import com.quransanctuary.app.data.prayer.PrayerTimesRepository
import com.quransanctuary.app.data.share.ShareVideoGenerator
import com.quransanctuary.app.ui.components.AmbientVideoHolder
import com.quransanctuary.app.ui.strings.StringsLoader

/** Manual dependency graph (no codegen DI — keeps the build hermetic and fast). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val assetLoader = AssetLoader(appContext)
    val stringsLoader = StringsLoader(appContext)
    val settings = SettingsRepository(appContext)
    val database: LibraryDatabase = Room.databaseBuilder(
        appContext,
        LibraryDatabase::class.java,
        "quran-library.db"
    ).build()
    val repository = QuranRepository(assetLoader)
    val prayerTimes = PrayerTimesRepository(appContext)
    val downloader = SurahDownloader(appContext, database)
    val audioEngine = AudioEngine(appContext, repository, settings, database)
    val shareVideo = ShareVideoGenerator(appContext, repository)
    // Shared ambient-video player: one instance for all screens, so navigation
    // never restarts the clip (see AmbientVideoHolder).
    val ambientVideo = AmbientVideoHolder(appContext)
}
