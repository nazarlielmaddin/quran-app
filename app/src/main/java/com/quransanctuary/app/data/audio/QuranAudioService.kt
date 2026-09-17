package com.quransanctuary.app.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the MediaSession for background playback + notification + headset
 * and lock-screen controls.
 *
 * Two past crashes fixed here:
 * 1) ForegroundServiceDidNotStartInTimeException — the service promotes
 *    itself to foreground INSTANTLY (onCreate/onStartCommand), before any
 *    heavy init, so the ~5s system deadline can never fire while ExoPlayer
 *    buffers on a slow network.
 * 2) Stuck "Preparing recitation" notification — Media3's own notification
 *    is pinned to the same stable id (onUpdateNotification), AND this
 *    service mirrors real track metadata (surah · reciter · play state)
 *    with working prev/play/next buttons, so the shade and lock screen
 *    always show correct info with controls, on every OEM skin.
 */
class QuranAudioService : MediaSessionService() {

    private var session: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Pin Media3's rich media notification to our stable id so the loading
        // placeholder morphs into full lock-screen/shade controls instead of
        // lingering (or duplicating) beside them.
        runCatching {
            val base = DefaultMediaNotificationProvider(this)
            setMediaNotificationProvider(object : MediaNotification.Provider {
                override fun createNotification(
                    session: MediaSession,
                    customLayout: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    callback: MediaNotification.Provider.Callback
                ): MediaNotification {
                    return try {
                        val built = base.createNotification(
                            session, customLayout, actionFactory, callback
                        )
                        MediaNotification(MEDIA_NOTIFICATION_ID, built.notification)
                    } catch (_: Exception) {
                        MediaNotification(MEDIA_NOTIFICATION_ID, buildNotification(null))
                    }
                }

                override fun handleCustomCommand(
                    session: MediaSession,
                    action: String,
                    extras: Bundle
                ): Boolean {
                    return try {
                        base.handleCustomCommand(session, action, extras)
                    } catch (_: Exception) {
                        false
                    }
                }
            })
        }
        // 1) Beat the ~5s FGS timeout FIRST (fast, no I/O beyond NotificationManager).
        runCatching { promoteToForeground(null) }
        // 2) ...then do the heavy work (ExoPlayer + SimpleCache disk I/O).
        runCatching {
            val app = application as com.quransanctuary.app.QuranApp
            app.container.audioEngine.initialize()
            val player = runCatching { app.container.audioEngine.quranPlayer }.getOrNull()
            if (player != null) {
                runCatching { session?.release() }
                // Tapping the notification / lock-screen artwork reopens the app.
                val openApp = PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.quransanctuary.app.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Route EVERY external transport command (notification buttons,
                // lock-screen, headset, Android Auto) through the engine's surah
                // logic. The raw ExoPlayer owns a single media item, so its own
                // next/previous would silently do nothing — this wrapper makes
                // prev/next switch surahs like the in-app buttons do.
                val engine = app.container.audioEngine
                val navPlayer = object : ForwardingPlayer(player) {
                    override fun seekToNext() {
                        runCatching { engine.nextSurah() }
                    }
                    override fun seekToPrevious() {
                        runCatching { engine.prevSurah() }
                    }
                    override fun seekToNextMediaItem() {
                        runCatching { engine.nextSurah() }
                    }
                    override fun seekToPreviousMediaItem() {
                        runCatching { engine.prevSurah() }
                    }
                    override fun hasNextMediaItem(): Boolean = true
                    override fun hasPreviousMediaItem(): Boolean = true
                    override fun getAvailableCommands(): Player.Commands {
                        return try {
                            super.getAvailableCommands().buildUpon()
                                .add(Player.COMMAND_SEEK_TO_NEXT)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .build()
                        } catch (_: Exception) {
                            super.getAvailableCommands()
                        }
                    }
                }
                session = MediaSession.Builder(this, navPlayer)
                    .setSessionActivity(openApp)
                    .build()
            }
        }
        // 3) Mirror real metadata into the notification (throttled to real
        //    changes — position ticks must not rebuild it 3x per second).
        runCatching { observePlayback() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                runCatching {
                    (application as com.quransanctuary.app.QuranApp).container.audioEngine.toggleQuran()
                }
                runCatching { refreshNow() }
                return START_STICKY
            }
            ACTION_NEXT -> {
                runCatching {
                    (application as com.quransanctuary.app.QuranApp).container.audioEngine.nextSurah()
                }
                runCatching { refreshNow() }
                return START_STICKY
            }
            ACTION_PREV -> {
                runCatching {
                    (application as com.quransanctuary.app.QuranApp).container.audioEngine.prevSurah()
                }
                runCatching { refreshNow() }
                return START_STICKY
            }
        }
        // Every startService() call resets the FGS deadline — re-promote instantly.
        runCatching { promoteToForeground(null) }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (_: Exception) {
            START_NOT_STICKY
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val playing = runCatching {
            val app = application as com.quransanctuary.app.QuranApp
            val engine = app.container.audioEngine
            engine.quranPlayer.isPlaying || engine.ambientPlayer.isPlaying
        }.getOrDefault(false)
        if (!playing) stopSelf()
    }

    override fun onDestroy() {
        runCatching { serviceScope.cancel() }
        // Release ONLY the session. The players belong to AudioEngine (app scope);
        // releasing them here left the engine holding dead ExoPlayer instances and
        // crashed the next play after a service restart.
        runCatching { session?.release() }
        session = null
        super.onDestroy()
    }

    // ---------- metadata mirror ----------

    private data class NotifData(
        val title: String,
        val subtitle: String,
        val playing: Boolean,
        val avatarAsset: String?
    )

    private fun loadingWord(localeId: String): String = when (localeId) {
        "az" -> "Yüklənir…"
        "tr" -> "Yükleniyor…"
        "ar" -> "جارٍ التحميل…"
        else -> "Loading…"
    }

    private fun observePlayback() {
        val app = application as com.quransanctuary.app.QuranApp
        val engine = app.container.audioEngine
        val repo = app.container.repository
        val settings = app.container.settings
        serviceScope.launch {
            try {
                combine(engine.state, settings.locale) { s, loc ->
                    val surah = runCatching { repo.getSurah(s.surahId) }.getOrNull()
                    val reciter = runCatching { repo.getReciter(s.reciterId) }.getOrNull()
                    val title = if (surah != null) "${surah.number}. ${surah.transliteratedName}"
                    else "Quran Sanctuary"
                    val sub = listOfNotNull(
                        reciter?.localizedName(loc.id),
                        if (s.quranLoading && !s.quranPlaying) loadingWord(loc.id) else null
                    ).joinToString(" · ").ifEmpty { "Quran Sanctuary" }
                    NotifData(title, sub, s.quranPlaying, reciter?.let { "reciters/${it.assetFileName()}" })
                }.distinctUntilChanged().collect { data ->
                    // Ownership rule: while Media3 owns active playback it posts
                    // its own rich notification — our mirror must not clobber it
                    // (same id, last writer wins). We only fill the gaps:
                    // starting / buffering / paused states and pre-session.
                    val media3OwnsIt = session != null && data.playing
                    if (!media3OwnsIt) runCatching { promoteToForeground(data) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshNow() {
        val app = runCatching { application as com.quransanctuary.app.QuranApp }.getOrNull() ?: return
        val engine = app.container.audioEngine
        val repo = app.container.repository
        val s = runCatching { engine.state.value }.getOrNull() ?: return
        val surah = runCatching { repo.getSurah(s.surahId) }.getOrNull()
        val reciter = runCatching { repo.getReciter(s.reciterId) }.getOrNull()
        val title = if (surah != null) "${surah.number}. ${surah.transliteratedName}" else "Quran Sanctuary"
        val sub = listOfNotNull(reciter?.localizedName("az")).joinToString(" · ").ifEmpty { "Quran Sanctuary" }
        runCatching {
            promoteToForeground(
                NotifData(title, sub, s.quranPlaying, reciter?.let { "reciters/${it.assetFileName()}" })
            )
        }
    }

    // ---------- foreground promotion ----------

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, QuranAudioService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAvatar(asset: String?): Bitmap? {
        if (asset == null) return null
        return try {
            assets.open(asset).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNotification(data: NotifData?): Notification {
        // Spotify-grade shade & lock-screen: MediaStyle + session token gives
        // artwork, always-visible transport buttons and system integration.
        val token = runCatching { session?.sessionCompatToken }.getOrNull()
        val manager = getSystemService(NotificationManager::class.java)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && manager != null) {
                val existing = runCatching { manager.getNotificationChannel(CHANNEL_ID) }.getOrNull()
                if (existing == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Quran playback",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply { setShowBadge(false) }
                    )
                }
            }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(data?.title ?: "Quran Sanctuary")
            .setContentText(data?.subtitle ?: "Preparing recitation…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, com.quransanctuary.app.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                actionIntent(ACTION_PREV, 11)
            )
            .addAction(
                if (data?.playing == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (data?.playing == true) "Pause" else "Play",
                actionIntent(ACTION_TOGGLE, 12)
            )
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                actionIntent(ACTION_NEXT, 13)
            )
            .apply {
                runCatching {
                    if (token != null) {
                        setStyle(
                            androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(token)
                                .setShowActionsInCompactView(0, 1, 2)
                        )
                    }
                }
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    }
                }
            }
        runCatching {
            val art = loadAvatar(data?.avatarAsset)
            if (art != null) builder.setLargeIcon(art)
        }
        return builder.build()
    }

    private fun promoteToForeground(data: NotifData?) {
        val notification = buildNotification(data)
        // Stable id shared with Media3 (see onUpdateNotification): one single
        // notification that morphs from loading → full media controls.
        try {
            ServiceCompat.startForeground(
                this,
                MEDIA_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (_: Exception) {
            // Last resort for exotic OEMs: try without the type, then give up
            // quietly (stopSelf) instead of crashing the whole app.
            try {
                @Suppress("DEPRECATION")
                startForeground(MEDIA_NOTIFICATION_ID, notification)
            } catch (_: Exception) {
                runCatching { stopSelf() }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "quran_playback"
        private const val MEDIA_NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.quransanctuary.app.audio.TOGGLE"
        private const val ACTION_NEXT = "com.quransanctuary.app.audio.NEXT"
        private const val ACTION_PREV = "com.quransanctuary.app.audio.PREV"
    }
}
