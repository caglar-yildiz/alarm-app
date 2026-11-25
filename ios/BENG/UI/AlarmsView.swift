import SwiftUI
import AlarmKit
import UserNotifications

struct EditorTarget: Identifiable {
    var id: String
    var alarm: Alarm?
}

struct AlarmsView: View {
    @EnvironmentObject private var app: AppModel
    @State private var editing: EditorTarget?
    @State private var deleting: Alarm?
    @State private var notificationsAllowed = true

    var body: some View {
        let _ = app.revision
        let alarms = AlarmStore.all().sorted { $0.time < $1.time }
        VStack(alignment: .leading, spacing: 10) {
            if let ring = app.ring { ringingCard(ring) }
            WakeConfirmations()
            nextAlarmCard()
            Button(L.s("add_alarm")) { editing = EditorTarget(id: "new", alarm: nil) }.bengStyle()
            PermissionsCard(notificationsAllowed: notificationsAllowed)
            Text(L.s("my_alarms")).sectionTitle()
            if alarms.isEmpty {
                Text(L.s("no_alarms")).foregroundStyle(Color.muted).card()
            }
            ForEach(alarms) { alarm in alarmCard(alarm) }
        }
        .screen()
        .sheet(item: $editing) { target in AlarmEditorView(original: target.alarm).environmentObject(app) }
        .confirmationDialog(L.s("editor_delete_confirm"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible) {
            Button(L.s("delete"), role: .destructive) {
                if let alarm = deleting { AlarmStore.delete(alarm.id); Scheduler.request() }
            }
            Button(L.s("cancel"), role: .cancel) {}
        }
        .task(id: app.revision) {
            notificationsAllowed = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus != .denied
        }
    }

    private func ringingCard(_ ring: RingState) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L.s("alarm_ringing")).font(.title3.weight(.semibold)).foregroundStyle(.white)
            if let label = AlarmStore.get(ring.alarmId)?.label, !label.isEmpty { Text(label).foregroundStyle(.white) }
            Button(L.s("open_ringing_alarm")) { app.ringDismissed = false }.bengStyle()
        }
        .card()
    }

    private func nextAlarmCard() -> some View {
        let next = AlarmStore.nextRing()?.1
        let authorized = AlarmManager.shared.authorizationState == .authorized
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L.s("next_alarm")).font(.subheadline).foregroundStyle(Color.muted)
                    Text(next == nil ? L.s("no_alarm_planned") : (!authorized ? L.s("alarm_permission_needed") : L.nextAlarm(next!)))
                        .font(.title2.weight(.semibold)).foregroundStyle(.white)
                    if let summary = RemindersView.nextReminderSummary() {
                        Button { app.tab = .reminders } label: {
                            Text(summary).font(.footnote).foregroundStyle(Color(hex: 0xD9DCF5)).lineLimit(2).multilineTextAlignment(.leading)
                        }
                    }
                    if authorized, let sleep = sleepNowText() {
                        Text(sleep).font(.footnote).foregroundStyle(Color(hex: 0xC9C3F0))
                    }
                }
                Spacer(minLength: 4)
                AstralPlanet().frame(width: 112, height: 96)
            }
            Button {
                app.tab = .tools
                app.tool = .wake
            } label: {
                if Premium.unlocked && WakeCheck.enabled {
                    Pill(text: L.s("wake_chip_on", WakeCheck.delay), color: .mint, fill: Color(argb: 0x403A8A64), stroke: Color(argb: 0x889BE8C0), icon: "alarm.waves.left.and.right")
                } else {
                    Pill(text: L.s("wake_chip_off"), color: .gold, fill: Color(argb: 0x30FFD98C), stroke: Color(argb: 0x88FFD98C), icon: "alarm.waves.left.and.right")
                }
            }
            .frame(minHeight: 40)
            if let vacation = AlarmStore.vacation {
                Button {
                    app.tab = .tools
                    app.tool = .vacation
                } label: {
                    Pill(text: vacation.started() ? L.s("vacation_chip_active", L.date(vacation.until)) : L.s("vacation_chip_scheduled", L.date(vacation.from)),
                         color: Color(hex: 0xB8F0EA), fill: Color(argb: 0x403AA6A0), stroke: Color(argb: 0x883AA6A0), icon: "beach.umbrella")
                }
                .frame(minHeight: 40)
            }
        }
        .card()
    }

    /// "Sleep now and you get 7 h 40 min" when the next real alarm is within a night's reach.
    private func sleepNowText() -> String? {
        guard let next = Bedtime.nextAlarm() else { return nil }
        let minutes = Int(next.timeIntervalSinceNow / 60)
        guard (60...16 * 60).contains(minutes) else { return nil }
        return L.s("bedtime_sleep_now", L.sleepDuration(minutes))
    }

    static func repeatText(_ alarm: Alarm) -> String {
        if alarm.days.isEmpty { return alarm.date.map { L.date($0) } ?? L.s("repeat_once") }
        if alarm.days.count == 7 { return L.s("repeat_every_day") }
        if alarm.days == [6, 7] { return L.s("repeat_weekend") }
        if alarm.days == Set(1...5) { return L.s("repeat_weekdays") }
        return alarm.days.sorted().map { L.weekday($0, short: true) }.joined(separator: " · ")
    }

    private func skippedDate(_ alarm: Alarm) -> Day? {
        guard let skip = alarm.skipDate, alarm.enabled, alarm.repeating, skip >= .today() else { return nil }
        return skip
    }

    private func alarmCard(_ alarm: Alarm) -> some View {
        let dateExpired = alarm.date != nil && !alarm.repeating && alarm.nextAfter(Date()) == nil
        let color: Color = alarm.enabled ? .white : .muted
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                TimeText(alarm.time.text, size: 35, color: color)
                if !alarm.label.isEmpty {
                    Text(alarm.label).font(.body.weight(.semibold)).foregroundStyle(color).lineLimit(2)
                }
                Spacer()
                Toggle("", isOn: Binding(get: { alarm.enabled && !dateExpired }, set: { on in
                    if on && dateExpired {
                        editing = EditorTarget(id: alarm.id, alarm: alarm)
                    } else {
                        var copy = alarm
                        copy.enabled = on
                        copy.snoozeAt = nil
                        AlarmStore.save(copy)
                        Scheduler.request()
                    }
                }))
                .labelsHidden()
                .tint(Color(hex: 0x6969BD))
                .accessibilityLabel(L.s("alarm_switch_description", alarm.time.text))
                Menu {
                    menuItems(alarm)
                } label: {
                    Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(Color.muted).frame(width: 36, height: 44)
                }
                .accessibilityLabel(L.s("alarm_more", alarm.time.text))
            }
            HStack(spacing: 5) {
                Pill(text: Self.repeatText(alarm))
                if alarm.task != .noTask { Pill(text: alarm.task.label) }
            }
            if let skipped = skippedDate(alarm) {
                Button {
                    var copy = alarm
                    copy.skipDate = nil
                    AlarmStore.save(copy)
                    Scheduler.request()
                } label: {
                    Text(L.s("skip_until", L.date(skipped))).font(.footnote.weight(.medium)).foregroundStyle(Color.accent)
                        .padding(.horizontal, 10).frame(minHeight: 36)
                        .background(Color(argb: 0x30A0D8FF), in: Capsule()).overlay(Capsule().stroke(Color(argb: 0x669BDFFF)))
                }
                .accessibilityLabel(L.s("skip_tag_description", L.s("skip_until", L.date(skipped))))
            }
            if dateExpired && alarm.enabled {
                Text(L.s("alarm_date_passed")).font(.footnote).foregroundStyle(Color.muted)
            }
            if let snooze = alarm.snoozeAt, snooze > Date() {
                Button(L.s("cancel_snooze")) {
                    var copy = alarm
                    copy.snoozeAt = nil
                    AlarmStore.save(copy)
                    Scheduler.request()
                }.bengStyle(.secondary)
            }
        }
        .card()
        .contentShape(Rectangle())
        .onTapGesture { editing = EditorTarget(id: alarm.id, alarm: alarm) }
    }

    @ViewBuilder private func menuItems(_ alarm: Alarm) -> some View {
        if alarm.enabled && alarm.repeating {
            if skippedDate(alarm) != nil {
                Button(L.s("skip_undo_menu")) {
                    var copy = alarm
                    copy.skipDate = nil
                    AlarmStore.save(copy)
                    Scheduler.request()
                }
            } else if let next = AlarmStore.next(alarm) {
                Button(L.s("skip_next_menu", L.date(Day(next)))) {
                    var copy = alarm
                    copy.skipDate = Day(next)
                    AlarmStore.save(copy)
                    Scheduler.request()
                }
            }
        }
        Button(L.s("editor_delete"), role: .destructive) { deleting = alarm }
    }
}

/// Pending "are you awake?" confirmations; shown wherever the user lands first.
struct WakeConfirmations: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        let pending = Premium.unlocked ? WakeCheck.active().compactMap { AlarmStore.get($0.alarmId) } : []
        if !pending.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("wake_prompt_title")).font(.title3.weight(.semibold)).foregroundStyle(.white)
                Text(L.s("wake_prompt_text")).font(.subheadline).foregroundStyle(Color(hex: 0xF1E6D6))
                ForEach(pending) { alarm in
                    Button(L.s("wake_confirm_for", alarm.label.isEmpty ? alarm.time.text : alarm.label)) {
                        WakeCheck.confirm(alarm.id)
                        Scheduler.request()
                    }.bengStyle()
                }
            }
            .card([Color(argb: 0xEE6B4A2A), Color(argb: 0xEE4A2A5E)])
        }
    }
}

/// Alarm and notification permissions that are missing, with a way to fix each.
struct PermissionsCard: View {
    var notificationsAllowed: Bool
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        let alarmState = AlarmManager.shared.authorizationState
        if alarmState != .authorized || !notificationsAllowed {
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("missing_permissions")).font(.headline).foregroundStyle(.white)
                if alarmState == .notDetermined {
                    Text(L.s("permission_exact_alarm")).font(.footnote).foregroundStyle(Color.muted)
                    Button(L.s("permission_exact_alarm_button")) {
                        Task {
                            _ = try? await AlarmManager.shared.requestAuthorization()
                            Scheduler.request()
                        }
                    }.bengStyle(.secondary)
                } else if alarmState == .denied {
                    Text(L.s("ios_alarms_denied")).font(.footnote).foregroundStyle(Color.muted)
                    Button(L.s("ios_open_settings")) { openSettings() }.bengStyle(.secondary)
                }
                if !notificationsAllowed {
                    Text(L.s("permission_notifications")).font(.footnote).foregroundStyle(Color.muted)
                    Button(L.s("permission_notifications_button")) { openSettings() }.bengStyle(.secondary)
                }
            }
            .card()
        }
    }
}

func openSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
}
