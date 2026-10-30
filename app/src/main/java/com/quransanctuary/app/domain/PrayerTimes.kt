package com.quransanctuary.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Astronomical prayer-time calculation (PrayTimes-style, no network required).
 *
 * The five daily prayers are derived from the sun's declination and the
 * equation of time for the requested date. This is the offline fallback for
 * [com.quransanctuary.app.data.prayer.PrayerTimesRepository]; the repository
 * prefers the Aladhan (Muslim World League) response when the device is online.
 *
 * Angles: Fajr 18°, Isha 17°, Asr shadow factor 1 (Shafi). Never invents a
 * location: callers must supply valid coordinates from the device or a city.
 */
enum class Prayer(val order: Int) {
    FAJR(0),
    SUNRISE(1),
    DHUHR(2),
    ASR(3),
    MAGHRIB(4),
    ISHA(5);

    companion object {
        /** The five obligatory prayers, in display order (sunrise excluded). */
        val daily: List<Prayer> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}

data class NextPrayer(
    val prayer: Prayer,
    val time: LocalTime,
    val tomorrow: Boolean
)

private const val DEG = PI / 180.0

private fun fixAngle(angle: Double): Double {
    val a = angle % 360.0
    return if (a < 0) a + 360.0 else a
}

private fun fixHour(hour: Double): Double {
    val h = hour % 24.0
    return if (h < 0) h + 24.0 else h
}

private fun hoursToLocalTime(hours: Double): LocalTime {
    val total = ((hours * 60.0).roundToInt() % 1440 + 1440) % 1440
    return LocalTime.of(total / 60, total % 60)
}

/**
 * Returns the five prayer times (plus sunrise) in the [zone]'s local clock.
 * Unknown / polar cases degrade gracefully to conservative intervals; the
 * result is always complete.
 */
fun calculatePrayerTimes(
    date: LocalDate,
    latitude: Double,
    longitude: Double,
    zone: ZoneId
): Map<Prayer, LocalTime> {
    val lat = latitude.coerceIn(-89.9, 89.9)
    val lng = longitude.coerceIn(-180.0, 180.0)

    // Julian day at 00:00 UT + solar position.
    val jd = date.toEpochDay() + 2440587.5
    val d = jd - 2451545.0
    val g = fixAngle(357.529 + 0.98560028 * d)
    val q = fixAngle(280.459 + 0.98564736 * d)
    val l = fixAngle(q + 1.915 * sin(g * DEG) + 0.020 * sin(2 * g * DEG))
    val e = 23.439 - 0.00000036 * d
    val declination = asin(sin(e * DEG) * sin(l * DEG)) / DEG
    val rightAscension = fixHour(
        atan2(cos(e * DEG) * sin(l * DEG), cos(l * DEG)) / DEG / 15.0
    )
    var equation = q / 15.0 - rightAscension
    if (equation > 12.0) equation -= 24.0
    if (equation < -12.0) equation += 24.0

    val offsetHours = date.atTime(12, 0).atZone(zone).offset.totalSeconds / 3600.0
    val dhuhr = 12.0 + offsetHours - lng / 15.0 - equation

    fun hourAngle(altitude: Double): Double? {
        val cosH = (sin(altitude * DEG) - sin(lat * DEG) * sin(declination * DEG)) /
            (cos(lat * DEG) * cos(declination * DEG))
        if (cosH.isNaN() || cosH < -1.0 || cosH > 1.0) return null
        return acos(cosH) / DEG / 15.0
    }

    val sunriseH = hourAngle(-0.833)
    val fajrH = hourAngle(-18.0)
    val ishaH = hourAngle(-17.0)

    // Asr: shadow length equals object height (Shafi / standard).
    val asrAltitude = atan(1.0 / (1.0 + tan(abs(lat - declination) * DEG))) / DEG
    val asrH = hourAngle(asrAltitude)

    val sunrise = sunriseH?.let { dhuhr - it }
    val sunset = sunriseH?.let { dhuhr + it }
    val maghribHours = sunset ?: (dhuhr + 6.0)
    val sunriseHours = sunrise ?: (dhuhr - 6.0)

    val fajr = fajrH?.let { dhuhr - it } ?: (sunriseHours - 1.5)
    val isha = ishaH?.let { dhuhr + it } ?: (maghribHours + 1.5)
    val asr = asrH?.let { dhuhr + it } ?: (dhuhr + 3.0)

    return mapOf(
        Prayer.FAJR to hoursToLocalTime(fajr),
        Prayer.SUNRISE to hoursToLocalTime(sunriseHours),
        Prayer.DHUHR to hoursToLocalTime(dhuhr),
        Prayer.ASR to hoursToLocalTime(asr),
        Prayer.MAGHRIB to hoursToLocalTime(maghribHours),
        Prayer.ISHA to hoursToLocalTime(isha)
    )
}

/** First prayer strictly after [now]; falls back to tomorrow's Fajr. */
fun nextPrayer(
    times: Map<Prayer, LocalTime>,
    now: LocalTime,
    tomorrowFajr: LocalTime? = null
): NextPrayer? {
    for (prayer in Prayer.daily) {
        val time = times[prayer] ?: continue
        if (time.isAfter(now)) return NextPrayer(prayer, time, tomorrow = false)
    }
    val fajr = tomorrowFajr ?: return null
    return NextPrayer(Prayer.FAJR, fajr, tomorrow = true)
}
