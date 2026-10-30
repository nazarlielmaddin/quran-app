import AVFoundation
import SwiftUI

@MainActor
final class AmbientPlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    @Published private(set) var currentId: String? = nil
    @Published private(set) var volume: Float = 0.6

    private var player: AVPlayer?
    private var endObserver: NSObjectProtocol?

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
        p.volume = volume
        p.actionAtItemEnd = .none
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self, weak p] _ in
            p?.seek(to: .zero)
            if self?.isPlaying == true {
                p?.play()
            }
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
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = nil
        player?.pause()
        player = nil
        isPlaying = false
        currentId = nil
    }

    func setVolume(_ value: Float) {
        let safeValue = min(max(value, 0), 1)
        volume = safeValue
        player?.volume = safeValue
    }

    func toggle(id: String, url: URL) {
        if currentId == id {
            if isPlaying { pause() } else { player?.play(); isPlaying = true }
        } else {
            play(url: url, id: id)
        }
    }
}
