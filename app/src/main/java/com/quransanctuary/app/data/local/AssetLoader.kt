package com.quransanctuary.app.data.local

import android.content.Context
import com.quransanctuary.app.data.model.AmbientSound
import com.quransanctuary.app.data.model.Recitation
import com.quransanctuary.app.data.model.Reciter
import com.quransanctuary.app.data.model.Surah
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Reads the website-parity JSON catalogs bundled in assets/data. */
class AssetLoader(private val context: Context) {

    // coerceInputValues: wrong-typed values fall back to defaults instead of crashing.
    // explicitNulls=false: explicit `null` for a non-nullable field with default
    // (e.g. Recitation.source="") is treated as "missing" -> default is used.
    // Together they fix: Unexpected symbol 'n' in numeric literal at $[114].durationSeconds
    // when recitations.json contains "durationSeconds":null.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun readAssetText(path: String): String =
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    /**
     * Per-element tolerant list decode: a single corrupt object must never kill
     * the whole catalog (previously 1 bad item in 1140 recitations crashed startup).
     * Bad elements are skipped; if everything fails we return emptyList() instead of throwing.
     */
    private fun <T> decodeListTolerant(
        rawJson: String,
        elementSerializer: kotlinx.serialization.KSerializer<T>
    ): List<T> {
        val listSerializer = ListSerializer(elementSerializer)
        return try {
            // Fast path — whole list is clean.
            json.decodeFromString(listSerializer, rawJson)
        } catch (_: Exception) {
            try {
                val array = json.decodeFromString(JsonArray.serializer(), rawJson)
                val out = ArrayList<T>(array.size)
                for (element in array) {
                    try {
                        out.add(json.decodeFromJsonElement(elementSerializer, element))
                    } catch (_: Exception) {
                        // Skip single corrupt entry (log in debug builds only).
                    }
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun loadSurahs(): List<Surah> = try {
        decodeListTolerant(readAssetText("data/surahs.json"), Surah.serializer())
    } catch (_: Exception) {
        emptyList()
    }

    fun loadReciters(): List<Reciter> = try {
        decodeListTolerant(readAssetText("data/reciters.json"), Reciter.serializer())
    } catch (_: Exception) {
        emptyList()
    }

    fun loadRecitations(): List<Recitation> = try {
        decodeListTolerant(readAssetText("data/recitations.json"), Recitation.serializer())
    } catch (_: Exception) {
        emptyList()
    }

    fun loadAmbients(): List<AmbientSound> = try {
        decodeListTolerant(readAssetText("data/ambient.json"), AmbientSound.serializer())
    } catch (_: Exception) {
        emptyList()
    }

    /** Verse-level ayah-start offsets in milliseconds, keyed by surah number. Null when the reciter has no sync data. */
    fun loadTimings(reciterId: String): Map<Int, List<Long>>? {
        return try {
            context.assets.open("data/timings-$reciterId.json").use {
                val raw: Map<String, JsonElement> = json.decodeFromString(
                    MapSerializer(String.serializer(), JsonElement.serializer()),
                    it.readBytes().toString(Charsets.UTF_8)
                )
                val out = LinkedHashMap<Int, List<Long>>(raw.size)
                for ((key, value) in raw) {
                    val surahId = key.toIntOrNull() ?: continue
                    try {
                        val timings = value as? JsonArray ?: continue
                        val longs = ArrayList<Long>(timings.size)
                        for (item in timings) {
                            try {
                                val parsed = try {
                                    item.jsonPrimitive.longOrNull
                                        ?: item.jsonPrimitive.intOrNull?.toLong()
                                        ?: item.jsonPrimitive.content.toDoubleOrNull()?.toLong()
                                        ?: item.jsonPrimitive.content.toLongOrNull()
                                } catch (_: Exception) {
                                    null
                                }
                                if (parsed != null && parsed >= 0) longs.add(parsed)
                            } catch (_: Exception) {
                                // Skip single corrupt offset — never fail the whole surah.
                            }
                        }
                        if (longs.isNotEmpty()) out[surahId] = longs
                    } catch (_: Exception) {
                        // Skip corrupt surah entry.
                    }
                }
                out.ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadArabic(): Map<Int, List<String>> = loadStringMap("arabic.json")

    fun loadTranslationsAz(): Map<Int, List<String>> = loadStringMap("translations-az.json")

    fun loadTransliterations(): Map<Int, List<String>> = loadStringMap("transliterations.json")

    private fun loadStringMap(file: String): Map<Int, List<String>> {
        return try {
            context.assets.open("data/$file").use {
                val raw: Map<String, List<String>> = try {
                    json.decodeFromString(
                        MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                        it.readBytes().toString(Charsets.UTF_8)
                    )
                } catch (_: Exception) {
                    return emptyMap()
                }
                val out = LinkedHashMap<Int, List<String>>(raw.size)
                for ((key, value) in raw) {
                    val id = key.toIntOrNull() ?: continue
                    out[id] = value
                }
                out
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

}
