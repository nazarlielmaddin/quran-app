package com.quransanctuary.app.data.local

import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.data.model.Recitation
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.data.model.Surah
import com.quransanctuary.app.data.model.VerseTexts
import com.quransanctuary.app.domain.toAzerbaijaniReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Single source of truth for the Quran catalog — mirrors the website's data layer. */
class QuranRepository(private val assets: AssetLoader) {

    val surahs: List<Surah> by lazy { assets.loadSurahs() }
    val reciters: List<Reciter> by lazy { assets.loadReciters() }
    val ambients: List<AmbientSound> by lazy { assets.loadAmbients() }

    private val recitations: List<Recitation> by lazy { assets.loadRecitations() }
    private val recitationIndex: Map<Pair<String, Int>, Recitation> by lazy {
        recitations.associateBy { it.reciterId to it.surahId }
    }

    private val arabic: Map<Int, List<String>> by lazy { assets.loadArabic() }
    private val translationsAz: Map<Int, List<String>> by lazy { assets.loadTranslationsAz() }
    private val transliterations: Map<Int, List<String>> by lazy { assets.loadTransliterations() }

    private val timingsCache = mutableMapOf<String, Map<Int, List<Long>>?>()
    private val timingsLock = Any()

    fun getSurah(number: Int): Surah? = surahs.firstOrNull { it.number == number }
    fun getReciter(id: String): Reciter? = reciters.firstOrNull { it.id == id }
    fun getRecitation(reciterId: String, surahId: Int): Recitation? = recitationIndex[reciterId to surahId]
    fun getAmbient(id: String?): AmbientSound? = ambients.firstOrNull { it.id == id }

    fun recitationsForSurah(surahId: Int): Map<String, Recitation> =
        recitations.filter { it.surahId == surahId }.associateBy { it.reciterId }

    suspend fun getTimings(reciterId: String, surahId: Int): List<Long>? = withContext(Dispatchers.IO) {
        val all = synchronized(timingsLock) {
            timingsCache.getOrPut(reciterId) { assets.loadTimings(reciterId) }
        }
        all?.get(surahId)
    }

    suspend fun hasTimings(reciterId: String, surahId: Int): Boolean = getTimings(reciterId, surahId) != null

    /**
     * Verse texts with the CORRECT Azerbaijani reading: raw alquran.cloud
     * en.transliteration converted by the website-faithful toAzerbaijaniReading()
     * (exact port of nazarlielmaddin/quran toAzerbaijani).
     */
    suspend fun getVerses(surahId: Int): VerseTexts? = withContext(Dispatchers.Default) {
        try {
            val ar = arabic[surahId] ?: return@withContext null
            if (ar.isEmpty()) return@withContext null
            val tr = try {
                translationsAz[surahId] ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val raw = try {
                transliterations[surahId] ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val tl = try {
                raw.map {
                    try {
                        toAzerbaijaniReading(it)
                    } catch (_: Exception) {
                        it
                    }
                }
            } catch (_: Exception) {
                raw
            }
            VerseTexts(surahId, ar, tr, tl)
        } catch (_: Exception) {
            null
        }
    }

    /** Website search logic: matches English + transliterated + Azerbaijani names (case-insensitive) or number. */
    fun searchSurahs(query: String): List<Surah> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return surahs
        return surahs.filter { s ->
            "${s.englishName} ${s.transliteratedName} ${s.azerbaijaniName} ${s.meaning}".lowercase().contains(q) ||
                s.number.toString().contains(q) ||
                s.transliteratedName.lowercase().startsWith(q)
        }
    }

    companion object {
        const val BISMILLAH = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

        /** Bismillah header is shown for every surah except Al-Fatiha (1) and At-Tawba (9). */
        fun showsBismillah(surahId: Int): Boolean = surahId != 1 && surahId != 9

        /** The first verse of most surahs embeds the Bismillah prefix — strip it when the header is shown. */
        fun stripBismillahPrefix(verse: String): String =
            if (verse.startsWith(BISMILLAH)) verse.removePrefix(BISMILLAH).trim() else verse
    }
}
