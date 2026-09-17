package com.quransanctuary.app.ui.strings

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NavStrings(val reciters: String = "", val surahs: String = "", val atmosphere: String = "", val nowPlaying: String = "")

@Serializable
data class HeroStrings(
    val titleA: String = "", val titleB: String = "", val subtitle: String = "",
    val cta: String = "", val secondary: String = "", val anfalAyah: String = "",
    val anfalCite: String = "", val scroll: String = ""
)

@Serializable
data class RecitersStrings(
    val title: String = "", val subtitle: String = "", val subtitleCount: String = "",
    val listen: String = "", val select: String = ""
)

@Serializable
data class SurahsStrings(
    val title: String = "", val subtitle: String = "", val searchPlaceholder: String = "",
    val ayahs: String = "", val duration: String = "", val play: String = "",
    val noneFound: String = "", val clear: String = "", val meccan: String = "", val medinan: String = ""
)

@Serializable
data class AtmosphereStrings(
    val title: String = "", val subtitle: String = "", val nature: String = "",
    val cozy: String = "", val focus: String = "", val on: String = "",
    val off: String = "", val preview: String = ""
)

@Serializable
data class DisplayModeStrings(val transliteration: String = "", val arabic: String = "", val both: String = "")

@Serializable
data class RecitationStrings(
    val title: String = "", val verse: String = "", val show: String = "", val hide: String = "",
    val loading: String = "", val unavailable: String = "",
    val translationLoading: String = "", val translationUnavailable: String = "",
    val synced: String = "", val tapVerse: String = "", val bismillahHint: String = "",
    val displayModes: DisplayModeStrings = DisplayModeStrings()
)

@Serializable
data class PlayerStrings(
    val quran: String = "", val ambient: String = "", val on: String = "", val off: String = "",
    val play: String = "", val pause: String = "", val previous: String = "", val next: String = "",
    val quranVolume: String = "", val ambientVolume: String = "", val repeat: String = "",
    val autoNext: String = "", val resume: String = "", val loading: String = "",
    val none: String = "", val seek: String = "", val loopAmbient: String = "", val dismiss: String = ""
)

@Serializable
data class RepeatsStrings(val off: String = "", val one: String = "", val all: String = "")

@Serializable
data class RepeatStatusStrings(val off: String = "", val one: String = "", val all: String = "")

@Serializable
data class SettingsStrings(val title: String = "")

@Serializable
data class ErrorsStrings(val audio: String = "", val ambient: String = "", val video: String = "")

@Serializable
data class FooterStrings(val madeWith: String = "", val tagline: String = "")

/** Website-parity localized strings (az / en / tr / ar — tr & ar fall back to en, exactly like the website). */
@Serializable
data class AppStrings(
    val brand: String = "",
    val brandSub: String = "",
    val nav: NavStrings = NavStrings(),
    val hero: HeroStrings = HeroStrings(),
    val reciters: RecitersStrings = RecitersStrings(),
    val surahs: SurahsStrings = SurahsStrings(),
    val atmosphere: AtmosphereStrings = AtmosphereStrings(),
    val recitation: RecitationStrings = RecitationStrings(),
    val player: PlayerStrings = PlayerStrings(),
    val repeats: RepeatsStrings = RepeatsStrings(),
    val repeatStatus: RepeatStatusStrings = RepeatStatusStrings(),
    val settings: SettingsStrings = SettingsStrings(),
    val errors: ErrorsStrings = ErrorsStrings(),
    val footer: FooterStrings = FooterStrings(),
    val developedBy: String = ""
) {
    fun subtitleCount(count: Int): String =
        reciters.subtitleCount.replace("{count}", count.toString())
}

val LocalStrings = compositionLocalOf { AppStrings() }

class StringsLoader(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = mutableMapOf<String, AppStrings>()

    fun load(locale: String): AppStrings = synchronized(cache) {
        cache.getOrPut(locale) {
            val file = "data/strings-$locale.json"
            try {
                appContext.assets.open(file).use {
                    json.decodeFromString(AppStrings.serializer(), it.readBytes().toString(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                AppStrings()
            }
        }
    }
}
