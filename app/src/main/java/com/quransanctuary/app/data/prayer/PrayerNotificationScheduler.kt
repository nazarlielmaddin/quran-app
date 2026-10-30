package com.quransanctuary.app.data.prayer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quransanctuary.app.MainActivity
import com.quransanctuary.app.data.azan.AzanPreferences
import com.quransanctuary.app.domain.Prayer
import com.quransanctuary.app.domain.calculatePrayerTimes
import java.time.LocalDate
import java.time.ZoneId

object PrayerNotificationScheduler {

    const val ACTION_REMINDER = "com.quransanctuary.app.PRAYER_REMINDER"
    const val ACTION_REFRESH = "com.quransanctuary.app.PRAYER_REFRESH"
    const val ACTION_DISMISS = "com.quransanctuary.app.PRAYER_DISMISS"
    const val ACTION_AZAN = "com.quransanctuary.app.PRAYER_AZAN"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_DATE = "date"
    const val EXTRA_NOTIF_ID = "notifId"
    const val EXTRA_PRAYER_AT = "prayerAt"
    const val EXTRA_SOUND = "sound"

    private const val REMINDER_MINUTES = 15L
    private const val REFRESH_REQUEST_CODE = 999_999
    private const val DISMISS_REQUEST_OFFSET = 10_000_000
    private const val AZAN_REQUEST_OFFSET = 20_000_000

    fun prayerName(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "Sübh"
        Prayer.DHUHR -> "Zöhr"
        Prayer.ASR -> "Əsr"
        Prayer.MAGHRIB -> "Məğrib"
        Prayer.ISHA -> "İşa"
        else -> prayer.name
    }

    fun notificationId(prayer: Prayer, date: LocalDate): Int =
        prayer.ordinal * 1_000_000 + date.year * 1000 + date.dayOfYear

    /** Android 12+ gates exact alarms; without this the reminder can be hours late. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * Schedules (or refreshes) today's reminders from the exact times that the
     * UI shows. A reminder fires 15 minutes before the prayer, and a dismissal
     * alarm clears it the moment the prayer time starts — even if the device
     * was asleep and the reminder itself was delayed.
     */
    fun scheduleDay(
        context: Context,
        day: PrayerDay,
        date: LocalDate,
        zone: ZoneId,
        latitude: Double,
        longitude: Double
    ) {
        PrayerScheduleCache.save(context, date, day, zone, latitude, longitude)
        val now = System.currentTimeMillis()
        val azanEnabled = AzanPreferences.isEnabled(context)
        val regularAzan = AzanPreferences.regularSound(context)

        for (prayer in Prayer.daily) {
            val time = day.times[prayer] ?: continue
            val prayerAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            val triggerAt = prayerAt - REMINDER_MINUTES * 60_000L
            val notifId = notificationId(prayer, date)

            if (prayerAt <= now) {
                // The time has already started: a reminder must not survive it.
                cancelNotification(context, notifId)
                cancelAzan(context, notifId + AZAN_REQUEST_OFFSET)
                continue
            }

            if (triggerAt > now) {
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = ACTION_REMINDER
                    putExtra(EXTRA_PRAYER, prayer.name)
                    putExtra(EXTRA_DATE, date.toEpochDay())
                    putExtra(EXTRA_NOTIF_ID, notifId)
                    putExtra(EXTRA_PRAYER_AT, prayerAt)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    notifId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                scheduleAlarm(context, triggerAt, pi)
            }

            val dismissIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context,
                notifId + DISMISS_REQUEST_OFFSET,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleAlarm(context, prayerAt, dismissPi)

            val azanSound = if (prayer == Prayer.FAJR) {
                AzanPreferences.SOUND_FAJR
            } else {
                regularAzan
            }
            scheduleAzan(
                context = context,
                triggerAt = prayerAt,
                requestCode = notifId + AZAN_REQUEST_OFFSET,
                sound = azanSound,
                prayerKey = prayer.name,
                enabled = azanEnabled && AzanPreferences.isPrayerEnabled(context, prayer),
                now = now
            )
        }

        // Tahajjud starts in the last third of the night (after Isha, usually
        // after midnight) and uses the Fajr azan.
        val tahajjud = day.tahajjud
        val isha = day.times[Prayer.ISHA]
        if (tahajjud != null) {
            val tahajjudDate =
                if (isha != null && !tahajjud.isAfter(isha)) date.plusDays(1) else date
            val tahajjudAt = tahajjudDate.atTime(tahajjud)
                .atZone(zone).toInstant().toEpochMilli()
            scheduleAzan(
                context = context,
                triggerAt = tahajjudAt,
                requestCode = notificationId(Prayer.SUNRISE, date) + AZAN_REQUEST_OFFSET,
                sound = AzanPreferences.SOUND_FAJR,
                prayerKey = AzanPreferences.PRAYER_TAHAJJUD,
                enabled = azanEnabled && AzanPreferences.isTahajjudEnabled(context),
                now = now
            )
        }

        scheduleMidnightRefresh(context, date, zone)
        PrayerScheduleCache.prune(context, date.minusDays(2))
    }

    fun scheduleMidnightRefresh(context: Context, date: LocalDate, zone: ZoneId) {
        val triggerAt = date.plusDays(1).atTime(0, 5).atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(context, triggerAt, pi)
    }

    fun scheduleAlarm(context: Context, triggerAt: Long, pendingIntent: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            runCatching {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }.onFailure {
                // Permission revoked between the check and the call.
                runCatching {
                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            }
        } else {
            // Best effort until the user grants exact alarms from Home.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /**
     * Azan alarms use [AlarmManager.setAlarmClock] when possible: they wake the
     * device fully and grant the temporary background-start allowance that the
     * playback service needs on Android 12+.
     */
    private fun scheduleAzan(
        context: Context,
        triggerAt: Long,
        requestCode: Int,
        sound: String,
        prayerKey: String,
        enabled: Boolean,
        now: Long
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_AZAN
            putExtra(EXTRA_SOUND, sound)
            putExtra(EXTRA_PRAYER, prayerKey)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!enabled || triggerAt <= now) {
            cancelAlarm(context, pi)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (!exact) {
            scheduleAlarm(context, triggerAt, pi)
            return
        }

        runCatching {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
        }.onFailure {
            scheduleAlarm(context, triggerAt, pi)
        }
    }

    private fun cancelAzan(context: Context, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PrayerAlarmReceiver::class.java).apply { action = ACTION_AZAN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        cancelAlarm(context, pi)
    }

    private fun cancelAlarm(context: Context, pendingIntent: PendingIntent) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent)
        }
    }

    /**
     * Rebuilds alarms after a reboot, timezone change, app update or midnight
     * rollover. Uses the persisted UI schedule first so the reminder minute
     * never drifts from what the app displays.
     */
    fun rescheduleAll(context: Context) {
        val saved = PrayerScheduleCache.lastLocation(context)
        val zone = saved?.third ?: ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (latitude, longitude) = saved?.let { it.first to it.second }
            ?: DeviceLocation.lastKnown(context)
            ?: (DefaultPrayerCity.latitude to DefaultPrayerCity.longitude)

        val todayDay = PrayerScheduleCache.load(context, today, zone, latitude, longitude)
            ?: calculatedDay(today, latitude, longitude, zone)
        scheduleDay(context, todayDay, today, zone, latitude, longitude)

        val tomorrow = today.plusDays(1)
        val tomorrowDay =
            PrayerScheduleCache.load(context, tomorrow, zone, latitude, longitude)
                ?: calculatedDay(tomorrow, latitude, longitude, zone)
        scheduleDay(context, tomorrowDay, tomorrow, zone, latitude, longitude)
    }

    private fun calculatedDay(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId
    ): PrayerDay {
        val times = calculatePrayerTimes(date, latitude, longitude, zone)
        val tomorrowFajr = calculatePrayerTimes(
            date.plusDays(1), latitude, longitude, zone
        )[Prayer.FAJR]
        return PrayerDay(times, PrayerSource.CALCULATED, tomorrowFajr)
    }

    private fun cancelNotification(context: Context, notifId: Int) {
        runCatching {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notifId)
        }
    }
}
