import Foundation
import UserNotifications
import Shared

@MainActor
final class AzanScheduler: ObservableObject {
    @Published var isEnabled: Bool {
        didSet {
            UserDefaults.standard.set(isEnabled, forKey: AzanScheduler.key)
            if isEnabled {
                Task { await requestAuthorizationAndSchedule() }
            } else {
                cancelAll()
            }
        }
    }

    private static let key = "azanify.azan.enabled"
    private let center = UNUserNotificationCenter.current()
    private let catalog = PrayerCatalog()

    init() {
        isEnabled = UserDefaults.standard.bool(forKey: AzanScheduler.key)
    }

    // MARK: - Public

    func requestAuthorizationAndSchedule() async {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            if granted {
                await scheduleForSelectedCity()
            } else {
                isEnabled = false
            }
        } catch {
            isEnabled = false
        }
    }

    func scheduleForSelectedCity() async {
        guard isEnabled else { return }
        cancelAll()

        let cityId = UserDefaults.standard.string(forKey: "azanify.city") ?? PrayerStore.cities[0].id
        guard let city = PrayerStore.cities.first(where: { $0.id == cityId }) else { return }

        // 7 days of exact (non-repeating) triggers — prayer times drift daily,
        // so a fixed daily repeat would be wrong.
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        for dayOffset in 0..<7 {
            guard let date = calendar.date(byAdding: .day, value: dayOffset, to: today) else { continue }
            let comps = calendar.dateComponents([.year, .month, .day], from: date)
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

            for moment in moments {
                guard PrayerKeys.shared.daily.contains(moment.key) else { continue }
                guard let triggerDate = calendar.date(
                    bySettingHour: moment.hour, minute: moment.minute, second: 0, of: date
                ) else { continue }
                // Skip prayers that have already passed today.
                if triggerDate <= Date() { continue }

                let content = UNMutableNotificationContent()
                content.title = "Azanify"
                content.body = prayerName(moment.key) + " vaxtı"
                content.sound = .default
                content.categoryIdentifier = "AZAN"

                let triggerComps = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
                let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComps, repeats: false)

                let id = "azan.\(city.id).\(moment.key).\(dayOffset)"
                let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
                try? await center.add(request)
            }
        }
    }

    func cancelAll() {
        center.removeAllPendingNotificationRequests()
    }

    // MARK: - Helpers

    private func prayerName(_ key: String) -> String {
        switch key {
        case "fajr": return "Sübh"
        case "dhuhr": return "Zöhr"
        case "asr": return "Əsr"
        case "maghrib": return "Məğrib"
        case "isha": return "İşa"
        default: return key
        }
    }
}
