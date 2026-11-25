import Foundation

#if DEBUG
/// Sample data for screenshots in CI (`-seedDemo`); never part of a release build.
enum DemoData {
    static var active: Bool { ProcessInfo.processInfo.arguments.contains("-seedDemo") }

    static var tab: Tab? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-demoTab"), index + 1 < args.count else { return nil }
        return Tab(rawValue: args[index + 1])
    }

    static func seed() {
        guard active, AlarmStore.all().isEmpty else { return }
        let today = Day.today()
        AlarmStore.write([
            Alarm(time: ClockTime(hour: 7, minute: 0), days: Set(1...5), task: .math, label: "Work"),
            Alarm(time: ClockTime(hour: 9, minute: 30), days: [6, 7], task: .steps, label: "Weekend run"),
            Alarm(time: ClockTime(hour: 6, minute: 15), enabled: false, task: .noTask, date: today.adding(days: 3))
        ])
        Reminders.write([
            Reminder(title: "Dentist", note: "Bring the card", date: today.adding(days: 2), time: ClockTime(hour: 14, minute: 30)),
            Reminder(title: "Mom's birthday", date: today.adding(days: 12), time: ClockTime(hour: 9, minute: 0), repeatRule: .yearly, leadDays: 7)
        ])
        Premium.preview = true
        WakeCheck.enabled = true
        Holidays.enabled = true
        Holidays.country = "TR"
        WeatherStore.savePlace(WeatherPlace(name: "İstanbul", region: "Türkiye", lat: 41.0138, lon: 28.9497))
        AppLanguage.current = ProcessInfo.processInfo.arguments.contains("-demoTurkish") ? "tr" : ""
    }
}
#endif
