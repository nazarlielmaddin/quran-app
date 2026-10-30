package com.quransanctuary.app.data.azan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.quransanctuary.app.MainActivity
import com.quransanctuary.app.R
import com.quransanctuary.app.domain.Prayer

/**
 * Plays the azan for the prayer that just started.
 *
 * The alarm fires an exact alarm; this foreground service keeps the process
 * alive for the duration of the recitation, uses the alarm audio stream and
 * stops itself on completion (or from the notification action).
 */
class AzanService : Service() {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stopPlayback()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        // startForegroundService requires a notification even when we bail out.
        startForegroundNow()

        if (!AzanPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerKey = intent?.getStringExtra(EXTRA_PRAYER)
        if (!prayerAllowed(this, prayerKey)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sound = intent?.getStringExtra(EXTRA_SOUND)
            ?: AzanPreferences.SOUND_FAJR
        play(sound)
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Azan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Azan oxunur"
                setSound(null, null)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AzanService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                this,
                2,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Azan oxunur")
            .setContentText("Namaz vaxtı başladı")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(0, "Dayandır", stopIntent)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun play(sound: String) {
        stopPlayback()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
                .also { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(attributes)
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            val afd = resources.openRawResourceFd(AzanPreferences.resId(sound))
                ?: error("raw azan resource not found")
            afd.use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                stopPlayback()
                stopSelf()
            }
            mp.setOnErrorListener { _, _, _ ->
                stopPlayback()
                stopSelf()
                true
            }
            mp.prepareAsync()
        }.onFailure {
            stopPlayback()
            stopSelf()
        }
    }

    private fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.quransanctuary.app.AZAN_PLAY"
        const val ACTION_STOP = "com.quransanctuary.app.AZAN_STOP"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_PRAYER = "prayer"

        private const val CHANNEL_ID = "azan_playback"
        private const val NOTIFICATION_ID = 4242

        /**
         * Guards against a stale alarm firing right after the user muted this
         * prayer: the alarm itself is cancelled, but the broadcast may already
         * be in flight.
         */
        fun prayerAllowed(context: Context, prayerKey: String?): Boolean {
            if (prayerKey == null) return true
            if (prayerKey == AzanPreferences.PRAYER_TAHAJJUD) {
                return AzanPreferences.isTahajjudEnabled(context)
            }
            val prayer = runCatching { Prayer.valueOf(prayerKey) }.getOrNull()
                ?: return true
            return AzanPreferences.isPrayerEnabled(context, prayer)
        }

        fun playIntent(
            context: Context,
            sound: String,
            prayerKey: String?
        ): Intent =
            Intent(context, AzanService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_SOUND, sound)
                if (prayerKey != null) putExtra(EXTRA_PRAYER, prayerKey)
            }
    }
}
