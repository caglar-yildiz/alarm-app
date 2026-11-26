import Foundation

enum AlarmTask: String, Codable, CaseIterable, Identifiable {
    case noTask = "NONE", math = "MATH", memory = "MEMORY", typing = "TYPING"
    case shake = "SHAKE", qr = "QR", shapes = "SHAPES", steps = "STEPS"

    var id: String { rawValue }
    var premium: Bool { [.shake, .qr, .shapes, .steps].contains(self) }
    var labelKey: String {
        switch self {
        case .noTask: return "task_none"
        case .math: return "task_math"
        case .memory: return "task_memory"
        case .typing: return "task_typing"
        case .shake: return "task_shake"
        case .qr: return "task_qr"
        case .shapes: return "task_shapes"
        case .steps: return "task_steps"
        }
    }
    var label: String { L.s(labelKey) }
}

/// How many steps the walking mission asks for.
enum StepGoal {
    static let `default` = 30
    static let options = [20, 30, 50, 100]
    static func coerce(_ value: Int) -> Int { options.contains(value) ? value : `default` }
}

enum MathDifficulty: String, Codable, CaseIterable {
    case easy = "EASY", medium = "MEDIUM", hard = "HARD"
    var label: String { L.s("difficulty_\(rawValue.lowercased())") }
}

struct MathSettings: Codable, Hashable {
    var difficulty: MathDifficulty = .medium
    var count: Int = 1
    static let counts = 1...5
}

struct Vacation: Codable, Hashable {
    var from: Day
    var until: Day
    func started(_ today: Day = .today()) -> Bool { today >= from }
}

struct Alarm: Codable, Identifiable, Hashable {
    var id: String = UUID().uuidString
    var time: ClockTime
    /// ISO weekdays 1 (Monday) … 7 (Sunday); empty for a one-time alarm.
    var days: Set<Int> = []
    var enabled = true
    var task: AlarmTask = .math
    var snoozeAt: Date?
    var date: Day?
    var label = ""
    /// A built-in tone id such as "beng_digital", or nil for the system alarm sound.
    var tone: String?
    var crescendo = true
    var math = MathSettings()
    var qrCode = ""
    /// A repeating alarm skips its occurrence on this date only.
    var skipDate: Day?
    var stepGoal = StepGoal.default
    /// Made by a widget nap button; removed once it has rung.
    var quick = false

    var repeating: Bool { !days.isEmpty }

    func nextAfter(_ now: Date) -> Date? {
        if days.isEmpty, let date {
            let candidate = date.at(time)
            return candidate > now ? candidate : nil
        }
        // Two weeks, so a skipped weekly occurrence still finds the next one.
        let today = Day(now)
        for offset in 0...14 {
            let day = today.adding(days: offset)
            if !days.isEmpty && !days.contains(day.weekday) { continue }
            if !days.isEmpty && day == skipDate { continue }
            let candidate = day.at(time)
            if candidate > now { return candidate }
        }
        return nil
    }
}

/// The next ring of [alarm] after [now], jumping past [vacation] for repeating alarms.
func nextOutside(_ alarm: Alarm, _ now: Date, _ vacation: Vacation?) -> Date? {
    guard let candidate = alarm.nextAfter(now) else { return nil }
    guard let vacation, alarm.repeating else { return candidate }
    let day = Day(candidate)
    if day < vacation.from || day > vacation.until { return candidate }
    return alarm.nextAfter(max(now, vacation.until.adding(days: 1).start.addingTimeInterval(-1)))
}

enum AlarmStore {
    private static let key = "alarms"

    static func all() -> [Alarm] { Shared.load(key, as: [Alarm].self) ?? [] }

    static func get(_ id: String) -> Alarm? { all().first { $0.id == id } }

    static func write(_ alarms: [Alarm]) {
        Shared.save(alarms, key)
        notifyChanged()
    }

    static func save(_ alarm: Alarm) {
        write(all().filter { $0.id != alarm.id } + [alarm])
    }

    static func delete(_ id: String) {
        WakeCheck.confirm(id)
        write(all().filter { $0.id != id })
    }

    /// Repeating alarms stay quiet from `from` through `until` (both inclusive). One-time alarms still ring.
    static var vacation: Vacation? {
        guard let vacation = Shared.load("vacation", as: Vacation.self), vacation.until >= .today() else { return nil }
        return vacation
    }

    static func setVacation(_ vacation: Vacation?) {
        Shared.save(vacation, "vacation")
        notifyChanged()
    }

    /// When [alarm] rings next (not counting snoozes), respecting skipped dates and vacation mode.
    static func next(_ alarm: Alarm, _ now: Date = Date()) -> Date? {
        alarm.enabled ? nextOutside(alarm, now, vacation) : nil
    }

    /// The next ring of any alarm, snoozes included.
    static func nextRing(_ now: Date = Date()) -> (Alarm, Date)? {
        all().compactMap { alarm -> (Alarm, Date)? in
            let times = [alarm.snoozeAt.flatMap { $0 > now ? $0 : nil }, next(alarm, now)].compactMap { $0 }
            return times.min().map { (alarm, $0) }
        }.min { $0.1 < $1.1 }
    }

    /// iOS runs no code when an alarm rings, so what Android did at ring time happens here instead:
    /// one-time alarms whose moment passed turn off, used-up skips and snoozes clear, rung widget naps go.
    static func reconcile(_ now: Date = Date()) {
        var changed = false
        let ringing = Set(RingStore.queue.map(\.alarmId))
        var list = all()
        list.removeAll { alarm in
            let done = alarm.quick && !ringing.contains(alarm.id) && (alarm.nextAfter(now) == nil || !alarm.enabled) &&
                (alarm.snoozeAt.map { $0 <= now } ?? true)
            if done { changed = true }
            return done
        }
        for index in list.indices {
            var alarm = list[index]
            if let snooze = alarm.snoozeAt, snooze <= now { alarm.snoozeAt = nil }
            if alarm.enabled && !alarm.repeating && alarm.nextAfter(now) == nil { alarm.enabled = false }
            if let skip = alarm.skipDate, skip < Day(now) { alarm.skipDate = nil }
            if alarm != list[index] {
                list[index] = alarm
                changed = true
            }
        }
        if changed { write(list) }
    }
}
