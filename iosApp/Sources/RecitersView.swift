import SwiftUI
import UIKit

struct Reciter: Decodable, Identifiable {
    let id: String
    let name: String?
    let nameAz: String?
    let arabicName: String?
    let bio: String?
    let bioAz: String?
    let origin: String?
    let originAz: String?
    let hasTimestamps: Bool?

    /// Portraits are bundled from `assets/reciters` inside the APK.
    var imageName: String? {
        let png = ["maher-al-muaiqly", "mahmoud-khalil-al-husary", "saad-al-ghamdi"]
        let name = png.contains(id) ? "\(id).png" : "\(id).jpg"
        return UIImage(named: name) != nil ? name : nil
    }
}

enum ReciterCatalog {
    static let all: [Reciter] = {
        guard
            let url = Bundle.main.url(forResource: "reciters", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let list = try? JSONDecoder().decode([Reciter].self, from: data)
        else {
            return []
        }
        return list
    }()
}

struct RecitersView: View {
    @EnvironmentObject private var l10n: L10n

    var body: some View {
        ZStack {
            Azanify.background.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    header

                    ForEach(ReciterCatalog.all) { reciter in
                        ReciterCard(reciter: reciter)
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
            Text(l10n.t("nav.reciters").uppercased())
                .font(AppFont.display(19, .semibold))
                .tracking(5)
                .foregroundStyle(Azanify.Palette.gold)
            Text(l10n.t("reciters.title"))
                .font(AppFont.display(26, .medium))
                .foregroundStyle(Azanify.Palette.mist)
            Text(l10n.t("reciters.subtitle"))
                .font(AppFont.text(12))
                .foregroundStyle(Azanify.Palette.mistFaint)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 4)
    }
}

private struct ReciterCard: View {
    @EnvironmentObject private var l10n: L10n
    let reciter: Reciter

    var body: some View {
        HStack(spacing: 14) {
            portrait

            VStack(alignment: .leading, spacing: 5) {
                Text(displayName)
                    .font(AppFont.text(16, .semibold))
                    .foregroundStyle(Azanify.Palette.mist)
                Text(reciter.arabicName ?? "")
                    .font(AppFont.arabic(16))
                    .foregroundStyle(Azanify.Palette.gold.opacity(0.85))
                Text(shortBio)
                    .font(AppFont.text(11))
                    .foregroundStyle(Azanify.Palette.mistFaint)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)

            Image(systemName: "play.circle")
                .font(.system(size: 26, weight: .light))
                .foregroundStyle(Azanify.Palette.gold.opacity(0.35))
        }
        .padding(14)
        .azanifyCard()
    }

    private var portrait: some View {
        Group {
            if let name = reciter.imageName, let ui = UIImage(named: name) {
                Image(uiImage: ui)
                    .resizable()
                    .scaledToFill()
            } else {
                Azanify.Palette.forestSoft
            }
        }
        .frame(width: 62, height: 62)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Azanify.Palette.goldDeep.opacity(0.35), lineWidth: 1)
        )
    }

    private var displayName: String {
        l10n.language == .az ? (reciter.nameAz ?? reciter.name ?? reciter.id) : (reciter.name ?? reciter.id)
    }

    private var shortBio: String {
        l10n.language == .az ? (reciter.bioAz ?? reciter.bio ?? "") : (reciter.bio ?? "")
    }
}
