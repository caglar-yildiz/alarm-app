import Foundation

/// One alarm as it ended. [snoozes] counts the snoozes before it ended, [seconds] how long the final ring took to
/// turn off, [rerung] that wake check had to ring again, [missed] that the alarm rang out without being turned off.
struct WakeEntry: Codable, Hashable {
    var date: Day
    var alarmId: String
    var snoozes: Int
    var seconds: Int
    var rerung: Bool
    var missed: Bool
    var at: Date

    var onTime: Bool { !missed && !rerung && snoozes == 0 }
}

enum DayResult { case onTime, late, noAlarm }

struct WakeSummary {
    var streak: Int
    var bestStreak: Int
    var onTimeDays: Int
    var alarmDays: Int
    var snoozes: Int
    var averageSeconds: Int?
    /// The last seven days, oldest first, ending today.
    var week: [(Day, DayResult)]
    var total: Int
}

/// Pure calculations, kept apart from storage so they can be unit-tested.
enum WakeMath {
    static func dayResult(_ entries: [WakeEntry], _ date: Day) -> DayResult {
        let day = entries.filter { $0.date == date }
        if day.isEmpty { return .noAlarm }
        return day.contains { $0.onTime } ? .onTime : .late
    }

    /// Consecutive on-time days counting back from today. Days without any alarm are skipped instead of
    /// breaking the streak.
    static func streak(_ entries: [WakeEntry], _ today: Day) -> Int {
        let days = Set(entries.map(\.date)).filter { $0 <= today }.sorted(by: >)
        var count = 0
        for date in days {
            if dayResult(entries, date) == .onTime { count += 1 } else { break }
        }
        return count
    }

    static func bestStreak(_ entries: [WakeEntry]) -> Int {
        var best = 0, run = 0
        for date in Set(entries.map(\.date)).sorted() {
            if dayResult(entries, date) == .onTime {
                run += 1
                best = max(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    static func summary(_ entries: [WakeEntry], _ today: Day) -> WakeSummary {
        let first = today.adding(days: -29)
        let recent = entries.filter { $0.date >= first && $0.date <= today }
        let days = Set(recent.map(\.date))
        let dismissed = recent.filter { !$0.missed }
        return WakeSummary(
            streak: streak(entries, today),
            bestStreak: bestStreak(entries),
            onTimeDays: days.filter { dayResult(recent, $0) == .onTime }.count,
            alarmDays: days.count,
            snoozes: recent.reduce(0) { $0 + $1.snoozes },
            averageSeconds: dismissed.isEmpty ? nil : dismissed.reduce(0) { $0 + $1.seconds } / dismissed.count,
            week: (0...6).reversed().map { offset in
                let date = today.adding(days: -offset)
                return (date, dayResult(entries, date))
            },
            total: entries.count
        )
    }
}

/// Records how each alarm ended, on this phone only.
enum WakeStats {
    private static let maxEntries = 400
    private static let retryWindow: TimeInterval = 3 * 3600

    static func entries() -> [WakeEntry] { Shared.load("wakeStats", as: [WakeEntry].self) ?? [] }

    static func summary(_ today: Day = .today()) -> WakeSummary { WakeMath.summary(entries(), today) }

    private static func pendingKey(_ id: String) -> String { "wakeStats.pending.\(id)" }

    static func snoozed(_ alarmId: String) {
        Shared.defaults.set(Shared.defaults.integer(forKey: pendingKey(alarmId)) + 1, forKey: pendingKey(alarmId))
    }

    /// A ring was turned off. [retry] marks the ring wake check started because the user didn't confirm.
    static func dismissed(_ alarmId: String, startedAt: Date, retry: Bool, now: Date = Date()) {
        if retry {
            markRerung(alarmId, now)
        } else {
            add(alarmId, seconds: max(0, Int(now.timeIntervalSince(startedAt))), missed: false, now: now)
        }
    }

    /// A ring ended without being turned off.
    static func missed(_ alarmId: String, retry: Bool, now: Date = Date()) {
        if retry { markRerung(alarmId, now) } else { add(alarmId, seconds: 0, missed: true, now: now) }
    }

    private static func add(_ alarmId: String, seconds: Int, missed: Bool, now: Date) {
        let entry = WakeEntry(date: Day(now), alarmId: alarmId, snoozes: Shared.defaults.integer(forKey: pendingKey(alarmId)),
                              seconds: seconds, rerung: false, missed: missed, at: now)
        Shared.save(Array((entries() + [entry]).suffix(maxEntries)), "wakeStats")
        Shared.defaults.removeObject(forKey: pendingKey(alarmId))
    }

    private static func markRerung(_ alarmId: String, _ now: Date) {
        var list = entries()
        guard let index = list.lastIndex(where: { $0.alarmId == alarmId && now.timeIntervalSince($0.at) >= 0 &&
            now.timeIntervalSince($0.at) <= retryWindow }) else { return }
        list[index].rerung = true
        Shared.save(list, "wakeStats")
    }
}

/// Asks for an App Store rating after several on-time mornings, never in the first days, at most every 120 days.
enum ReviewPrompt {
    static let minStreak = 3
    static let minEntries = 5
    static let minInstallAge: TimeInterval = 4 * 24 * 3600
    static let interval: TimeInterval = 120 * 24 * 3600

    static func eligible(_ summary: WakeSummary, installedAt: Date, lastAsked: Date, now: Date) -> Bool {
        summary.streak >= minStreak && summary.total >= minEntries &&
            now.timeIntervalSince(installedAt) >= minInstallAge && now.timeIntervalSince(lastAsked) >= interval
    }

    static var installedAt: Date {
        if let date = Shared.defaults.object(forKey: "installedAt") as? Date { return date }
        let now = Date()
        Shared.defaults.set(now, forKey: "installedAt")
        return now
    }

    /// True when the app should ask now; remembers the time so it is not asked again for months.
    static func shouldAsk(now: Date = Date()) -> Bool {
        let lastAsked = Shared.defaults.object(forKey: "reviewLastAsked") as? Date ?? .distantPast
        guard eligible(WakeStats.summary(), installedAt: installedAt, lastAsked: lastAsked, now: now) else { return false }
        Shared.defaults.set(now, forKey: "reviewLastAsked")
        return true
    }
}
