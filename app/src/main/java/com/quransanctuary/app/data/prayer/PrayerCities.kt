package com.quransanctuary.app.data.prayer

/**
 * A real, manually selectable location. Coordinates are city centres from
 * public geographic data; the default is Baku for the Azerbaijani audience.
 * No location is ever fabricated: the UI shows the selected city by name.
 */
data class PrayerCity(
    val id: String,
    val nameAz: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String = "Asia/Baku"
) {
    fun localizedName(locale: String): String = when (locale) {
        "az" -> nameAz
        else -> nameEn
    }
}

val PrayerCities: List<PrayerCity> = listOf(
    PrayerCity("baku", "Bakı", "Baku", 40.4093, 49.8671),
    PrayerCity("ganja", "Gəncə", "Ganja", 40.6828, 46.3606),
    PrayerCity("sumqayit", "Sumqayıt", "Sumqayit", 40.5897, 49.6686),
    PrayerCity("mingachevir", "Mingəçevir", "Mingachevir", 40.7639, 47.0594),
    PrayerCity("nakhchivan", "Naxçıvan", "Nakhchivan", 39.2089, 45.4122),
    PrayerCity("shaki", "Şəki", "Shaki", 41.1919, 47.1706),
    PrayerCity("lankaran", "Lənkəran", "Lankaran", 38.7543, 48.8506),
    PrayerCity("quba", "Quba", "Quba", 41.3611, 48.5139),
    PrayerCity("shamakhi", "Şamaxı", "Shamakhi", 40.6314, 48.6414),
    PrayerCity("shirvan", "Şirvan", "Shirvan", 39.9378, 48.9290),
    PrayerCity("khachmaz", "Xaçmaz", "Khachmaz", 41.4644, 48.8056),
    PrayerCity("yevlakh", "Yevlax", "Yevlakh", 40.6172, 47.1500),
    PrayerCity("zaqatala", "Zaqatala", "Zaqatala", 41.6316, 46.6444),
    PrayerCity("qazakh", "Qazax", "Qazakh", 41.0933, 45.3661),
    PrayerCity("tovuz", "Tovuz", "Tovuz", 40.9922, 45.6289),
    PrayerCity("salyan", "Salyan", "Salyan", 39.5964, 48.9847),
    PrayerCity("bilasuvar", "Biləsuvar", "Bilasuvar", 39.4583, 48.5450),
    PrayerCity("jalilabad", "Cəlilabad", "Jalilabad", 39.2096, 48.4919)
)

val DefaultPrayerCity: PrayerCity = PrayerCities.first()

fun prayerCityById(id: String): PrayerCity =
    PrayerCities.firstOrNull { it.id == id } ?: DefaultPrayerCity
