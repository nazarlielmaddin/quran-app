import AVFoundation
import SwiftUI

@MainActor
final class AmbientPlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    @Published private(set) var currentId: String? = nil

    private var player: AVPlayer?

    func play(url: URL, id: String) {
        if currentId == id && isPlaying {
            pause()
            return
        }
        stop()
        currentId = id
        let item = AVPlayerItem(url: url)
        let p = AVPlayer(playerItem: item)
        p.isMuted = false
        p.volume = 0.6
        p.actionAtItemEnd = .none
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak p] _ in
            p?.seek(to: .zero)
            p?.play()
        }
        player = p
        p.play()
        isPlaying = true
    }

    func pause() {
        player?.pause()
        isPlaying = false
    }

    func stop() {
        player?.pause()
        player = nil
        isPlaying = false
        currentId = nil
    }

    func toggle(id: String, url: URL) {
        if currentId == id {
            if isPlaying { pause() } else { player?.play(); isPlaying = true }
        } else {
            play(url: url, id: id)
        }
    }
}
