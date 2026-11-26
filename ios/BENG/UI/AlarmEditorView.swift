import SwiftUI
import AVFoundation
import CoreMotion

struct AlarmEditorView: View {
    let original: Alarm?
    var enableOnSave = false
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var draft: Alarm
    /// Nil while a one-time alarm follows "today or tomorrow".
    @State private var pickedDate: Day?
    @State private var selectingMinutes = false
    @State private var sheet: EditorSheet?
    @State private var message: String?
    @State private var confirmDelete = false
    @State private var preview: RingSource?

    enum EditorSheet: String, Identifiable {
        case typeTime, date, typeDate, days, tone, task, difficulty, count, steps, qr
        var id: String { rawValue }
    }

    init(original: Alarm?, enableOnSave: Bool = false) {
        self.original = original
        self.enableOnSave = enableOnSave
        let start = Date().addingTimeInterval(120)
        let alarm = original ?? Alarm(time: ClockTime(start))
        _draft = State(initialValue: alarm)
        _pickedDate = State(initialValue: alarm.repeating ? nil : alarm.date)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                TextField(L.s("editor_name_hint"), text: $draft.label)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(LinearGradient(colors: [Color(argb: 0xDA11192F), Color(argb: 0xD6231C37)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0x748C8CD8)))
                    .onChange(of: draft.label) { _, value in if value.count > 60 { draft.label = String(value.prefix(60)) } }
                    .accessibilityLabel(L.s("editor_name"))
                schedule
                section(L.s("editor_date")) {
                    Button(dateText) { sheet = .date }.bengStyle(.secondary).disabled(draft.repeating)
                    if !draft.repeating { Button(L.s("type_date")) { sheet = .typeDate }.bengStyle(.secondary) }
                }
                section(L.s("editor_repeat_days")) {
                    Button(repeatText) { sheet = .days }.bengStyle(.secondary)
                }
                section(L.s("editor_sound")) {
                    Button(L.s("editor_tone_value", BuiltInTone.title(draft.tone))) { sheet = .tone }.bengStyle(.secondary)
                    Button(L.s(draft.crescendo ? "editor_crescendo_on" : "editor_crescendo_off")) { draft.crescendo.toggle() }.bengStyle(.secondary)
                }
                section(L.s("editor_task")) {
                    Button(draft.task.premium ? "\(draft.task.label)  ✦" : draft.task.label) { sheet = .task }.bengStyle(.secondary)
                    if draft.task == .qr {
                        Button(L.s(draft.qrCode.isEmpty ? "editor_qr_save" : "editor_qr_change")) { sheet = .qr }.bengStyle(.secondary)
                    }
                    if draft.task == .steps {
                        Button(L.s("editor_step_goal_value", draft.stepGoal)) { sheet = .steps }.bengStyle(.secondary)
                    }
                    if draft.task == .math {
                        Button(L.s("editor_difficulty_value", draft.math.difficulty.label)) { sheet = .difficulty }.bengStyle(.secondary)
                        Button(L.s("editor_question_count_value", draft.math.count)) { sheet = .count }.bengStyle(.secondary)
                    }
                    if draft.task != .noTask {
                        Text(L.s("ios_rering_note")).font(.footnote).foregroundStyle(Color.muted)
                    }
                    Button(L.s("editor_preview")) { openPreview() }.bengStyle(.secondary)
                }
                if let message {
                    Text(message).font(.subheadline).foregroundStyle(Color.rose).frame(maxWidth: .infinity, alignment: .center)
                }
                Button(L.s("save")) { save() }.bengStyle()
                Button(L.s("cancel")) { dismiss() }.bengStyle(.secondary)
                if original != nil {
                    Button(L.s("editor_delete")) { confirmDelete = true }.bengStyle(.destructive)
                }
            }
            .screen()
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(item: $sheet) { sheet in sheetView(sheet) }
        .fullScreenCover(item: $preview) { source in RingView(source: source).environmentObject(app) }
        .confirmationDialog(L.s("editor_delete_confirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L.s("delete"), role: .destructive) {
                if let original { AlarmStore.delete(original.id) }
                Scheduler.request()
                dismiss()
            }
            Button(L.s("cancel"), role: .cancel) {}
        }
        .onDisappear { AlarmAudio.shared.stop() }
    }

    private var schedule: some View {
        VStack(spacing: 8) {
            Button { sheet = .typeTime } label: {
                TimeText(draft.time.text, size: 56).frame(maxWidth: .infinity)
            }
            .accessibilityLabel(L.s("type_time_description", draft.time.text))
            HStack(spacing: 8) {
                Button(L.s("editor_hour")) { selectingMinutes = false }.bengStyle(selectingMinutes ? .secondary : .primary)
                Button(L.s("editor_minute")) { selectingMinutes = true }.bengStyle(selectingMinutes ? .primary : .secondary)
                Button(L.s("type_time")) { sheet = .typeTime }.bengStyle(.secondary).accessibilityLabel(L.s("type_time_title"))
            }
            OrbitDial(time: $draft.time, selectingMinutes: selectingMinutes).frame(height: 268)
        }
        .card()
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.headline).foregroundStyle(.white).accessibilityAddTraits(.isHeader)
            content()
        }
        .card()
    }

    private var autoDate: Day {
        let today = Day.today()
        return today.at(draft.time) > Date() ? today : today.adding(days: 1)
    }

    private var dateText: String {
        if draft.repeating { return L.s("editor_date_by_days") }
        if let pickedDate { return L.date(pickedDate) }
        return L.s(autoDate == .today() ? "editor_auto_today" : "editor_auto_tomorrow")
    }

    private var repeatText: String {
        switch draft.days.count {
        case 0: return L.s("repeat_once")
        case 7: return L.s("repeat_every_day")
        default: return draft.days.sorted().map { L.weekday($0, short: true) }.joined(separator: " · ")
        }
    }

    @ViewBuilder private func sheetView(_ sheet: EditorSheet) -> some View {
        switch sheet {
        case .typeTime:
            TypeTimeSheet(initial: draft.time) { draft.time = $0 }
        case .date:
            DatePickSheet(initial: pickedDate ?? .today(), earliest: .today(), extraTitle: L.s("editor_today_or_tomorrow"),
                          extra: { pickedDate = nil }) { pickedDate = $0 }
        case .typeDate:
            TypeDateSheet(initial: max(pickedDate ?? .today(), .today()), earliest: .today()) { pickedDate = $0 }
        case .days:
            DaysSheet(initial: draft.days) { draft.days = $0 }
        case .tone:
            ToneSheet(selected: draft.tone) { draft.tone = $0 }
        case .task:
            ChoiceSheet(title: L.s("editor_task"), values: AlarmTask.allCases, selected: draft.task,
                        label: { $0.premium ? "\($0.label)  ✦" : $0.label }) { selectTask($0) }
        case .difficulty:
            ChoiceSheet(title: L.s("editor_math_difficulty"), values: MathDifficulty.allCases, selected: draft.math.difficulty,
                        label: { $0.label }) { draft.math.difficulty = $0 }
        case .count:
            ChoiceSheet(title: L.s("editor_question_count"), values: Array(MathSettings.counts), selected: draft.math.count,
                        label: { L.p("question_count", $0) }) { draft.math.count = $0 }
        case .steps:
            ChoiceSheet(title: L.s("editor_step_goal"), values: StepGoal.options, selected: draft.stepGoal,
                        label: { L.s("editor_step_goal_value", $0) }) { draft.stepGoal = $0 }
        case .qr:
            QRScannerSheet { code in draft.qrCode = code }
        }
    }

    private func selectTask(_ task: AlarmTask) {
        message = nil
        if task == .shake && !CMMotionManager().isAccelerometerAvailable {
            message = L.s("editor_no_motion_sensor")
            return
        }
        if task == .steps {
            guard CMPedometer.isStepCountingAvailable() else {
                message = L.s("editor_no_step_sensor")
                return
            }
            // Asks for Motion & Fitness access now, not while the alarm rings.
            if CMPedometer.authorizationStatus() == .notDetermined {
                CMPedometer().queryPedometerData(from: Date().addingTimeInterval(-60), to: Date()) { _, _ in }
            }
        }
        draft.task = task
    }

    private func openPreview() {
        if draft.task == .qr && draft.qrCode.isEmpty {
            message = L.s("editor_qr_preview_needs_code")
            return
        }
        AlarmAudio.shared.stop()
        preview = .preview(draft)
    }

    private func save() {
        message = nil
        if draft.task.premium && !Premium.unlocked {
            dismiss()
            app.tab = .premium
            return
        }
        if let tone = BuiltInTone.find(draft.tone), tone.premium && !Premium.unlocked {
            message = L.s("tone_selected_premium_only")
            return
        }
        if draft.task == .qr && draft.qrCode.isEmpty {
            message = L.s("editor_qr_required")
            return
        }
        if draft.task == .steps && CMPedometer.authorizationStatus() == .denied {
            message = L.s("editor_steps_permission_denied")
            return
        }
        if let original, AlarmStore.get(original.id) == nil {
            dismiss()
            return
        }
        var alarm = draft
        alarm.label = alarm.label.trimmingCharacters(in: .whitespacesAndNewlines)
        // A one-time alarm always gets its real day, so the app knows when it has rung.
        alarm.date = alarm.repeating ? nil : (pickedDate ?? autoDate)
        alarm.enabled = true
        alarm.snoozeAt = nil
        alarm.quick = false
        guard alarm.nextAfter(Date()) != nil else {
            message = L.s("editor_future_required")
            return
        }
        AlarmStore.save(alarm)
        Scheduler.request()
        dismiss()
    }
}

/// Built-in tones with a short preview; Premium tones are marked ✦.
struct ToneSheet: View {
    var selected: String?
    var picked: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var choice: String?
    @State private var note: String?

    var body: some View {
        NavigationStack {
            List {
                row(nil, title: L.s("tone_device_default"), detail: nil)
                ForEach(BuiltInTone.all) { tone in
                    row(tone.id, title: tone.title + (tone.premium ? " ✦" : ""), detail: tone.detail)
                }
                if let note { Text(note).foregroundStyle(Color.gold) }
            }
            .scrollContentBackground(.hidden)
            .background(Starfield())
            .navigationTitle(L.s("editor_sound"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L.s("apply")) {
                        if let tone = BuiltInTone.find(choice), tone.premium && !Premium.unlocked {
                            note = L.s("tone_premium_only")
                            return
                        }
                        picked(choice)
                        dismiss()
                    }
                }
            }
            .onAppear { choice = selected }
            .onDisappear { AlarmAudio.shared.stop() }
        }
    }

    private func row(_ id: String?, title: String, detail: String?) -> some View {
        Button {
            choice = id
            note = nil
            if RingStore.active == nil { AlarmAudio.shared.play(tone: id, crescendo: false, loop: false) }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.white)
                    if let detail { Text(detail).font(.footnote).foregroundStyle(Color.muted) }
                }
                Spacer()
                if choice == id { Image(systemName: "checkmark").foregroundStyle(Color.accent) }
            }
        }
    }
}
