package com.quransanctuary.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Lenient nullable Int — crashes like
 * `Unexpected symbol 'n' in numeric literal at path: $[114].durationSeconds`
 * happen when the JSON contains explicit `null` (or "null" / "123" strings)
 * for a non-nullable numeric field. This serializer accepts:
 * null, JsonNull, numeric, numeric-string and double values — everything else -> null.
 * Never throws.
 */
object LenientNullableInt : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientNullableInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        return try {
            val jsonDecoder = decoder as? JsonDecoder
                ?: return try {
                    decoder.decodeInt()
                } catch (_: Exception) {
                    null
                }
            val element = try {
                jsonDecoder.decodeJsonElement()
            } catch (_: Exception) {
                return null
            }
            when {
                element is JsonNull -> null
                element is JsonPrimitive && element.isString -> {
                    val content = element.content.trim()
                    if (content.isEmpty() || content.equals("null", ignoreCase = true)) null
                    else content.toIntOrNull()
                        ?: content.toDoubleOrNull()?.toInt()
                        ?: content.toLongOrNull()?.toInt()
                }
                element is JsonPrimitive -> element.intOrNull
                    ?: element.longOrNull?.toInt()
                    ?: element.doubleOrNull?.toInt()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/**
 * Lenient nullable Double (for AmbientSound.volume etc.).
 * Accepts null / numeric / numeric-string — never throws.
 */
object LenientNullableDouble : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientNullableDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        return try {
            val jsonDecoder = decoder as? JsonDecoder
                ?: return try {
                    decoder.decodeDouble()
                } catch (_: Exception) {
                    null
                }
            val element = try {
                jsonDecoder.decodeJsonElement()
            } catch (_: Exception) {
                return null
            }
            when {
                element is JsonNull -> null
                element is JsonPrimitive && element.isString -> {
                    val content = element.content.trim()
                    if (content.isEmpty() || content.equals("null", ignoreCase = true)) null
                    else content.toDoubleOrNull()
                        ?: content.toIntOrNull()?.toDouble()
                }
                element is JsonPrimitive -> try {
                    element.content.toDoubleOrNull()
                } catch (_: Exception) {
                    null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

@Serializable
data class Surah(
    val id: Int,
    val number: Int,
    val arabicName: String,
    val englishName: String,
    val transliteratedName: String,
    val azerbaijaniName: String,
    val meaning: String,
    val verses: Int,
    val revelationType: String // "Meccan" | "Medinan"
)

@Serializable
data class Reciter(
    val id: String,
    val name: String,
    val nameAz: String,
    val arabicName: String,
    val image: String,
    val bio: String,
    val bioAz: String,
    val bioLong: String = "",
    val bioLongAz: String = "",
    val hasTimestamps: Boolean = false,
    val origin: String = "",
    val originAz: String = "",
    val provider: String = "",
    val audioFolder: String = "",
    val timingSource: String? = null
) {
    fun localizedName(locale: String): String = if (locale == "az") nameAz else name
    fun localizedBio(locale: String): String = if (locale == "az") bioAz.ifEmpty { bio } else bio
    fun localizedBioLong(locale: String): String =
        if (locale == "az") bioLongAz.ifEmpty { bioLong.ifEmpty { localizedBio(locale) } }
        else bioLong.ifEmpty { bio }
    fun localizedOrigin(locale: String): String = if (locale == "az") originAz.ifEmpty { origin } else origin
    /** Local bundled portrait file name (assets/reciters). Website serves .jpg even for PNG content. */
    fun assetFileName(): String {
        val base = image.substringAfterLast("/").substringBeforeLast(".")
        return when (id) {
            "maher-al-muaiqly", "mahmoud-khalil-al-husary", "saad-al-ghamdi" -> "$base.png"
            else -> "$base.jpg"
        }
    }
}

@Serializable
data class Recitation(
    val id: String,
    val reciterId: String,
    val surahId: Int,
    val audioUrl: String,
    @Serializable(with = LenientNullableInt::class)
    val durationSeconds: Int? = null,
    val source: String = ""
)

@Serializable
data class AmbientSound(
    val id: String,
    val name: String,
    val nameAz: String,
    val category: String, // nature | cozy | focus
    val categoryAz: String,
    val gradient: List<String> = emptyList(),
    val audioUrl: String,
    val videoUrl: String? = null,
    val source: String = "",
    val imageUrl: String? = null,
    val imageSource: String = "",
    @Serializable(with = LenientNullableDouble::class)
    val volume: Double? = null
) {
    fun localizedName(locale: String): String = if (locale == "az") nameAz else name
    fun localizedCategory(locale: String): String = if (locale == "az") categoryAz else category
}

enum class RepeatMode(val id: String) {
    OFF("off"), ONE("one"), ALL("all");
    fun next(): RepeatMode = when (this) {
        OFF -> ONE; ONE -> ALL; ALL -> OFF
    }
    companion object {
        fun from(id: String?): RepeatMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class DisplayMode(val id: String) { TRANSLITERATION("transliteration"), ARABIC("arabic"), BOTH("both");
    companion object { fun from(id: String?): DisplayMode = entries.firstOrNull { it.id == id } ?: BOTH }
}

enum class AppLocale(val id: String) { AZ("az"), EN("en"), TR("tr"), AR("ar");
    companion object { fun from(id: String?): AppLocale = entries.firstOrNull { it.id == id } ?: AZ }
}

enum class ThemeMode(val id: String) { DARK("dark"), LIGHT("light"), SYSTEM("system");
    companion object { fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: DARK }
}

data class VerseTexts(
    val surahId: Int,
    val arabic: List<String>,
    val translationAz: List<String>,
    val transliteration: List<String>
)
