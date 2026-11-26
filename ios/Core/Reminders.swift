import Foundation

enum ReminderRepeat: String, Codable, CaseIterable {
    case once = "ONCE", daily = "DAILY", weekly = "WEEKLY", monthly = "MONTHLY", yearly = "YEARLY"

    var labelKey: String {
        switch self {
        case .once: return "reminder_repeat_once"
        case .daily: return "repeat_every_day"
        case .weekly: return "reminder_repeat_weekly"
        case .monthly: return "reminder_repeat_monthly"
        case .yearly: return "reminder_repeat_yearly"
        }
    }
}

struct ReminderNotice: Hashable {
    var at: Date
    var eventDay: Day
    var early: Bool
    var snoozed = false
}

/// A reminder the user sets like an alarm. [leadDays] adds an early heads-up (1 or 7 days before, Premium);
/// [ring] makes it ring like an alarm instead of posting a notification (Premium).
struct Reminder: Codable, Identifiable, Hashable {
    var id: String = UUID().uuidString
    var title: String
    var note = ""
    var date: Day
    var time: ClockTime
    var repeatRule: ReminderRepeat = .once
    var days: Set<Int> = []
    var leadDays = 0
    var ring = false
    var enabled = true
    var snoozeAt: Date?

    /// Event days on or after [from], in order (at most [limit]). Months and years keep the day, or use the
    /// month's last day.
    func occurrences(from: Day, limit: Int = 40) -> [Day] {
        var result: [Day] = []
        switch repeatRule {
        case .once:
            if date >= from { result = [date] }
        case .daily:
            var day = max(from, date)
            while result.count < limit { result.append(day); day = day.adding(days: 1) }
        case .weekly:
            let weekdays = days.isEmpty ? [date.weekday] : days
            var day = max(from, date)
            var guardCount = 0
            while result.count < limit && guardCount < limit * 8 {
                if weekdays.contains(day.weekday) { result.append(day) }
                day = day.adding(days: 1)
                guardCount += 1
            }
        case .monthly:
            var month = from.with(day: 1)
            var guardCount = 0
            while result.count < limit && guardCount < limit * 2 {
                let day = month.with(day: min(date.day, month.lengthOfMonth))
                if day >= from && day >= date { result.append(day) }
                month = month.adding(months: 1)
                guardCount += 1
            }
        case .yearly:
            var year = from.year
            while result.count < limit && year < from.year + limit + 2 {
                let first = Day(year: year, month: date.month, day: 1)
                let day = first.with(day: min(date.day, first.lengthOfMonth))
                if day >= from && day >= date { result.append(day) }
                year += 1
            }
        }
        return result
    }

    /// Upcoming notices after [now]: the events themselves and their early heads-ups, soonest first.
    func notices(after now: Date, limit: Int = 40) -> [ReminderNotice] {
        guard enabled else { return [] }
        var list: [ReminderNotice] = []
        for day in occurrences(from: Day(now).adding(days: -7), limit: limit) {
            let event = day.at(time)
            list.append(ReminderNotice(at: event, eventDay: day, early: false))
            if leadDays > 0 {
                list.append(ReminderNotice(at: event.addingTimeInterval(-TimeInterval(leadDays) * 86400), eventDay: day, early: true))
            }
        }
        if let snoozeAt, snoozeAt > now {
            list.append(ReminderNotice(at: snoozeAt, eventDay: Day(snoozeAt), early: false, snoozed: true))
        }
        return list.filter { $0.at > now }.sorted { $0.at < $1.at }
    }

    func nextNotice(_ now: Date) -> ReminderNotice? { notices(after: now).first }

    /// The next time the event itself happens, for lists ("Tomorrow · 09:00").
    func nextEvent(_ now: Date) -> Date? {
        guard enabled else { return nil }
        return occurrences(from: Day(now)).map { $0.at(time) }.first { $0 > now }
    }
}

enum Reminders {
    /// Ring ids that belong to reminders, so the ringing screen can snooze the right thing.
    static let prefix = "reminder:"

    static func all() -> [Reminder] { Shared.load("reminders", as: [Reminder].self) ?? [] }
    static func get(_ id: String) -> Reminder? { all().first { $0.id == id } }

    static func write(_ list: [Reminder]) {
        Shared.save(list, "reminders")
        notifyChanged()
    }

    static func save(_ reminder: Reminder) { write(all().filter { $0.id != reminder.id } + [reminder]) }
    static func delete(_ id: String) { write(all().filter { $0.id != id }) }

    static func snooze(_ id: String, now: Date = Date()) {
        guard var reminder = get(id) else { return }
        reminder.snoozeAt = now.addingTimeInterval(300)
        reminder.enabled = true
        save(reminder)
    }

    /// One-time reminders whose moment passed are done; used snoozes clear.
    static func reconcile(_ now: Date = Date()) {
        var list = all()
        var changed = false
        for index in list.indices {
            var item = list[index]
            if let snooze = item.snoozeAt, snooze <= now { item.snoozeAt = nil }
            if item.enabled && item.repeatRule == .once && item.date.at(item.time) <= now && item.snoozeAt == nil {
                item.enabled = false
            }
            if item != list[index] { list[index] = item; changed = true }
        }
        if changed { write(list) }
    }
}
