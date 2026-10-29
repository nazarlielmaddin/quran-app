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
        ZStack {
            Azanify.background.ignoresSafeArea()

            VStack(spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("QURAN")
                            .font(.system(size: 19, weight: .semibold))
                            .tracking(5)
                            .foregroundStyle(Azanify.Palette.gold)
                        Text("114 surə · Azərbaycan dilində")
                            .font(.system(size: 12))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)

                searchField

                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered) { surah in
                            SurahRow(surah: surah)
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

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 13))
                .foregroundStyle(Azanify.Palette.mistFaint)
            TextField("Surə axtar", text: $query)
                .font(.system(size: 14))
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
    let surah: Surah

    var body: some View {
        HStack(spacing: 14) {
            Text("\(surah.number)")
                .font(.system(size: 15, weight: .medium, design: .serif))
                .foregroundStyle(Azanify.Palette.gold)
                .frame(width: 32, height: 32)
                .overlay(
                    Circle().stroke(Azanify.Palette.goldDeep.opacity(0.55), lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 3) {
                Text(surah.azerbaijaniName)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Azanify.Palette.mist)
                Text("\(surah.englishName) · \(surah.verses) ayə")
                    .font(.system(size: 11))
                    .foregroundStyle(Azanify.Palette.mistFaint)
            }

            Spacer(minLength: 8)

            Text(surah.arabicName)
                .font(.system(size: 17))
                .foregroundStyle(Azanify.Palette.goldSoft.opacity(0.9))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
}
