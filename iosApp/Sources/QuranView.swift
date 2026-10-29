import SwiftUI

struct Surah: Decodable, Identifiable {
    let id: Int
    let number: Int
    let arabicName: String
    let englishName: String
    let transliteratedName: String
    let azerbaijaniName: String
    let meaning: String
    let verses: Int
    let revelationType: String
}

enum SurahCatalog {
    static let all: [Surah] = {
        guard
            let url = Bundle.main.url(forResource: "surahs", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let list = try? JSONDecoder().decode([Surah].self, from: data)
        else {
            return []
        }
        return list
    }()
}

struct QuranView: View {
    @EnvironmentObject private var l10n: L10n
    @State private var query = ""

    private var filtered: [Surah] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return SurahCatalog.all }
        let q = query.lowercased()
        return SurahCatalog.all.filter {
            $0.azerbaijaniName.lowercased().contains(q)
                || $0.englishName.lowercased().contains(q)
                || $0.transliteratedName.lowercased().contains(q)
                || String($0.number) == q
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Azanify.background.ignoresSafeArea()

                VStack(spacing: 14) {
                    header
                    searchField

                    if filtered.isEmpty {
                        Spacer()
                        Text(l10n.t("surahs.noneFound"))
                            .font(AppFont.text(13))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                        Spacer()
                    } else {
                        ScrollView(showsIndicators: false) {
                            LazyVStack(spacing: 0) {
                                ForEach(filtered) { surah in
                                    NavigationLink(destination: ReaderView(surahNumber: surah.number)) {
                                        SurahRow(surah: surah)
                                    }
                                    .buttonStyle(.plain)

                                    Rectangle()
                                        .fill(Color.white.opacity(0.05))
                                        .frame(height: 1)
                                        .padding(.leading, 62)
                                }
                            }
                            .padding(.bottom, 24)
                        }
                    }
                }
            }
            .navigationTitle(l10n.t("nav.surahs"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Azanify.Palette.ink, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(l10n.t("nav.surahs").uppercased())
                    .font(AppFont.display(19, .semibold))
                    .tracking(5)
                    .foregroundStyle(Azanify.Palette.gold)
                Text(l10n.t("surahs.title"))
                    .font(AppFont.text(12))
                    .foregroundStyle(Azanify.Palette.mistFaint)
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 13))
                .foregroundStyle(Azanify.Palette.mistFaint)
            TextField(l10n.t("surahs.searchPlaceholder"), text: $query)
                .font(AppFont.text(14))
                .foregroundStyle(Azanify.Palette.mist)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .padding(.horizontal, 20)
    }
}

private struct SurahRow: View {
    @EnvironmentObject private var l10n: L10n
    @EnvironmentObject private var player: AudioPlayer
    let surah: Surah

    var body: some View {
        HStack(spacing: 14) {
            Text("\(surah.number)")
                .font(AppFont.display(15, .medium))
                .foregroundStyle(Azanify.Palette.gold)
                .frame(width: 32, height: 32)
                .overlay(
                    Circle().stroke(Azanify.Palette.goldDeep.opacity(0.55), lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 3) {
                Text(primaryName)
                    .font(AppFont.text(15, .medium))
                    .foregroundStyle(Azanify.Palette.mist)
                Text("\(secondaryName) · \(surah.verses) \(l10n.t("surahs.ayahs"))")
                    .font(AppFont.text(11))
                    .foregroundStyle(Azanify.Palette.mistFaint)
            }

            Spacer(minLength: 8)

            Text(surah.arabicName)
                .font(AppFont.arabic(18))
                .foregroundStyle(Azanify.Palette.goldSoft.opacity(0.9))
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Button {
                player.play(
                    reciterId: "yasir-al-dawsari",
                    surahId: surah.number,
                    title: primaryName,
                    reciterName: "Yasir Al-Dawsari"
                )
            } label: {
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 30, weight: .light))
                    .foregroundStyle(isPlaying ? Azanify.Palette.goldSoft : Azanify.Palette.gold.opacity(0.7))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private var primaryName: String {
        l10n.language == .az ? surah.azerbaijaniName : surah.englishName
    }

    private var secondaryName: String {
        l10n.language == .az ? surah.englishName : surah.transliteratedName
    }

    private var isPlaying: Bool {
        player.surahNumber == surah.number && player.isPlaying
    }
}
