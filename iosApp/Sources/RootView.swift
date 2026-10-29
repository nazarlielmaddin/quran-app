import SwiftUI

struct RootView: View {
    @StateObject private var store = PrayerStore()

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Ana səhifə", systemImage: "moon.stars.fill") }

            QuranView()
                .tabItem { Label("Quran", systemImage: "book.fill") }

            SettingsView()
                .tabItem { Label("Ayarlar", systemImage: "gearshape.fill") }
        }
        .environmentObject(store)
        .tint(Azanify.Palette.gold)
        .preferredColorScheme(.dark)
    }
}
