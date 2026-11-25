import Foundation
import StoreKit

/// The two auto-renewing plans of the "BENG Premium" subscription group in App Store Connect.
enum PremiumPlan: String, CaseIterable {
    case yearly = "com.caglaryildiz.alarm.premium.yearly"
    case monthly = "com.caglaryildiz.alarm.premium.monthly"
}

/// StoreKit 2: loads the plans, buys, restores and keeps Premium access in sync with the App Store.
@MainActor
final class StoreModel: ObservableObject {
    @Published private(set) var products: [PremiumPlan: Product] = [:]
    @Published var selected: PremiumPlan = .yearly
    @Published private(set) var loading = false
    @Published private(set) var catalogStatus = ""
    @Published var status = ""
    @Published private(set) var purchasing = false
    @Published private(set) var trialEligible = false

    private var updates: Task<Void, Never>?

    init() {
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result { await transaction.finish() }
                await self?.refreshEntitlements()
            }
        }
    }

    deinit { updates?.cancel() }

    var plans: [PremiumPlan] { PremiumPlan.allCases.filter { products[$0] != nil } }
    var canBuy: Bool { products[selected] != nil && !purchasing && !Premium.subscribed }

    func price(_ plan: PremiumPlan) -> String? { products[plan]?.displayPrice }

    /// How much cheaper a year is than twelve months, in whole percent.
    func yearlySavingsPercent() -> Int? {
        guard let year = products[.yearly]?.price, let month = products[.monthly]?.price, month > 0 else { return nil }
        let ratio = NSDecimalNumber(decimal: year).doubleValue / (NSDecimalNumber(decimal: month).doubleValue * 12)
        let percent = Int(100 - ratio * 100)
        return (1...90).contains(percent) ? percent : nil
    }

    /// The yearly price spread over twelve months.
    func yearlyPerMonth() -> String? {
        guard let year = products[.yearly] else { return nil }
        return (year.price / 12).formatted(year.priceFormatStyle)
    }

    func refresh() async {
        await refreshEntitlements()
        guard products.isEmpty, !loading else { return }
        loading = true
        catalogStatus = L.s("billing_loading_price")
        do {
            let list = try await Product.products(for: PremiumPlan.allCases.map(\.rawValue))
            var found: [PremiumPlan: Product] = [:]
            for product in list { if let plan = PremiumPlan(rawValue: product.id) { found[plan] = product } }
            products = found
            if found[selected] == nil, let first = PremiumPlan.allCases.first(where: { found[$0] != nil }) { selected = first }
            catalogStatus = found.isEmpty ? L.s("billing_product_not_found") : ""
            if let subscription = found.values.first?.subscription {
                trialEligible = await subscription.isEligibleForIntroOffer
            }
        } catch {
            catalogStatus = L.s("billing_query_failed")
        }
        loading = false
    }

    func refreshEntitlements() async {
        var active = false
        var trial = false
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result, PremiumPlan(rawValue: transaction.productID) != nil,
                  transaction.revocationDate == nil else { continue }
            if let expiry = transaction.expirationDate, expiry < Date() { continue }
            active = true
            if transaction.offer?.type == .introductory { trial = true }
        }
        let changed = Premium.subscribed != active
        Premium.subscribed = active
        Premium.trialActive = trial
        if changed { Scheduler.request() }
    }

    func buy() async {
        guard let product = products[selected], canBuy else { return }
        purchasing = true
        status = ""
        defer { purchasing = false }
        do {
            switch try await product.purchase() {
            case .success(let result):
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    await refreshEntitlements()
                } else {
                    status = L.s("billing_verify_failed")
                }
            case .pending:
                status = L.s("billing_pending")
            case .userCancelled:
                status = L.s("billing_canceled")
            @unknown default:
                status = L.s("billing_failed")
            }
        } catch {
            status = L.s("billing_failed")
        }
    }

    func restore() async {
        do {
            try await AppStore.sync()
        } catch {
            status = L.s("billing_verify_failed")
        }
        await refreshEntitlements()
        products = [:]
        await refresh()
    }
}
