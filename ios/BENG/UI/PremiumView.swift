import SwiftUI
import StoreKit

struct PremiumView: View {
    @EnvironmentObject private var app: AppModel
    @State private var managing = false

    var body: some View {
        let _ = app.revision
        PremiumContent(store: app.store, managing: $managing)
            .screen()
            .manageSubscriptionsSheet(isPresented: $managing)
            .task { await app.store.refresh() }
    }
}

private struct PremiumContent: View {
    @ObservedObject var store: StoreModel
    @Binding var managing: Bool

    var body: some View {
        let unlocked = Premium.unlocked
        hero
        VStack(alignment: .leading, spacing: 4) {
            Text(L.s(unlocked ? "premium_features_unlocked" : "premium_features_title")).font(.headline).foregroundStyle(.white)
            feature("figure.walk", L.s("task_steps"), L.s("premium_feature_steps"), unlocked)
            feature("alarm.waves.left.and.right", L.s("wake_check"), L.s("premium_feature_wake"), unlocked)
            feature("square.on.circle", L.s("task_shapes"), L.s("premium_feature_shapes"), unlocked)
            feature("qrcode", L.s("task_qr"), L.s("premium_feature_qr"), unlocked)
            feature("iphone.radiowaves.left.and.right", L.s("task_shake"), L.s("premium_feature_shake"), unlocked)
            feature("sun.horizon", L.s("briefing_speak"), L.s("premium_feature_briefing"), unlocked)
            feature("music.note", L.s("premium_feature_tones_title"), L.s("premium_feature_tones"), unlocked)
        }
        .card()
        if Premium.subscribed {
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("premium_active_note")).font(.subheadline).foregroundStyle(Color.muted)
                Button(L.s("premium_manage")) { managing = true }.bengStyle(.secondary)
            }
            .card()
        } else {
            if store.trialEligible { trial }
            plan
        }
        VStack(alignment: .leading, spacing: 6) {
            Text(L.s("premium_free_title")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.muted)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading, spacing: 6) {
                ForEach(["premium_free_alarms", "task_math", "task_memory", "task_typing", "tool_stopwatch", "tool_countdowns"], id: \.self) {
                    Text("✓  " + L.s($0)).font(.footnote).foregroundStyle(Color(hex: 0xDCD6F5))
                }
            }
            Text(L.s("premium_no_account")).font(.caption).foregroundStyle(Color.muted).padding(.top, 8)
        }
        .card()
        if !store.status.isEmpty { Text(store.status).font(.footnote).foregroundStyle(Color.muted) }
    }

    private var hero: some View {
        VStack(spacing: 8) {
            Text("✦").font(.system(size: 34)).foregroundStyle(Color(hex: 0x3A2A12)).frame(width: 76, height: 76)
                .background(LinearGradient(colors: [Color(hex: 0xFFF1C7), .gold, Color(hex: 0xE59A4F)], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.33), lineWidth: 3))
                .accessibilityHidden(true)
            Text(L.s("premium_title")).font(.title.weight(.semibold)).foregroundStyle(.white).padding(.top, 6)
            Text(L.s("premium_tagline")).font(.subheadline).foregroundStyle(Color(hex: 0xE4DDFF)).multilineTextAlignment(.center)
            if Premium.subscribed {
                Pill.on(L.s("premium_status_active")).padding(.top, 6)
            } else if Premium.preview {
                Pill.on(L.s("premium_status_preview")).padding(.top, 6)
            }
        }
        .frame(maxWidth: .infinity)
        .card([Color(hex: 0x4B2F8F), Color(hex: 0x2A2F78), Color(hex: 0x162452)], radius: 24, stroke: Color(argb: 0x99FFD98C))
    }

    private func feature(_ symbol: String, _ title: String, _ detail: String, _ included: Bool) -> some View {
        HStack(spacing: 14) {
            IconBadge(symbol: symbol, size: 44, colors: [Color(hex: 0x4A3C9E), Color(hex: 0x243567)], tint: Color(hex: 0xE9E2FF))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body.weight(.semibold)).foregroundStyle(.white)
                Text(detail).font(.footnote).foregroundStyle(Color.muted)
            }
            Spacer(minLength: 0)
            if included { Image(systemName: "checkmark").foregroundStyle(Color.mint) }
        }
        .padding(.vertical, 8)
    }

    private var trial: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                IconBadge(symbol: "crown", size: 44, colors: [Color(hex: 0x3A8A64), Color(hex: 0x2B4C86)], tint: .gold)
                Text(L.s("trial_title")).font(.headline).foregroundStyle(.white)
            }
            Text(L.s("trial_body")).font(.subheadline).foregroundStyle(Color(hex: 0xDCE7F2))
        }
        .card([Color(argb: 0xEE2E4A5C), Color(argb: 0xEE2B2D62)], radius: 22, stroke: Color(argb: 0x889BE8C0))
    }

    private var plan: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(L.s("premium_plan_choose")).font(.headline).foregroundStyle(Color.accent)
                Spacer()
                Pill(text: L.s("premium_cancel_anytime"))
            }
            if store.plans.isEmpty {
                Text(store.catalogStatus.isEmpty ? L.s("billing_loading_price") : store.catalogStatus).font(.footnote).foregroundStyle(Color.muted)
                Button(L.s("billing_reload_price")) { Task { await store.refresh() } }.bengStyle()
            } else {
                ForEach(store.plans, id: \.self) { option in planRow(option) }
                Text(L.s("premium_auto_renew")).font(.caption).foregroundStyle(Color.muted)
                let price = store.price(store.selected) ?? ""
                Button(store.trialEligible ? L.s("ios_trial_cta")
                       : L.s(store.selected == .yearly ? "premium_subscribe_yearly" : "premium_subscribe", price)) {
                    Task { await store.buy() }
                }
                .bengStyle(.gold)
                .disabled(!store.canBuy)
                if store.trialEligible {
                    Text(L.s(store.selected == .yearly ? "premium_subscribe_yearly" : "premium_subscribe", price))
                        .font(.caption).foregroundStyle(Color.muted).frame(maxWidth: .infinity)
                }
            }
            Button(L.s("billing_restore")) { Task { await store.restore() } }.bengStyle(.secondary)
            if Premium.previewAvailable && !Premium.preview {
                Button(L.s("premium_enable_preview")) {
                    Premium.preview = true
                    Scheduler.request()
                }.bengStyle(.secondary)
            }
            HStack {
                Link("Terms of Use", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
                Spacer()
                Link("Privacy", destination: URL(string: "https://caglar-yildiz.github.io/alarm-app/")!)
            }
            .font(.caption)
            .foregroundStyle(Color.muted)
        }
        .card([Color(argb: 0xEE2B2559), Color(argb: 0xEE3A2350)], radius: 22, stroke: Color(argb: 0xAAC5BAFF))
    }

    private func planRow(_ option: PremiumPlan) -> some View {
        let selected = store.selected == option
        let yearly = option == .yearly
        let price = store.price(option) ?? ""
        let period = L.s(yearly ? "premium_per_year" : "premium_per_month")
        let savings = yearly ? store.yearlySavingsPercent() : nil
        return Button { store.selected = option } label: {
            HStack(spacing: 12) {
                Text(selected ? "◉" : "○").font(.title3).foregroundStyle(selected ? Color.gold : Color.muted)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(L.s(yearly ? "premium_plan_yearly" : "premium_plan_monthly")).font(.body.weight(.semibold)).foregroundStyle(.white)
                        if let savings { Pill(text: L.s("premium_save", savings), color: .midnight, fill: .gold, stroke: .gold) }
                    }
                    if yearly, let perMonth = store.yearlyPerMonth() {
                        Text(L.s("premium_yearly_per_month", perMonth)).font(.caption).foregroundStyle(Color.muted)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text(price).font(.headline).foregroundStyle(.white)
                    Text(period).font(.caption).foregroundStyle(Color.muted)
                }
            }
            .padding(14)
            .frame(minHeight: 64)
            .background(selected ? Color(argb: 0x40C5BAFF) : Color(argb: 0x18FFFFFF), in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Color.gold : Color(argb: 0x668C8CD8), lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
