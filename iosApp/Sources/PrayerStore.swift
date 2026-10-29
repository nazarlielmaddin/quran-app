import Foundation
import Shared

struct City: Identifiable, Hashable {
    let id: String
    let nameAz: String
    let nameEn: String
    let latitude: Double
    let longitude: Double
    let utcOffsetHours: Double

    /// Wall clock of the city, independent of the device's own time zone.
    var timeZone: TimeZone {
        TimeZone(secondsFromGMT: Int(utcOffsetHours * 3600)) ?? .current
    }

    var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = timeZone
        return c
    }
}

struct Slot: Identifiable {
    let id: String
    let hour: Int
    let minute: Int

    var minutes: Int { hour * 60 + minute }
    var time: String { String(format: "%02d:%02d", hour, minute) }
    var isObligatory: Bool { id != "sunrise" }
}

@MainActor
final class PrayerStore: ObservableObject {
    @Published private(set) var city: City
    @Published private(set) var slots: [Slot] = []
    @Published private(set) var now: Date = Date()

    private let catalog = PrayerCatalog()
    private let defaults = UserDefaults.standard
    private static let cityKey = "azanify.city"

    static let cities: [City] = {
        PrayerCatalog().cities().map {
            City(
                id: $0.id,
                nameAz: $0.nameAz,
                nameEn: $0.nameEn,
                latitude: $0.latitude,
                longitude: $0.longitude,
                utcOffsetHours: $0.utcOffsetHours
            )
        }
    }()

    init() {
        let saved = UserDefaults.standard.string(forKey: PrayerStore.cityKey)
        city = PrayerStore.cities.first { $0.id == saved } ?? PrayerStore.cities[0]
        recalculate()
    }

    var currentMinutes: Int {
        let c = city.calendar.dateComponents([.hour, .minute], from: now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    var nextSlot: Slot? {
        slots.first { $0.isObligatory && $0.minutes > currentMinutes }
    }

    /// Minutes until the next obligatory prayer; wraps to tomorrow's Fajr.
    var remainingMinutes: Int {
        guard let first = slots.first else { return 0 }
        let target = nextSlot?.minutes ?? (first.minutes + 1440)
        return max(0, target - currentMinutes)
    }

    func tick() {
        let dayBefore = city.calendar.startOfDay(for: now)
        now = Date()
        if city.calendar.startOfDay(for: now) != dayBefore || slots.isEmpty {
            recalculate()
        }
    }

    func select(_ newCity: City) {
        city = newCity
        defaults.set(newCity.id, forKey: PrayerStore.cityKey)
        recalculate()
    }

    private func recalculate() {
        now = Date()
        let comps = city.calendar.dateComponents([.year, .month, .day], from: now)
        let epoch = catalog.epochDay(
            year: Int32(comps.year ?? 1970),
            month: Int32(comps.month ?? 1),
            day: Int32(comps.day ?? 1)
        )
        let moments = catalog.calculate(
            epochDay: epoch,
            latitude: city.latitude,
            longitude: city.longitude,
            utcOffsetHours: city.utcOffsetHours
        )
        slots = moments.map { Slot(id: $0.key, hour: Int($0.hour), minute: Int($0.minute)) }
    }
}
