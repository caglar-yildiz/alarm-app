import Foundation

/// The Gregorian calendar in the device time zone, used for every date calculation.
enum Cal {
    static var zone: TimeZone = .current
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        calendar.firstWeekday = 2
        return calendar
    }
}

/// A wall-clock time without a date, like Java's LocalTime (minute precision).
struct ClockTime: Codable, Hashable, Comparable {
    var hour: Int
    var minute: Int

    init(hour: Int, minute: Int) {
        self.hour = hour
        self.minute = minute
    }

    init(_ date: Date) {
        let parts = Cal.calendar.dateComponents([.hour, .minute], from: date)
        self.init(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }

    var text: String { String(format: "%02d:%02d", hour, minute) }

    func adding(hours: Int) -> ClockTime {
        ClockTime(hour: ((hour + hours) % 24 + 24) % 24, minute: minute)
    }

    static func < (a: ClockTime, b: ClockTime) -> Bool { (a.hour, a.minute) < (b.hour, b.minute) }
}

/// A calendar day without a time, like Java's LocalDate.
struct Day: Codable, Hashable, Comparable {
    var year: Int
    var month: Int
    var day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init(_ date: Date) {
        let parts = Cal.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 2000, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    /// Parses "2026-11-23".
    init?(iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, Day.isValid(year: parts[0], month: parts[1], day: parts[2]) else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    static func today(_ now: Date = Date()) -> Day { Day(now) }

    static func isValid(year: Int, month: Int, day: Int) -> Bool {
        guard (1...12).contains(month), day >= 1 else { return false }
        return day <= Day(year: year, month: month, day: 1).lengthOfMonth
    }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }

    var start: Date {
        Cal.calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? Date()
    }

    func at(_ time: ClockTime) -> Date {
        Cal.calendar.date(from: DateComponents(year: year, month: month, day: day, hour: time.hour, minute: time.minute)) ?? start
    }

    func adding(days: Int) -> Day {
        Day(Cal.calendar.date(byAdding: .day, value: days, to: start.addingTimeInterval(12 * 3600)) ?? start)
    }

    func adding(months: Int) -> Day {
        var y = year, m = month + months
        while m > 12 { m -= 12; y += 1 }
        while m < 1 { m += 12; y -= 1 }
        return Day(year: y, month: m, day: 1)
    }

    /// ISO weekday: 1 = Monday … 7 = Sunday.
    var weekday: Int {
        let value = Cal.calendar.component(.weekday, from: start.addingTimeInterval(12 * 3600)) // 1 = Sunday
        return value == 1 ? 7 : value - 1
    }

    var lengthOfMonth: Int {
        let isLeap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        switch month {
        case 2: return isLeap ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    func with(day newDay: Int) -> Day { Day(year: year, month: month, day: newDay) }

    func days(until other: Day) -> Int {
        Cal.calendar.dateComponents([.day], from: start, to: other.start).day ?? 0
    }

    static func < (a: Day, b: Day) -> Bool { (a.year, a.month, a.day) < (b.year, b.month, b.day) }
}

/// "00:01:05" (and ".3" tenths for the stopwatch).
func formatDuration(_ seconds: TimeInterval, tenths: Bool = false) -> String {
    let total = max(0, seconds)
    let whole = Int(total)
    let text = String(format: "%02d:%02d:%02d", whole / 3600, whole / 60 % 60, whole % 60)
    return tenths ? "\(text).\(Int(total * 10) % 10)" : text
}
