package com.azanify.shared

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
 * Shared (Android + iOS) prayer-time core.
 *
 * This is a 1:1 port of the Android `domain/PrayerTimes.kt` engine from
 * Azanify 1.21.1. Pure Kotlin, zero dependencies, so it compiles for
 * Kotlin/Native without a single third-party artifact.
 *
 * Angles: Fajr 18 deg, Isha 17 deg, Asr shadow factor 1 (Shafi).
 * Unknown / polar cases degrade to conservative intervals; the result is
 * always complete. No network, no location invention.
 */

/** A single prayer row, already reduced to wall-clock hour/minute. */
data class PrayerMoment(
    val key: String,
    val hour: Int,
    val minute: Int
) {
    val minutesFromMidnight: Int get() = hour * 60 + minute
    val display: String
        get() = "${if (hour < 10) "0$hour" else "$hour"}:${if (minute < 10) "0$minute" else "$minute"}"
}

/** Selectable location. Coordinates are city centres from public geographic data. */
data class PrayerCity(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val utcOffsetHours: Double
)

object PrayerKeys {
    const val FAJR = "fajr"
    const val SUNRISE = "sunrise"
    const val DHUHR = "dhuhr"
    const val ASR = "asr"
    const val MAGHRIB = "maghrib"
    const val ISHA = "isha"

    /** The five obligatory prayers, in display order (sunrise excluded). */
    val daily: List<String> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
}

private const val DEG = PI / 180.0

private fun fixAngle(angle: Double): Double {
    val a = angle % 360.0
    return if (a < 0) a + 360.0 else a
}

private fun fixHour(hour: Double): Double {
    val h = hour % 24.0
    return if (h < 0) h + 24.0 else h
}

private fun toMoment(key: String, hours: Double): PrayerMoment {
    val total = (((hours * 60.0).roundToInt() % 1440) + 1440) % 1440
    return PrayerMoment(key, total / 60, total % 60)
}

/**
 * Entry point consumed by Swift: `PrayerCatalog().calculate(...)`.
 * Kept as a class (not free functions) so the Objective-C surface stays tiny.
 */
class PrayerCatalog {

    fun cities(): List<PrayerCity> = PrayerCities

    /**
     * @param epochDay days since 1970-01-01 of the *local* calendar date.
     * @param utcOffsetHours device offset at local noon, e.g. +4.0 for Baku.
     */
    fun calculate(
        epochDay: Long,
        latitude: Double,
        longitude: Double,
        utcOffsetHours: Double
    ): List<PrayerMoment> {
        val lat = latitude.coerceIn(-89.9, 89.9)
        val lng = longitude.coerceIn(-180.0, 180.0)

        // Julian day at 00:00 UT + solar position.
        val jd = epochDay.toDouble() + 2440587.5
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

        val dhuhr = 12.0 + utcOffsetHours - lng / 15.0 - equation

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

        return listOf(
            toMoment(PrayerKeys.FAJR, fajr),
            toMoment(PrayerKeys.SUNRISE, sunriseHours),
            toMoment(PrayerKeys.DHUHR, dhuhr),
            toMoment(PrayerKeys.ASR, asr),
            toMoment(PrayerKeys.MAGHRIB, maghribHours),
            toMoment(PrayerKeys.ISHA, isha)
        )
    }

    /** First obligatory prayer strictly after [nowMinutes]; null when the day is over. */
    fun next(moments: List<PrayerMoment>, nowMinutes: Int): PrayerMoment? =
        PrayerKeys.daily.firstNotNullOfOrNull { key ->
            moments.firstOrNull { it.key == key && it.minutesFromMidnight > nowMinutes }
        }

    /** Whole days since 1970-01-01 for a Gregorian y/m/d (proleptic after 1582-10-15). */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + a / 4
        val jdn = (365.25 * (y + 4716)).toLong() +
            (30.6001 * (m + 1)).toLong() +
            day + b - 1524
        return jdn - 2440588L
    }
}

val PrayerCities: List<PrayerCity> = listOf(
    PrayerCity("baku", "Bakı", "Baku", 40.4093, 49.8671, 4.0),
    PrayerCity("ganja", "Gəncə", "Ganja", 40.6828, 46.3606, 4.0),
    PrayerCity("sumqayit", "Sumqayıt", "Sumqayit", 40.5897, 49.6686, 4.0),
    PrayerCity("mingachevir", "Mingəçevir", "Mingachevir", 40.7639, 47.0594, 4.0),
    PrayerCity("nakhchivan", "Naxçıvan", "Nakhchivan", 39.2089, 45.4122, 4.0),
    PrayerCity("shaki", "Şəki", "Shaki", 41.1919, 47.1706, 4.0),
    PrayerCity("lankaran", "Lənkəran", "Lankaran", 38.7543, 48.8506, 4.0),
    PrayerCity("quba", "Quba", "Quba", 41.3611, 48.5139, 4.0),
    PrayerCity("shamakhi", "Şamaxı", "Shamakhi", 40.6314, 48.6414, 4.0),
    PrayerCity("shirvan", "Şirvan", "Shirvan", 39.9378, 48.9290, 4.0),
    PrayerCity("khachmaz", "Xaçmaz", "Khachmaz", 41.4644, 48.8056, 4.0),
    PrayerCity("yevlakh", "Yevlax", "Yevlakh", 40.6172, 47.1500, 4.0),
    PrayerCity("zaqatala", "Zaqatala", "Zaqatala", 41.6316, 46.6444, 4.0),
    PrayerCity("qazakh", "Qazax", "Qazakh", 41.0933, 45.3661, 4.0),
    PrayerCity("tovuz", "Tovuz", "Tovuz", 40.9922, 45.6289, 4.0),
    PrayerCity("salyan", "Salyan", "Salyan", 39.5964, 48.9847, 4.0),
    PrayerCity("bilasuvar", "Biləsuvar", "Bilasuvar", 39.4583, 48.5450, 4.0),
    PrayerCity("jalilabad", "Cəlilabad", "Jalilabad", 39.2096, 48.4919, 4.0)
)
