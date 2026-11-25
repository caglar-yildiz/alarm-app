import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }

    /// ARGB like the Android colors (0xAARRGGBB).
    init(argb: UInt32) {
        self.init(hex: argb & 0xFFFFFF, alpha: Double((argb >> 24) & 0xFF) / 255)
    }

    static let midnight = Color(hex: 0x0B1021)
    static let muted = Color(hex: 0xC0C4E1)
    static let accent = Color(hex: 0xC5BAFF)
    static let gold = Color(hex: 0xFFD98C)
    static let mint = Color(hex: 0x9BE8C0)
    static let sky = Color(hex: 0x9BDFFF)
    static let rose = Color(hex: 0xFFB4C4)
}

let accentGradient = LinearGradient(colors: [Color(hex: 0x9BDFFF), Color(hex: 0xC3A0FC)], startPoint: .topLeading, endPoint: .bottomTrailing)

/// The night sky behind every screen: deep blue with two soft glows and a fixed field of stars.
struct Starfield: View {
    private struct Star { var x: CGFloat; var y: CGFloat; var size: CGFloat }
    private static let stars: [Star] = {
        var generator = SeededGenerator(seed: 87)
        return (0..<94).map { _ in
            Star(x: .random(in: 0...1, using: &generator), y: .random(in: 0...1, using: &generator), size: .random(in: 0...1, using: &generator))
        }
    }()

    var body: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.midnight))
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                Gradient(colors: [Color(argb: 0x783E1B66), Color(argb: 0x003E1B66)]),
                center: CGPoint(x: size.width * 0.94, y: size.height * 0.72), startRadius: 0, endRadius: size.width * 0.86))
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                Gradient(colors: [Color(argb: 0x68302C76), Color(argb: 0x00302C76)]),
                center: CGPoint(x: size.width * 0.13, y: size.height * 0.12), startRadius: 0, endRadius: size.width * 0.61))
            for star in Self.stars {
                let radius = (1 + star.size) * size.width / 530
                let rect = CGRect(x: star.x * size.width - radius, y: star.y * size.height - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect), with: .color(star.size > 0.9 ? Color(argb: 0xA7C6C6FF) : Color(argb: 0x49C6C6FF)))
            }
        }
        .ignoresSafeArea()
    }
}

/// A small deterministic random generator, so the stars stay in place.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed &* 0x9E3779B97F4A7C15 | 1 }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

/// The ringed purple planet used on the home card and the ringing screen.
struct AstralPlanet: View {
    var body: some View {
        Canvas { context, size in
            let x = size.width / 2, y = size.height / 2
            let radius = min(x, y) * 0.51
            guard radius > 0 else { return }
            let circle = Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
            context.fill(circle, with: .radialGradient(
                Gradient(stops: [.init(color: Color(hex: 0xB593FF), location: 0), .init(color: Color(hex: 0x514291), location: 0.43),
                                 .init(color: Color(hex: 0x131730), location: 1)]),
                center: CGPoint(x: x + radius * 0.42, y: y - radius * 0.48), startRadius: 0, endRadius: radius * 1.6))
            var ring = context
            ring.translateBy(x: x, y: y)
            ring.rotate(by: .degrees(-25))
            let oval = Path(ellipseIn: CGRect(x: -radius * 1.76, y: -radius * 0.34, width: radius * 3.52, height: radius * 0.68))
            ring.stroke(oval, with: .color(Color(argb: 0xB7C3BBFF)), lineWidth: radius * 0.047)
        }
        .accessibilityHidden(true)
    }
}

struct GlassCard: ViewModifier {
    var colors: [Color] = [Color(argb: 0xDD242F58), Color(argb: 0xD137204F)]
    var radius: CGFloat = 20
    var stroke = Color(argb: 0x748C8CD8)
    var padding = EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16)

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(stroke, lineWidth: 1))
    }
}

extension View {
    func card(_ colors: [Color] = [Color(argb: 0xDD242F58), Color(argb: 0xD137204F)], radius: CGFloat = 20,
              stroke: Color = Color(argb: 0x748C8CD8)) -> some View {
        modifier(GlassCard(colors: colors, radius: radius, stroke: stroke))
    }

    /// The standard screen frame: night sky, scrolling column, 20 pt margins.
    func screen() -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) { self }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Starfield())
    }
}

enum ButtonKind { case primary, secondary, destructive, gold, mint }

struct BengButtonStyle: ButtonStyle {
    var kind: ButtonKind = .primary
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        let background: AnyShapeStyle
        let foreground: Color
        switch kind {
        case .primary:
            background = AnyShapeStyle(accentGradient)
            foreground = .midnight
        case .secondary:
            background = AnyShapeStyle(LinearGradient(colors: [Color(argb: 0xA6253152), Color(argb: 0xA42F2149)], startPoint: .topLeading, endPoint: .bottomTrailing))
            foreground = .accent
        case .destructive:
            background = AnyShapeStyle(LinearGradient(colors: [Color(argb: 0xE65E213E), Color(argb: 0xE6461B36)], startPoint: .topLeading, endPoint: .bottomTrailing))
            foreground = Color(hex: 0xFFC4D1)
        case .gold:
            background = AnyShapeStyle(LinearGradient(colors: [.gold, Color(hex: 0xC3A0FC)], startPoint: .topLeading, endPoint: .bottomTrailing))
            foreground = .midnight
        case .mint:
            background = AnyShapeStyle(LinearGradient(colors: [.mint, .sky], startPoint: .topLeading, endPoint: .bottomTrailing))
            foreground = .midnight
        }
        return configuration.label
            .font(.body.weight(kind == .secondary ? .regular : .medium))
            .multilineTextAlignment(.center)
            .foregroundStyle(enabled ? foreground : .muted)
            .padding(.vertical, 12)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color(argb: 0x748C8CD8), lineWidth: 1))
            .opacity(configuration.isPressed ? 0.75 : (enabled ? 1 : 0.6))
    }
}

extension Button {
    func bengStyle(_ kind: ButtonKind = .primary) -> some View { buttonStyle(BengButtonStyle(kind: kind)) }
}

/// A small rounded tag, e.g. a status or a Premium marker.
struct Pill: View {
    var text: String
    var color: Color = Color(hex: 0xE7DFFF)
    var fill: Color = Color(argb: 0x604E407B)
    var stroke: Color = Color(argb: 0x668C8CD8)
    var icon: String?

    var body: some View {
        HStack(spacing: 6) {
            if let icon { Image(systemName: icon).font(.caption) }
            Text(text).lineLimit(1)
        }
        .font(.caption.weight(.medium))
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(fill, in: Capsule())
        .overlay(Capsule().stroke(stroke, lineWidth: 1))
    }

    static func premium() -> Pill { Pill(text: L.s("premium_badge"), color: .gold, fill: Color(argb: 0x40FFD98C), stroke: Color(argb: 0x99FFD98C)) }
    static func on(_ text: String) -> Pill { Pill(text: text, color: .mint, fill: Color(argb: 0x403A8A64), stroke: Color(argb: 0x889BE8C0)) }
}

/// A round, softly lit badge that holds a line icon.
struct IconBadge: View {
    var symbol: String
    var size: CGFloat = 52
    var colors: [Color] = [Color(hex: 0x5B4BC4), Color(hex: 0x2B3C86)]
    var tint: Color = .white

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.42, weight: .regular))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
            .overlay(Circle().stroke(Color.white.opacity(0.4), lineWidth: 1))
            .accessibilityHidden(true)
    }
}

/// A heading with a light time-like font.
struct TimeText: View {
    var text: String
    var size: CGFloat
    var color: Color = .white

    init(_ text: String, size: CGFloat, color: Color = .white) {
        self.text = text
        self.size = size
        self.color = color
    }

    var body: some View {
        Text(text)
            .font(.system(size: size, weight: .light, design: .default))
            .tracking(size * 0.055)
            .monospacedDigit()
            .foregroundStyle(color)
    }
}

/// A toggle row in the app's style.
struct BengToggle: View {
    var title: String
    var locked = false
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            Text(locked ? "\(title)  ✦" : title).font(.body).foregroundStyle(.white)
        }
        .tint(Color(hex: 0x6969BD))
        .frame(minHeight: 48)
        .accessibilityLabel(locked ? L.s("premium_feature_description", title) : title)
    }
}

/// A row of choices that look like buttons, the chosen one filled.
struct ChoiceRow<Value: Hashable>: View {
    var values: [Value]
    var selected: Value
    var columns = 3
    var label: (Value) -> String
    var pick: (Value) -> Void

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: columns), spacing: 8) {
            ForEach(values, id: \.self) { value in
                Button(label(value)) { pick(value) }.bengStyle(value == selected ? .primary : .secondary)
            }
        }
    }
}

struct HeroHeader: View {
    var symbol: String
    var colors: [Color]
    var title: String
    var description: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                IconBadge(symbol: symbol, size: 60, colors: colors)
                Text(title).font(.title3.weight(.semibold)).foregroundStyle(.white)
            }
            Text(description).font(.subheadline).foregroundStyle(Color.muted)
        }
        .card()
    }
}

extension Text {
    func sectionTitle() -> some View {
        font(.title2.weight(.semibold)).foregroundStyle(.white).padding(.top, 18).accessibilityAddTraits(.isHeader)
    }
}
