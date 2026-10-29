import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: PrayerStore
    @EnvironmentObject private var l10n: L10n

    var body: some View {
        ZStack {
            Azanify.background.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    languageCard
                    cityCard
                    aboutCard
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(l10n.t("settings.title").uppercased())
                .font(AppFont.display(19, .semibold))
                .tracking(5)
                .foregroundStyle(Azanify.Palette.gold)
            Text(l10n.t("footer.tagline"))
                .font(AppFont.text(12))
                .foregroundStyle(Azanify.Palette.mistFaint)
        }
        .padding(.top, 10)
    }

    private var languageCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: l10n.t("settings.language"))

            ForEach(AppLanguage.allCases) { language in
                Button {
                    l10n.set(language)
                } label: {
                    HStack {
                        Text(language.title)
                            .font(AppFont.text(15))
                            .foregroundStyle(Azanify.Palette.mist)
                        Spacer()
                        if language == l10n.language {
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
        .azanifyCard()
    }

    private var cityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: l10n.t("settings.city"))

            ForEach(PrayerStore.cities) { city in
                Button {
                    store.select(city)
                } label: {
                    HStack {
                        Text(l10n.language == .az ? city.nameAz : city.nameEn)
                            .font(AppFont.text(15))
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
        .azanifyCard()
    }

    private var aboutCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: l10n.t("settings.about"))
            Text(l10n.t("about.body"))
                .font(AppFont.text(13))
                .foregroundStyle(Azanify.Palette.mistDim)
                .fixedSize(horizontal: false, vertical: true)
            Text("1.21.1 (iOS)")
                .font(AppFont.text(11, .medium))
                .foregroundStyle(Azanify.Palette.mistFaint)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .azanifyCard()
    }
}
