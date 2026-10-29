import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: PrayerStore

    var body: some View {
        ZStack {
            Azanify.background.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("AYARLAR")
                            .font(.system(size: 19, weight: .semibold))
                            .tracking(5)
                            .foregroundStyle(Azanify.Palette.gold)
                        Text("Şəhər, bildirişlər və görünüş")
                            .font(.system(size: 12))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                    }
                    .padding(.top, 10)

                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel(text: "Şəhər")

                        ForEach(PrayerStore.cities) { city in
                            Button {
                                store.select(city)
                            } label: {
                                HStack {
                                    Text(city.nameAz)
                                        .font(.system(size: 15))
                                        .foregroundStyle(Azanify.Palette.mist)
                                    Spacer()
                                    if city.id == store.city.id {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Azanify.Palette.gold)
                                    }
                                }
                                .padding(.vertical, 6)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Azanify.card)

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "Haqqında")
                        Text("Azanify 1.21.1 (iOS)")
                            .font(.system(size: 14))
                            .foregroundStyle(Azanify.Palette.mist)
                        Text("Android tətbiqinin Kotlin əsaslı nüvəsi iOS-da SwiftUI ilə işləyir. Namaz vaxtları şəbəkəsiz, astronomik hesablamayla tapılır.")
                            .font(.system(size: 12))
                            .foregroundStyle(Azanify.Palette.mistDim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Azanify.card)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
        }
    }
}
