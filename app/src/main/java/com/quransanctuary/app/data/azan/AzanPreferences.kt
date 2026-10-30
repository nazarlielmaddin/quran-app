package com.quransanctuary.app.data.azan

import android.content.Context
import com.quransanctuary.app.R
import com.quransanctuary.app.domain.Prayer

/**
 * Azan playback preferences. Stored in SharedPreferences (not DataStore) so
 * alarm scheduling from a BroadcastReceiver can read them synchronously.
 */
object AzanPreferences {

    const val SOUND_FAJR = "fajr"
    const val SOUND_MAKKAH = "makkah"
    const val SOUND_MADINAH = "madinah"

    /** Tahajjud is not a [Prayer], so it gets its own key. */
    const val PRAYER_TAHAJJUD = "TAHAJJUD"

    private const val PREFS = "azan_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REGULAR = "regular_sound"
    private const val KEY_PRAYER_PREFIX = "prayer_"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Per-prayer master gates: the azan for a given prayer only plays when the
     * global switch AND this flag are on. Defaults to on so enabling azan is a
     * single tap and users then mute the times they don't want.
     */
    fun isPrayerEnabled(context: Context, prayer: Prayer): Boolean =
        prefs(context).getBoolean(KEY_PRAYER_PREFIX + prayer.name, true)

    fun setPrayerEnabled(context: Context, prayer: Prayer, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PRAYER_PREFIX + prayer.name, enabled)
            .apply()
    }

    fun isTahajjudEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRAYER_PREFIX + PRAYER_TAHAJJUD, true)

    fun setTahajjudEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PRAYER_PREFIX + PRAYER_TAHAJJUD, enabled)
            .apply()
    }

    /** Makkah or Madinah — used for Dhuhr, Asr, Maghrib and Isha. */
    fun regularSound(context: Context): String {
        val saved = prefs(context).getString(KEY_REGULAR, SOUND_MAKKAH)
        return if (saved == SOUND_MADINAH) SOUND_MADINAH else SOUND_MAKKAH
    }

    fun setRegularSound(context: Context, sound: String) {
        val safe = if (sound == SOUND_MADINAH) SOUND_MADINAH else SOUND_MAKKAH
        prefs(context).edit().putString(KEY_REGULAR, safe).apply()
    }

    /** Tahajjud (null prayer) and Fajr always use the Fajr azan. */
    fun soundFor(context: Context, prayer: Prayer?): String =
        if (prayer == null || prayer == Prayer.FAJR) SOUND_FAJR else regularSound(context)

    fun resId(sound: String): Int = when (sound) {
        SOUND_MADINAH -> R.raw.azan_madinah
        SOUND_MAKKAH -> R.raw.azan_makkah
        else -> R.raw.azan_fajr
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
