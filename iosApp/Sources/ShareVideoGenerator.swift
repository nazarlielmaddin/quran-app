import AVFoundation
import CoreImage
import CoreVideo
import UIKit

enum ShareVideoGenerator {
    private static let width = 1080
    private static let height = 1920
    private static let frameRate = 30
    private static let duration: Double = 6

    static func generate(surah: Surah, verses: [Verse]) async throws -> URL {
        guard !verses.isEmpty else {
            throw NSError(domain: "AzanifyShare", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Paylaşmaq üçün ayə tapılmadı."
            ])
        }

        let directory = FileManager.default.temporaryDirectory
        let output = directory.appendingPathComponent(
            "azanify-\(surah.number)-\(UUID().uuidString).mp4"
        )
        try? FileManager.default.removeItem(at: output)

        let writer = try AVAssetWriter(outputURL: output, fileType: .mp4)
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: width,
            AVVideoHeightKey: height,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 6_000_000,
                AVVideoExpectedSourceFrameRateKey: frameRate,
                AVVideoMaxKeyFrameIntervalKey: frameRate
            ]
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height
            ]
        )
        guard writer.canAdd(input) else {
            throw NSError(domain: "AzanifyShare", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Video kodlayıcısı bu cihazda dəstəklənmir."
            ])
        }
        writer.add(input)
        guard writer.startWriting() else {
            throw writer.error ?? NSError(domain: "AzanifyShare", code: 5, userInfo: [
                NSLocalizedDescriptionKey: "Video yazılması başlatıla bilmədi."
            ])
        }
        writer.startSession(atSourceTime: .zero)

        let image = renderImage(surah: surah, verses: verses)
        let context = CIContext()
        let totalFrames = Int(duration * Double(frameRate))
        for frame in 0..<totalFrames {
            while !input.isReadyForMoreMediaData {
                guard writer.status == .writing else {
                    throw writer.error ?? NSError(domain: "AzanifyShare", code: 8, userInfo: [
                        NSLocalizedDescriptionKey: "Video kodlayıcısı dayandı."
                    ])
                }
                try await Task.sleep(nanoseconds: 10_000_000)
            }
            guard let buffer = makeBuffer(image: image, context: context) else {
                input.markAsFinished()
                writer.cancelWriting()
                throw NSError(domain: "AzanifyShare", code: 3, userInfo: [
                    NSLocalizedDescriptionKey: "Video kadrı yaradıla bilmədi."
                ])
            }
            let time = CMTime(value: CMTimeValue(frame), timescale: CMTimeScale(frameRate))
            guard writer.status == .writing,
                  adaptor.append(buffer, withPresentationTime: time) else {
                input.markAsFinished()
                writer.cancelWriting()
                throw writer.error ?? NSError(domain: "AzanifyShare", code: 6, userInfo: [
                    NSLocalizedDescriptionKey: "Video kadrı yazıla bilmədi."
                ])
            }
        }

        input.markAsFinished()
        try await finishWriting(writer)
        guard writer.status == .completed else {
            throw writer.error ?? NSError(domain: "AzanifyShare", code: 4, userInfo: [
                NSLocalizedDescriptionKey: "Video ixracı uğursuz oldu."
            ])
        }
        return output
    }

    private static func finishWriting(_ writer: AVAssetWriter) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            writer.finishWriting {
                if writer.status == .completed {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: writer.error ?? NSError(
                        domain: "AzanifyShare",
                        code: 7,
                        userInfo: [NSLocalizedDescriptionKey: "Video ixracı uğursuz oldu."]
                    ))
                }
            }
        }
    }

    private static func renderImage(surah: Surah, verses: [Verse]) -> CGImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        return renderer.image { context in
            let bounds = CGRect(x: 0, y: 0, width: width, height: height)
            let colors = [UIColor(red: 0.03, green: 0.08, blue: 0.12, alpha: 1).cgColor,
                          UIColor(red: 0.12, green: 0.18, blue: 0.15, alpha: 1).cgColor]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
            context.cgContext.drawLinearGradient(
                gradient,
                start: CGPoint(x: 0, y: 0),
                end: CGPoint(x: width, y: height),
                options: []
            )

            let inset: CGFloat = 88
            let card = bounds.insetBy(dx: inset, dy: 180)
            UIColor.white.withAlphaComponent(0.06).setFill()
            context.cgContext.fill(card)
            UIColor(red: 0.82, green: 0.62, blue: 0.25, alpha: 0.8).setStroke()
            context.cgContext.setLineWidth(3)
            context.cgContext.stroke(card)

            let title = "\(surah.azerbaijaniName)  •  \(surah.englishName)"
            NSString(string: title).draw(
                in: CGRect(x: inset + 36, y: 260, width: CGFloat(width) - inset * 2 - 72, height: 70),
                withAttributes: [
                    .font: UIFont.systemFont(ofSize: 38, weight: .semibold),
                    .foregroundColor: UIColor(red: 0.95, green: 0.78, blue: 0.42, alpha: 1)
                ]
            )

            var y: CGFloat = 460
            for verse in verses.prefix(8) {
                let arabic = NSString(string: verse.arabic)
                arabic.draw(
                    in: CGRect(x: inset + 42, y: y, width: CGFloat(width) - inset * 2 - 84, height: 150),
                    withAttributes: [
                        .font: UIFont.systemFont(ofSize: 48),
                        .foregroundColor: UIColor.white,
                        .paragraphStyle: {
                            let style = NSMutableParagraphStyle()
                            style.alignment = .right
                            style.lineSpacing = 10
                            return style
                        }()
                    ]
                )
                y += 180
                NSString(string: verse.translation).draw(
                    in: CGRect(x: inset + 42, y: y, width: CGFloat(width) - inset * 2 - 84, height: 110),
                    withAttributes: [
                        .font: UIFont.systemFont(ofSize: 28),
                        .foregroundColor: UIColor.white.withAlphaComponent(0.72),
                        .paragraphStyle: {
                            let style = NSMutableParagraphStyle()
                            style.alignment = .left
                            style.lineSpacing = 6
                            return style
                        }()
                    ]
                )
                y += 170
                if y > CGFloat(height) - 260 { break }
            }

            NSString(string: "Azanify").draw(
                in: CGRect(x: inset, y: CGFloat(height) - 220, width: CGFloat(width) - inset * 2, height: 60),
                withAttributes: [
                    .font: UIFont.systemFont(ofSize: 30, weight: .medium),
                    .foregroundColor: UIColor.white.withAlphaComponent(0.55)
                ]
            )
        }.cgImage!
    }

    private static func makeBuffer(image: CGImage, context: CIContext) -> CVPixelBuffer? {
        var buffer: CVPixelBuffer?
        let attributes = [
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true
        ] as CFDictionary
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attributes,
            &buffer
        )
        guard let buffer else { return nil }
        context.render(CIImage(cgImage: image), to: buffer)
        return buffer
    }
}
