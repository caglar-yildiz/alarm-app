package com.caglaryildiz.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
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

internal class PremiumAccess(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("premium_preview", Context.MODE_PRIVATE)
    val previewAvailable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val preview get() = previewAvailable && prefs.getBoolean("enabled", false)
    val subscribed get() = prefs.getBoolean("subscribed", false)
    val unlocked get() = preview || subscribed

    fun enablePreview() {
        if (previewAvailable) prefs.edit().putBoolean("enabled", true).apply()
    }

    fun updateSubscription(active: Boolean) {
        prefs.edit().putBoolean("subscribed", active).apply()
    }
}

internal class PremiumBilling(private val activity: Activity, private val changed: () -> Unit) {
    private val access = PremiumAccess(activity)
    private var details: ProductDetails? = null
    private var offer: ProductDetails.SubscriptionOfferDetails? = null
    private var connecting = false
    private var pending = false
    private var closed = false
    var status = "Google Play fiyatı yükleniyor…"
        private set
    val price get() = offer?.pricingPhases?.pricingPhaseList?.singleOrNull()?.formattedPrice

    private val client = BillingClient.newBuilder(activity.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases ->
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                        status = "Ödeme onayı bekleniyor."
                        changed()
                    }
                    refreshPurchases()
                } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                    status = "Satın alma tamamlanamadı."
                    changed()
                }
            }
        }.build()

    fun refresh() {
        if (closed) return
        if (client.isReady) {
            refreshPurchases()
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, result ->
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    details = result.productDetailsList.firstOrNull()
                    offer = details?.subscriptionOfferDetails?.firstOrNull {
                        it.basePlanId == "monthly" && it.offerId == null &&
                            it.pricingPhases.pricingPhaseList.singleOrNull()?.billingPeriod == "P1M"
                    }
                    status = when {
                        pending -> "Ödeme onayı bekleniyor."
                        price == null -> "Aylık plan şu anda Google Play’de bulunamadı."
                        else -> ""
                    }
                    changed()
                }
            }
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                    else {
                        status = "Google Play satın alma hizmetine ulaşılamadı."
                        changed()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                activity.runOnUiThread {
                    if (closed) return@runOnUiThread
                    connecting = false
                    details = null
                    offer = null
                    status = "Google Play bağlantısı kesildi."
                    changed()
                }
            }
        })
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            activity.runOnUiThread {
                if (closed || result.responseCode != BillingClient.BillingResponseCode.OK) return@runOnUiThread
                val matching = purchases.filter { PRODUCT in it.products }
                val active = matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                access.updateSubscription(active.any { it.isAcknowledged })
                pending = matching.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                if (pending) status = "Ödeme onayı bekleniyor."
                else if (price != null) status = ""
                for (purchase in active.filterNot { it.isAcknowledged }) {
                    val acknowledgement = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                    client.acknowledgePurchase(acknowledgement) { acknowledgementResult ->
                        if (acknowledgementResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            activity.runOnUiThread { if (!closed) refreshPurchases() }
                        }
                    }
                }
                changed()
            }
        }
    }

    fun buy() {
        val product = details ?: return
        val selected = offer ?: return
        val item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(selected.offerToken).build()
        val result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            status = "Satın alma ekranı açılamadı."
            changed()
        }
    }

    fun manage() {
        val url = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("sku", PRODUCT)
            .appendQueryParameter("package", activity.packageName).build()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url)) }
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
    }

    protected abstract fun onPremiumChanged()

    protected fun showPremium() {
        val membership = card()
        when {
            premiumAccess.subscribed -> label("✦  Premium üyeliğin aktif", 23f, membership, bold = true)
            premiumAccess.preview -> label("✦  Premium test erişimi", 23f, membership, bold = true)
            else -> label("BENG Premium", 23f, membership, bold = true)
        }
        label("Uyanıklık kontrolü", 17f, membership, bold = true)
        label("Alarmı kapattıktan sonra uyanık olduğunu onaylamazsan alarm tekrar çalar.", 14f, membership, color = mutedColor)
        label("QR kod ve telefonu sallama", 17f, membership, bold = true)
        label("Alarmı bir QR kod okutarak veya telefonunu sallayarak kapat.", 14f, membership, color = mutedColor)

        if (premiumAccess.subscribed) {
            button("Aboneliği yönet", membership) { billing?.manage() }
        } else {
            val price = billing?.price
            if (price != null) {
                label("$price / ay", 24f, membership, bold = true)
                label("Abonelik otomatik yenilenir. Google Play’den iptal edebilirsin.", 14f, membership, color = mutedColor)
                button("Premium’a abone ol", membership) { billing?.buy() }
            }
            billing?.status?.takeIf { it.isNotBlank() }?.let { label(it, 14f, membership, color = mutedColor) }
            button("Satın alımları geri yükle", membership, primary = false) { refreshPremium() }
            if (premiumAccess.previewAvailable && !premiumAccess.preview) {
                button("Premium’u test için aç", membership, primary = false) {
                    premiumAccess.enablePreview()
                    onPremiumChanged()
                }
            }
        }
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
        layout("Premium")
        showPremium()
        button("Alarm ayarına dön", primary = false) { finish() }
    }
}
