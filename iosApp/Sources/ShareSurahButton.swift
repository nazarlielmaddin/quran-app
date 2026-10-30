import SwiftUI
import UIKit

struct ShareSurahButton: View {
    let surah: Surah
    let verses: [Verse]
    @EnvironmentObject private var downloader: AudioDownloader

    var body: some View {
        Button {
            share()
        } label: {
            Image(systemName: "square.and.arrow.up")
                .foregroundStyle(Azanify.Palette.gold)
        }
    }

    private func share() {
        let title = "\(surah.azerbaijaniName) (\(surah.englishName)) — \(surah.verses) ayə"
        let body = verses.map { verse in
            "\(verse.arabic)\n\(verse.translation)"
        }.joined(separator: "\n\n")
        var items: [Any] = ["\(title)\n\n\(body)"]

        if let url = downloader.localURL(reciterId: "yasir-al-dawsari", surahId: surah.number) {
            items.append(url)
        }

        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first?.rootViewController else { return }
        // iPad requires a popover anchor.
        if let popover = controller.popoverPresentationController {
            popover.sourceView = root.view
            popover.sourceRect = CGRect(x: root.view.bounds.midX, y: root.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        root.present(controller, animated: true)
    }
}
