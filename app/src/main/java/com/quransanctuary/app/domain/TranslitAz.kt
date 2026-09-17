package com.quransanctuary.app.domain

/**
 * Azerbaijani reading (oxunus) — EXACT port of the website `toAzerbaijani()`
 * (nazarlielmaddin/quran, data/transliterations.ts).
 *
 * Order is load-bearing: word fixes first, then multi-letter clusters,
 * then the Al- article, apostrophes and w->v. Do NOT reorder.
 * Both engines treat \b as ASCII-only, so boundary semantics match JS.
 */
private val azWordFixes: List<Pair<Regex, String>> = listOf(
    Regex("""\bRahmaan""") to """Rəhmân""",
    Regex("""\bRahman""") to """Rəhman""",
    Regex("""\brahmaan""") to """rəhmân""",
    Regex("""\bRaheem""") to """Rəhîm""",
    Regex("""\bRahem""") to """Rəhim""",
    Regex("""\braheem""") to """rəhîm""",
    Regex("""\bRabb""") to """Rəbb""",
    Regex("""\brabb""") to """rəbb""",
    Regex("""\bAlhamdu""") to """Əlhəmdu""",
    Regex("""\balhamdu""") to """əlhəmdu""",
    Regex("""\bAalameen""") to """Aləmîn""",
    Regex("""\baalameen""") to """aləmîn""",
    Regex("""\bDeen""") to """Dîn""",
    Regex("""\bSiraat""") to """Sirât""",
    Regex("""\bsiraat""") to """sirât""",
    Regex("""\bMustaqeem""") to """Mustəqîm""",
    Regex("""\bmustaqeem""") to """mustəqîm""",
    Regex("""\bMaghdoobi""") to """Məğzûbi""",
    Regex("""\bmaghdoobi""") to """məğzûbi""",
    Regex("""\bDaaalleen""") to """Dâllîn""",
    Regex("""\bdaaalleen""") to """dâllîn""",
    Regex("""\bQur-aan""") to """Qur-ân""",
    Regex("""\bqur-aan""") to """qur-ân""",
    Regex("""\bHakeem""") to """Həkîm""",
    Regex("""\bhakeem""") to """həkîm""",
    Regex("""\bMuzzammil""") to """Müzzəmmil""",
    Regex("""\bmuzzammil""") to """müzzəmmil""",
    Regex("""\bAyyuhal""") to """Əyyuhəl""",
    Regex("""\bayyuhal""") to """əyyuhəl""",
    Regex("""\bYaw\b""") to """Ya""",
    Regex("""\byaw\b""") to """ya""",
    Regex("""\bMaal""") to """Məal""",
    Regex("""\bmaal""") to """məal""",
    Regex("""\bFalaq""") to """Fələq""",
    Regex("""\bfalaq""") to """fələq""",
    Regex("""\bNas\b""") to """Nəs""",
    Regex("""\bnas\b""") to """nəs""",
    Regex("""\bAalim""") to """Alim""",
    Regex("""\baalim""") to """alim""",
    Regex("""\bAalimin""") to """Alimin""",
    Regex("""\baalimin""") to """alimin""",
    Regex("""\bMalik""") to """Malik""",
    Regex("""\bmalik""") to """malik""",
    Regex("""\bMaliki""") to """Maliki""",
    Regex("""\bmaliki""") to """maliki""",
    Regex("""\bTaw\b""") to """Tov""",
    Regex("""\btaw\b""") to """tov""",
    Regex("""\bLaw\b""") to """Lo""",
    Regex("""\blaw\b""") to """lo""",
    Regex("""\bAman\b""") to """Əman""",
    Regex("""\baman\b""") to """əman""",
    Regex("""\bAmani""") to """Əmani""",
    Regex("""\bamani""") to """əmani""",
    Regex("""\bSaadiq""") to """Sadiq""",
    Regex("""\bsaadiq""") to """sadiq""",
    Regex("""\bSadiqin""") to """Sadiqin""",
    Regex("""\bsadiqin""") to """sadiqin""",
    Regex("""\bKaafir""") to """Kafir""",
    Regex("""\bkaafir""") to """kafir""",
    Regex("""\bKaafirun""") to """Kafirun""",
    Regex("""\bkaafirun""") to """kafirun""",
    Regex("""\bMufsid""") to """Müfsid""",
    Regex("""\bmufsid""") to """müfsid""",
    Regex("""\bMufsidoon""") to """Müfsidun""",
    Regex("""\bmufsidoon""") to """müfsidun""",
    Regex("""\bMuta""") to """Müta""",
    Regex("""\bmuta""") to """müta""",
    Regex("""\bMutaa""") to """Müta""",
    Regex("""\bmutaa""") to """müta""",
    Regex("""\bSaaghar""") to """Səqər""",
    Regex("""\bsaaghar""") to """səqər""",
    Regex("""\bWaswas""") to """Vəsvəs""",
    Regex("""\bwaswas""") to """vəsvəs""",
    Regex("""\bWaswasa""") to """Vəsvəsə""",
    Regex("""\bwaswasa""") to """vəsvəsə""",
    Regex("""\bAasif""") to """Əsif""",
    Regex("""\baasif""") to """əsif""",
    Regex("""\bAasifa""") to """Əsifə""",
    Regex("""\baasifa""") to """əsifə""",
    Regex("""\bAakif""") to """Əkif""",
    Regex("""\baakif""") to """əkif""",
    Regex("""\bAakifin""") to """Əkifin""",
    Regex("""\baakifin""") to """əkifin""",
    Regex("""\bAayaatil laahi\b""") to """Əyətilləhi""",
    Regex("""\baayaatil laahi\b""") to """əyətilləhi""",
    Regex("""\bWa\b""") to """Və""",
    Regex("""\bwa\b""") to """və""",
    Regex("""naasi""") to """nəsi""",
    Regex("""Naasi""") to """Nəsi""",
    Regex("""\banzalal""") to """ənzələl""",
    Regex("""\bAnzalal""") to """Ənzələl""",
    Regex("""\binnallazeena\b""") to """innəlləzînə""",
    Regex("""\bInnallazeena\b""") to """Innəlləzînə""",
    Regex("""allazeena\b""") to """əlləzînə""",
    Regex("""Allazeena\b""") to """Əlləzînə""",
    Regex("""kafaroo""") to """kəfərû""",
    Regex("""Kafaroo""") to """Kəfərû""",
    Regex("""\bhudal\b""") to """hudəl""",
    Regex("""\bHudal\b""") to """Hudəl""",
    Regex("""lahum""") to """ləhum""",
    Regex("""Lahum""") to """Ləhum""",
    Regex("""shadeed""") to """şədîd""",
    Regex("""Shadeed""") to """Şədîd""",
    Regex("""laahi""") to """ləhi""",
    Regex("""Laahi""") to """Ləhi""",
    Regex("""Aayaa""") to """Əyə""",
    Regex("""aayaa""") to """əyə""",
    Regex("""\binna""") to """innə""",
    Regex("""\bInna""") to """Innə""",
    Regex("""\banna""") to """ənnə""",
    Regex("""\bAnna""") to """Ənnə""",
    Regex("""ahad""") to """əhəd""",
    Regex("""Ahad""") to """Əhəd""",
    Regex("""naas\b""") to """nəs""",
    Regex("""Naas\b""") to """Nəs""",
    Regex("""rabb""") to """rəbb""",
    Regex("""Rabb""") to """Rəbb""",
    Regex("""\ba'""") to """ə""",
    Regex("""\bA'""") to """Ə""",
    Regex("""Laaam""") to """Lâm""",
    Regex("""laaam""") to """lâm""",
    Regex("""Meeem""") to """Mîm""",
    Regex("""meeem""") to """mîm""",
    Regex("""taq""") to """təq""",
    Regex("""Taq""") to """Təq""",
)

private val azClusters: List<Pair<Regex, String>> = listOf(
    Regex("""sh""") to """ş""",
    Regex("""Sh""") to """Ş""",
    Regex("""SH""") to """Ş""",
    Regex("""gh""") to """ğ""",
    Regex("""Gh""") to """Ğ""",
    Regex("""GH""") to """Ğ""",
    Regex("""kh""") to """x""",
    Regex("""Kh""") to """X""",
    Regex("""KH""") to """X""",
    Regex("""th""") to """s""",
    Regex("""Th""") to """S""",
    Regex("""TH""") to """S""",
    Regex("""dh""") to """z""",
    Regex("""Dh""") to """Z""",
    Regex("""DH""") to """Z""",
    Regex("""aa""") to """â""",
    Regex("""AA""") to """Â""",
    Regex("""ee""") to """î""",
    Regex("""EE""") to """Î""",
    Regex("""ii""") to """î""",
    Regex("""II""") to """Î""",
    Regex("""oo""") to """û""",
    Regex("""OO""") to """Û""",
    Regex("""uu""") to """û""",
    Regex("""UU""") to """Û""",
)

/** Converts raw alquran.cloud en.transliteration into the correct Azerbaijani reading. Never throws. */
fun toAzerbaijaniReading(input: String): String {
    if (input.isEmpty()) return input
    var out = input
    for ((regex, rep) in azWordFixes) {
        try {
            out = regex.replace(out, Regex.escapeReplacement(rep))
        } catch (_: Exception) {
        }
    }
    for ((regex, rep) in azClusters) {
        try {
            out = regex.replace(out, Regex.escapeReplacement(rep))
        } catch (_: Exception) {
        }
    }
    // Al- article, apostrophes and w->v (website steps 3/5/6, same order).
    try {
        out = Regex("""\bAl-""").replace(out, "Əl-")
        out = Regex("""\bAL-""").replace(out, "ƏL-")
        out = Regex("""\bAl """).replace(out, "Əl ")
        out = Regex("""\bal """).replace(out, "əl ")
        out = Regex("""'a""").replace(out, "ə")
        out = Regex("""'A""").replace(out, "Ə")
        out = Regex("""'i""").replace(out, "i")
        out = Regex("""'u""").replace(out, "u")
        out = out.replace("w", "v")
        out = out.replace("W", "V")
    } catch (_: Exception) {
    }
    return out
}
