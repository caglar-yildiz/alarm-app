package com.caglaryildiz.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Some phone makers stop background apps far more aggressively than Android itself, and a stopped app's
 * alarms can be dropped. This finds what the user should change and opens the right settings screen.
 *
 * No battery-optimization permission is requested: the user turns the exemption on in the system list,
 * which keeps the app clear of Google Play's restricted-permission review.
 */
internal class Reliability(private val context: Context) {
    private val prefs = context.getSharedPreferences("reliability", Context.MODE_PRIVATE)

    /** The phone maker's own background limits, if it is one known to stop alarm apps. */
    val brand: Brand? = Brand.detect(Build.MANUFACTURER, Build.BRAND)

    val batteryRestricted: Boolean
        get() = !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    var dismissed: Boolean
        get() = prefs.getBoolean("dismissed", false)
        set(value) { prefs.edit().putBoolean("dismissed", value).apply() }

    /** Whether the "make sure alarms ring" card should be shown. */
    val needsAttention get() = !dismissed && (batteryRestricted || brand != null)

    fun openBatterySettings() {
        open(listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails()))
    }

    fun openBrandSettings() {
        val brand = brand ?: return
        open(brand.components.map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) } + appDetails())
    }

    private fun appDetails() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Tries each screen in turn; maker screens move between OS versions, so the app page is the last resort. */
    private fun open(candidates: List<Intent>) {
        for (intent in candidates) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
    }

    internal enum class Brand(val displayName: String, val components: List<Pair<String, String>>) {
        XIAOMI("Xiaomi", listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        HUAWEI("Huawei", listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        HONOR("Honor", listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        OPPO("OPPO", listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity")),
        VIVO("vivo", listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        ONEPLUS("OnePlus", listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        SAMSUNG("Samsung", listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity"));

        companion object {
            fun detect(manufacturer: String?, brand: String?): Brand? {
                val name = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
                return when {
                    listOf("xiaomi", "redmi", "poco").any(name::contains) -> XIAOMI
                    "honor" in name -> HONOR
                    "huawei" in name -> HUAWEI
                    "oneplus" in name -> ONEPLUS
                    listOf("oppo", "realme").any(name::contains) -> OPPO
                    listOf("vivo", "iqoo").any(name::contains) -> VIVO
                    "samsung" in name -> SAMSUNG
                    else -> null
                }
            }
        }
    }
}
