package com.caglaryildiz.alarm

import android.content.Context
import org.json.JSONArray

/**
 * Remembers which alarms are ringing, so restarting the phone or updating the app does not silence them.
 *
 * Kept in device-protected storage, so it can be read at LOCKED_BOOT_COMPLETED before the phone is unlocked.
 * The record is cleared only when the last ringing alarm is dismissed, snoozed or times out. [since] stays
 * at the first ring while the record is set, so restarting over and over cannot keep an alarm alive forever:
 * after [MAX_AGE] it is no longer resumed.
 */
internal class RingRecord(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Saves the ids of the ringing and queued alarms, or clears the record when [ids] is empty. */
    fun update(ids: List<String>, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) {
            prefs.edit().clear().commit()
            return
        }
        // A record left over from long ago (e.g. the phone stayed off) must not shorten a new ring's window.
        val since = prefs.getLong(SINCE, 0).takeIf { now - it in 0..MAX_AGE } ?: now
        // commit, not apply: the process may be killed by a restart right after this.
        prefs.edit().putString(IDS, JSONArray(ids.distinct()).toString()).putLong(SINCE, since).commit()
    }

    /** Alarms that were still ringing when the phone went down, if that was recent enough to ring again. */
    fun interrupted(now: Long = System.currentTimeMillis()): List<String> {
        val since = prefs.getLong(SINCE, 0)
        if (since <= 0 || now - since !in 0..MAX_AGE) return emptyList()
        val data = runCatching { JSONArray(prefs.getString(IDS, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until data.length()).mapNotNull { data.optString(it).takeIf(String::isNotBlank) }
    }

    companion object {
        private const val PREFS = "ring_record"
        private const val IDS = "ids"
        private const val SINCE = "since"
        internal const val MAX_AGE = 30 * 60_000L
    }
}
