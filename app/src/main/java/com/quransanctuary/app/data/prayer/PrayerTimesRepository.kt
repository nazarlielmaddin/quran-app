package com.quransanctuary.app.data.prayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.quransanctuary.app.domain.Prayer
import com.quransanctuary.app.domain.calculatePrayerTimes
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PrayerSource { API, CALCULATED }

data class PrayerDay(
    val times: Map<Prayer, LocalTime>,
    val source: PrayerSource,
    /** Tomorrow's Fajr, used for the after-Isha countdown. */
    val tomorrowFajr: LocalTime?
) {
    /**
     * Start of the last third of the night (Isha → Fajr) — the recommended
     * window for Tahajjud. Null when the night cannot be determined.
     */
    val tahajjud: LocalTime?
        get() {
            val isha = times[Prayer.ISHA] ?: return null
            val fajr = tomorrowFajr ?: return null
            val night = (fajr.toSecondOfDay() - isha.toSecondOfDay() + 86_400) % 86_400
            if (night <= 0) return null
            val start = (isha.toSecondOfDay() + night * 2L / 3L) % 86_400L
            return LocalTime.ofSecondOfDay(start)
        }
}

/**
 * Prayer times provider.
 *
 * 1. Emits a local astronomical calculation immediately (works fully offline).
 * 2. If the device is online, refreshes with Aladhan's Muslim World League timings
 *    for the same coordinates and emits the authoritative values.
 *
 * The repository never invents a location: coordinates come from the selected
 * city or from Android's last known location (with explicit permission).
 */
class PrayerTimesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, PrayerDay>()

    fun observe(
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        date: LocalDate
    ): Flow<PrayerDay> = flow {
        val key = cacheKey(latitude, longitude, zone, date)
        val cached = cache[key]
        if (cached != null) {
            emit(cached)
            return@flow
        }

        val local = localDay(latitude, longitude, zone, date)
        emit(local)

        val remote = runCatching { fetchRemote(latitude, longitude, zone, date) }.getOrNull()
        if (remote != null) {
            cache[key] = remote
            emit(remote)
        } else {
            // Cache the offline result for the rest of the day so a dead
            // network never blocks the Home screen on every recomposition.
            cache[key] = local
        }
    }.flowOn(Dispatchers.IO)

    /**
     * One-shot authoritative fetch used to pre-schedule tomorrow's reminders
     * with the same Aladhan/MWL values the Home screen will display.
     * Returns null when the device is offline; callers fall back to the local
     * calculation.
     */
    suspend fun fetchDay(
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        date: LocalDate
    ): PrayerDay? = withContext(Dispatchers.IO) {
        val key = cacheKey(latitude, longitude, zone, date)
        cache[key]?.takeIf { it.source == PrayerSource.API }
            ?: runCatching { fetchRemote(latitude, longitude, zone, date) }
                .getOrNull()
                ?.also { cache[key] = it }
    }

    private fun localDay(
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        date: LocalDate
    ): PrayerDay {
        val times = calculatePrayerTimes(date, latitude, longitude, zone)
        val tomorrowFajr = calculatePrayerTimes(
            date.plusDays(1), latitude, longitude, zone
        )[Prayer.FAJR]
        return PrayerDay(times, PrayerSource.CALCULATED, tomorrowFajr)
    }

    private fun fetchRemote(
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        date: LocalDate
    ): PrayerDay? {
        val datePath = "%02d-%02d-%04d".format(
            date.dayOfMonth, date.monthValue, date.year
        )
        val timezone = URLEncoder.encode(zone.id, "UTF-8")
        val url = URL(
            "https://api.aladhan.com/v1/timings/$datePath" +
                "?latitude=$latitude&longitude=$longitude" +
                "&method=3&timezonestring=$timezone"
        )
        var connection: HttpURLConnection? = null
        return try {
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4_500
            connection.readTimeout = 4_500
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "QuranSanctuary/1.0 (Android)")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val timings = root["data"]?.jsonObject?.get("timings")?.jsonObject ?: return null

            fun read(name: String): LocalTime? {
                val raw = timings[name]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
                val clock = raw.substringBefore(' ').substringBefore('(').trim()
                return runCatching { LocalTime.parse(clock) }.getOrNull()
            }

            val times = mutableMapOf<Prayer, LocalTime>()
            read("Fajr")?.let { times[Prayer.FAJR] = it }
            read("Sunrise")?.let { times[Prayer.SUNRISE] = it }
            read("Dhuhr")?.let { times[Prayer.DHUHR] = it }
            read("Asr")?.let { times[Prayer.ASR] = it }
            read("Maghrib")?.let { times[Prayer.MAGHRIB] = it }
            read("Isha")?.let { times[Prayer.ISHA] = it }
            if (times.size < 6) return null

            val tomorrowFajr = calculatePrayerTimes(
                date.plusDays(1), latitude, longitude, zone
            )[Prayer.FAJR]
            PrayerDay(times, PrayerSource.API, tomorrowFajr)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun cacheKey(
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        date: LocalDate
    ): String = "${(latitude * 1000).roundToInt()}:" +
        "${(longitude * 1000).roundToInt()}:${zone.id}:$date"
}

/** Last known device location — only used after the user grants permission. */
object DeviceLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val best = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
        return best?.let { it.latitude to it.longitude }
    }
}
