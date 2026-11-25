import Foundation

/// Why a system alarm rang: the alarm itself, its snooze, a re-ring after Stop without the mission, the wake
/// check re-ring or a reminder set to ring.
enum RingKind: String, Codable {
    case alarm, snooze, rering, wake, reminder, quick
}

/// An alarm that rang and is not finished yet (its mission is open or still to do).
struct RingState: Codable, Hashable {
    var alarmId: String
    var startedAt: Date
    /// True when wake check rang this alarm again because the user didn't confirm being awake.
    var retry: Bool
    /// The safety ring planned for when the user leaves without finishing the mission.
    var reringAt: Date?
}

/// Rings that are in progress. The first one is shown on the ring screen.
enum RingStore {
    /// Like Android's RingRecord: after 30 minutes an unfinished ring is given up instead of ringing forever.
    static let maxAge: TimeInterval = 30 * 60
    static let reringDelay: TimeInterval = 60

    static var queue: [RingState] {
        get { Shared.load("rings", as: [RingState].self) ?? [] }
        set {
            Shared.save(newValue, "rings")
            notifyChanged()
        }
    }

    static var active: RingState? { queue.first }

    static func update(_ state: RingState) {
        var list = queue
        if let index = list.firstIndex(where: { $0.alarmId == state.alarmId }) { list[index] = state } else { list.append(state) }
        queue = list
    }

    static func remove(_ alarmId: String) { queue = queue.filter { $0.alarmId != alarmId } }

    /// Drops rings that are too old, counting them as rung out.
    static func expire(_ now: Date = Date()) {
        let old = queue.filter { now.timeIntervalSince($0.startedAt) > maxAge }
        guard !old.isEmpty else { return }
        for ring in old where !ring.alarmId.hasPrefix(Reminders.prefix) {
            WakeStats.missed(ring.alarmId, retry: ring.retry, now: now)
        }
        queue = queue.filter { now.timeIntervalSince($0.startedAt) <= maxAge }
    }
}

/// What happens when a ring is stopped, finished or snoozed. Shared by the app, its intents and the widget.
enum RingFlow {
    /// The mission that will actually run: Premium missions fall back to math without Premium, like on Android.
    static func effectiveTask(_ alarm: Alarm) -> AlarmTask {
        if alarm.task.premium && !Premium.unlocked { return .math }
        if alarm.task == .qr && alarm.qrCode.isEmpty { return .math }
        return alarm.task
    }

    /// The system alarm opened the mission ("Mission" button) or the app found a ring in progress.
    static func began(_ alarmId: String, kind: RingKind, now: Date = Date()) {
        if alarmId.hasPrefix(Reminders.prefix) {
            RingStore.update(RingState(alarmId: alarmId, startedAt: now, retry: false, reringAt: nil))
            return
        }
        var state = RingStore.queue.first { $0.alarmId == alarmId } ??
            RingState(alarmId: alarmId, startedAt: now, retry: kind == .wake, reringAt: nil)
        if kind == .wake { state.retry = true }
        state.reringAt = now.addingTimeInterval(RingStore.reringDelay + 30)
        RingStore.update(state)
    }

    /// The system alarm's Stop button. Without a finished mission the alarm rings again a minute later;
    /// alarms without a mission (and reminders) end here.
    static func stopped(_ alarmId: String, kind: RingKind, now: Date = Date()) {
        if alarmId.hasPrefix(Reminders.prefix) || kind == .reminder {
            RingStore.remove(alarmId)
            return
        }
        guard let alarm = AlarmStore.get(alarmId) else {
            RingStore.remove(alarmId)
            return
        }
        if effectiveTask(alarm) == .noTask {
            let state = RingStore.queue.first { $0.alarmId == alarmId }
            finish(alarmId, startedAt: state?.startedAt ?? now, retry: state?.retry ?? (kind == .wake), now: now)
            return
        }
        var state = RingStore.queue.first { $0.alarmId == alarmId } ??
            RingState(alarmId: alarmId, startedAt: now, retry: kind == .wake, reringAt: nil)
        if now.timeIntervalSince(state.startedAt) > RingStore.maxAge {
            RingStore.remove(alarmId)
            WakeStats.missed(alarmId, retry: state.retry, now: now)
            return
        }
        state.reringAt = now.addingTimeInterval(RingStore.reringDelay)
        RingStore.update(state)
    }

    /// The mission was completed (or the alarm had none): record it and start the wake check.
    static func finish(_ alarmId: String, startedAt: Date, retry: Bool, now: Date = Date()) {
        RingStore.remove(alarmId)
        guard !alarmId.hasPrefix(Reminders.prefix) else { return }
        WakeStats.dismissed(alarmId, startedAt: startedAt, retry: retry, now: now)
        WakeCheck.schedule(alarmId, now: now)
        MorningBriefing.pendingShow = true
    }

    /// Snooze from the ring screen: rings again in five minutes.
    static func snooze(_ alarmId: String, now: Date = Date()) {
        RingStore.remove(alarmId)
        if alarmId.hasPrefix(Reminders.prefix) {
            Reminders.snooze(String(alarmId.dropFirst(Reminders.prefix.count)), now: now)
            return
        }
        WakeStats.snoozed(alarmId)
        if var alarm = AlarmStore.get(alarmId) {
            alarm.snoozeAt = now.addingTimeInterval(300)
            AlarmStore.save(alarm)
        }
    }
}

/// "Are you awake?" (Premium): a notification some minutes after an alarm is turned off; without a
/// confirmation within 60 seconds the alarm rings again.
enum WakeCheck {
    struct Pending: Codable, Hashable {
        var alarmId: String
        var promptAt: Date
        var deadline: Date
    }

    static var enabled: Bool {
        get { Shared.defaults.bool(forKey: "wake.enabled") }
        set {
            Shared.defaults.set(newValue, forKey: "wake.enabled")
            if !newValue { pending = [] }
            notifyChanged()
        }
    }

    static var delay: Int {
        get { let value = Shared.defaults.integer(forKey: "wake.delay"); return value == 0 ? 3 : value }
        set { Shared.defaults.set(newValue, forKey: "wake.delay"); notifyChanged() }
    }

    static var pending: [Pending] {
        get { Shared.load("wake.pending", as: [Pending].self) ?? [] }
        set { Shared.save(newValue, "wake.pending") }
    }

    /// Confirmations the user can give right now.
    static func active(_ now: Date = Date()) -> [Pending] {
        pending.filter { now >= $0.promptAt && now < $0.deadline }
    }

    static func schedule(_ alarmId: String, now: Date = Date()) {
        guard enabled, Premium.unlocked else { return }
        let promptAt = now.addingTimeInterval(TimeInterval(delay * 60))
        pending = pending.filter { $0.alarmId != alarmId } +
            [Pending(alarmId: alarmId, promptAt: promptAt, deadline: promptAt.addingTimeInterval(60))]
    }

    static func confirm(_ alarmId: String) {
        pending = pending.filter { $0.alarmId != alarmId }
        notifyChanged()
    }

    /// Drops checks whose re-ring time is over (the system alarm has rung by then).
    static func expire(_ now: Date = Date()) {
        let list = pending.filter { $0.deadline > now.addingTimeInterval(-5) }
        if list.count != pending.count { pending = list }
    }
}
