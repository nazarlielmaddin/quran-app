import Foundation
import SwiftUI

@MainActor
final class AudioDownloader: NSObject, ObservableObject, URLSessionDownloadDelegate {
    static let shared = AudioDownloader()

    @Published private(set) var progress: [String: Double] = [:]   // key: "reciter:surah"
    @Published private(set) var downloaded: Set<String> = []

    private var session: URLSession!
    private var continuations: [String: (URL?) -> Void] = [:]

    override init() {
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: .main)
        scanDownloaded()
    }

    // MARK: - Public

    func key(reciterId: String, surahId: Int) -> String { "\(reciterId):\(surahId)" }

    func isDownloaded(reciterId: String, surahId: Int) -> Bool {
        downloaded.contains(key(reciterId: reciterId, surahId: surahId))
    }

    func localURL(reciterId: String, surahId: Int) -> URL? {
        let url = fileURL(reciterId: reciterId, surahId: surahId)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    func download(reciterId: String, surahId: Int, remoteURL: URL) {
        let k = key(reciterId: reciterId, surahId: surahId)
        guard !downloaded.contains(k) else { return }
        progress[k] = 0
        let task = session.downloadTask(with: remoteURL)
        task.taskDescription = k
        task.resume()
    }

    func cancel(reciterId: String, surahId: Int) {
        let k = key(reciterId: reciterId, surahId: surahId)
        progress.removeValue(forKey: k)
    }

    // MARK: - URLSessionDownloadDelegate

    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                               didFinishDownloadingTo location: URL) {
        guard let k = downloadTask.taskDescription else { return }
        let parts = k.split(separator: ":")
        guard parts.count == 2, let surah = Int(parts[1]) else { return }
        let reciterId = String(parts[0])

        let dest = fileURL(reciterId: reciterId, surahId: surah)
        try? FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? FileManager.default.removeItem(at: dest)
        try? FileManager.default.moveItem(at: location, to: dest)

        Task { @MainActor in
            self.progress.removeValue(forKey: k)
            if FileManager.default.fileExists(atPath: dest.path) {
                self.downloaded.insert(k)
            }
        }
    }

    nonisolated func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                               didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                               totalBytesExpectedToWrite: Int64) {
        guard let k = downloadTask.taskDescription else { return }
        let p = totalBytesExpectedToWrite > 0 ? Double(totalBytesWritten) / Double(totalBytesExpectedToWrite) : 0
        Task { @MainActor in
            self.progress[k] = p
        }
    }

    // MARK: - Private

    private func fileURL(reciterId: String, surahId: Int) -> URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Azanify/audio/\(reciterId)", isDirectory: true)
        return dir.appendingPathComponent("\(surahId).mp3")
    }

    private func scanDownloaded() {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Azanify/audio", isDirectory: true)
        guard let reciters = try? FileManager.default.contentsOfDirectory(at: base, includingPropertiesForKeys: nil) else { return }
        for reciterDir in reciters {
            let reciterId = reciterDir.lastPathComponent
            guard let files = try? FileManager.default.contentsOfDirectory(at: reciterDir, includingPropertiesForKeys: nil) else { continue }
            for f in files where f.pathExtension == "mp3" {
                if let n = Int(f.deletingPathExtension().lastPathComponent) {
                    downloaded.insert("\(reciterId):\(n)")
                }
            }
        }
    }
}
