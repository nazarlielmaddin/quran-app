import SwiftUI
import UIKit

struct ShareSurahButton: View {
    let surah: Surah
    let verses: [Verse]
    @State private var isGenerating = false
    @State private var errorMessage: String?

    var body: some View {
        Button {
            generateAndShare()
        } label: {
            Image(systemName: isGenerating ? "hourglass" : "square.and.arrow.up")
                .foregroundStyle(Azanify.Palette.gold)
        }
        .disabled(isGenerating)
        .alert("Video paylaşımı", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func generateAndShare() {
        isGenerating = true
        let selectedVerses = verses
        Task {
            do {
                let url = try await ShareVideoGenerator.generate(surah: surah, verses: selectedVerses)
                await MainActor.run {
                    isGenerating = false
                    present(items: [url])
                }
            } catch {
                await MainActor.run {
                    isGenerating = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func present(items: [Any]) {
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
