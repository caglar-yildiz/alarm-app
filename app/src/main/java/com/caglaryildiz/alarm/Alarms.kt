package com.caglaryildiz.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal enum class AlarmTask(val labelRes: Int, val premium: Boolean = false) {
    NONE(R.string.task_none),
    MATH(R.string.task_math),
    MEMORY(R.string.task_memory),
    TYPING(R.string.task_typing),
    SHAKE(R.string.task_shake, premium = true),
    QR(R.string.task_qr, premium = true),
    SHAPES(R.string.task_shapes, premium = true),
    STEPS(R.string.task_steps, premium = true)
}

/** How many steps the walking mission asks for. */
internal object StepGoal {
    const val DEFAULT = 30
    val options = listOf(20, 30, 50, 100)
    fun coerce(value: Int) = value.takeIf { it in options } ?: DEFAULT
}

internal data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val time: LocalTime,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val task: AlarmTask = AlarmTask.MATH,
    val snoozeAt: Long = 0,
    val date: LocalDate? = null,
    val label: String = "",
    val tone: String? = null,
    val crescendo: Boolean = true,
    val mathSettings: MathSettings = MathSettings(),
    val qrCode: String = "",
    /** A repeating alarm skips its occurrence on this date only. */
    val skipDate: LocalDate? = null,
    val stepGoal: Int = StepGoal.DEFAULT,
    /** Made by a widget nap button; removed once it has rung. */
    val quick: Boolean = false
) {
    fun nextAfter(now: ZonedDateTime): Long? {
        if (days.isEmpty() && date != null) {
            val candidate = date.atTime(time).atZone(now.zone)
            return candidate.takeIf { it.isAfter(now) }?.toInstant()?.toEpochMilli()
        }
        // Two weeks, so a skipped weekly occurrence still finds the next one.
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (days.isNotEmpty() && date.dayOfWeek.value !in days) continue
            if (days.isNotEmpty() && date == skipDate) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli()
        }
        error("No matching alarm day")
    }
}

internal class Alarms(private val context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun all(): List<Alarm> {
        val data = JSONArray(prefs.getString("list", "[]"))
        return List(data.length()) { index ->
            val item = data.getJSONObject(index)
            val days = item.getJSONArray("days")
            Alarm(
                id = item.getString("id"),
                time = LocalTime.parse(item.getString("time")),
                days = List(days.length()) { days.getInt(it) }.toSet(),
                enabled = item.getBoolean("enabled"),
                task = AlarmTask.entries.firstOrNull { it.name == item.optString("task") }
                    ?: if (item.optBoolean("math", true)) AlarmTask.MATH else AlarmTask.NONE,
                snoozeAt = item.optLong("snoozeAt"),
                date = item.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                label = item.optString("label"),
                tone = item.optString("tone").takeIf { it.isNotEmpty() },
                crescendo = item.optBoolean("crescendo", true),
                mathSettings = MathSettings.from(item.optString("mathDifficulty"), item.optInt("mathCount", 1)),
                qrCode = item.optString("qrCode"),
                skipDate = item.optString("skipDate").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                stepGoal = StepGoal.coerce(item.optInt("stepGoal", StepGoal.DEFAULT)),
                quick = item.optBoolean("quick")
            )
        }
    }

    private fun write(alarms: List<Alarm>) {
        val data = JSONArray()
        alarms.forEach {
            data.put(JSONObject().put("id", it.id).put("time", it.time.toString())
                .put("days", JSONArray(it.days.sorted())).put("enabled", it.enabled)
                .put("task", it.task.name).put("snoozeAt", it.snoozeAt)
                .put("mathDifficulty", it.mathSettings.difficulty.name).put("mathCount", it.mathSettings.count)
                .put("qrCode", it.qrCode).put("date", it.date?.toString()).put("label", it.label)
                .put("tone", it.tone).put("crescendo", it.crescendo)
                .put("skipDate", it.skipDate?.toString()).put("stepGoal", it.stepGoal).put("quick", it.quick))
        }
        prefs.edit().putString("list", data.toString()).apply()
    }

    fun canSchedule() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun save(alarm: Alarm) {
        write(all().filterNot { it.id == alarm.id } + alarm)
        sync(alarm)
        changed()
    }

    fun delete(alarm: Alarm) {
        WakeCheck(context).confirm(alarm.id)
        write(all().filterNot { it.id == alarm.id })
        manager.cancel(operation(alarm.id, false))
        manager.cancel(operation(alarm.id, true))
        changed()
    }

    /**
     * Vacation mode: repeating alarms stay quiet through this date (inclusive). One-time alarms still ring,
     * because the user set them for a specific day. Null once the date has passed.
     */
    var pausedUntil: LocalDate?
        get() = prefs.getString("pausedUntil", null)?.let(LocalDate::parse)?.takeIf { !it.isBefore(LocalDate.now()) }
        set(value) {
            prefs.edit().putString("pausedUntil", value?.toString()).apply()
            syncAll()
        }

    /** When [alarm] rings next (not counting snoozes), respecting skipped dates and vacation mode. */
    fun next(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        if (!alarm.enabled) return null
        val pause = pausedUntil
        val from = if (pause != null && alarm.days.isNotEmpty()) {
            maxOf(now, pause.plusDays(1).atStartOfDay(now.zone))
        } else now
        return alarm.nextAfter(from)
    }

    /** The next ring of any alarm, snoozes included. */
    fun nextRing(now: ZonedDateTime = ZonedDateTime.now()): Pair<Alarm, Long>? = all().mapNotNull { alarm ->
        listOfNotNull(alarm.snoozeAt.takeIf { it > now.toInstant().toEpochMilli() }, next(alarm, now))
            .minOrNull()?.let { alarm to it }
    }.minByOrNull { it.second }

    fun syncAll() {
        val ringing = RingService.active?.id
        // Widget naps that already rang are not real alarms: drop them instead of listing them forever.
        val done = all().filter { it.quick && !it.enabled && it.snoozeAt == 0L && it.id != ringing }
        if (done.isNotEmpty()) write(all().filterNot { alarm -> done.any { it.id == alarm.id } })
        all().forEach(::sync)
        changed()
    }

    private fun sync(alarm: Alarm) {
        update(alarm.id, false, next(alarm) ?: 0)
        update(alarm.id, true, alarm.snoozeAt)
    }

    /** Things that show or depend on the next alarm. */
    private fun changed() {
        Bedtime(context).reschedule()
        NextAlarmWidget.update(context)
    }

    private fun update(id: String, snooze: Boolean, at: Long) {
        val pending = operation(id, snooze, at)
        if (at <= 0 || !canSchedule()) {
            manager.cancel(pending)
            return
        }
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending)
        } catch (_: SecurityException) {
            // The user can revoke exact alarm access between the check and scheduling.
        }
    }

    /**
     * Rings [id] again shortly after the phone restarted (or the app updated) while it was ringing.
     * Android 15 does not let a boot receiver start a media-playback foreground service, so the ring goes
     * through an exact alarm a few seconds later, exactly like a normal alarm.
     */
    fun resumeRinging(id: String) {
        val alarm = all().firstOrNull { it.id == id } ?: return
        if (canSchedule()) {
            val intent = Intent(context, AlarmReceiver::class.java).setAction(RESUME)
                .setData(Uri.parse("uyan://resume/$id"))
            val pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            try {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + RESUME_DELAY, show), pending)
                return
            } catch (_: SecurityException) {
                // Exact alarm access was revoked in between; try ringing right away below.
            }
        }
        // Works where the platform still allows it from a boot receiver (before Android 15).
        runCatching { context.startForegroundService(alarm.ringIntent(context).putExtra(RingService.EXTRA_RESUMED, true)) }
    }

    private fun operation(id: String, snooze: Boolean, at: Long = 0): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = if (snooze) SNOOZE else FIRE
        }
        // Use stable identity for replacing or cancelling a scheduled occurrence.
        intent.data = Uri.parse("uyan://alarm/$id")
        intent.putExtra("at", at)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (at > 0) PendingIntent.FLAG_UPDATE_CURRENT else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    fun consume(id: String, snooze: Boolean, at: Long): Alarm? {
        val alarm = all().firstOrNull { it.id == id } ?: return null
        if (at <= 0) return null
        if (snooze) {
            if (alarm.snoozeAt != at) return null
            save(alarm.copy(snoozeAt = 0))
        } else {
            val scheduled = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            if (!alarm.enabled || scheduled.toLocalDate().atTime(alarm.time).atZone(scheduled.zone) != scheduled ||
                (alarm.days.isNotEmpty() && scheduled.dayOfWeek.value !in alarm.days) ||
                (alarm.days.isEmpty() && alarm.date != null && scheduled.toLocalDate() != alarm.date) ||
                (alarm.days.isNotEmpty() && scheduled.toLocalDate() == alarm.skipDate)) return null
            // A skip that has been used up is cleared, so the alarm card stops mentioning it.
            save(alarm.copy(enabled = alarm.days.isNotEmpty(),
                skipDate = alarm.skipDate?.takeIf { it.isAfter(scheduled.toLocalDate()) }))
        }
        return alarm
    }

    companion object {
        const val FIRE = "com.caglaryildiz.alarm.FIRE"
        const val SNOOZE = "com.caglaryildiz.alarm.SNOOZE"
        const val RESUME = "com.caglaryildiz.alarm.RESUME_RING"
        private const val RESUME_DELAY = 3_000L
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarms = Alarms(context)
        when (intent.action) {
            Alarms.FIRE, Alarms.SNOOZE -> {
                val id = intent.data?.lastPathSegment ?: return
                val alarm = alarms.consume(id, intent.action == Alarms.SNOOZE,
                    intent.getLongExtra("at", 0)) ?: return
                context.startForegroundService(alarm.ringIntent(context))
            }
            Alarms.RESUME -> {
                val id = intent.data?.lastPathSegment ?: return
                val alarm = alarms.all().firstOrNull { it.id == id } ?: return
                // Re-check: the alarm may have been dismissed in a ring that started before this fired.
                if (id !in RingRecord(context).interrupted()) return
                context.startForegroundService(alarm.ringIntent(context).putExtra(RingService.EXTRA_RESUMED, true))
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                alarms.syncAll()
                WakeCheck(context).restore()
                Reminders(context).syncAll()
                // Restarting the phone or updating the app must not be a way out of a ringing alarm.
                if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    RingRecord(context).interrupted().forEach(alarms::resumeRinging)
                }
            }
        }
    }
}

internal fun Alarm.ringIntent(context: Context) = Intent(context, RingService::class.java)
    .putExtra("id", id)
    .putExtra("task", task.name)
    .putExtra("qrCode", qrCode)
    .putExtra("stepGoal", stepGoal)
    .putExtra("mathDifficulty", mathSettings.difficulty.name)
    .putExtra("mathCount", mathSettings.count)
    .putExtra("label", label)
    .putExtra("tone", tone)
    .putExtra("crescendo", crescendo)
