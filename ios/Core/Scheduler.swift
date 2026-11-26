import Foundation
import SwiftUI
import AlarmKit
import ActivityKit
import AppIntents
import UserNotifications
import WidgetKit

/// Extra data every BENG system alarm carries, so its Live Activity and intents know what rang.
struct BengAlarmData: AlarmMetadata {
    var alarmId: String
    var kind: String
    var label: String
}

/// The built-in alarm tones (bundled .wav files). Premium tones fall back to the first free one without Premium.
struct BuiltInTone: Identifiable, Hashable {
    var id: String
    var premium: Bool

    var titleKey: String { "tone_" + id.replacingOccurrences(of: "beng_", with: "") }
    var title: String { L.s(titleKey) }
    var detail: String { L.s(titleKey + "_desc") }

    static let all = [
        BuiltInTone(id: "beng_digital", premium: false),
        BuiltInTone(id: "beng_gentle", premium: false),
        BuiltInTone(id: "beng_energetic", premium: false),
        BuiltInTone(id: "beng_nature", premium: true),
        BuiltInTone(id: "beng_heavy", premium: true),
        // Extra-loud tones for heavy sleepers.
        BuiltInTone(id: "beng_klaxon", premium: true),
        BuiltInTone(id: "beng_wail", premium: true),
        BuiltInTone(id: "beng_bell", premium: true),
        BuiltInTone(id: "beng_pulse", premium: true)
    ]

    static func find(_ id: String?) -> BuiltInTone? { all.first { $0.id == id } }

    /// The tone that will really play for a stored choice (nil = the system alarm sound).
    static func playable(_ id: String?) -> String? {
        guard let tone = find(id) else { return nil }
        return tone.premium && !Premium.unlocked ? all[0].id : tone.id
    }

    static func title(_ id: String?) -> String {
        guard let tone = find(id) else { return L.s("tone_device_default") }
        return tone.title + (tone.premium ? " ✦" : "")
    }
}

/// Plans every system alarm (AlarmKit) and notification from the stored state, and makes the system match.
/// iOS runs no app code when an alarm rings, so instead of reacting at ring time like Android, the whole plan is
/// recomputed whenever something changes, the app opens, an alarm button is pressed or background refresh runs.
actor Scheduler {
    static let shared = Scheduler()

    private var running = false
    private var again = false

    /// Fire-and-forget resync, for UI code.
    static func request() {
        Task { await shared.sync() }
    }

    func sync() async {
        if running {
            again = true
            return
        }
        running = true
        repeat {
            again = false
            await perform()
        } while again
        running = false
    }

    private func perform() async {
        let now = Date()
        AlarmStore.reconcile(now)
        Reminders.reconcile(now)
        RingStore.expire(now)
        WakeCheck.expire(now)
        await syncAlarms(now)
        await syncNotifications(now)
        WidgetCenter.shared.reloadAllTimelines()
        notifyChanged()
    }

    // MARK: - System alarms

    struct Planned {
        var key: String
        var date: Date?
        var relative: (ClockTime, Set<Int>)?
        var title: String
        var alarmId: String
        var kind: RingKind
        var tone: String?
        var mission: Bool
    }

    static func plan(_ now: Date = Date()) -> [Planned] {
        var result: [Planned] = []
        let vacation = AlarmStore.vacation
        for alarm in AlarmStore.all() {
            let title = alarm.label.isEmpty ? L.s("alarm_ringing") : alarm.label
            let task = RingFlow.effectiveTask(alarm)
            let tone = BuiltInTone.playable(alarm.tone)
            let fingerprint = "\(title)|\(tone ?? "-")|\(task.rawValue)"
            let kind: RingKind = alarm.quick ? .quick : .alarm
            func fixed(_ date: Date, _ prefix: String, _ kind: RingKind) -> Planned {
                Planned(key: "\(prefix)|\(alarm.id)|\(Int(date.timeIntervalSince1970))|\(fingerprint)", date: date, relative: nil,
                        title: title, alarmId: alarm.id, kind: kind, tone: tone, mission: task != .noTask)
            }
            if let snooze = alarm.snoozeAt, snooze > now { result.append(fixed(snooze, "s", .snooze)) }
            guard alarm.enabled else { continue }
            if !alarm.repeating {
                if let next = alarm.nextAfter(now) { result.append(fixed(next, "a", kind)) }
                continue
            }
            let skip = alarm.skipDate.flatMap { $0 >= Day(now) ? $0 : nil }
            if skip == nil && vacation == nil {
                // A plain weekly alarm: the system repeats it on its own, even if the app is never opened again.
                let days = alarm.days.sorted().map(String.init).joined()
                result.append(Planned(key: "r|\(alarm.id)|\(alarm.time.text)|\(days)|\(fingerprint)", date: nil,
                                      relative: (alarm.time, alarm.days), title: title, alarmId: alarm.id, kind: kind,
                                      tone: tone, mission: task != .noTask))
                continue
            }
            // A skipped day or vacation: single occurrences until well after the exception.
            let exceptionEnd = [skip, vacation?.until].compactMap { $0 }.max() ?? Day(now)
            let horizon = max(exceptionEnd.adding(days: 14), Day(now).adding(days: 21)).at(ClockTime(hour: 23, minute: 59))
            var cursor = now
            var count = 0
            while count < 60, let next = nextOutside(alarm, cursor, vacation), next <= horizon {
                result.append(fixed(next, "a", kind))
                cursor = next
                count += 1
            }
        }
        for ring in RingStore.queue {
            guard let at = ring.reringAt, at > now.addingTimeInterval(-5), let alarm = AlarmStore.get(ring.alarmId) else { continue }
            let title = alarm.label.isEmpty ? L.s("alarm_ringing") : alarm.label
            result.append(Planned(key: "rr|\(alarm.id)|\(Int(at.timeIntervalSince1970))", date: max(at, now.addingTimeInterval(2)),
                                  relative: nil, title: title, alarmId: alarm.id, kind: ring.retry ? .wake : .rering,
                                  tone: BuiltInTone.playable(alarm.tone), mission: true))
        }
        if Premium.unlocked {
            for check in WakeCheck.pending where check.deadline > now {
                guard let alarm = AlarmStore.get(check.alarmId) else { continue }
                result.append(Planned(key: "w|\(alarm.id)|\(Int(check.deadline.timeIntervalSince1970))", date: check.deadline,
                                      relative: nil, title: L.s("wake_prompt_title"), alarmId: alarm.id, kind: .wake,
                                      tone: BuiltInTone.playable(alarm.tone), mission: RingFlow.effectiveTask(alarm) != .noTask))
            }
            for reminder in Reminders.all() where reminder.ring {
                for notice in reminder.notices(after: now).filter({ !$0.early }).prefix(5) {
                    result.append(Planned(key: "rm|\(reminder.id)|\(Int(notice.at.timeIntervalSince1970))|\(reminder.title)",
                                          date: notice.at, relative: nil, title: reminder.title,
                                          alarmId: Reminders.prefix + reminder.id, kind: .reminder, tone: nil, mission: false))
                }
            }
        }
        return result
    }

    private func syncAlarms(_ now: Date) async {
        let manager = AlarmManager.shared
        guard manager.authorizationState == .authorized else { return }
        let desired = Dictionary(Self.plan(now).map { (Shared.uuid(for: $0.key), $0) }, uniquingKeysWith: { first, _ in first })
        let existing = (try? manager.alarms) ?? []
        for alarm in existing where desired[alarm.id] == nil && alarm.state != .alerting {
            try? manager.cancel(id: alarm.id)
        }
        let present = Set(existing.map(\.id))
        for (id, plan) in desired where !present.contains(id) {
            _ = try? await manager.schedule(id: id, configuration: Self.configuration(id, plan))
        }
    }

    private static func text(_ value: String) -> LocalizedStringResource { LocalizedStringResource("\(value)") }

    static func configuration(_ id: UUID, _ plan: Planned) -> AlarmManager.AlarmConfiguration<BengAlarmData> {
        let stop = AlarmButton(text: text(L.s("ios_stop")), textColor: .white, systemImageName: "stop.circle")
        let secondary = plan.mission
            ? AlarmButton(text: text(L.s("ios_open_mission")), textColor: .black, systemImageName: "puzzlepiece.extension")
            : AlarmButton(text: text(L.s("ios_snooze")), textColor: .black, systemImageName: "zzz")
        let alert = AlarmPresentation.Alert(title: text(plan.title), stopButton: stop, secondaryButton: secondary,
                                            secondaryButtonBehavior: .custom)
        let attributes = AlarmAttributes(presentation: AlarmPresentation(alert: alert),
                                         metadata: BengAlarmData(alarmId: plan.alarmId, kind: plan.kind.rawValue, label: plan.title),
                                         tintColor: Color(red: 0.77, green: 0.73, blue: 1.0))
        let schedule: AlarmKit.Alarm.Schedule
        if let relative = plan.relative {
            let (time, days) = relative
            let weekdays = days.sorted().map(weekday)
            schedule = .relative(.init(time: .init(hour: time.hour, minute: time.minute), repeats: .weekly(weekdays)))
        } else {
            schedule = .fixed(plan.date ?? Date())
        }
        let secondaryIntent: any LiveActivityIntent
        if plan.mission {
            secondaryIntent = BengMissionIntent(alarmKitID: id.uuidString, alarmID: plan.alarmId, kind: plan.kind.rawValue)
        } else {
            secondaryIntent = BengSnoozeIntent(alarmKitID: id.uuidString, alarmID: plan.alarmId, kind: plan.kind.rawValue)
        }
        let sound: AlertConfiguration.AlertSound = plan.tone.map { .named("\($0).wav") } ?? .default
        return AlarmManager.AlarmConfiguration.alarm(schedule: schedule, attributes: attributes,
                                               stopIntent: BengStopIntent(alarmKitID: id.uuidString, alarmID: plan.alarmId, kind: plan.kind.rawValue),
                                               secondaryIntent: secondaryIntent, sound: sound)
    }

    private static func weekday(_ iso: Int) -> Locale.Weekday {
        switch iso {
        case 1: return .monday
        case 2: return .tuesday
        case 3: return .wednesday
        case 4: return .thursday
        case 5: return .friday
        case 6: return .saturday
        default: return .sunday
        }
    }

    // MARK: - Notifications

    static let wakeCategory = "WAKE"
    static let upcomingCategory = "UPCOMING"
    static let wakeConfirmAction = "wake.confirm"
    static let upcomingSkipAction = "upcoming.skip"

    private func syncNotifications(_ now: Date) async {
        let center = UNUserNotificationCenter.current()
        var requests: [(Date, UNNotificationRequest)] = []

        func add(_ id: String, _ at: Date, _ title: String, _ body: String, sound: Bool = true, category: String? = nil,
                 info: [String: String] = [:], level: UNNotificationInterruptionLevel = .active) {
            guard at > now else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = sound ? .default : nil
            content.interruptionLevel = level
            if let category { content.categoryIdentifier = category }
            content.userInfo = info
            let parts = Cal.calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: at)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            requests.append((at, UNNotificationRequest(identifier: id, content: content, trigger: trigger)))
        }

        if Premium.unlocked && WakeCheck.enabled {
            for check in WakeCheck.pending {
                add("wake.\(check.alarmId)", check.promptAt, L.s("wake_prompt_title"), L.s("wake_prompt_text"),
                    category: Self.wakeCategory, info: ["alarmId": check.alarmId, "screen": "wake"], level: .timeSensitive)
            }
        }
        for timer in TimeTools.timers() {
            if let deadline = timer.deadline {
                add("timer.\(timer.id)", deadline, L.s("countdown_finished_title"),
                    L.s("countdown_finished_text", formatDuration(timer.duration)), info: ["screen": "timers"], level: .timeSensitive)
            }
        }
        if let upcoming = UpcomingAlarm.next(now) {
            let (alarm, at) = upcoming
            add("upcoming", at.addingTimeInterval(-UpcomingAlarm.lead), L.s("upcoming_title", L.clock(at)),
                alarm.label.isEmpty ? L.s("upcoming_text") : alarm.label, sound: false, category: Self.upcomingCategory,
                info: ["alarmId": alarm.id, "at": String(at.timeIntervalSince1970)], level: .passive)
        }
        if let plan = Bedtime.plan(now) {
            let bedtime = L.clock(plan.alarmAt.addingTimeInterval(-TimeInterval(Bedtime.goal * 60)))
            add("bedtime", plan.remindAt, L.s("bedtime_notification_title"),
                L.s("bedtime_notification_text", bedtime, L.sleepDuration(Bedtime.goal), L.clock(plan.alarmAt)))
        }
        let premium = Premium.unlocked
        for reminder in Reminders.all() {
            for notice in reminder.notices(after: now).prefix(8) {
                if notice.early && !premium { continue }
                if reminder.ring && premium && !notice.early { continue }
                let body: String
                if notice.early {
                    body = L.s(reminder.leadDays == 1 ? "reminder_notice_tomorrow" : "reminder_notice_week", reminder.title)
                } else {
                    body = reminder.note.isEmpty ? L.s("reminder_notice_now") : reminder.note
                }
                add("reminder.\(reminder.id).\(Int(notice.at.timeIntervalSince1970))", notice.at, reminder.title, body,
                    info: ["screen": "reminders"], level: .timeSensitive)
            }
        }
        for notice in Holidays.notices(now) {
            let body: String
            if notice.early { body = L.s("holiday_notice_tomorrow") } else if notice.holiday.days > 1 {
                body = L.p("holiday_notice_days", notice.holiday.days)
            } else { body = L.s("holiday_notice_today") }
            add("holiday.\(notice.holiday.date.iso).\(notice.early)", notice.at, Holidays.displayName(notice.holiday), body,
                info: ["screen": "reminders"])
        }

        center.removeAllPendingNotificationRequests()
        // iOS keeps at most 64 pending notifications per app; the soonest ones matter most.
        for (_, request) in requests.sorted(by: { $0.0 < $1.0 }).prefix(60) {
            try? await center.add(request)
        }
    }
}

// MARK: - Alarm buttons

/// The system alarm's Stop button. Without a finished mission the alarm rings again a minute later.
struct BengStopIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Stop alarm"
    static var description = IntentDescription("Stops a ringing BENG alarm.")
    static var isDiscoverable = false

    @Parameter(title: "System alarm") var alarmKitID: String
    @Parameter(title: "Alarm") var alarmID: String
    @Parameter(title: "Kind") var kind: String

    init(alarmKitID: String, alarmID: String, kind: String) {
        self.alarmKitID = alarmKitID
        self.alarmID = alarmID
        self.kind = kind
    }

    init() {}

    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: alarmKitID) { try? AlarmManager.shared.stop(id: id) }
        RingFlow.stopped(alarmID, kind: RingKind(rawValue: kind) ?? .alarm)
        await Scheduler.shared.sync()
        return .result()
    }
}

/// The "Mission" button: stops the system sound and opens BENG on the mission, where the alarm keeps ringing.
struct BengMissionIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Open alarm mission"
    static var description = IntentDescription("Opens the mission that turns the alarm off.")
    static var openAppWhenRun = true
    static var isDiscoverable = false

    @Parameter(title: "System alarm") var alarmKitID: String
    @Parameter(title: "Alarm") var alarmID: String
    @Parameter(title: "Kind") var kind: String

    init(alarmKitID: String, alarmID: String, kind: String) {
        self.alarmKitID = alarmKitID
        self.alarmID = alarmID
        self.kind = kind
    }

    init() {}

    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: alarmKitID) { try? AlarmManager.shared.stop(id: id) }
        RingFlow.began(alarmID, kind: RingKind(rawValue: kind) ?? .alarm)
        await Scheduler.shared.sync()
        return .result()
    }
}

/// The "Snooze" button of alarms without a mission (and of reminders that ring): rings again in five minutes.
struct BengSnoozeIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Snooze alarm"
    static var description = IntentDescription("Snoozes a ringing BENG alarm for five minutes.")
    static var isDiscoverable = false

    @Parameter(title: "System alarm") var alarmKitID: String
    @Parameter(title: "Alarm") var alarmID: String
    @Parameter(title: "Kind") var kind: String

    init(alarmKitID: String, alarmID: String, kind: String) {
        self.alarmKitID = alarmKitID
        self.alarmID = alarmID
        self.kind = kind
    }

    init() {}

    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: alarmKitID) { try? AlarmManager.shared.stop(id: id) }
        RingFlow.snooze(alarmID)
        await Scheduler.shared.sync()
        return .result()
    }
}

/// The widget's "Nap 20 min" / "Alarm in 30 min" buttons.
struct QuickAlarmIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Quick alarm"
    static var description = IntentDescription("Sets a one-time alarm a few minutes from now.")

    @Parameter(title: "Minutes") var minutes: Int

    init(minutes: Int) { self.minutes = minutes }
    init() { minutes = 20 }

    static let napMinutes = 20
    static let soonMinutes = 30

    func perform() async throws -> some IntentResult {
        guard minutes == Self.napMinutes || minutes == Self.soonMinutes else { return .result() }
        // Whole minutes, rounded up, so "20 min" never rings early.
        let target = Date().addingTimeInterval(TimeInterval(minutes * 60 + 59))
        let parts = Cal.calendar.dateComponents([.year, .month, .day, .hour, .minute], from: target)
        let at = Cal.calendar.date(from: parts) ?? target
        let label = L.s(minutes == Self.napMinutes ? "widget_nap_label" : "widget_soon_label")
        AlarmStore.save(Alarm(time: ClockTime(at), task: .noTask, date: Day(at), label: label, quick: true))
        await Scheduler.shared.sync()
        return .result()
    }
}
