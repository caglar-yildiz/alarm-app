import SwiftUI
import UserNotifications

struct ReminderTarget: Identifiable {
    var id: String
    var reminder: Reminder?
}

/// The Reminders tab: the user's own reminders and the country's public holidays and special days.
struct RemindersView: View {
    @EnvironmentObject private var app: AppModel
    @State private var editing: ReminderTarget?
    @State private var loading = false
    @State private var failed = false
    @State private var showAll = false
    @State private var pickingCountry = false
    @State private var pickingTime = false
    @State private var notificationsOff = false
    @State private var hint: String?

    var body: some View {
        let _ = app.revision
        let now = Date()
        let list = Reminders.all().sorted { a, b in
            if a.enabled != b.enabled { return a.enabled }
            return (a.nextEvent(now) ?? .distantFuture) < (b.nextEvent(now) ?? .distantFuture)
        }
        VStack(alignment: .leading, spacing: 10) {
            Button(L.s("reminder_add")) { editing = ReminderTarget(id: "new", reminder: nil) }.bengStyle()
            if notificationsOff {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L.s("reminder_notifications_off")).font(.footnote).foregroundStyle(Color.muted)
                    Button(L.s("notification_settings")) { openSettings() }.bengStyle(.secondary)
                }
                .card()
            }
            Text(L.s("reminder_mine")).sectionTitle()
            if list.isEmpty { Text(L.s("reminder_empty")).font(.subheadline).foregroundStyle(Color.muted).card() }
            ForEach(list) { reminder in reminderCard(reminder, now) }
            holidayCard
            upcomingHolidays
        }
        .screen()
        .sheet(item: $editing) { target in ReminderEditorView(original: target.reminder).environmentObject(app) }
        .sheet(isPresented: $pickingCountry) {
            let codes = Array(Set((Holidays.countries.isEmpty ? [Holidays.country] : Holidays.countries) + [Holidays.country]))
                .sorted { Self.countryName($0) < Self.countryName($1) }
            ChoiceSheet(title: L.s("holiday_country_title"), values: codes, selected: Holidays.country, label: Self.countryName) { code in
                Holidays.country = code
                showAll = false
                Task { await load(force: false) }
            }
        }
        .sheet(isPresented: $pickingTime) {
            TimePickSheet(initial: Holidays.time) {
                Holidays.time = $0
                Scheduler.request()
            }
        }
        .task(id: app.revision) {
            notificationsOff = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
        }
        .task { await load(force: false) }
    }

    private func load(force: Bool) async {
        guard !loading else { return }
        loading = true
        failed = false
        let result = await Holidays.refresh(force: force)
        loading = false
        failed = result == false
        Scheduler.request()
    }

    static func countryName(_ code: String) -> String { AppLanguage.locale.localizedString(forRegionCode: code) ?? code }

    static func whenText(_ date: Day, _ today: Day) -> String {
        if date == today { return L.s("weather_today") }
        if date == today.adding(days: 1) { return L.s("weather_tomorrow") }
        return L.date(date, style: .medium)
    }

    static func repeatText(_ reminder: Reminder) -> String {
        if reminder.repeatRule == .weekly {
            return (reminder.days.isEmpty ? [reminder.date.weekday] : reminder.days).sorted().map { L.weekday($0, short: true) }
                .joined(separator: " · ")
        }
        return L.s(reminder.repeatRule.labelKey)
    }

    /// The soonest reminder or holiday, for the home screen; nil when there is none.
    static func nextReminderSummary() -> String? {
        let now = Date()
        let own = Reminders.all().compactMap { r in r.nextEvent(now).map { ($0, r.title) } }.min { $0.0 < $1.0 }
        var holiday: (Date, String)?
        if Holidays.enabled, let next = Holidays.upcoming().first(where: { $0.date >= Day(now) }) {
            holiday = (next.date.at(Holidays.time), Holidays.displayName(next))
        }
        let pick: (Date, String)?
        if let own, holiday == nil || own.0 < holiday!.0 { pick = own } else { pick = holiday }
        guard let pick else { return nil }
        let (time, title) = pick
        return L.s("reminder_next_summary", title, whenText(Day(time), Day(now)) + " " + L.clock(time))
    }

    private func reminderCard(_ reminder: Reminder, _ now: Date) -> some View {
        let next = reminder.nextEvent(now)
        let color: Color = reminder.enabled ? .white : .muted
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                IconBadge(symbol: "bell", size: 42)
                VStack(alignment: .leading, spacing: 2) {
                    Text(reminder.title).font(.headline).foregroundStyle(color).lineLimit(2)
                    Text(next.map { Self.whenText(Day($0), Day(now)) + " · " + L.clock($0) } ?? L.s("reminder_done"))
                        .font(.subheadline).foregroundStyle(Color.muted)
                }
                Spacer()
                Toggle("", isOn: Binding(get: { reminder.enabled && next != nil }, set: { on in
                    var copy = reminder
                    copy.enabled = true
                    if on && copy.nextEvent(Date()) == nil {
                        editing = ReminderTarget(id: reminder.id, reminder: reminder)
                    } else {
                        copy.enabled = on
                        copy.snoozeAt = nil
                        Reminders.save(copy)
                        Scheduler.request()
                    }
                }))
                .labelsHidden()
                .tint(Color(hex: 0x6969BD))
                .accessibilityLabel(reminder.title)
            }
            HStack(spacing: 6) {
                Pill(text: Self.repeatText(reminder))
                if reminder.leadDays > 0 { Pill(text: L.s(reminder.leadDays == 1 ? "reminder_lead_day" : "reminder_lead_week")) }
                if reminder.ring { Pill(text: L.s("reminder_ring_tag"), color: .gold, fill: Color(argb: 0x30FFD98C), stroke: Color(argb: 0x88FFD98C)) }
            }
            if !reminder.note.isEmpty {
                Text(reminder.note).font(.footnote).foregroundStyle(Color(hex: 0xD9DCF5)).lineLimit(2)
            }
        }
        .card()
        .contentShape(Rectangle())
        .onTapGesture { editing = ReminderTarget(id: reminder.id, reminder: reminder) }
    }

    private var holidayCard: some View {
        let premium = Premium.unlocked
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                IconBadge(symbol: "flag", size: 44, colors: [Color(hex: 0xE0A14F), Color(hex: 0x7A3F8F)])
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.s("holiday_title")).font(.headline).foregroundStyle(.white)
                    Text(L.s("holiday_subtitle")).font(.footnote).foregroundStyle(Color.muted)
                }
            }
            BengToggle(title: L.s("holiday_toggle"), isOn: Binding(get: { Holidays.enabled }, set: {
                Holidays.enabled = $0
                Scheduler.request()
            }))
            HStack(spacing: 6) {
                Button(L.s("holiday_country", Self.countryName(Holidays.country))) { pickingCountry = true }.bengStyle(.secondary)
                Button(Holidays.time.text) { pickingTime = true }.bengStyle(.secondary).frame(maxWidth: 110)
                    .accessibilityLabel(L.s("holiday_time_description", Holidays.time.text))
            }
            premiumToggle(L.s("holiday_day_before"), Holidays.dayBefore, premium) { Holidays.dayBefore = $0 }
            premiumToggle(L.s("holiday_special"), Holidays.specialDays, premium) { Holidays.specialDays = $0 }
            if Holidays.specialDays || !premium {
                Text(L.s("holiday_special_hint")).font(.caption).foregroundStyle(Color.muted)
            }
            if let hint { Text(hint).font(.footnote).foregroundStyle(Color.gold) }
            if let status = holidayStatus { Text(status).font(.footnote).foregroundStyle(Color.gold) }
            if failed && !Holidays.hasData() {
                Button(L.s("weather_refresh")) { Task { await load(force: true) } }.bengStyle(.secondary)
            }
        }
        .card([Color(argb: 0xEE2E3F6E), Color(argb: 0xEE33285E)], radius: 22)
    }

    private var holidayStatus: String? {
        if loading && !Holidays.hasData() { return L.s("holiday_loading") }
        if Holidays.unsupported() { return L.s("holiday_unsupported", Self.countryName(Holidays.country)) }
        if failed && !Holidays.hasData() { return L.s("holiday_failed") }
        return nil
    }

    /// A switch that only Premium can turn on; otherwise it leads to the Premium tab.
    private func premiumToggle(_ title: String, _ value: Bool, _ premium: Bool, change: @escaping (Bool) -> Void) -> some View {
        BengToggle(title: title, locked: !premium, isOn: Binding(get: { value && premium }, set: { on in
            if on && !premium {
                hint = L.s("reminder_premium_hint")
                app.tab = .premium
            } else {
                change(on)
                Scheduler.request()
            }
        }))
    }

    @ViewBuilder private var upcomingHolidays: some View {
        let today = Day.today()
        let all = Holidays.upcoming(today)
        if !all.isEmpty {
            Text(L.s("holiday_upcoming", Self.countryName(Holidays.country))).sectionTitle()
            VStack(spacing: 0) {
                ForEach(Array((showAll ? all : Array(all.prefix(5))).enumerated()), id: \.offset) { index, holiday in
                    if index > 0 { Divider().overlay(Color(argb: 0x338C8CD8)) }
                    holidayRow(holiday, today)
                }
            }
            .card()
            if all.count > 5 && !showAll {
                Button(L.s("holiday_show_all", all.count)) { showAll = true }.bengStyle(.secondary)
            }
        }
    }

    private func holidayRow(_ holiday: Holiday, _ today: Day) -> some View {
        var parts = [holiday.date == today || holiday.date == today.adding(days: 1) ? Self.whenText(holiday.date, today) : L.weekday(holiday.date.weekday)]
        if holiday.days > 1 { parts.append(L.p("day_count", holiday.days)) }
        if holiday.special { parts.append(L.s("holiday_special_tag")) }
        if holiday.tentative { parts.append(L.s("holiday_tentative")) }
        return HStack(spacing: 12) {
            VStack(spacing: 2) {
                Text("\(holiday.date.day)").font(.title3.weight(.semibold)).foregroundStyle(.white)
                Text(L.pattern("MMM", holiday.date.start).uppercased(with: AppLanguage.locale)).font(.caption2).foregroundStyle(Color.muted)
            }
            .frame(width: 54, height: 54)
            .background(holiday.special ? Color(argb: 0x30FFD98C) : Color(argb: 0x404E407B), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(holiday.special ? Color(argb: 0x88FFD98C) : Color(argb: 0x668C8CD8)))
            VStack(alignment: .leading, spacing: 2) {
                Text(Holidays.displayName(holiday)).font(.subheadline.weight(.semibold)).foregroundStyle(.white).lineLimit(2)
                Text(parts.joined(separator: " · ")).font(.footnote).foregroundStyle(Color.muted)
            }
            Spacer()
        }
        .padding(.vertical, 10)
        .accessibilityElement(children: .combine)
    }
}

struct TimePickSheet: View {
    var initial: ClockTime
    var picked: (ClockTime) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            DatePicker("", selection: $date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "en_GB"))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Starfield())
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("OK") {
                            picked(ClockTime(date))
                            dismiss()
                        }
                    }
                }
                .onAppear { date = Day.today().at(initial) }
        }
        .presentationDetents([.medium])
    }
}

/// Creates or edits a reminder: what, when, how often, an early heads-up and whether it rings.
struct ReminderEditorView: View {
    let original: Reminder?
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var draft: Reminder
    @State private var sheet: Sheet?
    @State private var message: String?
    @State private var confirmDelete = false

    enum Sheet: String, Identifiable {
        case date, typeDate, time, repeatRule, days, lead
        var id: String { rawValue }
    }

    init(original: Reminder?) {
        self.original = original
        let start = Date().addingTimeInterval(3600)
        let hour = Cal.calendar.component(.hour, from: start)
        _draft = State(initialValue: original ?? Reminder(title: "", date: Day(start), time: ClockTime(hour: hour, minute: 0)))
    }

    private var premium: Bool { Premium.unlocked }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                VStack(spacing: 8) {
                    input(L.s("reminder_title_hint"), $draft.title, max: 80, font: .title3)
                    input(L.s("reminder_note_hint"), $draft.note, max: 200, font: .body)
                }
                .card()
                VStack(alignment: .leading, spacing: 8) {
                    Text(L.s("reminder_when")).font(.headline).foregroundStyle(.white)
                    HStack(spacing: 6) {
                        Button(L.date(draft.date)) { sheet = .date }.bengStyle(.secondary).disabled(draft.repeatRule == .daily)
                        Button(draft.time.text) { sheet = .time }.bengStyle(.secondary).frame(maxWidth: 120)
                    }
                    Button(L.s("reminder_repeat_value", RemindersView.repeatText(draft))) { sheet = .repeatRule }.bengStyle(.secondary)
                }
                .card()
                VStack(alignment: .leading, spacing: 8) {
                    Text(L.s("reminder_extras")).font(.headline).foregroundStyle(.white)
                    if draft.repeatRule != .daily {
                        Button(L.s("reminder_lead_value", leadText) + (premium ? "" : "  ✦")) {
                            if premium { sheet = .lead } else { askPremium() }
                        }.bengStyle(.secondary)
                    }
                    BengToggle(title: L.s("reminder_ring"), locked: !premium, isOn: Binding(get: { draft.ring && premium }, set: { on in
                        if on && !premium { askPremium() } else { draft.ring = on }
                    }))
                    Text(L.s("reminder_ring_hint")).font(.caption).foregroundStyle(Color.muted)
                }
                .card()
                if let message { Text(message).font(.subheadline).foregroundStyle(Color.rose).frame(maxWidth: .infinity) }
                Button(L.s("save")) { save() }.bengStyle()
                Button(L.s("cancel")) { dismiss() }.bengStyle(.secondary)
                if original != nil { Button(L.s("reminder_delete")) { confirmDelete = true }.bengStyle(.destructive) }
            }
            .screen()
            .navigationTitle(L.s(original == nil ? "reminder_new" : "reminder_edit"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .sheet(item: $sheet) { sheet in sheetView(sheet) }
        .confirmationDialog(L.s("reminder_delete_confirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L.s("delete"), role: .destructive) {
                if let original { Reminders.delete(original.id) }
                Scheduler.request()
                dismiss()
            }
            Button(L.s("cancel"), role: .cancel) {}
        }
    }

    private var leadText: String {
        L.s(draft.leadDays == 1 ? "reminder_lead_day" : draft.leadDays == 7 ? "reminder_lead_week" : "reminder_lead_none")
    }

    private func input(_ hint: String, _ text: Binding<String>, max: Int, font: Font) -> some View {
        TextField(hint, text: text)
            .font(font)
            .foregroundStyle(.white)
            .padding(12)
            .background(LinearGradient(colors: [Color(argb: 0xDA11192F), Color(argb: 0xD6231C37)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0x748C8CD8)))
            .onChange(of: text.wrappedValue) { _, value in if value.count > max { text.wrappedValue = String(value.prefix(max)) } }
            .accessibilityLabel(hint)
    }

    @ViewBuilder private func sheetView(_ sheet: Sheet) -> some View {
        switch sheet {
        case .date:
            DatePickSheet(initial: draft.date, earliest: .today(), extraTitle: L.s("type_date"), extra: { self.sheet = .typeDate }) { setDate($0) }
        case .typeDate:
            TypeDateSheet(initial: max(draft.date, .today()), earliest: .today()) { setDate($0) }
        case .time:
            TimePickSheet(initial: draft.time) { draft.time = $0 }
        case .repeatRule:
            ChoiceSheet(title: L.s("reminder_repeat"), values: ReminderRepeat.allCases, selected: draft.repeatRule, label: { L.s($0.labelKey) }) { rule in
                draft.repeatRule = rule
                if rule == .daily { draft.leadDays = 0 }
                if rule == .weekly {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { self.sheet = .days }
                }
            }
        case .days:
            DaysSheet(initial: draft.days.isEmpty ? [draft.date.weekday] : draft.days, allowOnce: false) { days in
                draft.days = days.isEmpty ? [draft.date.weekday] : days
            }
        case .lead:
            ChoiceSheet(title: L.s("reminder_lead"), values: [0, 1, 7], selected: draft.leadDays,
                        label: { L.s($0 == 1 ? "reminder_lead_day" : $0 == 7 ? "reminder_lead_week" : "reminder_lead_none") }) { draft.leadDays = $0 }
        }
    }

    private func setDate(_ day: Day) {
        draft.date = day
        if draft.repeatRule == .weekly && draft.days.isEmpty { draft.days = [day.weekday] }
    }

    private func askPremium() {
        message = L.s("reminder_premium_hint")
        dismiss()
        app.tab = .premium
    }

    private func save() {
        var reminder = draft
        reminder.title = reminder.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !reminder.title.isEmpty else {
            message = L.s("reminder_title_required")
            return
        }
        reminder.note = reminder.note.trimmingCharacters(in: .whitespacesAndNewlines)
        reminder.days = reminder.repeatRule == .weekly ? (reminder.days.isEmpty ? [reminder.date.weekday] : reminder.days) : []
        reminder.leadDays = premium && reminder.repeatRule != .daily ? reminder.leadDays : 0
        reminder.ring = reminder.ring && premium
        reminder.enabled = true
        reminder.snoozeAt = nil
        guard reminder.nextEvent(Date()) != nil else {
            message = L.s("editor_future_required")
            return
        }
        Reminders.save(reminder)
        Scheduler.request()
        dismiss()
    }
}
