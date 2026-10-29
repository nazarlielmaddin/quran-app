package com.quransanctuary.app.data.prayer

import android.content.Context
import com.quransanctuary.app.domain.Prayer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONObject

/**
 * Persists the exact times that the Home screen shows for a given day.
 *
 * Reminder alarms must be derived from the same values the user sees. Reading
 * a cached snapshot (instead of recalculating) keeps boot-time and midnight
 * refreshes aligned with the Aladhan/MWL schedule, and remembers which city
 * the timetable belongs to.
 */
object PrayerScheduleCache {

    private const val PREFS = "prayer_schedule"
    private const val KEY_DAY = "day_"
    private const val KEY_LAT = "location_lat"
    private const val KEY_LON = "location_lon"
    private const val KEY_ZONE = "location_zone"

    private const val COORD_TOLERANCE = 0.05

    fun save(
        context: Context,
        date: LocalDate,
        day: PrayerDay,
        zone: ZoneId,
        latitude: Double,
        longitude: Double
    ) {
        val root = JSONObject().apply {
            put("zone", zone.id)
            put("source", day.source.name)
            put("lat", latitude)
            put("lon", longitude)
            put("tomorrowFajr", day.tomorrowFajr?.toSecondOfDay() ?: -1)
            put("times", JSONObject().apply {
                day.times.forEach { (prayer, time) ->
                    put(prayer.name, time.toSecondOfDay())
                }
            })
        }
        prefs(context).edit()
            .putString(KEY_DAY + date.toEpochDay(), root.toString())
            .putString(KEY_LAT, latitude.toString())
            .putString(KEY_LON, longitude.toString())
            .putString(KEY_ZONE, zone.id)
            .apply()
    }

    fun load(
        context: Context,
        date: LocalDate,
        zone: ZoneId,
        latitude: Double,
        longitude: Double
    ): PrayerDay? {
        val raw = prefs(context).getString(KEY_DAY + date.toEpochDay(), null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("zone") != zone.id) return null
        if (kotlin.math.abs(root.optDouble("lat", 999.0) - latitude) > COORD_TOLERANCE) {
            return null
        }
        if (kotlin.math.abs(root.optDouble("lon", 999.0) - longitude) > COORD_TOLERANCE) {
            return null
        }

        val timesJson = root.optJSONObject("times") ?: return null
        val times = LinkedHashMap<Prayer, LocalTime>()
        for (prayer in Prayer.values()) {
            if (!timesJson.has(prayer.name)) continue
            val seconds = timesJson.optLong(prayer.name, -1L)
            if (seconds in 0..86_399) times[prayer] = LocalTime.ofSecondOfDay(seconds)
        }
        if (times.size < 6) return null

        val fajrSeconds = root.optLong("tomorrowFajr", -1L)
        val tomorrowFajr = if (fajrSeconds in 0..86_399) {
            LocalTime.ofSecondOfDay(fajrSeconds)
        } else {
            null
        }
        val source = runCatching {
            PrayerSource.valueOf(root.optString("source", PrayerSource.CALCULATED.name))
        }.getOrDefault(PrayerSource.CALCULATED)
        return PrayerDay(times, source, tomorrowFajr)
    }

    /** Last location used by the Home screen, so boot/midnight refreshes use
     *  the selected city instead of a stale device fix or the built-in default. */
    fun lastLocation(context: Context): Triple<Double, Double, ZoneId>? {
        val p = prefs(context)
        val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        val zone = p.getString(KEY_ZONE, null)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: return null
        return Triple(lat, lon, zone)
    }

    /** Drop entries older than [keepFrom] so the file cannot grow forever. */
    fun prune(context: Context, keepFrom: LocalDate) {
        val p = prefs(context)
        val editor = p.edit()
        val oldest = keepFrom.toEpochDay()
        p.all.keys
            .filter { it.startsWith(KEY_DAY) }
            .filter { key ->
                val day = key.removePrefix(KEY_DAY).toLongOrNull() ?: return@filter false
                day < oldest
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
