import AVFoundation
import MediaPlayer
import SwiftUI

struct Recitation: Decodable {
    let id: String
    let reciterId: String
    let surahId: Int
    let audioUrl: String
    let durationSeconds: Int?
    let source: String?
    let fallbackAudioUrl: String?
}

enum RecitationCatalog {
    static let all: [Recitation] = {
        guard
            let url = Bundle.main.url(forResource: "recitations", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let list = try? JSONDecoder().decode([Recitation].self, from: data)
        else {
            return []
        }
        return list
    }()

    static func url(reciterId: String, surahId: Int) -> URL? {
        guard let entry = all.first(where: { $0.reciterId == reciterId && $0.surahId == surahId }) else {
            return nil
        }
        return URL(string: entry.audioUrl)
    }
}

@MainActor
final class AudioPlayer: ObservableObject {
    @Published private(set) var isPlaying = false
    @Published private(set) var title = ""
    @Published private(set) var reciterName = ""
    @Published private(set) var surahNumber: Int? = nil
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var nowPlayingInfo: [String: Any] = [:]

    init() {
        configureSession()
        configureRemoteCommands()
    }

    // MARK: - Public

    func play(reciterId: String, surahId: Int, title: String, reciterName: String) {
        guard let url = RecitationCatalog.url(reciterId: reciterId, surahId: surahId) else {
            return
        }
        stop()

        self.title = title
        self.reciterName = reciterName
        self.surahNumber = surahId

        let item = AVPlayerItem(url: url)
        let p = AVPlayer(playerItem: item)
        player = p

        p.play()
        isPlaying = true

        attachTimeObserver(to: p)
        updateNowPlaying(playing: true)
    }

    func toggle() {
        guard let p = player else { return }
        if isPlaying {
            p.pause()
            isPlaying = false
        } else {
            p.play()
            isPlaying = true
        }
        updateNowPlaying(playing: isPlaying)
    }

    func stop() {
        if let observer = timeObserver, let p = player {
            p.removeTimeObserver(observer)
        }
        timeObserver = nil
        player?.pause()
        player = nil
        isPlaying = false
        currentTime = 0
        duration = 0
        title = ""
        reciterName = ""
        surahNumber = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    func seek(to seconds: Double) {
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
    }

    // MARK: - Private

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            // Non-fatal: playback still works without the session in many cases.
        }
    }

    private func attachTimeObserver(to p: AVPlayer) {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = p.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self else { return }
            Task { @MainActor in
                self.currentTime = time.seconds.isFinite ? time.seconds : 0
                if let item = p.currentItem,
                   let d = item.duration.secondsIfFinite {
                    self.duration = d
                }
                if p.timeControlStatus == .playing {
                    self.isPlaying = true
                } else if p.timeControlStatus == .paused {
                    self.isPlaying = false
                }
            }
        }
    }

    private func updateNowPlaying(playing: Bool) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: reciterName,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0
        ]
        if duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
    }
}

private extension CMTime {
    var secondsIfFinite: Double? {
        let s = CMTimeGetSeconds(self)
        return s.isFinite ? s : nil
    }
}
