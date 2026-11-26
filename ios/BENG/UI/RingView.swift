import SwiftUI
import CoreMotion

enum RingSource: Identifiable {
    case live(RingState)
    case preview(Alarm)

    var id: String {
        switch self {
        case .live(let state): return "live-\(state.alarmId)"
        case .preview(let alarm): return "preview-\(alarm.id)"
        }
    }
}

/// The mission that is running, built once when the screen opens.
@MainActor
final class RingSession: ObservableObject {
    let alarmId: String
    let label: String
    let preview: Bool
    let tone: String?
    let crescendo: Bool
    let startedAt: Date
    let retry: Bool
    @Published var math: MathMission?
    @Published var extra: ExtraMission?
    @Published var tick = 0

    init(_ source: RingSource) {
        let alarm: Alarm?
        switch source {
        case .live(let state):
            alarmId = state.alarmId
            preview = false
            startedAt = state.startedAt
            retry = state.retry
            if state.alarmId.hasPrefix(Reminders.prefix) {
                let reminder = Reminders.get(String(state.alarmId.dropFirst(Reminders.prefix.count)))
                label = reminder?.title ?? ""
                alarm = nil
            } else {
                alarm = AlarmStore.get(state.alarmId)
                label = alarm?.label ?? ""
            }
        case .preview(let draft):
            alarmId = "preview"
            preview = true
            startedAt = Date()
            retry = false
            alarm = draft
            label = draft.label
        }
        tone = BuiltInTone.playable(alarm?.tone) ?? alarm.map { _ in BuiltInTone.all[0].id }
        crescendo = alarm?.crescendo ?? true
        let task: AlarmTask = alarm.map { preview ? $0.task : RingFlow.effectiveTask($0) } ?? .noTask
        if task == .math { math = MathMission(alarm?.math ?? MathSettings()) }
        if task != .math && task != .noTask {
            extra = ExtraMission(task: task, qrCode: alarm?.qrCode ?? "", phrase: ExtraMission.randomPhrase(), stepGoal: alarm?.stepGoal ?? StepGoal.default)
        }
    }

    func useMathFallback() {
        extra = nil
        math = MathMission(MathSettings())
    }

    var complete: Bool {
        if let math { return math.question == nil }
        if let extra { return extra.complete }
        return true
    }
}

struct RingView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var phase
    @StateObject private var session: RingSession
    @State private var toast: String?
    private let keepAlive = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    init(source: RingSource) {
        _session = StateObject(wrappedValue: RingSession(source))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            VStack(alignment: .leading, spacing: 10) { mission }.card()
            if !session.preview {
                Button(L.s("snooze_5")) { snooze() }.bengStyle(.secondary)
            }
            if let toast { Text(toast).font(.subheadline).foregroundStyle(Color.gold).frame(maxWidth: .infinity) }
        }
        .screen()
        .interactiveDismissDisabled(!session.preview)
        .onAppear(perform: start)
        .onDisappear { AlarmAudio.shared.stop() }
        .onReceive(keepAlive) { _ in keepRinging() }
        .onChange(of: phase) { _, phase in if phase == .active { keepRinging() } }
    }

    private var header: some View {
        VStack(spacing: 6) {
            Text(L.s(session.preview ? "preview" : "alarm_ringing")).font(.title2.weight(.semibold)).foregroundStyle(.white)
            if !session.label.isEmpty { Text(session.label).foregroundStyle(Color.muted) }
            TimeText(L.clock(session.startedAt), size: 60)
            AstralPlanet().frame(height: 112)
            if session.preview {
                Text(L.s("preview_note")).font(.footnote).foregroundStyle(Color.muted)
                Button(L.s("preview_close")) { close() }.bengStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .card()
    }

    @ViewBuilder private var mission: some View {
        if let extra = session.extra {
            Text(extra.task.label).font(.title3.weight(.semibold)).foregroundStyle(.white)
            switch extra.task {
            case .memory: MemoryMission(mission: extra, done: finish)
            case .shapes: ShapesMissionView(mission: extra, done: finish)
            case .typing: TypingMission(mission: extra, done: finish)
            case .shake: ShakeMission(mission: extra, done: finish, fallback: session.useMathFallback)
            case .steps: StepsMission(mission: extra, done: finish, fallback: session.useMathFallback)
            case .qr: QRMission(mission: extra, done: finish, fallback: session.useMathFallback)
            default: EmptyView()
            }
        } else if let math = session.math {
            MathMissionView(mission: math, done: finish)
        } else {
            Button(L.s("dismiss_alarm")) { finish() }.bengStyle()
        }
    }

    private func start() {
        if !AlarmAudio.shared.playing {
            let played = AlarmAudio.shared.play(tone: session.tone, crescendo: session.crescendo, limit: session.preview ? 30 : nil,
                                                vibrate: !session.preview)
            if !played && session.preview { toast = L.s("tone_play_failed") }
        }
        keepRinging()
    }

    /// While the mission is open, the safety re-ring keeps moving out; leaving the app lets it ring.
    private func keepRinging() {
        guard !session.preview, RingStore.queue.contains(where: { $0.alarmId == session.alarmId }) else { return }
        RingFlow.began(session.alarmId, kind: session.retry ? .wake : .rering)
        Scheduler.request()
    }

    private func finish() {
        AlarmAudio.shared.stop()
        if session.preview {
            toast = L.s("preview_done")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { dismiss() }
            return
        }
        RingFlow.finish(session.alarmId, startedAt: session.startedAt, retry: session.retry)
        let showBriefing = MorningBriefing.enabled && !session.alarmId.hasPrefix(Reminders.prefix)
        MorningBriefing.pendingShow = false
        Scheduler.request()
        dismiss()
        if showBriefing {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                app.briefingPreview = false
                app.showBriefing = true
            }
        }
    }

    private func snooze() {
        AlarmAudio.shared.stop()
        RingFlow.snooze(session.alarmId)
        Scheduler.request()
        dismiss()
    }

    private func close() {
        AlarmAudio.shared.stop()
        dismiss()
    }
}

// MARK: - Missions

struct MathMissionView: View {
    let mission: MathMission
    var done: () -> Void
    @State private var answer = ""
    @State private var wrong = false
    @State private var round = 0

    var body: some View {
        let _ = round
        Text(L.s("math_progress", mission.current, mission.total)).font(.subheadline).foregroundStyle(Color.muted)
        Text("\(mission.question?.expression ?? "") = ?").font(.system(size: 32, weight: .semibold)).foregroundStyle(.white)
            .frame(maxWidth: .infinity)
        TextField(L.s("math_answer"), text: $answer)
            .keyboardType(.numberPad)
            .font(.title2)
            .foregroundStyle(.white)
            .padding(12)
            .background(Color(argb: 0xDA11192F), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(wrong ? Color.rose : Color(argb: 0x748C8CD8)))
        if wrong { Text(L.s("try_again")).font(.footnote).foregroundStyle(Color.rose) }
        Button(L.s("math_check")) {
            switch mission.submit(Int(answer.trimmingCharacters(in: .whitespaces))) {
            case .incorrect: wrong = true
            case .next:
                wrong = false
                answer = ""
                round += 1
            case .complete: done()
            }
        }.bengStyle()
    }
}

struct MemoryMission: View {
    let mission: ExtraMission
    var done: () -> Void
    @State private var display = "•"
    @State private var status = L.s("memory_ready")
    @State private var keysEnabled = false
    @State private var showing: Task<Void, Never>?

    var body: some View {
        Text(L.s("memory_instructions")).foregroundStyle(.white)
        Text(display).font(.system(size: 64, weight: .bold)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 104)
        Text(status).foregroundStyle(.white)
        HStack(spacing: 8) {
            ForEach(1...4, id: \.self) { number in
                Button("\(number)") {
                    let correct = mission.tap(number)
                    status = correct ? "\(mission.progress) / \(mission.sequence.count)" : L.s("memory_wrong")
                    if mission.complete { done() }
                }
                .bengStyle(.secondary)
                .font(.system(size: 30))
                .disabled(!keysEnabled)
            }
        }
        Button(L.s("memory_show")) { show() }.bengStyle(.secondary)
    }

    private func show() {
        showing?.cancel()
        mission.resetSequence()
        status = L.s("memory_remember")
        keysEnabled = false
        showing = Task { @MainActor in
            for number in mission.sequence {
                display = "\(number)"
                try? await Task.sleep(for: .milliseconds(550))
                display = "•"
                try? await Task.sleep(for: .milliseconds(250))
                if Task.isCancelled { return }
            }
            status = L.s("memory_repeat", mission.progress, mission.sequence.count)
            keysEnabled = true
        }
    }
}

struct ShapesMissionView: View {
    let mission: ExtraMission
    var done: () -> Void
    @State private var status = ""
    @State private var round = 0

    private func name(_ shape: Int) -> String { L.s(ShapeMission.nameKeys[shape]) }

    var body: some View {
        let _ = round
        let shapes = mission.shapes
        Text(L.s("shapes_instructions")).foregroundStyle(.white)
        Text(shapes.targets.map { ShapeMission.symbols[$0] }.joined(separator: "  ")).font(.system(size: 36)).foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .accessibilityLabel(shapes.targets.map(name).joined(separator: ", "))
        Text(status.isEmpty ? L.s("shapes_find", shapes.progress, name(shapes.targets[min(shapes.progress, 3)])) : status)
            .font(.headline).foregroundStyle(.white)
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 4), spacing: 4) {
            ForEach(0..<16, id: \.self) { index in
                let picked = shapes.selected.contains(index)
                Button(ShapeMission.symbols[shapes.board[index]]) {
                    if mission.pickShape(index) {
                        if mission.complete { done() } else {
                            status = L.s("shapes_find", shapes.progress, name(shapes.targets[shapes.progress]))
                        }
                    } else {
                        status = L.s("shapes_next", name(shapes.targets[shapes.progress]))
                    }
                    round += 1
                }
                .bengStyle(.secondary)
                .font(.system(size: 32))
                .disabled(picked)
                .opacity(picked ? 0.35 : 1)
                .accessibilityLabel(L.s("shapes_cell", index / 4 + 1, index % 4 + 1, name(shapes.board[index])))
            }
        }
    }
}

struct TypingMission: View {
    let mission: ExtraMission
    var done: () -> Void
    @State private var text = ""
    @State private var wrong = false

    var body: some View {
        Text(mission.phrase).font(.title2).foregroundStyle(.white)
        TextField(L.s("typing_hint"), text: $text)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .foregroundStyle(.white)
            .padding(12)
            .background(Color(argb: 0xDA11192F), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(wrong ? Color.rose : Color(argb: 0x748C8CD8)))
        if wrong { Text(L.s("typing_wrong")).font(.footnote).foregroundStyle(Color.rose) }
        Button(L.s("typing_check")) {
            if mission.type(text) { done() } else { wrong = true }
        }.bengStyle()
    }
}

struct ShakeMission: View {
    let mission: ExtraMission
    var done: () -> Void
    var fallback: () -> Void
    @State private var progress = 0
    @State private var available = true
    @State private var motion = CMMotionManager()

    var body: some View {
        Text("\(progress) / \(ExtraMission.shakeCount)").font(.title).foregroundStyle(.white)
        if !available { Button(L.s("math_fallback")) { fallback() }.bengStyle(.secondary) }
        Color.clear.frame(height: 1)
            .onAppear(perform: start)
            .onDisappear { motion.stopAccelerometerUpdates() }
    }

    private func start() {
        guard motion.isAccelerometerAvailable else {
            available = false
            return
        }
        var lastShake = Date.distantPast
        var above = false
        motion.accelerometerUpdateInterval = 1 / 50
        motion.startAccelerometerUpdates(to: .main) { data, _ in
            guard let a = data?.acceleration, !mission.complete else { return }
            // Same threshold as Android: about 23 m/s² including gravity.
            let high = a.x * a.x + a.y * a.y + a.z * a.z >= 2.35 * 2.35
            if high && !above && Date().timeIntervalSince(lastShake) >= 0.3 {
                lastShake = Date()
                mission.shake()
                progress = mission.progress
                if mission.complete {
                    motion.stopAccelerometerUpdates()
                    done()
                }
            }
            above = high
        }
    }
}

/// Walk to turn the alarm off: the pedometer only counts real steps, not shaking in bed.
struct StepsMission: View {
    let mission: ExtraMission
    var done: () -> Void
    var fallback: () -> Void
    @State private var progress = 0
    @State private var available = true
    @State private var pedometer = CMPedometer()

    var body: some View {
        Text(L.s("steps_instructions")).foregroundStyle(.white)
        TimeText(L.s("steps_progress", progress, mission.stepGoal), size: 40).frame(maxWidth: .infinity)
        if !available { Button(L.s("math_fallback")) { fallback() }.bengStyle(.secondary) }
        Color.clear.frame(height: 1)
            .onAppear(perform: start)
            .onDisappear { pedometer.stopUpdates() }
    }

    private func start() {
        guard CMPedometer.isStepCountingAvailable(), CMPedometer.authorizationStatus() != .denied,
              CMPedometer.authorizationStatus() != .restricted else {
            available = false
            return
        }
        pedometer.startUpdates(from: Date()) { data, error in
            DispatchQueue.main.async {
                if error != nil, data == nil {
                    available = false
                    return
                }
                guard let steps = data?.numberOfSteps.doubleValue, !mission.complete else { return }
                mission.stepTotal(steps)
                progress = mission.progress
                if mission.complete {
                    pedometer.stopUpdates()
                    done()
                }
            }
        }
    }
}

struct QRMission: View {
    let mission: ExtraMission
    var done: () -> Void
    var fallback: () -> Void
    @State private var scanning = false
    @State private var failed = false
    @State private var wrong = false

    var body: some View {
        Text(L.s("qr_scan_instructions")).foregroundStyle(.white)
        Button(L.s("qr_open_camera")) { scanning = true }.bengStyle()
        if wrong { Text(L.s("qr_wrong_code")).font(.footnote).foregroundStyle(Color.rose) }
        if failed {
            Text(L.s("camera_unavailable")).foregroundStyle(.white)
            Button(L.s("math_fallback")) { fallback() }.bengStyle(.secondary)
        }
        Color.clear.frame(height: 1)
            .sheet(isPresented: $scanning) {
                QRScannerSheet(failed: { failed = true }) { code in
                    if mission.scan(code) { done() } else { wrong = true }
                }
            }
    }
}
