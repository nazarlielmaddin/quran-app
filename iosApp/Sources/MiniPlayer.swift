import SwiftUI

struct MiniPlayer: View {
    @EnvironmentObject private var player: AudioPlayer

    var body: some View {
        if player.surahNumber != nil {
            VStack(spacing: 0) {
                ProgressView(value: player.currentTime, total: max(player.duration, 1))
                    .tint(Azanify.Palette.gold)
                    .scaleEffect(x: 1, y: 0.6, anchor: .center)

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(player.title)
                            .font(AppFont.text(13, .semibold))
                            .foregroundStyle(Azanify.Palette.mist)
                            .lineLimit(1)
                        Text(player.reciterName)
                            .font(AppFont.text(11))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)

                    Button {
                        player.toggle()
                    } label: {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(Azanify.Palette.goldSoft)
                    }
                    .buttonStyle(.plain)

                    Button {
                        player.stop()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Azanify.Palette.mistFaint)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            .background(Azanify.Palette.inkPanel.opacity(0.96))
            .overlay(alignment: .top) {
                Rectangle().fill(Color.white.opacity(0.06)).frame(height: 1)
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
