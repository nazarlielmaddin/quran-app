package com.quransanctuary.app.ui.strings

import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.data.prayer.PrayerSource
import com.quransanctuary.app.domain.Prayer
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * New Home / Reciter-selection copy. Kept in Kotlin instead of the legacy
 * JSON files so the redesign is self-contained and cannot corrupt the
 * website-parity string assets.
 */
data class RedesignStrings(
    val locale: AppLocale,
    val overline: String,
    val greeting: String,
    val greetingSub: String,
    val prayerTitle: String,
    val prayerNext: String,
    val prayerLoading: String,
    val prayerUnavailable: String,
    val prayerSourceApi: String,
    val prayerSourceCalc: String,
    val prayerScheduleTitle: String,
    val prayerCityPicker: String,
    val prayerUseLocation: String,
    val prayerLocationDenied: String,
    val prayerLocationFailed: String,
    val prayerDeviceLocation: String,
    val prayerChangeCity: String,
    val prayerClose: String,
    val prayerTomorrow: String,
    val prayerNow: String,
    val prayerTahajjud: String,
    val prayerTahajjudHint: String,
    val continueOverline: String,
    val firstListenOverline: String,
    val firstListenTitle: String,
    val firstListenText: String,
    val recentTitle: String,
    val recentEmptyTitle: String,
    val recentEmptyText: String,
    val favoritesTitle: String,
    val favoritesEmptyTitle: String,
    val favoritesEmptyText: String,
    val chooseReciter: String,
    val seeAll: String,
    val allSurahsTitle: String,
    val allSurahsText: String,
    val recitersTitle: String,
    val recitersSubtitle: String,
    val searchReciters: String,
    val filterAll: String,
    val filterFavorites: String,
    val selectedLabel: String,
    val noResultsTitle: String,
    val noResultsText: String,
    val noFavoritesTitle: String,
    val noFavoritesText: String,
    val clearFilters: String,
    val surahsScreenTitle: String,
    val surahsScreenSubtitle: String,
    val currentReciter: String,
    val listen: String,
    val bio: String,
    val verseLabel: String,
    val minutesShort: String,
    val hoursShort: String,
    val retry: String,
    val locationUnavailable: String,
    val prayerNames: Map<Prayer, String>,
    val exactAlarmNotice: String,
    val exactAlarmAction: String
) {
    fun prayerName(prayer: Prayer): String = prayerNames[prayer] ?: prayer.name

    fun sourceLabel(source: PrayerSource): String = when (source) {
        PrayerSource.API -> prayerSourceApi
        PrayerSource.CALCULATED -> prayerSourceCalc
    }

    fun remaining(hours: Long, minutes: Long): String = when {
        hours > 0 -> "$hours $hoursShort $minutes $minutesShort"
        minutes > 0 -> "$minutes $minutesShort"
        else -> prayerNow
    }

    fun dateLabel(date: LocalDate): String = when (locale) {
        AppLocale.AZ -> {
            val month = azMonths[date.monthValue - 1]
            val day = azWeekdays[date.dayOfWeek.value - 1]
            "${date.dayOfMonth} $month, $day"
        }
        AppLocale.EN -> date.format(
            DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.ENGLISH)
        )
        AppLocale.TR -> date.format(
            DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("tr"))
        )
        AppLocale.AR -> date.format(
            DateTimeFormatter.ofPattern("d MMMM، EEEE", Locale.forLanguageTag("ar"))
        )
    }

    /** Umm al-Qura Hijri date, shown under the Gregorian date on Home. */
    fun hijriDateLabel(date: LocalDate): String = runCatching {
        val hijri = HijrahDate.from(date)
        val monthIndex = hijri.get(ChronoField.MONTH_OF_YEAR) - 1
        val month = (hijriMonths[locale] ?: hijriMonths.getValue(AppLocale.AZ))[monthIndex]
        val prefix = when (locale) {
            AppLocale.AZ, AppLocale.TR -> "Hicri"
            AppLocale.EN -> "Hijri"
            AppLocale.AR -> "هـ"
        }
        "$prefix: ${hijri.get(ChronoField.DAY_OF_MONTH)} $month ${hijri.get(ChronoField.YEAR)}"
    }.getOrDefault("")
}

private val azMonths = arrayOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr"
)

private val azWeekdays = arrayOf(
    "bazar ertəsi", "çərşənbə axşamı", "çərşənbə",
    "cümə axşamı", "cümə", "şənbə", "bazar"
)

private val hijriMonths: Map<AppLocale, Array<String>> = mapOf(
    AppLocale.AZ to arrayOf(
        "Məhərrəm", "Səfər", "Rəbiül-əvvəl", "Rəbiül-axir",
        "Cəmadiyəl-əvvəl", "Cəmadiyəl-axir", "Rəcəb", "Şaban",
        "Ramazan", "Şəvval", "Zilqədə", "Zilhiccə"
    ),
    AppLocale.EN to arrayOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    ),
    AppLocale.TR to arrayOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir",
        "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban",
        "Ramazan", "Şevval", "Zilkade", "Zilhicce"
    ),
    AppLocale.AR to arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
)

fun redesignStrings(locale: AppLocale): RedesignStrings = when (locale) {
    AppLocale.AZ -> RedesignStrings(
        locale = locale,
        overline = "AZANIFY",
        greeting = "Əssalamu Aleykum!",
        greetingSub = "Sakitlikdə oxu, dinlə və düşün.",
        prayerTitle = "Namaz vaxtları",
        prayerNext = "Növbəti namaz",
        prayerLoading = "Vaxtlar hesablanır…",
        prayerUnavailable = "Namaz vaxtları hazırda əlçatmazdır.",
        prayerSourceApi = "Mənbə: Aladhan · MWL",
        prayerSourceCalc = "Mənbə: astronomik hesablama · MWL",
        prayerScheduleTitle = "Bugünün namaz vaxtları",
        prayerCityPicker = "Şəhər seçin",
        prayerUseLocation = "Cihazın konumunu istifadə et",
        prayerLocationDenied = "Konum icazəsi verilmədi. Şəhəri əl ilə seçin.",
        prayerLocationFailed = "Konum tapılmadı. Şəhəri əl ilə seçin.",
        prayerDeviceLocation = "Cihaz konumu",
        prayerChangeCity = "Şəhəri dəyiş",
        prayerClose = "Bağla",
        prayerTomorrow = "sabah",
        prayerNow = "indi",
        prayerTahajjud = "Təhəccüd",
        prayerTahajjudHint = "Gecənin son üçdə biri",
        continueOverline = "DAVAM ET",
        firstListenOverline = "İLK DİNLƏMƏ",
        firstListenTitle = "Quranla səyahətə başlayın",
        firstListenText = "Seçilmiş hafizdən ilk surəni dinləyin.",
        recentTitle = "Son dinlədiklərim",
        recentEmptyTitle = "Tarixçə hələ boşdur",
        recentEmptyText = "Dinlədiyiniz surələr burada görünəcək.",
        favoritesTitle = "Seçilmiş hafizlər",
        favoritesEmptyTitle = "Seçilmiş hafiz yoxdur",
        favoritesEmptyText = "Bəyəndiyiniz hafizləri ürək ilə işarələyin.",
        chooseReciter = "Hafiz seç",
        seeAll = "Hamısına bax",
        allSurahsTitle = "Bütün surələr",
        allSurahsText = "114 surə · oxu və dinlə",
        recitersTitle = "Hafiz seçimi",
        recitersSubtitle = "Səsini seçin, Quranı öz ritminizdə dinləyin.",
        searchReciters = "Hafiz axtarın…",
        filterAll = "Hamısı",
        filterFavorites = "Seçilmişlər",
        selectedLabel = "Seçildi",
        noResultsTitle = "Hafiz tapılmadı",
        noResultsText = "Axtarışı dəyişdirin və ya filtri sıfırlayın.",
        noFavoritesTitle = "Seçilmiş hafiz yoxdur",
        noFavoritesText = "Bəyəndiyiniz hafizin ürək işarəsinə toxunun.",
        clearFilters = "Filtri sıfırla",
        surahsScreenTitle = "Surələr",
        surahsScreenSubtitle = "Bir surə seçin və oxuyun və ya dinləyin.",
        currentReciter = "Hafiz",
        listen = "Dinlə",
        bio = "Bio",
        verseLabel = "Ayə",
        minutesShort = "dəq",
        hoursShort = "saat",
        retry = "Yenidən cəhd et",
        locationUnavailable = "Konum əlçatmazdır",
        exactAlarmNotice = "Bildirişlər tam 15 dəqiqə əvvəl gəlsin deyə dəqiq alarm icazəsi lazımdır.",
        exactAlarmAction = "İcazə ver",
        prayerNames = mapOf(
            Prayer.FAJR to "Sübh",
            Prayer.SUNRISE to "Günəş",
            Prayer.DHUHR to "Zöhr",
            Prayer.ASR to "Əsr",
            Prayer.MAGHRIB to "Məğrib",
            Prayer.ISHA to "İşa"
        )
    )

    AppLocale.EN -> RedesignStrings(
        locale = locale,
        overline = "AZANIFY",
        greeting = "Assalamu Alaikum!",
        greetingSub = "Read, listen and reflect in stillness.",
        prayerTitle = "Prayer times",
        prayerNext = "Next prayer",
        prayerLoading = "Calculating times…",
        prayerUnavailable = "Prayer times are unavailable.",
        prayerSourceApi = "Source: Aladhan · MWL",
        prayerSourceCalc = "Source: astronomical calculation · MWL",
        prayerScheduleTitle = "Today's prayer times",
        prayerCityPicker = "Choose a city",
        prayerUseLocation = "Use device location",
        prayerLocationDenied = "Location permission denied. Choose a city manually.",
        prayerLocationFailed = "Location not found. Choose a city manually.",
        prayerDeviceLocation = "Device location",
        prayerChangeCity = "Change city",
        prayerClose = "Close",
        prayerTomorrow = "tomorrow",
        prayerNow = "now",
        prayerTahajjud = "Tahajjud",
        prayerTahajjudHint = "Last third of the night",
        continueOverline = "CONTINUE",
        firstListenOverline = "FIRST LISTEN",
        firstListenTitle = "Begin your Quran journey",
        firstListenText = "Start with a surah from your selected reciter.",
        recentTitle = "Recently played",
        recentEmptyTitle = "No listening history yet",
        recentEmptyText = "Surahs you listen to will appear here.",
        favoritesTitle = "Favorite reciters",
        favoritesEmptyTitle = "No favorite reciters",
        favoritesEmptyText = "Tap the heart on a reciter you love.",
        chooseReciter = "Choose reciter",
        seeAll = "See all",
        allSurahsTitle = "All surahs",
        allSurahsText = "114 surahs · read and listen",
        recitersTitle = "Choose a reciter",
        recitersSubtitle = "Pick a voice and listen at your own pace.",
        searchReciters = "Search reciters…",
        filterAll = "All",
        filterFavorites = "Favorites",
        selectedLabel = "Selected",
        noResultsTitle = "No reciters found",
        noResultsText = "Try a different search or clear the filters.",
        noFavoritesTitle = "No favorite reciters yet",
        noFavoritesText = "Tap the heart on a reciter to save them here.",
        clearFilters = "Clear filters",
        surahsScreenTitle = "Surahs",
        surahsScreenSubtitle = "Choose a surah to read or listen.",
        currentReciter = "Reciter",
        listen = "Listen",
        bio = "Bio",
        verseLabel = "Verse",
        minutesShort = "min",
        hoursShort = "h",
        retry = "Retry",
        locationUnavailable = "Location unavailable",
        exactAlarmNotice = "Allow exact alarms so reminders arrive a full 15 minutes before prayer.",
        exactAlarmAction = "Allow",
        prayerNames = mapOf(
            Prayer.FAJR to "Fajr",
            Prayer.SUNRISE to "Sunrise",
            Prayer.DHUHR to "Dhuhr",
            Prayer.ASR to "Asr",
            Prayer.MAGHRIB to "Maghrib",
            Prayer.ISHA to "Isha"
        )
    )

    AppLocale.TR -> RedesignStrings(
        locale = locale,
        overline = "AZANIFY",
        greeting = "Selamun Aleyküm!",
        greetingSub = "Sükûnette oku, dinle ve düşün.",
        prayerTitle = "Namaz vakitleri",
        prayerNext = "Sonraki namaz",
        prayerLoading = "Vakitler hesaplanıyor…",
        prayerUnavailable = "Namaz vakitleri şu anda kullanılamıyor.",
        prayerSourceApi = "Kaynak: Aladhan · MWL",
        prayerSourceCalc = "Kaynak: astronomik hesaplama · MWL",
        prayerScheduleTitle = "Bugünün namaz vakitleri",
        prayerCityPicker = "Şehir seçin",
        prayerUseLocation = "Cihaz konumunu kullan",
        prayerLocationDenied = "Konum izni verilmedi. Şehri elle seçin.",
        prayerLocationFailed = "Konum bulunamadı. Şehri elle seçin.",
        prayerDeviceLocation = "Cihaz konumu",
        prayerChangeCity = "Şehri değiştir",
        prayerClose = "Kapat",
        prayerTomorrow = "yarın",
        prayerNow = "şimdi",
        prayerTahajjud = "Teheccüd",
        prayerTahajjudHint = "Gecenin son üçte biri",
        continueOverline = "DEVAM ET",
        firstListenOverline = "İLK DİNLEME",
        firstListenTitle = "Kur'an yolculuğuna başlayın",
        firstListenText = "Seçtiğiniz kariden ilk sureyi dinleyin.",
        recentTitle = "Son dinlediklerim",
        recentEmptyTitle = "Dinleme geçmişi henüz boş",
        recentEmptyText = "Dinlediğiniz sureler burada görünecek.",
        favoritesTitle = "Favori okuyucular",
        favoritesEmptyTitle = "Favori okuyucu yok",
        favoritesEmptyText = "Sevdiğiniz okuyucunun kalbine dokunun.",
        chooseReciter = "Okuyucu seç",
        seeAll = "Tümünü gör",
        allSurahsTitle = "Tüm sureler",
        allSurahsText = "114 sure · oku ve dinle",
        recitersTitle = "Kari seçimi",
        recitersSubtitle = "Sesinizi seçin, Kur'an'ı kendi ritminizde dinleyin.",
        searchReciters = "Kari ara…",
        filterAll = "Tümü",
        filterFavorites = "Favoriler",
        selectedLabel = "Seçildi",
        noResultsTitle = "Kari bulunamadı",
        noResultsText = "Aramayı değiştirin veya filtreleri temizleyin.",
        noFavoritesTitle = "Henüz favori kari yok",
        noFavoritesText = "Bir karini favorilere eklemek için kalbe dokunun.",
        clearFilters = "Filtreleri temizle",
        surahsScreenTitle = "Sureler",
        surahsScreenSubtitle = "Okumak veya dinlemek için bir sure seçin.",
        currentReciter = "Kari",
        listen = "Dinle",
        bio = "Biyografi",
        verseLabel = "Ayet",
        minutesShort = "dk",
        hoursShort = "sa",
        retry = "Yeniden dene",
        locationUnavailable = "Konum kullanılamıyor",
        exactAlarmNotice = "Hatırlatmalar tam 15 dakika önce gelsin diye kesin alarm izni gerekli.",
        exactAlarmAction = "İzin ver",
        prayerNames = mapOf(
            Prayer.FAJR to "İmsak",
            Prayer.SUNRISE to "Güneş",
            Prayer.DHUHR to "Öğle",
            Prayer.ASR to "İkindi",
            Prayer.MAGHRIB to "Akşam",
            Prayer.ISHA to "Yatsı"
        )
    )

    AppLocale.AR -> RedesignStrings(
        locale = locale,
        overline = "AZANIFY",
        greeting = "السلام عليكم!",
        greetingSub = "اقرأ واستمع وتأمّل في سكينة.",
        prayerTitle = "أوقات الصلاة",
        prayerNext = "الصلاة القادمة",
        prayerLoading = "جارٍ حساب الأوقات…",
        prayerUnavailable = "أوقات الصلاة غير متاحة الآن.",
        prayerSourceApi = "المصدر: Aladhan · MWL",
        prayerSourceCalc = "المصدر: حساب فلكي · MWL",
        prayerScheduleTitle = "أوقات صلاة اليوم",
        prayerCityPicker = "اختر مدينة",
        prayerUseLocation = "استخدم موقع الجهاز",
        prayerLocationDenied = "تم رفض إذن الموقع. اختر مدينة يدويًا.",
        prayerLocationFailed = "لم يتم العثور على الموقع. اختر مدينة يدويًا.",
        prayerDeviceLocation = "موقع الجهاز",
        prayerChangeCity = "تغيير المدينة",
        prayerClose = "إغلاق",
        prayerTomorrow = "غدًا",
        prayerNow = "الآن",
        prayerTahajjud = "التهجد",
        prayerTahajjudHint = "الثلث الأخير من الليل",
        continueOverline = "متابعة",
        firstListenOverline = "أول استماع",
        firstListenTitle = "ابدأ رحلتك مع القرآن",
        firstListenText = "استمع إلى أول سورة بصوت القارئ المختار.",
        recentTitle = "استمعت مؤخرًا",
        recentEmptyTitle = "سجل الاستماع فارغ",
        recentEmptyText = "ستظهر السور التي تستمع إليها هنا.",
        favoritesTitle = "القراء المفضلون",
        favoritesEmptyTitle = "لا يوجد قراء مفضلون",
        favoritesEmptyText = "اضغط على القلب بجانب القارئ الذي تحبه.",
        chooseReciter = "اختر قارئًا",
        seeAll = "عرض الكل",
        allSurahsTitle = "جميع السور",
        allSurahsText = "١١٤ سورة · اقرأ واستمع",
        recitersTitle = "اختيار القارئ",
        recitersSubtitle = "اختر الصوت واستمع بالوتيرة التي تناسبك.",
        searchReciters = "ابحث عن قارئ…",
        filterAll = "الكل",
        filterFavorites = "المفضلة",
        selectedLabel = "محدد",
        noResultsTitle = "لم يتم العثور على قارئ",
        noResultsText = "جرّب بحثًا آخر أو امسح عوامل التصفية.",
        noFavoritesTitle = "لا يوجد قراء مفضلون بعد",
        noFavoritesText = "اضغط على القلب لحفظ القارئ هنا.",
        clearFilters = "مسح التصفية",
        surahsScreenTitle = "السور",
        surahsScreenSubtitle = "اختر سورة للقراءة أو الاستماع.",
        currentReciter = "القارئ",
        listen = "استمع",
        bio = "السيرة",
        verseLabel = "آية",
        minutesShort = "د",
        hoursShort = "س",
        retry = "إعادة المحاولة",
        locationUnavailable = "الموقع غير متاح",
        exactAlarmNotice = "اسمح بالمنبهات الدقيقة ليصل التذكير قبل الصلاة بـ ١٥ دقيقة تمامًا.",
        exactAlarmAction = "السماح",
        prayerNames = mapOf(
            Prayer.FAJR to "الفجر",
            Prayer.SUNRISE to "الشروق",
            Prayer.DHUHR to "الظهر",
            Prayer.ASR to "العصر",
            Prayer.MAGHRIB to "المغرب",
            Prayer.ISHA to "العشاء"
        )
    )
}
