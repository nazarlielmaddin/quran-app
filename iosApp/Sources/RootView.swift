import SwiftUI

struct RootView: View {
    @StateObject private var store = PrayerStore()
    @StateObject private var l10n = L10n()
    @StateObject private var player = AudioPlayer()
    @StateObject private var azan = AzanScheduler()
    @StateObject private var downloader = AudioDownloader.shared
    @State private var selection: Int = RootView.initialTab

    /// CI launches the app with `-tab-<name>` so every screen can be screenshotted.
    private static var initialTab: Int {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-tab-reciters") { return 1 }
        if args.contains("-tab-quran") { return 2 }
        if args.contains("-tab-atmosphere") { return 3 }
        if args.contains("-tab-settings") { return 4 }
        return 0
    }

    var body: some View {
        TabView(selection: $selection) {
            HomeView()
                .tabItem { Label(l10n.t("prayer.tab"), systemImage: "moon.stars.fill") }
                .tag(0)

            RecitersView()
                .tabItem { Label(l10n.t("nav.reciters"), systemImage: "person.wave.2.fill") }
                .tag(1)

            QuranView()
                .tabItem { Label(l10n.t("nav.surahs"), systemImage: "book.fill") }
                .tag(2)

            AtmosphereView()
                .tabItem { Label(l10n.t("nav.atmosphere"), systemImage: "leaf.fill") }
                .tag(3)

            SettingsView()
                .tabItem { Label(l10n.t("settings.title"), systemImage: "gearshape.fill") }
                .tag(4)
        }
        .environmentObject(store)
        .environmentObject(l10n)
        .environmentObject(player)
        .environmentObject(azan)
        .environmentObject(downloader)
        .environment(\.layoutDirection, l10n.language.direction)
        .tint(Azanify.Palette.gold)
        .preferredColorScheme(.dark)
        .overlay(alignment: .bottom) {
            MiniPlayer()
                .padding(.bottom, 52)
        }
        .task {
            if azan.isEnabled {
                await azan.scheduleForSelectedCity()
            }
        }
    }
}
