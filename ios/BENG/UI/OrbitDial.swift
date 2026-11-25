import SwiftUI

/// The orbit clock face: hours on two rings (0–11 outside, 12–23 inside) or minutes on one, tap to pick.
struct OrbitDial: View {
    @Binding var time: ClockTime
    var selectingMinutes: Bool

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let outer = min(size.width, size.height) * 0.40
            let inner = outer * 0.66
            Canvas { context, _ in
                for radius in [outer, inner, outer * 1.13] {
                    context.stroke(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                                   with: .color(Color(argb: 0x70626EBC)), lineWidth: 1)
                }
                let selected = selectingMinutes ? time.minute : time.hour % 12
                let count = selectingMinutes ? 60 : 12
                let selectedRadius = (!selectingMinutes && time.hour >= 12) ? inner : outer
                let hand = point(selected, count, selectedRadius, center)
                var line = Path()
                line.move(to: center)
                line.addLine(to: hand)
                context.stroke(line, with: .color(Color(argb: 0xB09BDFFF)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                context.fill(Path(ellipseIn: CGRect(x: hand.x - 26, y: hand.y - 26, width: 52, height: 52)),
                             with: .radialGradient(Gradient(colors: [Color(argb: 0xA6B3A8FF), Color(argb: 0x00B3A8FF)]),
                                                   center: hand, startRadius: 0, endRadius: 26))
                context.fill(Path(ellipseIn: CGRect(x: hand.x - 15, y: hand.y - 15, width: 30, height: 30)),
                             with: .linearGradient(Gradient(colors: [Color(hex: 0x9BDFFF), Color(hex: 0xC3A0FC)]),
                                                   startPoint: CGPoint(x: hand.x - 15, y: hand.y - 15), endPoint: CGPoint(x: hand.x + 15, y: hand.y + 15)))
                func number(_ text: String, _ index: Int, _ count: Int, _ radius: CGFloat, _ isSelected: Bool) {
                    let spot = point(index, count, radius, center)
                    context.draw(Text(text).font(.system(size: isSelected ? 14 : 13, weight: .medium))
                        .foregroundStyle(isSelected ? Color.midnight : Color(hex: 0xF2F2FF)), at: spot)
                }
                if selectingMinutes {
                    for minute in 0..<60 {
                        if minute % 5 == 0 {
                            number(String(format: "%02d", minute), minute, 60, outer, minute == selected)
                        } else {
                            let tick = point(minute, 60, outer, center)
                            let r: CGFloat = minute == selected ? 3 : 1
                            context.fill(Path(ellipseIn: CGRect(x: tick.x - r, y: tick.y - r, width: r * 2, height: r * 2)),
                                         with: .color(minute == selected ? .midnight : Color(argb: 0x876CA7F1)))
                        }
                    }
                } else {
                    for hour in 0..<12 {
                        number("\(hour)", hour, 12, outer, time.hour == hour)
                        number("\(hour + 12)", hour, 12, inner, time.hour == hour + 12)
                    }
                }
                context.fill(Path(ellipseIn: CGRect(x: center.x - 3, y: center.y - 3, width: 6, height: 6)), with: .color(Color(hex: 0xD6D9FF)))
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onEnded { value in
                pick(value.location, center: center, outer: outer, inner: inner)
            })
        }
        .accessibilityElement()
        .accessibilityLabel(selectingMinutes ? L.s("orbit_minute", time.minute) : L.s("orbit_hour", time.hour))
        .accessibilityAdjustableAction { direction in
            let delta = direction == .increment ? 1 : -1
            time = selectingMinutes ? ClockTime(hour: time.hour, minute: ((time.minute + delta) % 60 + 60) % 60) : time.adding(hours: delta)
        }
    }

    private func point(_ index: Int, _ count: Int, _ radius: CGFloat, _ center: CGPoint) -> CGPoint {
        let angle = 2 * Double.pi * Double(index) / Double(count)
        return CGPoint(x: center.x + radius * sin(angle), y: center.y - radius * cos(angle))
    }

    private func pick(_ location: CGPoint, center: CGPoint, outer: CGFloat, inner: CGFloat) {
        let x = location.x - center.x, y = location.y - center.y
        let distance = hypot(x, y)
        guard distance >= inner * 0.65, distance <= outer + 30 else { return }
        var turns = atan2(Double(x), Double(-y)) / (2 * Double.pi)
        if turns < 0 { turns += 1 }
        if selectingMinutes {
            time = ClockTime(hour: time.hour, minute: Int((turns * 60).rounded()) % 60)
        } else {
            let hour = Int((turns * 12).rounded()) % 12
            let useInner = distance < (inner + outer) / 2
            time = ClockTime(hour: hour + (useInner ? 12 : 0), minute: time.minute)
        }
        UISelectionFeedbackGenerator().selectionChanged()
    }
}

/// Type a time as hours and minutes instead of tapping it on the clock.
struct TypeTimeSheet: View {
    var initial: ClockTime
    var picked: (ClockTime) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var hour = ""
    @State private var minute = ""
    @State private var error: String?
    @FocusState private var focus: Int?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                HStack(spacing: 8) {
                    DigitField(hint: L.s("type_hint_hour"), text: $hour, length: 2).focused($focus, equals: 0)
                        .onChange(of: hour) { _, value in if value.count >= 2 { focus = 1 } }
                    Text(":").font(.system(size: 26)).foregroundStyle(.white)
                    DigitField(hint: L.s("type_hint_minute"), text: $minute, length: 2).focused($focus, equals: 1)
                }
                if let error { Text(error).font(.footnote).foregroundStyle(Color.rose) }
                Spacer()
            }
            .padding(24)
            .background(Starfield())
            .navigationTitle(L.s("type_time_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("OK") { submit() } }
            }
            .onAppear {
                hour = String(format: "%02d", initial.hour)
                minute = String(format: "%02d", initial.minute)
                focus = 0
            }
        }
        .presentationDetents([.medium])
    }

    private func submit() {
        guard let time = TypedInput.time(hour, minute) else {
            error = L.s("type_invalid_time")
            return
        }
        picked(time)
        dismiss()
    }
}

/// Type a date as day, month and year, in the order the app language writes them.
struct TypeDateSheet: View {
    var initial: Day
    var earliest: Day
    var picked: (Day) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var day = ""
    @State private var month = ""
    @State private var year = ""
    @State private var error: String?
    @FocusState private var focus: TypedInput.Field?

    var body: some View {
        let order = TypedInput.dateOrder(AppLanguage.locale)
        let separator = TypedInput.separator(AppLanguage.locale)
        NavigationStack {
            VStack(spacing: 16) {
                HStack(spacing: 6) {
                    ForEach(Array(order.enumerated()), id: \.offset) { index, field in
                        if index > 0 { Text(separator).font(.system(size: 26)).foregroundStyle(.white) }
                        fieldView(field, next: index + 1 < order.count ? order[index + 1] : nil)
                    }
                }
                if let error { Text(error).font(.footnote).foregroundStyle(Color.rose) }
                Spacer()
            }
            .padding(24)
            .background(Starfield())
            .navigationTitle(L.s("type_date_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("OK") { submit() } }
            }
            .onAppear {
                day = String(format: "%02d", initial.day)
                month = String(format: "%02d", initial.month)
                year = String(initial.year)
                focus = order.first
            }
        }
        .presentationDetents([.medium])
    }

    @ViewBuilder private func fieldView(_ field: TypedInput.Field, next: TypedInput.Field?) -> some View {
        switch field {
        case .day:
            DigitField(hint: L.s("type_hint_day"), text: $day, length: 2).focused($focus, equals: .day)
                .onChange(of: day) { _, value in if value.count >= 2, let next { focus = next } }
        case .month:
            DigitField(hint: L.s("type_hint_month"), text: $month, length: 2).focused($focus, equals: .month)
                .onChange(of: month) { _, value in if value.count >= 2, let next { focus = next } }
        case .year:
            DigitField(hint: L.s("type_hint_year"), text: $year, length: 4).focused($focus, equals: .year)
                .onChange(of: year) { _, value in if value.count >= 4, let next { focus = next } }
        }
    }

    private func submit() {
        switch TypedInput.date(day, month, year, earliest: earliest) {
        case .ok(let date):
            picked(date)
            dismiss()
        case .tooEarly: error = L.s("type_date_past")
        case .invalid: error = L.s("type_invalid_date")
        }
    }
}

struct DigitField: View {
    var hint: String
    @Binding var text: String
    var length: Int

    var body: some View {
        TextField(hint, text: $text)
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(size: 26))
            .foregroundStyle(.white)
            .padding(.vertical, 9)
            .frame(width: length == 4 ? 110 : 80)
            .background(LinearGradient(colors: [Color(argb: 0xDA11192F), Color(argb: 0xD6231C37)], startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0x748C8CD8)))
            .onChange(of: text) { _, value in
                let digits = String(value.filter(\.isNumber).prefix(length))
                if digits != value { text = digits }
            }
            .accessibilityLabel(hint)
    }
}

/// A calendar in a sheet, with an extra action (e.g. "Today or tomorrow" or "Type a date").
struct DatePickSheet: View {
    var initial: Day
    var earliest: Day
    var extraTitle: String?
    var extra: (() -> Void)?
    var picked: (Day) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker("", selection: $date, in: earliest.start..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .environment(\.locale, AppLanguage.locale)
                if let extraTitle, let extra {
                    Button(extraTitle) {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { extra() }
                    }.bengStyle(.secondary)
                }
                Spacer()
            }
            .padding()
            .background(Starfield())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("OK") {
                        picked(Day(date))
                        dismiss()
                    }
                }
            }
            .onAppear { date = max(initial, earliest).start.addingTimeInterval(12 * 3600) }
        }
    }
}

/// Weekday checkboxes with quick choices.
struct DaysSheet: View {
    var initial: Set<Int>
    var allowOnce = true
    var picked: (Set<Int>) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var days: Set<Int> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(1...7, id: \.self) { day in
                        Button {
                            if days.contains(day) { days.remove(day) } else { days.insert(day) }
                        } label: {
                            HStack {
                                Text(L.weekday(day)).foregroundStyle(.white)
                                Spacer()
                                if days.contains(day) { Image(systemName: "checkmark").foregroundStyle(Color.accent) }
                            }
                        }
                    }
                }
                Section {
                    Button(L.s("editor_select_all")) { finish(Set(1...7)) }
                    Button(L.s("repeat_weekend")) { finish([6, 7]) }
                    Button(L.s("repeat_weekdays")) { finish(Set(1...5)) }
                    if allowOnce { Button(L.s("repeat_once")) { finish([]) } }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Starfield())
            .navigationTitle(L.s("editor_repeat_days"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L.s("apply")) { finish(days) } }
            }
            .onAppear { days = initial }
        }
    }

    private func finish(_ value: Set<Int>) {
        picked(value)
        dismiss()
    }
}

/// A list of choices in a sheet, one marked as current.
struct ChoiceSheet<Value: Hashable>: View {
    var title: String
    var values: [Value]
    var selected: Value?
    var label: (Value) -> String
    var picked: (Value) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(values, id: \.self) { value in
                Button {
                    picked(value)
                    dismiss()
                } label: {
                    HStack {
                        Text(label(value)).foregroundStyle(.white)
                        Spacer()
                        if value == selected { Image(systemName: "checkmark").foregroundStyle(Color.accent) }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Starfield())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
