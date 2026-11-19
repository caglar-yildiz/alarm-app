package com.caglaryildiz.alarm

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct

internal class PremiumAccess(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("premium_preview", Context.MODE_PRIVATE)
    val previewAvailable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val preview get() = previewAvailable && prefs.getBoolean("enabled", false)
    val subscribed get() = prefs.getBoolean("subscribed", false)
    val trial = FreeTrial(context)
    val trialActive get() = !subscribed && trial.active()
    val unlocked get() = preview || subscribed || trial.active()

    fun enablePreview() {
        if (previewAvailable) prefs.edit().putBoolean("enabled", true).apply()
    }

    fun updateSubscription(active: Boolean) {
        prefs.edit().putBoolean("subscribed", active).apply()
    }
}

/** Google Play base plans of the `beng_premium` subscription, both without offers (the trial is in-app). */
internal enum class PremiumPlan(val basePlanId: String, val period: String) {
    YEARLY("yearly", "P1Y"),
    MONTHLY("monthly", "P1M")
}

internal class PremiumBilling(private val activity: Activity, private val changed: () -> Unit) {
    private val access = PremiumAccess(activity)
    private var details: ProductDetails? = null
    private var offers = emptyMap<PremiumPlan, ProductDetails.SubscriptionOfferDetails>()
    /** The plan the user picked; yearly is preselected because it is the better deal. */
    var selectedPlan = PremiumPlan.YEARLY
    private var connecting = false
    private var pending = false
    private var closed = false
    var catalogStatus = activity.getString(R.string.billing_loading_price)
        private set
    private var catalogReport = activity.getString(R.string.billing_report_waiting)
    private var queryingCatalog = false
    var status = ""
        private set
    private var purchasing = false
    val plans get() = PremiumPlan.entries.filter { it in offers }
    val canBuy get() = client.isReady && selectedPlan in offers && !pending && !purchasing && !access.subscribed

    private fun phase(plan: PremiumPlan) = offers[plan]?.pricingPhases?.pricingPhaseList?.singleOrNull()
    fun price(plan: PremiumPlan): String? = phase(plan)?.formattedPrice

    /** How much cheaper a year is than twelve months, in whole percent; null when not both are offered. */
    fun yearlySavingsPercent(): Int? {
        val year = phase(PremiumPlan.YEARLY)?.priceAmountMicros ?: return null
        val month = phase(PremiumPlan.MONTHLY)?.priceAmountMicros ?: return null
        if (month <= 0) return null
        return (100 - year * 100.0 / (month * 12)).toInt().takeIf { it in 1..90 }
    }

    /** The yearly price spread over twelve months, in the store's currency. */
    fun yearlyPerMonth(): String? {
        val year = phase(PremiumPlan.YEARLY) ?: return null
        return runCatching {
            java.text.NumberFormat.getCurrencyInstance(activity.resources.configuration.locales[0]).apply {
                currency = java.util.Currency.getInstance(year.priceCurrencyCode)
            }.format(year.priceAmountMicros / 12_000_000.0)
        }.getOrNull()
    }

    private val client = BillingClient.newBuilder(activity.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases ->
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                purchasing = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                        status = activity.getString(R.string.billing_pending)
                        changed()
                    }
                    refreshPurchases()
                } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    status = activity.getString(R.string.billing_canceled)
                    changed()
                } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    refreshPurchases()
                } else {
                    status = activity.getString(R.string.billing_failed)
                    changed()
                }
            }
        }.build()

    fun refresh() {
        if (closed) return
        if (client.isReady) {
            refreshPurchases()
            if (queryingCatalog) return
            queryingCatalog = true
            catalogStatus = activity.getString(R.string.billing_loading_price)
            catalogReport = activity.getString(R.string.billing_report_querying)
            changed()
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { queryResult, result ->
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    queryingCatalog = false
                    val returnedProduct = result.productDetailsList.firstOrNull { it.productId == PRODUCT }
                    val returnedOffers = returnedProduct?.subscriptionOfferDetails.orEmpty()
                    val unfetched = result.unfetchedProductList.firstOrNull { it.productId == PRODUCT }
                    catalogReport = buildString {
                        appendLine(activity.getString(R.string.billing_report_query, queryResult.responseCode))
                        if (unfetched != null) appendLine(activity.getString(R.string.billing_report_status, unfetched.statusCode))
                        appendLine(activity.getString(R.string.billing_report_product, returnedProduct?.productId ?: activity.getString(R.string.billing_none)))
                        if (returnedOffers.isEmpty()) appendLine(activity.getString(R.string.billing_report_plan_none))
                        for (plan in returnedOffers) {
                            val periods = plan.pricingPhases.pricingPhaseList.joinToString(", ") { it.billingPeriod }
                            appendLine(activity.getString(R.string.billing_report_plan, plan.basePlanId, plan.offerId ?: activity.getString(R.string.billing_standard), periods))
                        }
                    }.trimEnd()
                    if (queryResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        details = null
                        offers = emptyMap()
                        catalogStatus = activity.getString(R.string.billing_query_failed)
                        changed()
                        return@runOnUiThread
                    }
                    details = returnedProduct
                    offers = PremiumPlan.entries.mapNotNull { plan ->
                        returnedOffers.firstOrNull {
                            it.basePlanId == plan.basePlanId && it.offerId == null &&
                                it.pricingPhases.pricingPhaseList.singleOrNull()?.billingPeriod == plan.period
                        }?.let { plan to it }
                    }.toMap()
                    if (selectedPlan !in offers) offers.keys.firstOrNull()?.let { selectedPlan = it }
                    catalogStatus = when {
                        offers.isNotEmpty() -> ""
                        unfetched?.statusCode == UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND ->
                            activity.getString(R.string.billing_product_not_found)
                        unfetched?.statusCode == UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER ->
                            activity.getString(R.string.billing_no_eligible_offer)
                        returnedProduct != null -> activity.getString(R.string.billing_plan_missing)
                        else -> activity.getString(R.string.billing_no_details)
                    }
                    changed()
                }
            }
            return
        }
        if (connecting) return
        connecting = true
        catalogStatus = activity.getString(R.string.billing_connecting)
        catalogReport = activity.getString(R.string.billing_report_connecting)
        changed()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                    else {
                        catalogStatus = activity.getString(R.string.billing_unavailable)
                        catalogReport = activity.getString(R.string.billing_report_connection, result.responseCode)
                        changed()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    connecting = false
                    details = null
                    offers = emptyMap()
                    queryingCatalog = false
                    catalogStatus = activity.getString(R.string.billing_disconnected)
                    catalogReport = activity.getString(R.string.billing_disconnected)
                    changed()
                }
            }
        })
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    status = activity.getString(R.string.billing_verify_failed)
                    changed()
                    return@runOnUiThread
                }
                val matching = purchases.filter { PRODUCT in it.products }
                val active = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                access.updateSubscription(active.any { it.isAcknowledged })
                pending = matching.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                if (pending) status = activity.getString(R.string.billing_pending)
                else if (offers.isNotEmpty()) status = ""
                for (purchase in active.filterNot { it.isAcknowledged }) {
                    val acknowledgement = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                    client.acknowledgePurchase(acknowledgement) { acknowledgementResult ->
                        activity.runOnUiThread {
                            if (!closed) {
                                if (acknowledgementResult.responseCode == BillingClient.BillingResponseCode.OK) refreshPurchases()
                                else {
                                    status = activity.getString(R.string.billing_acknowledging)
                                    changed()
                                }
                            }
                        }
                    }
                }
                changed()
            }
        }
    }

    fun showCatalogDiagnostics() {
        val installer = if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.packageManager.getInstallSourceInfo(activity.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.getInstallerPackageName(activity.packageName)
        }
        AlertDialog.Builder(activity, R.style.AstralDialogTheme)
            .setTitle(activity.getString(R.string.billing_diagnostics))
            .setMessage(activity.getString(R.string.billing_diagnostics_message, activity.packageName,
                installer ?: activity.getString(R.string.billing_unknown), PRODUCT,
                PremiumPlan.entries.joinToString(", ") { "${it.basePlanId} (${it.period})" }) + "\n\n$catalogReport")
            .setPositiveButton(activity.getString(R.string.close), null)
            .show()
    }

    fun buy() {
        if (!canBuy) return
        val product = details ?: return
        val selected = offers[selectedPlan] ?: return
        val item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(selected.offerToken).build()
        purchasing = true
        changed()
        val result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            purchasing = false
            status = activity.getString(R.string.billing_launch_failed)
            changed()
        }
    }

    fun manage() {
        val url = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("sku", PRODUCT)
            .appendQueryParameter("package", activity.packageName).build()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url)) }.onFailure {
            status = activity.getString(R.string.billing_manage_failed)
            changed()
        }
    }

    fun close() {
        closed = true
        client.endConnection()
    }

    companion object {
        private const val PRODUCT = "beng_premium"
    }
}

abstract class PremiumScreen : AlarmScreen() {
    internal val premiumAccess by lazy { PremiumAccess(this) }
    private var billing: PremiumBilling? = null

    protected fun refreshPremium() {
        val connection = billing ?: PremiumBilling(this, ::onPremiumChanged).also { billing = it }
        connection.refresh()
        if (!premiumAccess.trial.checked) premiumAccess.trial.sync { if (!isFinishing && !isDestroyed) onPremiumChanged() }
    }

    protected abstract fun onPremiumChanged()

    protected fun showPremium() {
        val unlocked = premiumAccess.unlocked
        showPremiumHero()

        val features = card()
        label(getString(if (unlocked) R.string.premium_features_unlocked else R.string.premium_features_title),
            17f, features, bold = true).accessibilityHeading()
        featureRow(R.drawable.ic_wake_check, getString(R.string.wake_check),
            getString(R.string.premium_feature_wake), features, unlocked)
        featureRow(R.drawable.ic_shapes, getString(R.string.task_shapes),
            getString(R.string.premium_feature_shapes), features, unlocked)
        featureRow(R.drawable.ic_qr, getString(R.string.task_qr),
            getString(R.string.premium_feature_qr), features, unlocked)
        featureRow(R.drawable.ic_shake, getString(R.string.task_shake),
            getString(R.string.premium_feature_shake), features, unlocked)
        featureRow(R.drawable.ic_music, getString(R.string.premium_feature_tones_title),
            getString(R.string.premium_feature_tones),
            features, unlocked)

        if (!premiumAccess.subscribed) showTrial()
        if (premiumAccess.subscribed) {
            val manage = card()
            label(getString(R.string.premium_active_note), 15f, manage, color = mutedColor)
            button(getString(R.string.premium_manage), manage, primary = false) { billing?.manage() }
        } else {
            showPlan()
        }

        val free = card()
        label(getString(R.string.premium_free_title), 15f, free, bold = true, color = mutedColor)
        val freeItems = listOf(R.string.premium_free_alarms, R.string.task_math, R.string.task_memory,
            R.string.task_typing, R.string.tool_stopwatch, R.string.tool_countdowns)
        for (pair in freeItems.chunked(2)) {
            val row = LinearLayout(this).apply { isBaselineAligned = false }
            free.addView(row)
            for (item in pair) {
                label("✓  " + getString(item), 14f, row, color = 0xFFDCD6F5.toInt()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
            }
        }
        label(getString(R.string.premium_no_account), 13f, free, color = mutedColor).setPadding(0, dp(8), 0, 0)
        billing?.status?.takeIf { it.isNotBlank() }?.let { label(it, 14f, color = mutedColor) }
    }

    private fun showPremiumHero() {
        val hero = card().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF4B2F8F.toInt(), 0xFF2A2F78.toInt(), 0xFF162452.toInt())).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x99FFD98C.toInt())
            }
            setPadding(dp(20), dp(22), dp(20), dp(22))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        hero.addView(TextView(this).apply {
            text = "✦"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(0xFF3A2A12.toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFF1C7.toInt(), goldColor, 0xFFE59A4F.toInt())).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), 0x55FFFFFF)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        label(getString(R.string.premium_title), 28f, hero, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = .04f
            setPadding(0, dp(14), 0, dp(2))
            accessibilityHeading()
        }
        label(getString(R.string.premium_tagline), 15f, hero, color = 0xFFE4DDFF.toInt()).gravity = Gravity.CENTER
        val status = when {
            premiumAccess.subscribed -> getString(R.string.premium_status_active)
            premiumAccess.trialActive -> premiumAccess.trial.daysLeft().let {
                resources.getQuantityString(R.plurals.trial_days_left, it, it)
            }
            premiumAccess.preview -> getString(R.string.premium_status_preview)
            else -> null
        }
        if (status != null) {
            val holder = LinearLayout(this).apply { gravity = Gravity.CENTER }
            hero.addView(holder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            pill(status, holder, 0xFF9BE8C0.toInt(), 0x403A8A64, 0x889BE8C0.toInt())
        }
    }

    /** The card-free 7-day trial: offered once, then shown with its end date, then as ended. */
    private fun showTrial() {
        val trial = premiumAccess.trial
        val card = card().apply {
            background = glass(0xEE2E4A5C.toInt(), 0xEE2B2D62.toInt(), 22).apply {
                setStroke(dp(1), 0x889BE8C0.toInt())
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        when {
            trial.active() -> {
                val end = java.time.Instant.ofEpochMilli(trial.endsAt).atZone(java.time.ZoneId.systemDefault())
                label(getString(R.string.trial_ends, end.format(dateFormat())), 15f, card)
            }
            trial.used -> label(getString(R.string.trial_ended), 15f, card, color = mutedColor)
            else -> {
                val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                card.addView(header)
                iconBadge(R.drawable.ic_crown, header, 44, 0xFF3A8A64.toInt(), 0xFF2B4C86.toInt(), goldColor)
                label(getString(R.string.trial_title), 18f, header, bold = true).layoutParams =
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
                label(getString(R.string.trial_body), 14f, card, color = 0xFFDCE7F2.toInt()).setPadding(0, dp(10), 0, 0)
                button(getString(if (trial.checked) R.string.trial_start else R.string.trial_checking), card) {
                    if (trial.start()) {
                        android.widget.Toast.makeText(this, getString(R.string.trial_started),
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                    onPremiumChanged()
                }.apply {
                    isEnabled = trial.checked
                    minimumHeight = dp(54)
                    background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF),
                        glass(0xFF9BE8C0.toInt(), 0xFF9BDFFF.toInt(), 20), null)
                }
            }
        }
    }

    private fun showPlan() {
        val connection = billing
        val plan = card().apply {
            background = glass(0xEE2B2559.toInt(), 0xEE3A2350.toInt(), 22).apply {
                setStroke(dp(2), 0xAAC5BAFF.toInt())
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        plan.addView(header)
        label(getString(R.string.premium_plan_choose), 16f, header, bold = true, color = accentColor)
            .layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        pill(getString(R.string.premium_cancel_anytime), header)
        val plans = connection?.plans.orEmpty()
        if (connection != null && plans.isNotEmpty()) {
            for (option in plans) planOption(connection, option, plan)
            label(getString(R.string.premium_auto_renew), 13f, plan, color = mutedColor).setPadding(0, dp(8), 0, 0)
            val selected = connection.selectedPlan
            val price = connection.price(selected).orEmpty()
            val cta = getString(if (selected == PremiumPlan.YEARLY) R.string.premium_subscribe_yearly
                else R.string.premium_subscribe, price)
            button(cta, plan) { connection.buy() }.apply {
                isEnabled = connection.canBuy
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                minimumHeight = dp(56)
                background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF),
                    glass(goldColor, 0xFFC3A0FC.toInt(), 20), null)
            }
        } else {
            label(connection?.catalogStatus ?: getString(R.string.billing_loading_price), 14f, plan, color = mutedColor)
            button(getString(R.string.billing_reload_price), plan) { refreshPremium() }
            button(getString(R.string.billing_show_diagnostics), plan, primary = false) { connection?.showCatalogDiagnostics() }
        }
        button(getString(R.string.billing_restore), plan, primary = false) { refreshPremium() }
        if (premiumAccess.previewAvailable && !premiumAccess.preview) {
            button(getString(R.string.premium_enable_preview), plan, primary = false) {
                premiumAccess.enablePreview()
                onPremiumChanged()
            }
        }
    }

    /** One selectable plan row: radio mark, name, savings badge, price and its period. */
    private fun planOption(connection: PremiumBilling, option: PremiumPlan, parent: LinearLayout) {
        val selected = connection.selectedPlan == option
        val yearly = option == PremiumPlan.YEARLY
        val price = connection.price(option).orEmpty()
        val period = getString(if (yearly) R.string.premium_per_year else R.string.premium_per_month)
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            minimumHeight = dp(64)
            background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF),
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (selected) 0x40C5BAFF else 0x18FFFFFF)
                    setStroke(dp(if (selected) 2 else 1), if (selected) goldColor else 0x668C8CD8)
                }, null)
            setOnClickListener {
                if (connection.selectedPlan != option) {
                    connection.selectedPlan = option
                    onPremiumChanged()
                }
            }
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        label(if (selected) "◉" else "○", 20f, row, color = if (selected) goldColor else mutedColor).apply {
            setPadding(0, 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val texts = column()
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        texts.addView(titleRow)
        label(getString(if (yearly) R.string.premium_plan_yearly else R.string.premium_plan_monthly),
            16f, titleRow, bold = true).setPadding(0, 0, dp(8), 0)
        val savings = if (yearly) connection.yearlySavingsPercent() else null
        if (savings != null) pill(getString(R.string.premium_save, savings), titleRow, midnight, goldColor, goldColor)
        val perMonth = if (yearly) connection.yearlyPerMonth() else null
        if (perMonth != null) label(getString(R.string.premium_yearly_per_month, perMonth), 13f, texts, color = mutedColor)
            .setPadding(0, dp(2), 0, 0)
        val priceBox = column().apply { gravity = Gravity.END }
        row.addView(priceBox, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        label(price, 18f, priceBox, bold = true).apply {
            gravity = Gravity.END
            setPadding(0, 0, 0, 0)
        }
        label(period, 12.5f, priceBox, color = mutedColor).apply {
            gravity = Gravity.END
            setPadding(0, 0, 0, 0)
        }
        row.contentDescription = listOfNotNull(
            getString(if (yearly) R.string.premium_plan_yearly else R.string.premium_plan_monthly),
            "$price $period",
            savings?.let { getString(R.string.premium_save, it) }
        ).joinToString(", ")
        row.isSelected = selected
    }

    override fun onDestroy() {
        billing?.close()
        super.onDestroy()
    }
}

class PremiumActivity : PremiumScreen() {
    override fun onResume() {
        super.onResume()
        onPremiumChanged()
        refreshPremium()
    }

    override fun onPremiumChanged() {
        layout(getString(R.string.tab_premium))
        showPremium()
        button(getString(R.string.premium_back_to_alarm), primary = false) { finish() }
    }
}
