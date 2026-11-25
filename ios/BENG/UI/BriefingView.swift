import SwiftUI

/// After the alarm: a greeting, the time and date, today's weather, the on-time streak and the wake check
/// reminder. Optionally read aloud (Premium).
struct BriefingView: View {
    var preview = false
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let _ = app.revision
        let now = Date()
        VStack(alignment: .leading, spacing: 10) {
            VStack(spacing: 8) {
                Text(Self.greeting(now)).font(.title.weight(.semibold)).foregroundStyle(.white).accessibilityAddTraits(.isHeader)
                TimeText(L.clock(now), size: 60)
                Text(L.pattern("EEEEdMMMMy", now)).foregroundStyle(Color.muted)
                AstralPlanet().frame(height: 96)
            }
            .frame(maxWidth: .infinity)
            .card()
            if let weather = Self.weatherLine() {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L.s("tab_weather")).font(.subheadline).foregroundStyle(Color.muted)
                    Text(weather).font(.title3).foregroundStyle(.white)
                }
                .card()
            }
            let lines = [Self.streakLine(), Self.wakeLine()].compactMap { $0 }
            if !lines.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(lines, id: \.self) { Text($0).foregroundStyle(.white) }
                }
                .card()
            }
            Button(L.s("briefing_close")) { dismiss() }.bengStyle()
        }
        .screen()
        .task {
            if MorningBriefing.speak && Premium.unlocked { AlarmAudio.shared.speak(Self.spokenText()) }
            if WeatherStore.place != nil && WeatherStore.stale { _ = await WeatherClient.refresh(force: false) }
        }
        .task {
            try? await Task.sleep(for: .seconds(180))
            dismiss()
        }
        .onDisappear { AlarmAudio.shared.stopSpeaking() }
    }

    static func greeting(_ date: Date) -> String {
        let hour = Cal.calendar.component(.hour, from: date)
        switch hour {
        case 4...11: return L.s("briefing_morning")
        case 12...17: return L.s("briefing_afternoon")
        default: return L.s("briefing_evening")
        }
    }

    /// Today's weather in one sentence, or nil if no place is set or nothing is cached.
    static func weatherLine() -> String? {
        guard let place = WeatherStore.place, let forecast = WeatherStore.forecast() else { return nil }
        let now = forecast.now
        let temp = "\(Int(now.temp.rounded()))°"
        if let today = forecast.days.first {
            return L.s("briefing_weather", place.name, temp, now.sky.label, "\(Int(today.max.rounded()))°", "\(Int(today.min.rounded()))°")
        }
        return L.s("briefing_weather_now", place.name, temp, now.sky.label)
    }

    static func streakLine() -> String? {
        let streak = WakeStats.summary().streak
        return streak > 0 ? L.p("stats_streak_status", streak) : nil
    }

    static func wakeLine() -> String? {
        Premium.unlocked && WakeCheck.enabled ? L.s("briefing_wake_check", WakeCheck.delay) : nil
    }

    static func spokenText() -> String {
        let now = Date()
        return ["\(greeting(now)).", L.s("briefing_spoken_time", L.clock(now)), weatherLine(), streakLine(), wakeLine()]
            .compactMap { $0 }.joined(separator: " ")
    }
}
