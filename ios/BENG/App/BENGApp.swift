import SwiftUI
import UserNotifications
import BackgroundTasks
import AlarmKit
import StoreKit

enum Tab: String, CaseIterable {
    case alarms, reminders, weather, tools, premium

    var titleKey: String { "tab_\(rawValue)" }
    var symbol: String {
        switch self {
        case .alarms: return "alarm"
        case .reminders: return "bell"
        case .weather: return "cloud.sun"
        case .tools: return "square.grid.2x2"
        case .premium: return "crown"
        }
    }
}

enum Tool: String, CaseIterable, Identifiable {
    case stopwatch, countdowns, wake, briefing, stats, bedtime, vacation, language
    var id: String { rawValue }
}

/// App-wide state: navigation, and a revision that changes whenever stored data changes, so screens redraw.
@MainActor
final class AppModel: ObservableObject {
    static let shared = AppModel()

    @Published var revision = 0
    @Published var tab: Tab = .alarms
    @Published var tool: Tool?
    @Published var showBriefing = false
    @Published var briefingPreview = false
    @Published var ringDismissed = false
    @Published var askReview = false
    let store = StoreModel()

    private var observer: Any?

    init() {
        #if DEBUG
        DemoData.seed()
        if let tab = DemoData.tab { self.tab = tab }
        #endif
        observer = NotificationCenter.default.addObserver(forName: .bengDataChanged, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.revision += 1 }
        }
    }

    var ring: RingState? { RingStore.active }

    func open(_ screen: String?) {
        switch screen {
        case "wake": tab = .tools; tool = .wake
        case "timers": tab = .tools; tool = .countdowns
        case "reminders": tab = .reminders
        default: break
        }
    }

    /// The app came to the front: catch up on everything that happened while it was closed.
    func activated() {
        Scheduler.request()
        Task { await store.refresh() }
        Task { await Holidays.refresh() }
        if MorningBriefing.pendingShow && RingStore.active == nil {
            MorningBriefing.pendingShow = false
            if MorningBriefing.enabled { briefingPreview = false; showBriefing = true }
        }
        if RingStore.active == nil && ReviewPrompt.shouldAsk() { askReview = true }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static let refreshTask = "com.caglaryildiz.alarm.refresh"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        let confirm = UNNotificationAction(identifier: Scheduler.wakeConfirmAction, title: L.s("wake_confirm"), options: [])
        let skip = UNNotificationAction(identifier: Scheduler.upcomingSkipAction, title: L.s("upcoming_skip"), options: [])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: Scheduler.wakeCategory, actions: [confirm], intentIdentifiers: []),
            UNNotificationCategory(identifier: Scheduler.upcomingCategory, actions: [skip], intentIdentifiers: [])
        ])
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.refreshTask, using: nil) { task in
            Self.scheduleRefresh()
            let work = Task {
                await Scheduler.shared.sync()
                await Holidays.refresh()
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
        Self.scheduleRefresh()
        _ = ReviewPrompt.installedAt
        return true
    }

    /// Keeps repeating plans (vacations, skipped days, reminders) topped up even if the app isn't opened.
    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTask)
        request.earliestBeginDate = Date().addingTimeInterval(6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        switch response.actionIdentifier {
        case Scheduler.wakeConfirmAction:
            if let id = info["alarmId"] as? String { WakeCheck.confirm(id) }
        case Scheduler.upcomingSkipAction:
            if let id = info["alarmId"] as? String, let at = (info["at"] as? String).flatMap(Double.init) {
                UpcomingAlarm.skip(id, at: Date(timeIntervalSince1970: at))
            }
        default:
            let screen = info["screen"] as? String
            await MainActor.run { AppModel.shared.open(screen) }
        }
        await Scheduler.shared.sync()
    }
}

@main
struct BENGApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environment(\.locale, AppLanguage.locale)
                .environment(\.layoutDirection, AppLanguage.isRightToLeft ? .rightToLeft : .leftToRight)
                .preferredColorScheme(.dark)
                .tint(.accent)
        }
        .onChange(of: phase) { _, phase in
            if phase == .active { model.activated() }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.requestReview) private var requestReview
    @State private var demoEditor: EditorTarget?
    @State private var demoRing: RingSource?

    var body: some View {
        let _ = app.revision
        TabView(selection: $app.tab) {
            ForEach(Tab.allCases, id: \.self) { tab in
                NavigationStack {
                    content(tab)
                        .navigationTitle(title(tab))
                        .navigationBarTitleDisplayMode(tab == .alarms ? .inline : .large)
                        .toolbarBackground(.hidden, for: .navigationBar)
                        .toolbar {
                            if tab == .alarms {
                                ToolbarItem(placement: .principal) {
                                    Text(L.s("app_name")).font(.title2.weight(.semibold)).tracking(4).foregroundStyle(Color.accent)
                                }
                            }
                        }
                }
                .tabItem { Label(L.s(tab.titleKey), systemImage: tab.symbol) }
                .tag(tab)
            }
        }
        .fullScreenCover(isPresented: Binding(get: { app.ring != nil && !app.ringDismissed }, set: { if !$0 { app.ringDismissed = true } })) {
            if let ring = app.ring { RingView(source: .live(ring)).environmentObject(app) }
        }
        .sheet(item: $demoEditor) { target in AlarmEditorView(original: target.alarm).environmentObject(app) }
        .fullScreenCover(item: $demoRing) { source in RingView(source: source).environmentObject(app) }
        .sheet(isPresented: $app.showBriefing) { BriefingView(preview: app.briefingPreview).environmentObject(app) }
        .onChange(of: app.ring?.alarmId) { _, id in if id != nil { app.ringDismissed = false } }
        .onChange(of: app.askReview) { _, ask in
            if ask {
                app.askReview = false
                requestReview()
            }
        }
        .task {
            #if DEBUG
            if DemoData.active {
                let first = AlarmStore.all().sorted { $0.time < $1.time }.first { $0.task == .math }
                switch DemoData.screen {
                case "editor": demoEditor = EditorTarget(id: "demo", alarm: first)
                case "ring": if let first { demoRing = .preview(first) }
                case "briefing": app.showBriefing = true
                case let name?: if let tool = Tool(rawValue: name) { app.tab = .tools; app.tool = tool }
                default: break
                }
                return
            }
            #endif
            if AlarmManager.shared.authorizationState == .notDetermined {
                _ = try? await AlarmManager.shared.requestAuthorization()
            }
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            Scheduler.request()
        }
    }

    private func title(_ tab: Tab) -> String {
        switch tab {
        case .alarms: return ""
        case .weather: return L.s("weather_title")
        case .tools: return app.tool.map { ToolsView.title($0) } ?? L.s("tab_tools")
        default: return L.s(tab.titleKey)
        }
    }

    @ViewBuilder private func content(_ tab: Tab) -> some View {
        switch tab {
        case .alarms: AlarmsView()
        case .reminders: RemindersView()
        case .weather: WeatherView()
        case .tools: ToolsView()
        case .premium: PremiumView()
        }
    }
}
