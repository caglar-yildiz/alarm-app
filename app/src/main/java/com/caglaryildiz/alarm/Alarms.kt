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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val time: LocalTime,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val math: Boolean = true,
    val snoozeAt: Long = 0
) {
    fun nextAfter(now: ZonedDateTime): Long {
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (days.isNotEmpty() && date.dayOfWeek.value !in days) continue
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
                math = item.getBoolean("math"),
                snoozeAt = item.optLong("snoozeAt")
            )
        }
    }

    private fun write(alarms: List<Alarm>) {
        val data = JSONArray()
        alarms.forEach {
            data.put(JSONObject().put("id", it.id).put("time", it.time.toString())
                .put("days", JSONArray(it.days.sorted())).put("enabled", it.enabled)
                .put("math", it.math).put("snoozeAt", it.snoozeAt))
        }
        prefs.edit().putString("list", data.toString()).apply()
    }

    fun canSchedule() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun save(alarm: Alarm) {
        write(all().filterNot { it.id == alarm.id } + alarm)
        sync(alarm)
    }

    fun delete(alarm: Alarm) {
        write(all().filterNot { it.id == alarm.id })
        manager.cancel(operation(alarm.id, false))
        manager.cancel(operation(alarm.id, true))
    }

    fun syncAll() = all().forEach(::sync)

    private fun sync(alarm: Alarm) {
        update(alarm.id, false, if (alarm.enabled) alarm.nextAfter(ZonedDateTime.now()) else 0)
        update(alarm.id, true, alarm.snoozeAt)
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
                (alarm.days.isNotEmpty() && scheduled.dayOfWeek.value !in alarm.days)) return null
            save(alarm.copy(enabled = alarm.days.isNotEmpty()))
        }
        return alarm
    }

    companion object {
        const val FIRE = "com.caglaryildiz.alarm.FIRE"
        const val SNOOZE = "com.caglaryildiz.alarm.SNOOZE"
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
                context.startForegroundService(Intent(context, RingService::class.java)
                    .putExtra("id", alarm.id).putExtra("math", alarm.math))
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> alarms.syncAll()
        }
    }
}
