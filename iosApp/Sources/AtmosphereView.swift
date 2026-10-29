import SwiftUI

struct AmbientSound: Decodable, Identifiable {
    let id: String
    let name: String
    let nameAz: String
    let category: String
    let categoryAz: String
    let gradient: [String]
    let audioUrl: String
    let videoUrl: String?
    let imageUrl: String?
}

enum AmbientCatalog {
    static let all: [AmbientSound] = {
        guard
            let url = Bundle.main.url(forResource: "ambient", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let list = try? JSONDecoder().decode([AmbientSound].self, from: data)
        else {
            return []
        }
        return list
    }()
}

struct AtmosphereView: View {
    @EnvironmentObject private var l10n: L10n
    @EnvironmentObject private var ambient: AmbientPlayer

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ZStack {
            Azanify.background.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header

                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(AmbientCatalog.all) { sound in
                            AmbientCard(sound: sound)
                                .onTapGesture {
                                    guard let url = URL(string: sound.audioUrl) else { return }
                                    ambient.toggle(id: sound.id, url: url)
                                }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 28)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(l10n.t("nav.atmosphere").uppercased())
                .font(AppFont.display(19, .semibold))
                .tracking(5)
                .foregroundStyle(Azanify.Palette.gold)
            Text(l10n.t("atmosphere.title"))
                .font(AppFont.display(24, .medium))
                .foregroundStyle(Azanify.Palette.mist)
            Text(l10n.t("atmosphere.subtitle"))
                .font(AppFont.text(12))
                .foregroundStyle(Azanify.Palette.mistFaint)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 4)
    }
}

private struct AmbientCard: View {
    @EnvironmentObject private var l10n: L10n
    @EnvironmentObject private var ambient: AmbientPlayer
    let sound: AmbientSound

    private var colors: [Color] {
        let parsed = sound.gradient.compactMap { Color(hexString: $0) }
        return parsed.count >= 2 ? parsed : [Azanify.Palette.forestSoft, Azanify.Palette.ink]
    }

    private var isPlaying: Bool {
        ambient.currentId == sound.id && ambient.isPlaying
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Spacer()
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 22, weight: .light))
                    .foregroundStyle(.white.opacity(isPlaying ? 0.95 : 0.5))
            }

            Spacer(minLength: 0)

            Text(categoryLabel)
                .font(AppFont.text(9, .semibold))
                .tracking(1.4)
                .foregroundStyle(.white.opacity(0.75))
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(Capsule().fill(Color.black.opacity(0.28)))

            Text(l10n.language == .az ? sound.nameAz : sound.name)
                .font(AppFont.display(16, .semibold))
                .foregroundStyle(Azanify.Palette.mist)
                .lineLimit(2)
                .padding(.top, 8)
        }
        .padding(14)
        .frame(height: 132)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(isPlaying ? Azanify.Palette.gold : Color.white.opacity(0.08), lineWidth: isPlaying ? 2 : 1)
        )
    }

    private var categoryLabel: String {
        let key = "atmosphere.\(sound.category)"
        let fallback = l10n.language == .az ? sound.categoryAz : sound.category
        let value = l10n.t(key)
        return value == key ? fallback : value
    }
}
