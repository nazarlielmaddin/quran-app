import SwiftUI

enum DisplayMode: String, CaseIterable, Identifiable {
    case transliteration, arabic, both
    var id: String { rawValue }
}

struct Verse: Identifiable {
    let surah: Int
    let index: Int
    let arabic: String
    let translation: String
    let transliteration: String
    let startMs: Int
    let endMs: Int

    var id: Int { index }
}

enum VerseCatalog {
    static let arabic: [String: [String]] = load("arabic")
    static let translation: [String: [String]] = load("translations-az")
    static let transliteration: [String: [String]] = load("transliterations")
    static let timings: [String: [Int]] = load("timings-yasir-al-dawsari")

    private static func load<T: Decodable>(_ name: String) -> T {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let value = try? JSONDecoder().decode(T.self, from: data)
        else {
            if T.self == [String: [String]].self { return [:] as! T }
            if T.self == [String: [Int]].self { return [:] as! T }
            fatalError("Failed to load \(name)")
        }
        return value
    }

    static func verses(for surah: Int) -> [Verse] {
        let key = String(surah)
        let ar = arabic[key] ?? []
        let tr = translation[key] ?? []
        let tl = transliteration[key] ?? []
        let tm = timings[key] ?? []
        let count = max(ar.count, max(tr.count, tl.count))

        var result: [Verse] = []
        for i in 0..<count {
            let start = i < tm.count ? tm[i] : 0
            let end = (i + 1) < tm.count ? tm[i + 1] : (tm.last ?? 0) + 5000
            result.append(Verse(
                surah: surah,
                index: i,
                arabic: i < ar.count ? ar[i] : "",
                translation: i < tr.count ? tr[i] : "",
                transliteration: i < tl.count ? tl[i] : "",
                startMs: start,
                endMs: end
            ))
        }
        return result
    }
}

struct ReaderView: View {
    let surahNumber: Int
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var l10n: L10n

    @State private var displayMode: DisplayMode = .both
    @State private var verses: [Verse] = []
    @State private var activeIndex: Int = 0

    private var isActiveSurah: Bool { player.surahNumber == surahNumber }

    var body: some View {
        VStack(spacing: 0) {
            modePicker

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        ForEach(verses) { verse in
                            VerseCard(
                                verse: verse,
                                isActive: isActiveSurah && verse.index == activeIndex,
                                mode: displayMode
                            )
                            .id(verse.index)
                            .onTapGesture {
                                player.seek(to: Double(verse.startMs) / 1000.0)
                                if !player.isPlaying {
                                    // start playback if not already
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .onChange(of: player.currentTime) { _ in
                    guard isActiveSurah else { return }
                    let newActive = activeVerseIndex(for: Int(player.currentTime * 1000))
                    if newActive != activeIndex {
                        activeIndex = newActive
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo(newActive, anchor: .center)
                        }
                    }
                }
            }
        }
        .navigationTitle(surahTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    player.play(
                        reciterId: "yasir-al-dawsari",
                        surahId: surahNumber,
                        title: surahTitle,
                        reciterName: "Yasir Al-Dawsari"
                    )
                } label: {
                    Image(systemName: player.isPlaying && isActiveSurah ? "pause.fill" : "play.fill")
                        .foregroundStyle(Azanify.Palette.gold)
                }
            }
        }
        .onAppear {
            verses = VerseCatalog.verses(for: surahNumber)
        }
    }

    private var surahTitle: String {
        // Use the surahs catalog name
        SurahCatalog.all.first { $0.number == surahNumber }?.azerbaijaniName ?? "Surə \(surahNumber)"
    }

    private var modePicker: some View {
        Picker("", selection: $displayMode) {
            Text("Translit").tag(DisplayMode.transliteration)
            Text("Ərəb").tag(DisplayMode.arabic)
            Text("Hər ikisi").tag(DisplayMode.both)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Azanify.Palette.ink)
    }

    private func activeVerseIndex(for ms: Int) -> Int {
        guard !verses.isEmpty else { return 0 }
        // binary search: largest i with verses[i].startMs <= ms
        var lo = 0, hi = verses.count - 1, result = 0
        while lo <= hi {
            let mid = (lo + hi) / 2
            if verses[mid].startMs <= ms {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}

private struct VerseCard: View {
    let verse: Verse
    let isActive: Bool
    let mode: DisplayMode

    var body: some View {
        VStack(alignment: .trailing, spacing: 10) {
            // Verse number badge
            HStack {
                Spacer()
                Text("\(verse.index + 1)")
                    .font(AppFont.text(11, .semibold))
                    .foregroundStyle(Azanify.Palette.gold.opacity(0.7))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Azanify.Palette.goldDeep.opacity(0.15)))
            }

            if mode == .arabic || mode == .both {
                Text(verse.arabic)
                    .font(AppFont.arabic(22))
                    .foregroundStyle(isActive ? Azanify.Palette.goldSoft : Azanify.Palette.mist)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .lineSpacing(6)
            }

            if mode == .transliteration || mode == .both {
                Text(verse.transliteration)
                    .font(AppFont.text(13, .medium))
                    .foregroundStyle(isActive ? Azanify.Palette.gold.opacity(0.9) : Azanify.Palette.mistDim)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .italic()
            }

            Text(verse.translation)
                .font(AppFont.text(14))
                .foregroundStyle(isActive ? Azanify.Palette.mist : Azanify.Palette.mistDim)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 2)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(isActive ? Azanify.Palette.goldDeep.opacity(0.12) : Azanify.Palette.inkPanel.opacity(0.6))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(isActive ? Azanify.Palette.gold.opacity(0.35) : Color.white.opacity(0.04), lineWidth: 1)
        )
        .animation(.easeInOut(duration: 0.25), value: isActive)
    }
}
