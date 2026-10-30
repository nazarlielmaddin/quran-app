import SwiftUI

struct MiniPlayer: View {
    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var ambient: AmbientPlayer

    var body: some View {
        if player.surahNumber != nil || ambient.currentId != nil {
            VStack(spacing: 0) {
                if player.surahNumber != nil {
                    ProgressView(value: player.currentTime, total: max(player.duration, 1))
                        .tint(Azanify.Palette.gold)
                        .scaleEffect(x: 1, y: 0.6, anchor: .center)
                }

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        if player.surahNumber != nil {
                            Text(player.title)
                                .font(AppFont.text(13, .semibold))
                                .foregroundStyle(Azanify.Palette.mist)
                                .lineLimit(1)
                            Text(player.reciterName)
                                .font(AppFont.text(11))
                                .foregroundStyle(Azanify.Palette.mistFaint)
                                .lineLimit(1)
                        } else {
                            Text("Atmosfer")
                                .font(AppFont.text(13, .semibold))
                                .foregroundStyle(Azanify.Palette.mist)
                            Text(ambient.currentId ?? "")
                                .font(AppFont.text(11))
                                .foregroundStyle(Azanify.Palette.mistFaint)
                        }
                    }

                    Spacer(minLength: 8)

                    if player.surahNumber != nil {
                        Button {
                            player.toggle()
                        } label: {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(Azanify.Palette.goldSoft)
                        }
                        .buttonStyle(.plain)
                    }

                    Button {
                        player.stop()
                        ambient.stop()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                if ambient.currentId != nil || player.surahNumber != nil {
                    Divider().overlay(Color.white.opacity(0.08))
                    HStack(spacing: 12) {
                        Image(systemName: "book.closed.fill")
                            .foregroundStyle(Azanify.Palette.goldSoft)
                        Slider(
                            value: Binding(
                                get: { Double(player.volume) },
                                set: { player.setVolume(Float($0)) }
                            ),
                            in: 0...1
                        )
                        Image(systemName: "leaf.fill")
                            .foregroundStyle(Azanify.Palette.forestSoft)
                        Slider(
                            value: Binding(
                                get: { Double(ambient.volume) },
                                set: { ambient.setVolume(Float($0)) }
                            ),
                            in: 0...1
                        )
                    }
                    .font(.system(size: 11))
                    .tint(Azanify.Palette.gold)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }
            }
            .background(Azanify.Palette.inkPanel.opacity(0.96))
            .overlay(alignment: .top) {
                Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1)
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
