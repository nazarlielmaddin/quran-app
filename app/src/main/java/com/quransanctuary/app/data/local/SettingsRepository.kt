package com.quransanctuary.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.data.model.DisplayMode
import com.quransanctuary.app.data.model.RepeatMode
import com.quransanctuary.app.data.model.ThemeMode
import com.quransanctuary.app.data.prayer.DefaultPrayerCity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "quran_settings")

/** Persists player + UI preferences. Mirrors the website's localStorage keys. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val LOCALE = stringPreferencesKey("quran:locale:v1")
        val THEME = stringPreferencesKey("theme")
        val RECITER = stringPreferencesKey("reciterId")
        val SURAH = intPreferencesKey("surahId")
        val AMBIENT = stringPreferencesKey("soundId")
        val QURAN_VOLUME = floatPreferencesKey("quranVolume")
        val AMBIENT_VOLUME = floatPreferencesKey("ambientVolume")
        val REPEAT = stringPreferencesKey("repeat")
        val AUTO_NEXT = booleanPreferencesKey("autoNext")
        val LOOP_AMBIENT = booleanPreferencesKey("loopAmbient")
        val QURAN_ENABLED = booleanPreferencesKey("quranEnabled")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambientEnabled")
        val DISPLAY_MODE = stringPreferencesKey("displayMode")
        val ARABIC_FONT_SCALE = floatPreferencesKey("arabicFontScale")
        val SLEEP_MINUTES = intPreferencesKey("sleepMinutes")
        val FAVORITE_RECITERS = stringSetPreferencesKey("favoriteReciters")
        val PRAYER_CITY = stringPreferencesKey("prayerCityId")
        val PRAYER_LABEL = stringPreferencesKey("prayerCityLabel")
        val PRAYER_LAT = stringPreferencesKey("prayerLatitude")
        val PRAYER_LON = stringPreferencesKey("prayerLongitude")
        val PRAYER_ZONE = stringPreferencesKey("prayerTimezone")
    }

    val locale: Flow<AppLocale> = context.settingsStore.data.map { AppLocale.from(it[Keys.LOCALE]) }
    val theme: Flow<ThemeMode> = context.settingsStore.data.map { ThemeMode.from(it[Keys.THEME]) }
    val reciterId: Flow<String> = context.settingsStore.data.map { it[Keys.RECITER] ?: "yasir-al-dawsari" }
    val surahId: Flow<Int> = context.settingsStore.data.map { it[Keys.SURAH] ?: 1 }
    val ambientId: Flow<String?> = context.settingsStore.data.map { it[Keys.AMBIENT] }
    val quranVolume: Flow<Float> = context.settingsStore.data.map { it[Keys.QURAN_VOLUME] ?: 0.9f }
    val ambientVolume: Flow<Float> = context.settingsStore.data.map { it[Keys.AMBIENT_VOLUME] ?: 0.6f }
    val repeat: Flow<RepeatMode> = context.settingsStore.data.map { RepeatMode.from(it[Keys.REPEAT]) }
    val autoNext: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AUTO_NEXT] ?: true }
    val loopAmbient: Flow<Boolean> = context.settingsStore.data.map { it[Keys.LOOP_AMBIENT] ?: true }
    val quranEnabled: Flow<Boolean> = context.settingsStore.data.map { it[Keys.QURAN_ENABLED] ?: true }
    val ambientEnabled: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AMBIENT] != null }
    val displayMode: Flow<DisplayMode> = context.settingsStore.data.map { DisplayMode.from(it[Keys.DISPLAY_MODE]) }
    val arabicFontScale: Flow<Float> = context.settingsStore.data.map { it[Keys.ARABIC_FONT_SCALE] ?: 1f }

    /** Reciters explicitly saved by the user (Home "Seçilmiş hafizlər"). */
    val favoriteReciters: Flow<Set<String>> =
        context.settingsStore.data.map { it[Keys.FAVORITE_RECITERS] ?: emptySet() }

    val prayerCityId: Flow<String> =
        context.settingsStore.data.map { it[Keys.PRAYER_CITY] ?: DefaultPrayerCity.id }
    val prayerCityLabel: Flow<String> =
        context.settingsStore.data.map { it[Keys.PRAYER_LABEL] ?: DefaultPrayerCity.nameAz }
    val prayerLatitude: Flow<Double> = context.settingsStore.data.map {
        it[Keys.PRAYER_LAT]?.toDoubleOrNull() ?: DefaultPrayerCity.latitude
    }
    val prayerLongitude: Flow<Double> = context.settingsStore.data.map {
        it[Keys.PRAYER_LON]?.toDoubleOrNull() ?: DefaultPrayerCity.longitude
    }
    val prayerZoneId: Flow<String> =
        context.settingsStore.data.map { it[Keys.PRAYER_ZONE] ?: DefaultPrayerCity.zoneId }

    suspend fun setLocale(v: AppLocale) = edit { it[Keys.LOCALE] = v.id }
    suspend fun setTheme(v: ThemeMode) = edit { it[Keys.THEME] = v.id }
    suspend fun setReciter(v: String) = edit { it[Keys.RECITER] = v }
    suspend fun setSurah(v: Int) = edit { it[Keys.SURAH] = v }
    suspend fun setAmbient(v: String?) = edit { if (v == null) it.remove(Keys.AMBIENT) else it[Keys.AMBIENT] = v }
    suspend fun setQuranVolume(v: Float) = edit { it[Keys.QURAN_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setAmbientVolume(v: Float) = edit { it[Keys.AMBIENT_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setRepeat(v: RepeatMode) = edit { it[Keys.REPEAT] = v.id }
    suspend fun setAutoNext(v: Boolean) = edit { it[Keys.AUTO_NEXT] = v }
    suspend fun setLoopAmbient(v: Boolean) = edit { it[Keys.LOOP_AMBIENT] = v }
    suspend fun setQuranEnabled(v: Boolean) = edit { it[Keys.QURAN_ENABLED] = v }
    suspend fun setDisplayMode(v: DisplayMode) = edit { it[Keys.DISPLAY_MODE] = v.id }
    suspend fun setArabicFontScale(v: Float) = edit { it[Keys.ARABIC_FONT_SCALE] = v.coerceIn(0.8f, 1.6f) }

    suspend fun toggleFavoriteReciter(id: String) = edit { prefs ->
        val current = prefs[Keys.FAVORITE_RECITERS] ?: emptySet()
        prefs[Keys.FAVORITE_RECITERS] =
            if (id in current) current - id else current + id
    }

    /** Persist a manual city or a device-location fix ("gps"). */
    suspend fun setPrayerLocation(
        id: String,
        label: String,
        latitude: Double,
        longitude: Double,
        zoneId: String
    ) = edit { prefs ->
        prefs[Keys.PRAYER_CITY] = id
        prefs[Keys.PRAYER_LABEL] = label
        prefs[Keys.PRAYER_LAT] = latitude.toString()
        prefs[Keys.PRAYER_LON] = longitude.toString()
        prefs[Keys.PRAYER_ZONE] = zoneId
    }

    fun progressKey(surah: Int) = longPreferencesKey("progress_ms_$surah")
    fun progressAyahKey(surah: Int) = intPreferencesKey("progress_ayah_$surah")

    suspend fun saveProgress(surah: Int, positionMs: Long, ayah: Int) {
        edit {
            it[progressKey(surah)] = positionMs
            it[progressAyahKey(surah)] = ayah
        }
    }

    suspend fun loadProgress(surah: Int): Pair<Long, Int> {
        val snap = context.settingsStore.data.first()
        return (snap[progressKey(surah)] ?: 0L) to (snap[progressAyahKey(surah)] ?: 0)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
