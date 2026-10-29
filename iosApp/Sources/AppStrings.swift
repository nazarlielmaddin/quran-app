import SwiftUI

/// The four languages Azanify ships with on Android (strings-*.json from the APK).
enum AppLanguage: String, CaseIterable, Identifiable {
    case az, en, tr, ar

    var id: String { rawValue }

    var title: String {
        switch self {
        case .az: return "Azərbaycanca"
        case .en: return "English"
        case .tr: return "Türkçe"
        case .ar: return "العربية"
        }
    }

    var locale: Locale {
        switch self {
        case .az: return Locale(identifier: "az_AZ")
        case .en: return Locale(identifier: "en_US")
        case .tr: return Locale(identifier: "tr_TR")
        case .ar: return Locale(identifier: "ar_SA")
        }
    }

    var direction: LayoutDirection { self == .ar ? .rightToLeft : .leftToRight }
}

/// Runtime string catalogue. Loads the exact `strings-{lang}.json` files that the
/// Android APK ships (84 keys each), flattened to dotted keys.
@MainActor
final class L10n: ObservableObject {
    @Published private(set) var language: AppLanguage
    @Published private(set) var table: [String: String] = [:]

    private static let langKey = "azanify.lang"

    init() {
        let saved = UserDefaults.standard.string(forKey: L10n.langKey) ?? "az"
        language = AppLanguage(rawValue: saved) ?? .az
        reload()
    }

    func set(_ lang: AppLanguage) {
        guard lang != language else { return }
        language = lang
        UserDefaults.standard.set(lang.rawValue, forKey: L10n.langKey)
        reload()
    }

    func t(_ key: String) -> String {
        if let value = table[key] { return value }
        if let extra = L10n.extras[language]?[key] { return extra }
        return key
    }

    func date(_ date: Date, in zone: TimeZone) -> String {
        let f = DateFormatter()
        f.locale = language.locale
        f.timeZone = zone
        f.dateFormat = "d MMMM, EEEE"
        return f.string(from: date).capitalizedFirst
    }

    private func reload() {
        guard
            let url = Bundle.main.url(forResource: "strings-\(language.rawValue)", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            table = [:]
            return
        }
        var flat: [String: String] = [:]
        L10n.flatten(root, prefix: "", into: &flat)
        table = flat
    }

    private static func flatten(_ obj: [String: Any], prefix: String, into out: inout [String: String]) {
        for (key, value) in obj {
            let full = prefix.isEmpty ? key : "\(prefix).\(key)"
            if let string = value as? String {
                out[full] = string
            } else if let nested = value as? [String: Any] {
                flatten(nested, prefix: full, into: &out)
            }
        }
    }

    /// Keys the Android app hardcodes in Compose rather than in strings-*.json.
    private static let extras: [AppLanguage: [String: String]] = [
        .az: [
            "prayer.tab": "Namaz",
            "prayer.next": "Növbəti namaz",
            "prayer.remaining.h": "%d saat %d dəq qaldı",
            "prayer.remaining.m": "%d dəq qaldı",
            "prayer.names.fajr": "Sübh", "prayer.names.sunrise": "Günəş",
            "prayer.names.dhuhr": "Zöhr", "prayer.names.asr": "Əsr",
            "prayer.names.maghrib": "Məğrib", "prayer.names.isha": "İşa",
            "prayer.footer": "Vaxtlar cihazda, oflayn hesablanır — Fəcr 18°, İşa 17°, Əsr kölgə əmsalı 1 (Şafi).",
            "settings.language": "Dil", "settings.city": "Şəhər", "settings.about": "Haqqında",
            "about.body": "Azanify 1.21.1 — Android nüvəsi Kotlin/Native, interfeys SwiftUI."
        ],
        .en: [
            "prayer.tab": "Prayer",
            "prayer.next": "Next prayer",
            "prayer.remaining.h": "%dh %dm left",
            "prayer.remaining.m": "%dm left",
            "prayer.names.fajr": "Fajr", "prayer.names.sunrise": "Sunrise",
            "prayer.names.dhuhr": "Dhuhr", "prayer.names.asr": "Asr",
            "prayer.names.maghrib": "Maghrib", "prayer.names.isha": "Isha",
            "prayer.footer": "Times are computed offline on device — Fajr 18°, Isha 17°, Asr shadow factor 1 (Shafi).",
            "settings.language": "Language", "settings.city": "City", "settings.about": "About",
            "about.body": "Azanify 1.21.1 — Kotlin/Native core, SwiftUI interface."
        ],
        .tr: [
            "prayer.tab": "Namaz",
            "prayer.next": "Sıradaki namaz",
            "prayer.remaining.h": "%d saat %d dk kaldı",
            "prayer.remaining.m": "%d dk kaldı",
            "prayer.names.fajr": "İmsak", "prayer.names.sunrise": "Güneş",
            "prayer.names.dhuhr": "Öğle", "prayer.names.asr": "İkindi",
            "prayer.names.maghrib": "Akşam", "prayer.names.isha": "Yatsı",
            "prayer.footer": "Vakitler cihazda çevrimdışı hesaplanır — Fecr 18°, Yatsı 17°, İkindi gölge 1.",
            "settings.language": "Dil", "settings.city": "Şehir", "settings.about": "Hakkında",
            "about.body": "Azanify 1.21.1 — Kotlin/Native çekirdek, SwiftUI arayüz."
        ],
        .ar: [
            "prayer.tab": "الصلاة",
            "prayer.next": "الصلاة القادمة",
            "prayer.remaining.h": "بعد %d ساعة و%d دقيقة",
            "prayer.remaining.m": "بعد %d دقيقة",
            "prayer.names.fajr": "الفجر", "prayer.names.sunrise": "الشروق",
            "prayer.names.dhuhr": "الظهر", "prayer.names.asr": "العصر",
            "prayer.names.maghrib": "المغرب", "prayer.names.isha": "العشاء",
            "prayer.footer": "تُحسب الأوقات على الجهاز دون اتصال — الفجر 18°، العشاء 17°، العصر ظل 1.",
            "settings.language": "اللغة", "settings.city": "المدينة", "settings.about": "حول",
            "about.body": "Azanify 1.21.1 — نواة Kotlin/Native وواجهة SwiftUI."
        ]
    ]
}

extension String {
    var capitalizedFirst: String {
        guard let first else { return self }
        return first.uppercased() + dropFirst()
    }
}
