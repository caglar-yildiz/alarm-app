import SwiftUI

/// Draws the weather symbols in the app's soft, night-sky style, without image assets.
enum WeatherArt {
    static func draw(_ context: GraphicsContext, sky: Sky, night: Bool, center c: CGPoint, size s: CGFloat) {
        switch sky {
        case .clear:
            celestial(context, night, c, s * 0.26)
        case .partly:
            celestial(context, night, CGPoint(x: c.x - s * 0.14, y: c.y - s * 0.14), s * 0.2)
            cloud(context, CGPoint(x: c.x + s * 0.06, y: c.y + s * 0.05), s * 0.62, night: night, wet: false)
        case .cloudy:
            var faded = context
            faded.opacity = 0.6
            cloud(faded, CGPoint(x: c.x - s * 0.13, y: c.y - s * 0.13), s * 0.48, night: true, wet: false)
            cloud(context, CGPoint(x: c.x + s * 0.05, y: c.y + s * 0.04), s * 0.68, night: night, wet: false)
        case .fog:
            cloud(context, CGPoint(x: c.x, y: c.y - s * 0.12), s * 0.62, night: night, wet: false)
            var lines = Path()
            lines.move(to: CGPoint(x: c.x - s * 0.3, y: c.y + s * 0.18))
            lines.addLine(to: CGPoint(x: c.x + s * 0.22, y: c.y + s * 0.18))
            lines.move(to: CGPoint(x: c.x - s * 0.2, y: c.y + s * 0.29))
            lines.addLine(to: CGPoint(x: c.x + s * 0.3, y: c.y + s * 0.29))
            context.stroke(lines, with: .color(Color(argb: 0xCCD4D8F0)), style: StrokeStyle(lineWidth: s * 0.045, lineCap: .round))
        case .rain, .sleet, .snow, .thunder:
            let top = c.y + s * 0.13
            switch sky {
            case .rain: drops(context, c.x, top, s, [-1, 0, 1])
            case .sleet:
                drops(context, c.x, top, s, [-1, 1])
                flakes(context, c.x, top, s, [0])
            case .snow: flakes(context, c.x, top, s, [-1, 0, 1])
            default: bolt(context, c.x, top, s)
            }
            cloud(context, CGPoint(x: c.x, y: c.y - s * 0.14), s * 0.7, night: night, wet: true)
        }
    }

    private static func circle(_ p: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
    }

    private static func celestial(_ context: GraphicsContext, _ night: Bool, _ p: CGPoint, _ r: CGFloat) {
        if night { moon(context, p, r) } else { sun(context, p, r) }
    }

    private static func sun(_ context: GraphicsContext, _ p: CGPoint, _ r: CGFloat) {
        context.fill(circle(p, r * 2.1), with: .radialGradient(Gradient(colors: [Color(argb: 0x66FFD98C), Color(argb: 0x00FFD98C)]),
                                                               center: p, startRadius: 0, endRadius: r * 2.1))
        var rays = Path()
        for ray in 0..<8 {
            let angle = Double(ray) * .pi / 4
            rays.move(to: CGPoint(x: p.x + cos(angle) * r * 1.3, y: p.y + sin(angle) * r * 1.3))
            rays.addLine(to: CGPoint(x: p.x + cos(angle) * r * 1.62, y: p.y + sin(angle) * r * 1.62))
        }
        context.stroke(rays, with: .color(.gold), style: StrokeStyle(lineWidth: r * 0.16, lineCap: .round))
        context.fill(circle(p, r), with: .radialGradient(
            Gradient(stops: [.init(color: Color(hex: 0xFFF6D2), location: 0), .init(color: Color(hex: 0xFFCF73), location: 0.55),
                             .init(color: Color(hex: 0xF5A04E), location: 1)]),
            center: CGPoint(x: p.x - r * 0.3, y: p.y - r * 0.35), startRadius: 0, endRadius: r * 1.3))
    }

    private static func moon(_ context: GraphicsContext, _ p: CGPoint, _ r: CGFloat) {
        context.fill(circle(p, r * 2), with: .radialGradient(Gradient(colors: [Color(argb: 0x55C5BAFF), Color(argb: 0x00C5BAFF)]),
                                                             center: p, startRadius: 0, endRadius: r * 2))
        let crescent = circle(p, r).subtracting(circle(CGPoint(x: p.x + r * 0.52, y: p.y - r * 0.34), r * 0.86))
        context.fill(crescent, with: .linearGradient(Gradient(colors: [Color(hex: 0xFBF8FF), Color(hex: 0xB9ADF5)]),
                                                     startPoint: CGPoint(x: p.x - r, y: p.y - r), endPoint: CGPoint(x: p.x + r, y: p.y + r)))
        context.fill(circle(CGPoint(x: p.x + r * 1.05, y: p.y + r * 0.35), r * 0.08), with: .color(Color(argb: 0xCCFFFFFF)))
        context.fill(circle(CGPoint(x: p.x + r * 0.7, y: p.y + r * 0.95), r * 0.05), with: .color(Color(argb: 0xCCFFFFFF)))
    }

    private static func cloud(_ context: GraphicsContext, _ p: CGPoint, _ w: CGFloat, night: Bool, wet: Bool) {
        var path = Path(roundedRect: CGRect(x: p.x - w * 0.5, y: p.y - w * 0.02, width: w, height: w * 0.3), cornerRadius: w * 0.15)
        path.addPath(circle(CGPoint(x: p.x - w * 0.22, y: p.y + w * 0.02), w * 0.2))
        path.addPath(circle(CGPoint(x: p.x + w * 0.04, y: p.y - w * 0.07), w * 0.28))
        path.addPath(circle(CGPoint(x: p.x + w * 0.29, y: p.y + w * 0.07), w * 0.17))
        let colors: [Color] = wet ? [Color(hex: 0xE4E7F7), Color(hex: 0x9EA6CC)]
            : night ? [Color(hex: 0xE2DDFA), Color(hex: 0x9890C9)] : [.white, Color(hex: 0xCED6F4)]
        context.fill(path, with: .linearGradient(Gradient(colors: colors), startPoint: CGPoint(x: p.x, y: p.y - w * 0.36),
                                                 endPoint: CGPoint(x: p.x, y: p.y + w * 0.28)))
    }

    private static func drops(_ context: GraphicsContext, _ cx: CGFloat, _ top: CGFloat, _ s: CGFloat, _ columns: [Int]) {
        var path = Path()
        for column in columns {
            let x = cx + CGFloat(column) * s * 0.17
            let offset = column == 0 ? s * 0.05 : 0
            path.move(to: CGPoint(x: x + s * 0.03, y: top + offset))
            path.addLine(to: CGPoint(x: x - s * 0.03, y: top + offset + s * 0.14))
        }
        context.stroke(path, with: .color(Color(hex: 0x8FD0FF)), style: StrokeStyle(lineWidth: s * 0.045, lineCap: .round))
    }

    private static func flakes(_ context: GraphicsContext, _ cx: CGFloat, _ top: CGFloat, _ s: CGFloat, _ columns: [Int]) {
        for column in columns {
            let x = cx + CGFloat(column) * s * 0.17
            let y = top + s * 0.07 + (column == 0 ? s * 0.05 : 0)
            context.fill(circle(CGPoint(x: x, y: y), s * 0.038), with: .color(Color(hex: 0xF4F7FF)))
        }
    }

    private static func bolt(_ context: GraphicsContext, _ cx: CGFloat, _ top: CGFloat, _ s: CGFloat) {
        var path = Path()
        path.move(to: CGPoint(x: cx + s * 0.04, y: top - s * 0.02))
        path.addLine(to: CGPoint(x: cx - s * 0.09, y: top + s * 0.15))
        path.addLine(to: CGPoint(x: cx, y: top + s * 0.15))
        path.addLine(to: CGPoint(x: cx - s * 0.05, y: top + s * 0.3))
        path.addLine(to: CGPoint(x: cx + s * 0.1, y: top + s * 0.1))
        path.addLine(to: CGPoint(x: cx + s * 0.01, y: top + s * 0.1))
        path.closeSubpath()
        context.fill(path, with: .color(.gold))
    }
}

/// A single weather symbol. With [floating] it drifts up and down slowly.
struct WeatherGlyph: View {
    var sky: Sky
    var night: Bool
    var floating = false
    @State private var up = false

    var body: some View {
        Canvas { context, size in
            WeatherArt.draw(context, sky: sky, night: night, center: CGPoint(x: size.width / 2, y: size.height / 2), size: min(size.width, size.height))
        }
        .offset(y: floating ? (up ? -4 : 4) : 0)
        .onAppear {
            guard floating else { return }
            withAnimation(.easeInOut(duration: 2.8).repeatForever(autoreverses: true)) { up = true }
        }
        .accessibilityHidden(true)
    }
}

/// The next hours as a strip: time, symbol, a smooth temperature curve and rain amounts.
struct HourlyChart: View {
    var hours: [WeatherHour]
    private let column: CGFloat = 60

    var body: some View {
        Canvas { context, size in
            guard !hours.isEmpty else { return }
            let curveTop: CGFloat = 98, curveBottom: CGFloat = 132
            let temps = hours.map(\.temp)
            let middle = (temps.min()! + temps.max()!) / 2
            let range = max(temps.max()! - temps.min()!, 6)
            let low = middle - range / 2
            let points = hours.enumerated().map { index, hour in
                CGPoint(x: column * (CGFloat(index) + 0.5), y: curveBottom - CGFloat((hour.temp - low) / range) * (curveBottom - curveTop))
            }
            var curve = Path()
            for (index, point) in points.enumerated() {
                if index == 0 { curve.move(to: point) } else {
                    let previous = points[index - 1]
                    let mid = (previous.x + point.x) / 2
                    curve.addCurve(to: point, control1: CGPoint(x: mid, y: previous.y), control2: CGPoint(x: mid, y: point.y))
                }
            }
            var fill = curve
            fill.addLine(to: CGPoint(x: points.last!.x, y: curveBottom + 26))
            fill.addLine(to: CGPoint(x: points.first!.x, y: curveBottom + 26))
            fill.closeSubpath()
            context.fill(fill, with: .linearGradient(Gradient(colors: [Color(argb: 0x66C5BAFF), Color(argb: 0x00C5BAFF)]),
                                                     startPoint: CGPoint(x: 0, y: curveTop), endPoint: CGPoint(x: 0, y: curveBottom + 26)))
            context.stroke(curve, with: .linearGradient(Gradient(colors: [.accent, .sky]), startPoint: .zero, endPoint: CGPoint(x: size.width, y: 0)),
                           style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            for (index, hour) in hours.enumerated() {
                let point = points[index]
                let first = index == 0
                context.draw(Text(first ? L.s("weather_now") : L.clock(hour.time)).font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(first ? Color.accent : Color.muted), at: CGPoint(x: point.x, y: 14))
                WeatherArt.draw(context, sky: hour.sky, night: hour.night, center: CGPoint(x: point.x, y: 46), size: 34)
                context.draw(Text("\(Int(hour.temp.rounded()))°").font(.system(size: 14.5)).foregroundStyle(.white),
                             at: CGPoint(x: point.x, y: point.y - 14))
                let r: CGFloat = first ? 4.5 : 3.2
                context.fill(Path(ellipseIn: CGRect(x: point.x - r, y: point.y - r, width: r * 2, height: r * 2)),
                             with: .color(first ? .gold : Color(hex: 0xEDE8FF)))
                if hour.precip >= 0.1 {
                    context.draw(Text(formatPrecip(hour.precip)).font(.system(size: 11.5)).foregroundStyle(Color(hex: 0x8FD0FF)),
                                 at: CGPoint(x: point.x, y: 168))
                }
            }
        }
        .frame(width: column * CGFloat(hours.count), height: 178)
        .accessibilityHidden(true)
    }
}

/// One day's low-to-high band on a track shared by the whole week.
struct TemperatureRange: View {
    var weekLow: Double
    var weekHigh: Double
    var low: Double
    var high: Double

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let span = max(weekHigh - weekLow, 1)
            let start = CGFloat((low - weekLow) / span) * width
            let end = max(CGFloat((high - weekLow) / span) * width, start + 6)
            ZStack(alignment: .leading) {
                Capsule().fill(Color(argb: 0x3D8C8CD8)).frame(height: 6)
                Capsule().fill(LinearGradient(colors: [Self.color(low), Self.color(high)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: end - start, height: 6)
                    .offset(x: start)
            }
            .frame(maxHeight: .infinity)
        }
        .frame(height: 20)
        .accessibilityHidden(true)
    }

    private static let stops: [(Double, UInt32)] = [(-5, 0x8FB8FF), (8, 0x9BDFFF), (18, 0x9BE8C0), (26, 0xFFD98C), (34, 0xFF9F7A)]

    static func color(_ temp: Double) -> Color {
        if temp <= stops[0].0 { return Color(hex: stops[0].1) }
        for index in 1..<stops.count where temp <= stops[index].0 {
            let (t0, c0) = stops[index - 1], (t1, c1) = stops[index]
            let f = (temp - t0) / (t1 - t0)
            func channel(_ shift: UInt32) -> Double {
                let a = Double((c0 >> shift) & 0xFF), b = Double((c1 >> shift) & 0xFF)
                return (a + (b - a) * f) / 255
            }
            return Color(red: channel(16), green: channel(8), blue: channel(0))
        }
        return Color(hex: stops.last!.1)
    }
}

func formatPrecip(_ mm: Double) -> String {
    mm >= 10 ? "\(Int(mm.rounded())) mm" : String(format: "%.1f mm", locale: AppLanguage.locale, mm)
}
