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
    @Published private(set) var volume: Float = 0.9

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var nowPlayingInfo: [String: Any] = [:]

    init() {
        configureSession()
        configureRemoteCommands()
    }

    // MARK: - Public

    func play(reciterId: String, surahId: Int, title: String, reciterName: String) {
        guard let remote = RecitationCatalog.url(reciterId: reciterId, surahId: surahId) else {
            return
        }
        stop()

        self.title = title
        self.reciterName = reciterName
        self.surahNumber = surahId

        let url = AudioDownloader.shared.localURL(reciterId: reciterId, surahId: surahId) ?? remote
        let item = AVPlayerItem(url: url)
        let p = AVPlayer(playerItem: item)
        p.volume = volume
        player = p

        p.play()
        isPlaying = true

        attachTimeObserver(to: p)
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.isPlaying = false
                self?.updateNowPlaying(playing: false)
            }
        }
        updateNowPlaying(playing: true)
    }

    func toggle() {
        guard let p = player else { return }
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    func play() {
        guard let player else { return }
        player.play()
        isPlaying = true
        updateNowPlaying(playing: true)
    }

    func pause() {
        player?.pause()
        isPlaying = false
        updateNowPlaying(playing: false)
    }

    func stop() {
        if let observer = timeObserver, let p = player {
            p.removeTimeObserver(observer)
        }
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = nil
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

    func setVolume(_ value: Float) {
        let safeValue = min(max(value, 0), 1)
        volume = safeValue
        player?.volume = safeValue
    }

    func seek(to seconds: Double) {
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
    }

    // MARK: - Private

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [.allowBluetoothA2DP, .allowAirPlay])
            try session.setActive(true)
        } catch {
            assertionFailure("Unable to activate playback audio session: \(error)")
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
                self.updateNowPlaying(playing: self.isPlaying)
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
            Task { @MainActor in self?.play() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
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
