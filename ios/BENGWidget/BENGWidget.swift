import WidgetKit
import SwiftUI
import AlarmKit
import ActivityKit
import AppIntents

struct NextAlarmEntry: TimelineEntry {
    var date: Date
    var next: Date?
    var label: String
}

struct NextAlarmProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextAlarmEntry {
        NextAlarmEntry(date: Date(), next: Date().addingTimeInterval(8 * 3600), label: "")
    }

    func getSnapshot(in context: Context, completion: @escaping (NextAlarmEntry) -> Void) {
        completion(entry(Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextAlarmEntry>) -> Void) {
        let now = Date()
        let first = entry(now)
        var entries = [first]
        // Move on to the following alarm right after the next one rings.
        if let next = first.next { entries.append(entry(next.addingTimeInterval(60))) }
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(3 * 3600))))
    }

    private func entry(_ date: Date) -> NextAlarmEntry {
        let next = AlarmStore.nextRing(date)
        return NextAlarmEntry(date: date, next: next?.1, label: next?.0.label ?? "")
    }
}

struct NextAlarmWidgetView: View {
    var entry: NextAlarmEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L.s("next_alarm")).font(.caption).foregroundStyle(Color(red: 0.75, green: 0.77, blue: 0.88))
            Text(entry.next.map { L.clock($0) } ?? "––:––")
                .font(.system(size: 34, weight: .light)).monospacedDigit().foregroundStyle(.white)
            Text(detail).font(.caption).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
            if family != .systemSmall {
                Spacer(minLength: 2)
                HStack(spacing: 8) {
                    Button(intent: QuickAlarmIntent(minutes: QuickAlarmIntent.napMinutes)) {
                        Text(L.s("widget_nap", QuickAlarmIntent.napMinutes)).font(.caption.weight(.medium)).frame(maxWidth: .infinity)
                    }
                    Button(intent: QuickAlarmIntent(minutes: QuickAlarmIntent.soonMinutes)) {
                        Text(L.s("widget_soon", QuickAlarmIntent.soonMinutes)).font(.caption.weight(.medium)).frame(maxWidth: .infinity)
                    }
                }
                .tint(Color(red: 0.77, green: 0.73, blue: 1.0))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(for: .widget) {
            LinearGradient(colors: [Color(red: 0.14, green: 0.18, blue: 0.35), Color(red: 0.08, green: 0.09, blue: 0.20)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    private var detail: String {
        guard let next = entry.next else { return L.s("no_alarm_planned") }
        let day = L.weekday(Day(next).weekday)
        return entry.label.isEmpty ? day : "\(day) · \(entry.label)"
    }
}

struct NextAlarmWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextAlarm", provider: NextAlarmProvider()) { entry in
            NextAlarmWidgetView(entry: entry)
        }
        .configurationDisplayName(L.s("widget_name"))
        .description(L.s("widget_description"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

/// What AlarmKit shows on the lock screen and in the Dynamic Island while a BENG alarm is active.
struct AlarmLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<BengAlarmData>.self) { context in
            HStack(spacing: 12) {
                Image(systemName: "alarm.fill").font(.title2).foregroundStyle(Color(red: 0.77, green: 0.73, blue: 1.0))
                VStack(alignment: .leading) {
                    Text("BENG").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Text(context.attributes.metadata?.label ?? L.s("alarm_ringing")).font(.headline)
                }
                Spacer()
            }
            .padding()
            .activityBackgroundTint(Color(red: 0.08, green: 0.09, blue: 0.20))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "alarm.fill").foregroundStyle(Color(red: 0.77, green: 0.73, blue: 1.0))
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.attributes.metadata?.label ?? L.s("alarm_ringing")).font(.headline)
                }
            } compactLeading: {
                Image(systemName: "alarm.fill")
            } compactTrailing: {
                Text("BENG").font(.caption2)
            } minimal: {
                Image(systemName: "alarm.fill")
            }
        }
    }
}

@main
struct BENGWidgets: WidgetBundle {
    var body: some Widget {
        NextAlarmWidget()
        AlarmLiveActivity()
    }
}
