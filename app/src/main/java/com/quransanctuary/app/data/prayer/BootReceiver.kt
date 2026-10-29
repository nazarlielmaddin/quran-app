package com.quransanctuary.app.data.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Rebuilds the alarm schedule whenever the clock, timezone, app or device
 * restarts — the four events that invalidate absolute alarm timestamps.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> PrayerNotificationScheduler.rescheduleAll(context)
        }
    }
}
