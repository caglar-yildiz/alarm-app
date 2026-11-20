package com.caglaryildiz.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One morning (or any alarm) as it ended.
 * [snoozes] counts the snoozes before it ended, [seconds] how long the final ring took to turn off,
 * [rerung] that wake check had to ring again, [missed] that the alarm rang out without being turned off.
 */
internal data class WakeEntry(
    val date: LocalDate,
    val alarmId: String,
    val snoozes: Int,
    val seconds: Int,
    val rerung: Boolean,
    val missed: Boolean,
    val at: Long
) {
    val onTime get() = !missed && !rerung && snoozes == 0
}

internal enum class DayResult { ON_TIME, LATE, NONE }

internal data class WakeSummary(
    val streak: Int,
    val bestStreak: Int,
    val onTimeDays: Int,
    val alarmDays: Int,
    val snoozes: Int,
    val averageSeconds: Int?,
    /** The last seven days, oldest first, ending today. */
    val week: List<Pair<LocalDate, DayResult>>,
    val total: Int
)

/** Pure calculations, kept apart from storage so they can be unit-tested. */
internal object WakeMath {
    fun dayResult(entries: List<WakeEntry>, date: LocalDate): DayResult {
        val day = entries.filter { it.date == date }
        return when {
            day.isEmpty() -> DayResult.NONE
            day.any { it.onTime } -> DayResult.ON_TIME
            else -> DayResult.LATE
        }
    }

    /**
     * Consecutive on-time days, counting back from today. Days without any alarm (weekends, a day off) are
     * skipped instead of breaking the streak, and an alarm-free or not-yet-rung today does not break it.
     */
    fun streak(entries: List<WakeEntry>, today: LocalDate): Int {
        val days = entries.map { it.date }.distinct().filter { !it.isAfter(today) }.sortedDescending()
        var count = 0
        for (date in days) {
            if (dayResult(entries, date) == DayResult.ON_TIME) count++ else break
        }
        return count
    }

    fun bestStreak(entries: List<WakeEntry>): Int {
        var best = 0
        var run = 0
        for (date in entries.map { it.date }.distinct().sorted()) {
            if (dayResult(entries, date) == DayResult.ON_TIME) best = maxOf(best, ++run) else run = 0
        }
        return best
    }

    fun summary(entries: List<WakeEntry>, today: LocalDate): WakeSummary {
        val recent = entries.filter { !it.date.isBefore(today.minusDays(29)) && !it.date.isAfter(today) }
        val days = recent.map { it.date }.distinct()
        val dismissed = recent.filter { !it.missed }
        return WakeSummary(
            streak = streak(entries, today),
            bestStreak = bestStreak(entries),
            onTimeDays = days.count { dayResult(recent, it) == DayResult.ON_TIME },
            alarmDays = days.size,
            snoozes = recent.sumOf { it.snoozes },
            averageSeconds = dismissed.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.seconds } / list.size },
            week = (6 downTo 0).map { today.minusDays(it.toLong()).let { date -> date to dayResult(entries, date) } },
            total = entries.size
        )
    }
}

/** Records how each alarm ended, on this phone only. */
internal class WakeStats(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("wake_stats", Context.MODE_PRIVATE)

    fun entries(): List<WakeEntry> {
        val data = runCatching { JSONArray(prefs.getString("entries", "[]")) }.getOrElse { JSONArray() }
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                WakeEntry(LocalDate.parse(item.getString("d")), item.getString("a"), item.optInt("s"),
                    item.optInt("sec"), item.optBoolean("r"), item.optBoolean("m"), item.optLong("t"))
            }.getOrNull()
        }
    }

    fun summary(today: LocalDate = LocalDate.now()) = WakeMath.summary(entries(), today)

    fun snoozed(alarmId: String) {
        prefs.edit().putInt("pending:$alarmId", prefs.getInt("pending:$alarmId", 0) + 1).apply()
    }

    /** A ring was turned off. [retry] marks the ring wake check started because the user didn't confirm. */
    fun dismissed(alarmId: String, startedAt: Long, retry: Boolean, now: Long = System.currentTimeMillis()) {
        if (retry) {
            markRerung(alarmId, now)
            return
        }
        add(alarmId, ((now - startedAt) / 1000).toInt().coerceAtLeast(0), missed = false, now)
    }

    /** A ring stopped on its own after ringing out. */
    fun missed(alarmId: String, retry: Boolean, now: Long = System.currentTimeMillis()) {
        if (retry) markRerung(alarmId, now) else add(alarmId, 0, missed = true, now)
    }

    private fun add(alarmId: String, seconds: Int, missed: Boolean, now: Long) {
        val entry = WakeEntry(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(), alarmId,
            prefs.getInt("pending:$alarmId", 0), seconds, rerung = false, missed = missed, at = now)
        write((entries() + entry).takeLast(MAX_ENTRIES), clearPending = alarmId)
    }

    private fun markRerung(alarmId: String, now: Long) {
        val list = entries().toMutableList()
        val index = list.indexOfLast { it.alarmId == alarmId && now - it.at in 0..RETRY_WINDOW }
        if (index < 0) return
        list[index] = list[index].copy(rerung = true)
        write(list, clearPending = null)
    }

    private fun write(list: List<WakeEntry>, clearPending: String?) {
        val data = JSONArray()
        list.forEach {
            data.put(JSONObject().put("d", it.date.toString()).put("a", it.alarmId).put("s", it.snoozes)
                .put("sec", it.seconds).put("r", it.rerung).put("m", it.missed).put("t", it.at))
        }
        prefs.edit().putString("entries", data.toString()).apply {
            if (clearPending != null) remove("pending:$clearPending")
        }.apply()
    }

    private companion object {
        const val MAX_ENTRIES = 400
        const val RETRY_WINDOW = 3 * 60 * 60_000L
    }
}
