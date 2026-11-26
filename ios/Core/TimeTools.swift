import Foundation

struct Countdown: Codable, Identifiable, Hashable {
    var id: String = UUID().uuidString
    var duration: TimeInterval
    var remaining: TimeInterval
    /// When a running countdown ends; nil while paused or finished.
    var deadline: Date?

    func remaining(at now: Date) -> TimeInterval {
        guard let deadline else { return remaining }
        return max(0, deadline.timeIntervalSince(now))
    }
}

/// The stopwatch and the countdown timers. A finished countdown is announced with a notification.
enum TimeTools {
    static var stopwatchStart: Date? {
        get { Shared.defaults.object(forKey: "stopwatch.start") as? Date }
        set { Shared.defaults.set(newValue, forKey: "stopwatch.start") }
    }

    static var stopwatchStored: TimeInterval {
        get { Shared.defaults.double(forKey: "stopwatch.elapsed") }
        set { Shared.defaults.set(newValue, forKey: "stopwatch.elapsed") }
    }

    static var stopwatchRunning: Bool { stopwatchStart != nil }

    static func stopwatchElapsed(_ now: Date = Date()) -> TimeInterval {
        stopwatchStored + (stopwatchStart.map { max(0, now.timeIntervalSince($0)) } ?? 0)
    }

    static func toggleStopwatch(_ now: Date = Date()) {
        if stopwatchRunning {
            stopwatchStored = stopwatchElapsed(now)
            stopwatchStart = nil
        } else {
            stopwatchStart = now
        }
        notifyChanged()
    }

    static func resetStopwatch() {
        stopwatchStart = nil
        stopwatchStored = 0
        notifyChanged()
    }

    static func timers() -> [Countdown] { Shared.load("countdowns", as: [Countdown].self) ?? [] }

    private static func write(_ list: [Countdown]) {
        Shared.save(list, "countdowns")
        notifyChanged()
    }

    private static func replace(_ timer: Countdown) { write(timers().map { $0.id == timer.id ? timer : $0 }) }

    static func add(_ duration: TimeInterval, now: Date = Date()) {
        write(timers() + [Countdown(duration: duration, remaining: duration, deadline: now.addingTimeInterval(duration))])
    }

    static func toggle(_ id: String, now: Date = Date()) {
        guard var timer = timers().first(where: { $0.id == id }) else { return }
        if timer.deadline != nil {
            if finishIfDue(id, now: now) { return }
            timer.remaining = timer.remaining(at: now)
            timer.deadline = nil
        } else {
            let remaining = timer.remaining > 0 ? timer.remaining : timer.duration
            timer.remaining = remaining
            timer.deadline = now.addingTimeInterval(remaining)
        }
        replace(timer)
    }

    static func reset(_ id: String) {
        guard var timer = timers().first(where: { $0.id == id }) else { return }
        timer.remaining = timer.duration
        timer.deadline = nil
        replace(timer)
    }

    static func remove(_ id: String) { write(timers().filter { $0.id != id }) }

    /// Marks a countdown whose time is up as finished. The notification was scheduled ahead of time.
    @discardableResult
    static func finishIfDue(_ id: String, now: Date = Date()) -> Bool {
        guard var timer = timers().first(where: { $0.id == id }), let deadline = timer.deadline, deadline <= now else { return false }
        timer.remaining = 0
        timer.deadline = nil
        replace(timer)
        return true
    }
}
