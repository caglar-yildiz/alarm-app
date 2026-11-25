import Foundation

/// Premium access as last seen from the App Store, kept so intents and the widget can check it offline.
enum Premium {
    static var subscribed: Bool {
        get { Shared.defaults.bool(forKey: "premium.subscribed") }
        set {
            guard newValue != subscribed else { return }
            Shared.defaults.set(newValue, forKey: "premium.subscribed")
            notifyChanged()
        }
    }

    /// Debug builds can unlock Premium without paying, like the Android test button.
    static var previewAvailable: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    static var preview: Bool {
        get { previewAvailable && Shared.defaults.bool(forKey: "premium.preview") }
        set { Shared.defaults.set(newValue, forKey: "premium.preview"); notifyChanged() }
    }

    /// True while an introductory free trial is running (reported by StoreKit).
    static var trialActive: Bool {
        get { Shared.defaults.bool(forKey: "premium.trial") }
        set { Shared.defaults.set(newValue, forKey: "premium.trial") }
    }

    static var unlocked: Bool { subscribed || preview }
}

/// Settings for the screen shown right after an alarm is turned off.
enum MorningBriefing {
    static var enabled: Bool {
        get { Shared.defaults.object(forKey: "briefing.enabled") as? Bool ?? true }
        set { Shared.defaults.set(newValue, forKey: "briefing.enabled"); notifyChanged() }
    }

    /// Read the briefing aloud (Premium).
    static var speak: Bool {
        get { Shared.defaults.bool(forKey: "briefing.speak") }
        set { Shared.defaults.set(newValue, forKey: "briefing.speak"); notifyChanged() }
    }

    /// Set when a ring was finished outside the app, so the app opens the briefing when it is opened soon after.
    static var pendingShow: Bool {
        get {
            guard let at = Shared.defaults.object(forKey: "briefing.pending") as? Date else { return false }
            return Date().timeIntervalSince(at) < 2 * 3600
        }
        set { Shared.defaults.set(newValue ? Date() : nil, forKey: "briefing.pending") }
    }
}

/// Reminds the user to go to bed so they get their sleep goal before the next alarm.
enum Bedtime {
    static let goals = [7 * 60, 7 * 60 + 30, 8 * 60, 8 * 60 + 30, 9 * 60]
    static let leads = [15, 30, 60]

    static var enabled: Bool {
        get { Shared.defaults.bool(forKey: "bedtime.enabled") }
        set { Shared.defaults.set(newValue, forKey: "bedtime.enabled"); notifyChanged() }
    }

    /// Sleep goal in minutes.
    static var goal: Int {
        get { let value = Shared.defaults.integer(forKey: "bedtime.goal"); return goals.contains(value) ? value : 8 * 60 }
        set { Shared.defaults.set(newValue, forKey: "bedtime.goal"); notifyChanged() }
    }

    /// How long before bedtime the reminder comes, in minutes.
    static var lead: Int {
        get { let value = Shared.defaults.integer(forKey: "bedtime.lead"); return leads.contains(value) ? value : 30 }
        set { Shared.defaults.set(newValue, forKey: "bedtime.lead"); notifyChanged() }
    }

    static func reminderTime(_ alarmAt: Date, goal: Int, lead: Int) -> Date {
        alarmAt.addingTimeInterval(-TimeInterval((goal + lead) * 60))
    }

    /// The next alarm that is not a snooze or a widget nap: bedtime is planned around real alarms only.
    static func nextAlarm(_ now: Date = Date()) -> Date? {
        AlarmStore.all().filter { !$0.quick }.compactMap { AlarmStore.next($0, now) }.min()
    }

    /// The next reminder and the alarm it is for, or nil.
    static func plan(_ now: Date = Date()) -> (remindAt: Date, alarmAt: Date)? {
        guard enabled, let alarm = nextAlarm(now) else { return nil }
        let remind = reminderTime(alarm, goal: goal, lead: lead)
        return remind > now ? (remind, alarm) : nil
    }
}

/// A quiet notification two hours before the next real alarm, with "Skip this time".
enum UpcomingAlarm {
    static let lead: TimeInterval = 2 * 3600

    static func next(_ now: Date = Date()) -> (Alarm, Date)? {
        AlarmStore.all().filter { !$0.quick }.compactMap { alarm in AlarmStore.next(alarm, now).map { (alarm, $0) } }
            .min { $0.1 < $1.1 }
    }

    /// "Skip this time": a repeating alarm skips that day, a one-time alarm is turned off.
    static func skip(_ alarmId: String, at: Date) {
        guard var alarm = AlarmStore.get(alarmId), let next = AlarmStore.next(alarm),
              abs(next.timeIntervalSince(at)) < 60 else { return }
        if alarm.repeating { alarm.skipDate = Day(at) } else { alarm.enabled = false }
        AlarmStore.save(alarm)
    }
}
