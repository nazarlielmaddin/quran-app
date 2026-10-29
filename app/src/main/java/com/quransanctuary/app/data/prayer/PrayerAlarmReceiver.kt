package com.quransanctuary.app.data.prayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.quransanctuary.app.MainActivity
import com.quransanctuary.app.R
import com.quransanctuary.app.data.azan.AzanPreferences
import com.quransanctuary.app.data.azan.AzanService
import com.quransanctuary.app.domain.Prayer

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PrayerNotificationScheduler.ACTION_REFRESH -> {
                PrayerNotificationScheduler.rescheduleAll(context)
            }
            PrayerNotificationScheduler.ACTION_DISMISS -> {
                val notifId = intent.getIntExtra(PrayerNotificationScheduler.EXTRA_NOTIF_ID, 0)
                if (notifId != 0) cancel(context, notifId)
            }
            PrayerNotificationScheduler.ACTION_AZAN -> {
                if (!AzanPreferences.isEnabled(context)) return
                val sound = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_SOUND)
                    ?: AzanPreferences.SOUND_FAJR
                val prayerKey = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_PRAYER)
                if (!AzanService.prayerAllowed(context, prayerKey)) return
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        AzanService.playIntent(context, sound, prayerKey)
                    )
                }
            }
            PrayerNotificationScheduler.ACTION_REMINDER -> {
                val prayerName = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_PRAYER)
                    ?: return
                val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
                val notifId = intent.getIntExtra(PrayerNotificationScheduler.EXTRA_NOTIF_ID, 0)
                val prayerAt = intent.getLongExtra(PrayerNotificationScheduler.EXTRA_PRAYER_AT, 0L)
                showReminder(context, prayer, notifId, prayerAt)
            }
        }
    }

    private fun showReminder(
        context: Context,
        prayer: Prayer,
        notifId: Int,
        prayerAt: Long
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // The notification must vanish the moment the prayer time starts.
        // If the alarm was delayed past that point, do not post it at all.
        val remaining = if (prayerAt > 0L) {
            prayerAt - System.currentTimeMillis()
        } else {
            15 * 60 * 1000L
        }
        if (remaining <= 0L) {
            cancel(context, notifId)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Namaz xatırlatmaları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Namaz vaxtından 15 dəqiqə əvvəl xatırlatma"
                enableVibration(true)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val title = "${PrayerNotificationScheduler.prayerName(prayer)} namazına 15 dəqiqə qaldı"
        val text = "Vaxtın çıxmasına 15 dəq"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Counts down to the prayer time itself, not from the moment the
            // alarm happened to be delivered.
            .setTimeoutAfter(remaining)
            .setAutoCancel(true)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            val contentIntent = PendingIntent.getActivity(
                context,
                notifId,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(contentIntent)
        }

        manager.notify(notifId, builder.build())
    }

    private fun cancel(context: Context, notifId: Int) {
        runCatching {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notifId)
        }
    }

    companion object {
        const val CHANNEL_ID = "prayer_reminders"
    }
}
