import SwiftUI

/// Design tokens mirrored 1:1 from the Android `ui/theme/Color.kt` (Azanify 1.21.1).
/// Deep forest / ink base, warm paper light, restrained champagne gold.
enum Azanify {

    enum Palette {
        static let ink = Color(hex: 0x07100C)
        static let inkPanel = Color(hex: 0x0B1712)
        static let inkLift = Color(hex: 0x0E1D17)
        static let mist = Color(hex: 0xF4F1E8)
        static let mistDim = Color(hex: 0xA7B3AA)
        static let mistFaint = Color(hex: 0x7D8B82)
        static let gold = Color(hex: 0xC9A86A)
        static let goldSoft = Color(hex: 0xE8D3A9)
        static let goldDeep = Color(hex: 0x8E6E3D)
        static let forest = Color(hex: 0x0E3B2E)
        static let forestSoft = Color(hex: 0x1E5C46)
    }

    static var background: some View {
        ZStack {
            LinearGradient(
                colors: [Palette.ink, Palette.inkLift, Palette.ink],
                startPoint: .top,
                endPoint: .bottom
            )
            RadialGradient(
                colors: [Palette.forest.opacity(0.55), .clear],
                center: .topLeading,
                startRadius: 0,
                endRadius: 420
            )
        }
    }

    static var card: some View {
        RoundedRectangle(cornerRadius: 24, style: .continuous)
            .fill(Palette.inkPanel.opacity(0.92))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.07), lineWidth: 1)
            )
    }
}

/// Fonts shipped inside the APK: Inter for latin, Inter Display for headings,
/// Amiri for Arabic script. PostScript names are used so SwiftUI picks the
/// exact face instead of synthesising a weight.
enum AppFont {
    enum Weight {
        case regular, medium, semibold, bold

        var postScript: String {
            switch self {
            case .regular: return "Regular"
            case .medium: return "Medium"
            case .semibold: return "SemiBold"
            case .bold: return "Bold"
            }
        }
    }

    static func text(_ size: CGFloat, _ weight: Weight = .regular) -> Font {
        .custom("Inter-\(weight.postScript)", size: size)
    }

    static func display(_ size: CGFloat, _ weight: Weight = .regular) -> Font {
        .custom("InterDisplay-\(weight.postScript)", size: size)
    }

    static func arabic(_ size: CGFloat, bold: Bool = false) -> Font {
        .custom(bold ? "Amiri-Bold" : "Amiri-Regular", size: size)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }

    init?(hexString: String) {
        var value = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.count == 6, let number = UInt32(value, radix: 16) else { return nil }
        self.init(hex: number)
    }
}

/// Small caps section label used across the app.
struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(AppFont.text(11, .semibold))
            .tracking(2.2)
            .foregroundStyle(Azanify.Palette.gold.opacity(0.85))
    }
}

extension View {
    func azanifyCard() -> some View {
        background(Azanify.card)
    }
}
