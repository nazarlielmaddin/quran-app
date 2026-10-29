import SwiftUI

struct RootView: View {
    @StateObject private var store = PrayerStore()
    @State private var selection: Int = RootView.initialTab

    /// CI launches the app with `-tab-quran` / `-tab-settings` to screenshot each tab.
    private static var initialTab: Int {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-tab-quran") { return 1 }
        if args.contains("-tab-settings") { return 2 }
        return 0
    }

    var body: some View {
        TabView(selection: $selection) {
            HomeView()
                .tabItem { Label("Ana səhifə", systemImage: "moon.stars.fill") }
                .tag(0)

            QuranView()
                .tabItem { Label("Quran", systemImage: "book.fill") }
                .tag(1)

            SettingsView()
                .tabItem { Label("Ayarlar", systemImage: "gearshape.fill") }
                .tag(2)
        }
        .environmentObject(store)
        .tint(Azanify.Palette.gold)
        .preferredColorScheme(.dark)
    }
}
