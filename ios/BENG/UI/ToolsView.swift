import SwiftUI
import UserNotifications

struct ToolsView: View {
    @EnvironmentObject private var app: AppModel
    @State private var pickingLanguage = false

    static func title(_ tool: Tool) -> String {
        switch tool {
        case .stopwatch: return L.s("tool_stopwatch")
        case .countdowns: return L.s("tool_countdowns")
        case .wake: return L.s("wake_check")
        case .briefing: return L.s("briefing_title")
        case .stats: return L.s("tool_stats")
        case .bedtime: return L.s("bedtime_title")
        case .vacation: return L.s("vacation_title")
        case .language: return L.s("tool_language")
        }
    }

    static func symbol(_ tool: Tool) -> String {
        switch tool {
        case .stopwatch: return "stopwatch"
        case .countdowns: return "hourglass"
        case .wake: return "alarm.waves.left.and.right"
        case .briefing: return "sun.horizon"
        case .stats: return "chart.bar"
        case .bedtime: return "bed.double"
        case .vacation: return "beach.umbrella"
        case .language: return "globe"
        }
    }

    static func colors(_ tool: Tool) -> [Color] {
        switch tool {
        case .stopwatch: return [Color(hex: 0x3E7BD8), Color(hex: 0x3B3594)]
        case .countdowns: return [Color(hex: 0x7A55E0), Color(hex: 0x3A2C86)]
        case .wake: return [Color(hex: 0xE0A14F), Color(hex: 0x7A3F8F)]
        case .stats: return [Color(hex: 0x4FB28A), Color(hex: 0x2B4C86)]
        case .briefing: return [Color(hex: 0xE8B54A), Color(hex: 0xB0577A)]
        case .bedtime: return [Color(hex: 0x5B4BC4), Color(hex: 0x1E2A5E)]
        case .vacation, .language: return [Color(hex: 0x3AA6A0), Color(hex: 0x2B4C86)]
        }
    }

    var body: some View {
        let _ = app.revision
        VStack(alignment: .leading, spacing: 10) {
            if let tool = app.tool {
                Button(L.s("back_to_tools")) { app.tool = nil }.bengStyle(.secondary)
                switch tool {
                case .stopwatch: StopwatchTool()
                case .countdowns: CountdownsTool()
                case .wake: WakeCheckTool()
                case .briefing: BriefingTool()
                case .stats: StatsTool()
                case .bedtime: BedtimeTool()
                case .vacation: VacationTool()
                case .language: EmptyView()
                }
            } else {
                Text(L.s("tools_subtitle")).foregroundStyle(Color.muted)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    ForEach(Tool.allCases) { tool in tile(tool) }
                }
            }
        }
        .screen()
        .sheet(isPresented: $pickingLanguage) {
            ChoiceSheet(title: L.s("tool_language"), values: AppLanguage.supported, selected: AppLanguage.current,
                        label: AppLanguage.displayName) { tag in
                AppLanguage.current = tag
                Scheduler.request()
                notifyChanged()
            }
        }
    }

    private func tile(_ tool: Tool) -> some View {
        Button {
            if tool == .language { pickingLanguage = true } else { app.tool = tool }
        } label: {
            VStack(spacing: 6) {
                IconBadge(symbol: Self.symbol(tool), size: 60, colors: Self.colors(tool))
                Text(Self.title(tool)).font(.headline).foregroundStyle(.white).multilineTextAlignment(.center).padding(.top, 6)
                TimelineView(.periodic(from: .now, by: 1)) { _ in
                    Text(status(tool)).font(.footnote).foregroundStyle(Color.muted).multilineTextAlignment(.center).lineLimit(2)
                }
                if tool == .wake && !Premium.unlocked { Pill.premium().padding(.top, 4) }
            }
            .frame(maxWidth: .infinity, minHeight: 168)
            .padding(.horizontal, 8)
            .background(LinearGradient(colors: [Color(argb: 0xDD242F58), Color(argb: 0xD137204F)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Color(argb: 0x748C8CD8)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(Self.title(tool)), \(status(tool))")
    }

    private func status(_ tool: Tool) -> String {
        switch tool {
        case .stopwatch:
            let elapsed = TimeTools.stopwatchElapsed()
            if TimeTools.stopwatchRunning { return formatDuration(elapsed) }
            return elapsed > 0 ? L.s("tool_status_paused", formatDuration(elapsed)) : L.s("tool_stopwatch_desc")
        case .countdowns:
            let timers = TimeTools.timers()
            let running = timers.filter { $0.deadline != nil }.count
            if timers.isEmpty { return L.s("tool_countdowns_desc") }
            return running > 0 ? L.p("countdowns_running", running) : L.p("countdowns_saved", timers.count)
        case .wake:
            if !Premium.unlocked { return L.s("wake_tile_locked") }
            return WakeCheck.enabled ? L.s("wake_status_on", WakeCheck.delay) : L.s("wake_status_off")
        case .stats:
            let summary = WakeStats.summary()
            return summary.total == 0 ? L.s("tool_stats_desc") : L.p("stats_streak_status", summary.streak)
        case .briefing:
            return L.s(MorningBriefing.enabled ? "briefing_status_on" : "briefing_desc")
        case .vacation:
            guard let vacation = AlarmStore.vacation else { return L.s("vacation_desc") }
            return vacation.started() ? L.s("vacation_chip_active", L.date(vacation.until)) : L.s("vacation_chip_scheduled", L.date(vacation.from))
        case .bedtime:
            return Bedtime.enabled ? L.s("bedtime_status_on", L.sleepDuration(Bedtime.goal)) : L.s("bedtime_desc")
        case .language:
            return AppLanguage.displayName(AppLanguage.current)
        }
    }
}

struct StopwatchTool: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        VStack(spacing: 10) {
            IconBadge(symbol: "stopwatch", size: 56, colors: ToolsView.colors(.stopwatch))
            TimelineView(.periodic(from: .now, by: 0.1)) { context in
                TimeText(formatDuration(TimeTools.stopwatchElapsed(context.date), tenths: true), size: 44)
            }
            Button(L.s(TimeTools.stopwatchRunning ? "pause" : "start")) { TimeTools.toggleStopwatch() }.bengStyle()
            Button(L.s("reset")) { TimeTools.resetStopwatch() }.bengStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .card()
    }
}

struct CountdownsTool: View {
    @EnvironmentObject private var app: AppModel
    @State private var adding = false
    @State private var blocked = false

    var body: some View {
        let _ = app.revision
        let timers = TimeTools.timers()
        Button(L.s("countdown_add_button")) { adding = true }.bengStyle()
        if timers.isEmpty { Text(L.s("countdown_empty")).foregroundStyle(Color.muted).card() }
        ForEach(timers) { timer in
            VStack(alignment: .leading, spacing: 8) {
                Text("\(formatDuration(timer.duration)) · \(L.s(statusKey(timer)))").font(.headline).foregroundStyle(.white)
                TimelineView(.periodic(from: .now, by: 0.25)) { context in
                    let remaining = timer.remaining(at: context.date)
                    TimeText(formatDuration(remaining.rounded(.up)), size: 36).frame(maxWidth: .infinity)
                        .onChange(of: remaining == 0) { _, finished in
                            if finished && timer.deadline != nil { TimeTools.finishIfDue(timer.id) }
                        }
                }
                Button(L.s(actionKey(timer))) {
                    TimeTools.toggle(timer.id)
                    Scheduler.request()
                }.bengStyle()
                HStack(spacing: 8) {
                    Button(L.s("reset")) {
                        TimeTools.reset(timer.id)
                        Scheduler.request()
                    }.bengStyle(.secondary)
                    Button(L.s("delete")) {
                        TimeTools.remove(timer.id)
                        Scheduler.request()
                    }.bengStyle(.destructive)
                }
            }
            .card()
        }
        if blocked {
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("countdown_notifications_hint")).font(.footnote).foregroundStyle(Color.muted)
                Button(L.s("notification_settings")) { openSettings() }.bengStyle(.secondary)
            }
            .card()
        }
        Color.clear.frame(height: 0)
            .sheet(isPresented: $adding) { AddCountdownSheet() }
            .task(id: app.revision) {
                for timer in TimeTools.timers() where timer.deadline != nil { TimeTools.finishIfDue(timer.id) }
                blocked = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
            }
    }

    private func statusKey(_ timer: Countdown) -> String {
        if timer.deadline != nil { return "countdown_running" }
        if timer.remaining == 0 { return "countdown_done" }
        return timer.remaining < timer.duration ? "countdown_paused" : "countdown_ready"
    }

    private func actionKey(_ timer: Countdown) -> String {
        if timer.deadline != nil { return "pause" }
        if timer.remaining == 0 { return "restart" }
        return timer.remaining < timer.duration ? "resume" : "start"
    }
}

struct AddCountdownSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var hours = ""
    @State private var minutes = ""
    @State private var seconds = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                field(L.s("countdown_hours"), $hours)
                field(L.s("countdown_minutes"), $minutes)
                field(L.s("countdown_seconds"), $seconds)
                if let error { Text(error).font(.footnote).foregroundStyle(Color.rose) }
                Spacer()
            }
            .padding(20)
            .background(Starfield())
            .navigationTitle(L.s("countdown_add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L.s("start")) { start() } }
            }
        }
        .presentationDetents([.medium])
    }

    private func field(_ title: String, _ text: Binding<String>) -> some View {
        TextField(title, text: text)
            .keyboardType(.numberPad)
            .foregroundStyle(.white)
            .padding(12)
            .background(Color(argb: 0xDA11192F), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0x748C8CD8)))
    }

    private func start() {
        func read(_ text: String, _ max: Int) -> Int? {
            let trimmed = text.trimmingCharacters(in: .whitespaces)
            guard let value = trimmed.isEmpty ? 0 : Int(trimmed), (0...max).contains(value) else {
                error = L.s("countdown_range_error", max)
                return nil
            }
            return value
        }
        guard let h = read(hours, 99), let m = read(minutes, 59), let s = read(seconds, 59) else { return }
        let duration = TimeInterval(h * 3600 + m * 60 + s)
        guard duration > 0 else {
            error = L.s("countdown_min_error")
            return
        }
        TimeTools.add(duration)
        Scheduler.request()
        dismiss()
    }
}

struct WakeCheckTool: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        let unlocked = Premium.unlocked
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                IconBadge(symbol: "alarm.waves.left.and.right", size: 60, colors: ToolsView.colors(.wake))
                VStack(alignment: .leading, spacing: 6) {
                    Text(L.s("wake_headline")).font(.title3.weight(.semibold)).foregroundStyle(.white)
                    if !unlocked { Pill.premium() } else if WakeCheck.enabled { Pill.on(L.s("wake_status_on", WakeCheck.delay)) } else {
                        Pill(text: L.s("wake_status_off"))
                    }
                }
            }
            Text(L.s("wake_description")).font(.subheadline).foregroundStyle(Color.muted)
        }
        .card()
        WakeConfirmations()
        VStack(alignment: .leading, spacing: 10) {
            Text(L.s("wake_how_title")).font(.headline).foregroundStyle(.white)
            ForEach(Array([L.s("wake_step_1"), L.s("wake_step_2", WakeCheck.delay), L.s("wake_step_3")].enumerated()), id: \.offset) { index, text in
                HStack(spacing: 12) {
                    Text("\(index + 1)").font(.subheadline.weight(.semibold)).foregroundStyle(Color.midnight)
                        .frame(width: 28, height: 28).background(accentGradient, in: Circle())
                    Text(text).foregroundStyle(.white)
                }
            }
        }
        .card()
        if !unlocked {
            Button(L.s("premium_explore")) { app.tab = .premium }.bengStyle()
        } else {
            VStack(alignment: .leading, spacing: 8) {
                BengToggle(title: L.s("wake_check"), isOn: Binding(get: { WakeCheck.enabled }, set: { WakeCheck.enabled = $0; Scheduler.request() }))
                Text(L.s("wake_delay")).font(.subheadline).foregroundStyle(Color.muted)
                ChoiceRow(values: [1, 3, 5, 10], selected: WakeCheck.delay, columns: 4, label: { L.s("minutes_short", $0) }) {
                    WakeCheck.delay = $0
                }
            }
            .card()
        }
    }
}

struct BriefingTool: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        HeroHeader(symbol: "sun.horizon", colors: ToolsView.colors(.briefing), title: L.s("briefing_headline"), description: L.s("briefing_description"))
        VStack(alignment: .leading, spacing: 8) {
            BengToggle(title: L.s("briefing_show"), isOn: Binding(get: { MorningBriefing.enabled }, set: { MorningBriefing.enabled = $0 }))
            BengToggle(title: L.s("briefing_speak"), locked: !Premium.unlocked, isOn: Binding(
                get: { MorningBriefing.speak && Premium.unlocked },
                set: { value in if Premium.unlocked { MorningBriefing.speak = value } else { app.tab = .premium } }))
            Text(L.s("briefing_speak_hint")).font(.footnote).foregroundStyle(Color.muted)
            Button(L.s("briefing_preview")) {
                app.briefingPreview = true
                app.showBriefing = true
            }.bengStyle(.secondary)
        }
        .card()
    }
}

struct StatsTool: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        let stats = WakeStats.summary()
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 14) {
                IconBadge(symbol: "chart.bar", size: 60, colors: ToolsView.colors(.stats))
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.s("stats_streak_title")).font(.subheadline).foregroundStyle(Color.muted)
                    TimeText(L.p("stats_days", stats.streak), size: 34)
                    if stats.bestStreak > 0 {
                        Text(L.s("stats_best", L.p("stats_days", stats.bestStreak))).font(.footnote).foregroundStyle(Color.muted)
                    }
                }
            }
            if stats.total == 0 {
                Text(L.s("stats_empty")).foregroundStyle(Color.muted)
                Text(L.s("stats_how")).font(.footnote).foregroundStyle(Color.muted)
            }
        }
        .card()
        if stats.total > 0 {
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("stats_week")).font(.headline).foregroundStyle(.white)
                HStack {
                    ForEach(stats.week.indices, id: \.self) { index in
                        let (day, result) = stats.week[index]
                        VStack(spacing: 4) {
                            Text(L.weekday(day.weekday, short: true)).font(.caption).foregroundStyle(Color.muted)
                            Text(result == .onTime ? "✓" : result == .late ? "✕" : "–")
                                .foregroundStyle(.white)
                                .frame(width: 32, height: 32)
                                .background(result == .onTime ? Color(hex: 0x3A8A64) : result == .late ? Color(hex: 0x8A3A55) : Color.white.opacity(0.25), in: Circle())
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(L.weekday(day.weekday)): \(L.s(result == .onTime ? "stats_on_time" : result == .late ? "stats_late" : "stats_no_alarm"))")
                    }
                }
                Text("✓ \(L.s("stats_on_time"))  ·  ✕ \(L.s("stats_late"))  ·  – \(L.s("stats_no_alarm"))").font(.caption).foregroundStyle(Color.muted)
            }
            .card()
            VStack(alignment: .leading, spacing: 6) {
                Text(L.s("stats_month")).font(.headline).foregroundStyle(.white)
                Text(L.s("stats_on_time_rate", stats.onTimeDays, stats.alarmDays)).foregroundStyle(.white)
                Text(L.p("stats_snoozes", stats.snoozes)).foregroundStyle(.white)
                if let seconds = stats.averageSeconds {
                    Text(L.s("stats_average", String(format: "%d:%02d", seconds / 60, seconds % 60))).foregroundStyle(.white)
                }
                Text(L.s("stats_how")).font(.footnote).foregroundStyle(Color.muted)
                Text(L.s("stats_private")).font(.footnote).foregroundStyle(Color.muted)
            }
            .card()
        }
    }
}

struct BedtimeTool: View {
    @EnvironmentObject private var app: AppModel

    var body: some View {
        let _ = app.revision
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 14) {
                IconBadge(symbol: "bed.double", size: 60, colors: ToolsView.colors(.bedtime))
                Text(L.s("bedtime_headline")).font(.title3.weight(.semibold)).foregroundStyle(.white)
            }
            Text(L.s("bedtime_description")).font(.subheadline).foregroundStyle(Color.muted)
            if let next = Bedtime.nextAlarm() {
                let minutes = Int(next.timeIntervalSinceNow / 60)
                if (60...16 * 60).contains(minutes) {
                    Text(L.s("bedtime_sleep_now", L.sleepDuration(minutes))).font(.footnote).foregroundStyle(Color(hex: 0xC9C3F0))
                }
            }
        }
        .card()
        VStack(alignment: .leading, spacing: 8) {
            BengToggle(title: L.s("bedtime_reminder"), isOn: Binding(get: { Bedtime.enabled }, set: {
                Bedtime.enabled = $0
                Scheduler.request()
            }))
            Text(L.s("bedtime_goal")).font(.subheadline).foregroundStyle(Color.muted)
            ChoiceRow(values: Bedtime.goals, selected: Bedtime.goal, label: L.sleepDuration) {
                Bedtime.goal = $0
                Scheduler.request()
            }
            Text(L.s("bedtime_lead")).font(.subheadline).foregroundStyle(Color.muted)
            ChoiceRow(values: Bedtime.leads, selected: Bedtime.lead, label: { L.s("minutes_short", $0) }) {
                Bedtime.lead = $0
                Scheduler.request()
            }
            if Bedtime.enabled {
                Text(Bedtime.plan().map { L.s("bedtime_next", L.nextAlarm($0.remindAt)) } ?? L.s("bedtime_none"))
                    .font(.footnote).foregroundStyle(Color.muted)
            }
        }
        .card()
    }
}

struct VacationTool: View {
    @EnvironmentObject private var app: AppModel
    @State private var from: Day?
    @State private var until: Day?
    @State private var picking: Picking?

    enum Picking: String, Identifiable {
        case from, until, typeFrom, typeUntil
        var id: String { rawValue }
    }

    var body: some View {
        let _ = app.revision
        let today = Day.today()
        let start = max(from ?? today, today)
        let end = max(until ?? start.adding(days: 6), start)
        HeroHeader(symbol: "beach.umbrella", colors: ToolsView.colors(.vacation), title: L.s("vacation_headline"), description: L.s("vacation_description"))
        VStack(alignment: .leading, spacing: 8) {
            if let current = AlarmStore.vacation {
                Text(current.started() ? L.s("vacation_active", L.date(current.until))
                     : L.s("vacation_scheduled", L.date(current.from), L.date(current.until))).foregroundStyle(.white)
                Button(L.s("vacation_end")) {
                    AlarmStore.setVacation(nil)
                    Scheduler.request()
                }.bengStyle()
            } else {
                Button(L.s("vacation_start_value", L.date(start))) { picking = .from }.bengStyle(.secondary)
                Button(L.s("vacation_end_value", L.date(end))) { picking = .until }.bengStyle(.secondary)
                Button(L.s("vacation_turn_on")) {
                    AlarmStore.setVacation(Vacation(from: start, until: end))
                    from = nil
                    until = nil
                    Scheduler.request()
                }.bengStyle()
            }
        }
        .card()
        .sheet(item: $picking) { item in
            switch item {
            case .from:
                DatePickSheet(initial: start, earliest: today, extraTitle: L.s("type_date"), extra: { picking = .typeFrom }) { pickFrom($0, end) }
            case .until:
                DatePickSheet(initial: end, earliest: start, extraTitle: L.s("type_date"), extra: { picking = .typeUntil }) { until = $0 }
            case .typeFrom:
                TypeDateSheet(initial: start, earliest: today) { pickFrom($0, end) }
            case .typeUntil:
                TypeDateSheet(initial: end, earliest: start) { until = $0 }
            }
        }
    }

    private func pickFrom(_ day: Day, _ end: Day) {
        from = day
        if end < day { until = day }
    }
}
