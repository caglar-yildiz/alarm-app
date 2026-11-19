package com.caglaryildiz.alarm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A 7-day Premium trial that starts on the phone, with no card and no account.
 *
 * Without a server there is no tamper-proof record, so the start time is kept in two places and the
 * earliest one wins. Uninstalling, reinstalling or restoring a backup therefore does not grant a new trial:
 *  1. device-protected preferences, which are part of Android's Auto Backup and follow the Google account,
 *  2. Google Block Store, which keeps small values across reinstalls on the phone and backs them up
 *     end-to-end encrypted to the Google account.
 * The record also carries a hash of the phone's ANDROID_ID, ready for a server check later.
 * This is best effort: wiping Google Play services data, turning backup off or a factory reset can still
 * allow a new trial. Moving the clock back does not extend it: elapsed time never goes below the latest
 * time seen.
 */
internal class FreeTrial(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val startedAt: Long get() = prefs.getLong("startedAt", 0)
    val endsAt: Long get() = startedAt + DURATION
    val used get() = startedAt > 0

    fun active(now: Long = System.currentTimeMillis()): Boolean {
        if (!used) return false
        val seen = maxOf(now, prefs.getLong("lastSeen", 0))
        if (seen > prefs.getLong("lastSeen", 0)) prefs.edit().putLong("lastSeen", seen).apply()
        return seen < endsAt
    }

    fun daysLeft(now: Long = System.currentTimeMillis()): Int {
        val left = endsAt - maxOf(now, prefs.getLong("lastSeen", 0))
        return ((left + DAY - 1) / DAY).toInt().coerceIn(0, 7)
    }

    /** True once this process has compared the local record with Block Store. */
    val checked get() = Companion.checked

    /** Reads Block Store and keeps the earliest known start. [done] runs on the main thread. */
    fun sync(done: () -> Unit) {
        if (Companion.checked) {
            done()
            return
        }
        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun finish(remoteStart: Long?) {
            if (finished) return
            finished = true
            if (remoteStart != null && remoteStart > 0 && (!used || remoteStart < startedAt)) {
                prefs.edit().putLong("startedAt", remoteStart).apply()
            }
            // A local record that Block Store lacks (e.g. restored from Auto Backup) is copied back to it.
            if (used && (remoteStart == null || remoteStart <= 0)) saveRemote(startedAt)
            Companion.checked = true
            main.post(done)
        }
        // Phones without Google Play services never answer; do not keep the user waiting.
        main.postDelayed({ finish(null) }, 5_000)
        try {
            Blockstore.getClient(app)
                .retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build())
                .addOnSuccessListener { response ->
                    val bytes = response.blockstoreDataMap[KEY]?.bytes
                    finish(bytes?.let { parseStart(String(it, Charsets.UTF_8)) })
                }
                .addOnFailureListener { finish(null) }
        } catch (_: Exception) {
            finish(null)
        }
    }

    /** Starts the trial if this phone and account never had one. Returns false when it is not allowed. */
    fun start(now: Long = System.currentTimeMillis()): Boolean {
        if (used || !Companion.checked) return false
        prefs.edit().putLong("startedAt", now).putLong("lastSeen", now).putString("device", deviceHash()).apply()
        saveRemote(now)
        return true
    }

    private fun saveRemote(start: Long) {
        val record = JSONObject().put("startedAt", start).put("device", deviceHash()).toString()
        try {
            Blockstore.getClient(app).storeBytes(StoreBytesData.Builder()
                .setKey(KEY)
                .setBytes(record.toByteArray(Charsets.UTF_8))
                .setShouldBackupToCloud(true)
                .build())
        } catch (_: Exception) {
            // Block Store is optional: the local and backed-up record still applies.
        }
    }

    // ANDROID_ID is used only, hashed, to stop repeated free trials (fraud prevention, allowed by Play policy).
    @android.annotation.SuppressLint("HardwareIds")
    private fun deviceHash(): String {
        val id = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest("beng:$id".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    companion object {
        private const val PREFS = "free_trial"
        private const val KEY = "com.caglaryildiz.alarm.free_trial"
        const val DAY = 24 * 60 * 60 * 1000L
        const val DURATION = 7 * DAY
        @Volatile private var checked = false

        internal fun parseStart(json: String): Long? =
            runCatching { JSONObject(json).optLong("startedAt", 0) }.getOrNull()?.takeIf { it > 0 }
    }
}
