package com.caglaryildiz.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.time.ZonedDateTime
import java.util.UUID

internal enum class ReminderRepeat(val labelRes: Int) {
    ONCE(R.string.reminder_repeat_once),
    DAILY(R.string.repeat_every_day),
    WEEKLY(R.string.reminder_repeat_weekly),
    MONTHLY(R.string.reminder_repeat_monthly),
    YEARLY(R.string.reminder_repeat_yearly)
}

/**
 * A reminder the user sets like an alarm: a title, an optional note, a date and time and how it repeats.
 * [leadDays] adds an early heads-up (1 or 7 days before, Premium) next to the on-the-day notice.
 * [ring] makes it ring like an alarm instead of posting a notification (Premium).
 */
internal data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val date: LocalDate,
    val time: LocalTime,
    val repeat: ReminderRepeat = ReminderRepeat.ONCE,
    val days: Set<Int> = emptySet(),
    val leadDays: Int = 0,
    val ring: Boolean = false,
    val enabled: Boolean = true,
    val snoozeAt: Long = 0
) {
    /** Event days on or after [from], in order. Months and years keep the day, or use the month's last day. */
    fun occurrences(from: LocalDate): Sequence<LocalDate> = when (repeat) {
        ReminderRepeat.ONCE -> sequenceOf(date).filter { !it.isBefore(from) }
        ReminderRepeat.DAILY -> generateSequence(maxOf(from, date)) { it.plusDays(1) }
        ReminderRepeat.WEEKLY -> {
            val weekdays = days.ifEmpty { setOf(date.dayOfWeek.value) }
            generateSequence(maxOf(from, date)) { it.plusDays(1) }.filter { it.dayOfWeek.value in weekdays }
        }
        ReminderRepeat.MONTHLY -> generateSequence(from.withDayOfMonth(1)) { it.plusMonths(1) }
            .map { it.withDayOfMonth(minOf(date.dayOfMonth, it.lengthOfMonth())) }
            .filter { !it.isBefore(from) && !it.isBefore(date) }
        ReminderRepeat.YEARLY -> generateSequence(from.year) { it + 1 }
            .map { year -> LocalDate.of(year, date.month, 1).let { it.withDayOfMonth(minOf(date.dayOfMonth, it.lengthOfMonth())) } }
            .filter { !it.isBefore(from) && !it.isBefore(date) }
    }

    /** The next notice after [now]: either an event itself or its early heads-up. */
    fun nextNotice(now: ZonedDateTime): ReminderNotice? {
        if (!enabled) return null
        val notices = occurrences(now.toLocalDate()).take(40).flatMap { day ->
            val event = day.atTime(time).atZone(now.zone)
            sequenceOf(
                ReminderNotice(event.toInstant().toEpochMilli(), day, early = false),
                if (leadDays > 0) ReminderNotice(event.minusDays(leadDays.toLong()).toInstant().toEpochMilli(), day, early = true) else null
            ).filterNotNull()
        }
        val snooze = snoozeAt.takeIf { it > now.toInstant().toEpochMilli() }
            ?.let { ReminderNotice(it, Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate(), early = false, snoozed = true) }
        return (notices + listOfNotNull(snooze)).filter { it.at > now.toInstant().toEpochMilli() }.minByOrNull { it.at }
    }

    /** The next time the event itself happens, for lists ("Tomorrow · 09:00"). */
    fun nextEvent(now: ZonedDateTime): ZonedDateTime? {
        if (!enabled) return null
        return occurrences(now.toLocalDate()).take(40).map { it.atTime(time).atZone(now.zone) }.firstOrNull { it.isAfter(now) }
    }
}

internal data class ReminderNotice(val at: Long, val eventDay: LocalDate, val early: Boolean, val snoozed: Boolean = false)

/** Reminders live next to the alarms in device-protected storage, which is also backed up. */
internal class Reminders(context: Context) {
    private val app = AppLanguage.wrap(context.applicationContext)
    private val prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)

    fun all(): List<Reminder> {
        val items = JSONArray(prefs.getString("list", "[]"))
        return List(items.length()) { index -> fromJson(items.getJSONObject(index)) }.filterNotNull()
    }

    fun get(id: String) = all().firstOrNull { it.id == id }

    fun save(reminder: Reminder) {
        write(all().filterNot { it.id == reminder.id } + reminder)
        schedule(reminder)
    }

    fun delete(id: String) {
        write(all().filterNot { it.id == id })
        manager.cancel(operation(id))
        notifications.cancel(id, NOTIFICATION_ID)
    }

    fun syncAll() {
        all().forEach(::schedule)
        Holidays(app).schedule()
    }

    /** Called by the receiver: shows the notice when it is still due, then plans the next one. */
    fun fire(id: String, at: Long) {
        val reminder = get(id) ?: return
        val notice = reminder.nextNotice(ZonedDateTime.now().minusSeconds(90)) ?: return
        if (kotlin.math.abs(notice.at - at) > 60_000) {
            schedule(reminder)
            return
        }
        val premium = PremiumAccess(app).unlocked
        if (reminder.ring && premium && !notice.early) {
            app.startForegroundService(Intent(app, RingService::class.java)
                .putExtra("id", REMINDER_PREFIX + reminder.id)
                .putExtra("task", AlarmTask.NONE.name)
                .putExtra("label", reminder.title)
                .putExtra("crescendo", true))
        } else if (!notice.early || premium) {
            notify(reminder, notice)
        }
        var updated = reminder.copy(snoozeAt = if (notice.snoozed) 0 else reminder.snoozeAt)
        // A one-time reminder is done once its own moment has passed.
        if (reminder.repeat == ReminderRepeat.ONCE && !notice.early && !notice.snoozed) updated = updated.copy(enabled = false)
        write(all().map { if (it.id == id) updated else it })
        schedule(updated)
    }

    /** Snooze from the ringing screen: rings again in five minutes. */
    fun snooze(id: String) {
        val reminder = get(id) ?: return
        save(reminder.copy(snoozeAt = System.currentTimeMillis() + 5 * 60_000L, enabled = true))
    }

    private fun schedule(reminder: Reminder) {
        val pending = operation(reminder.id)
        val notice = reminder.nextNotice(ZonedDateTime.now())
        if (notice == null) {
            manager.cancel(pending)
            return
        }
        scheduleExact(manager, notice.at, operation(reminder.id, notice.at))
    }

    private fun notify(reminder: Reminder, notice: ReminderNotice) {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH))
        if (!canNotify(app)) return
        val text = when {
            notice.early -> app.getString(if (reminder.leadDays == 1) R.string.reminder_notice_tomorrow
                else R.string.reminder_notice_week, reminder.title)
            reminder.note.isNotBlank() -> reminder.note
            else -> app.getString(R.string.reminder_notice_now)
        }
        val open = PendingIntent.getActivity(app, 2, Intent(app, MainActivity::class.java).putExtra("remindersTab", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(reminder.id, NOTIFICATION_ID, Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun operation(id: String, at: Long = 0) = PendingIntent.getBroadcast(app, 0,
        Intent(app, ReminderReceiver::class.java).setAction(FIRE)
            .setData(Uri.fromParts("beng-reminder", id, null)).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or if (at > 0) PendingIntent.FLAG_UPDATE_CURRENT else 0)

    private fun write(items: List<Reminder>) {
        val json = JSONArray()
        items.forEach { json.put(toJson(it)) }
        prefs.edit().putString("list", json.toString()).apply()
    }

    companion object {
        const val FIRE = "com.caglaryildiz.alarm.REMINDER"
        const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 3
        /** Ring ids that belong to reminders, so the ringing screen can snooze the right thing. */
        const val REMINDER_PREFIX = "reminder:"

        internal fun toJson(it: Reminder): JSONObject = JSONObject()
            .put("id", it.id).put("title", it.title).put("note", it.note)
            .put("date", it.date.toString()).put("time", it.time.toString())
            .put("repeat", it.repeat.name).put("days", JSONArray(it.days.sorted()))
            .put("leadDays", it.leadDays).put("ring", it.ring).put("enabled", it.enabled).put("snoozeAt", it.snoozeAt)

        internal fun fromJson(item: JSONObject): Reminder? = runCatching {
            val days = item.optJSONArray("days") ?: JSONArray()
            Reminder(
                id = item.getString("id"),
                title = item.getString("title"),
                note = item.optString("note"),
                date = LocalDate.parse(item.getString("date")),
                time = LocalTime.parse(item.getString("time")),
                repeat = ReminderRepeat.entries.firstOrNull { it.name == item.optString("repeat") } ?: ReminderRepeat.ONCE,
                days = List(days.length()) { days.getInt(it) }.toSet(),
                leadDays = item.optInt("leadDays"),
                ring = item.optBoolean("ring"),
                enabled = item.optBoolean("enabled", true),
                snoozeAt = item.optLong("snoozeAt")
            )
        }.getOrNull()

        fun canNotify(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)
        }

        /** Exact when allowed, so a 09:00 reminder arrives at 09:00; otherwise as close as Android allows. */
        fun scheduleExact(manager: AlarmManager, at: Long, operation: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                }
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            when (intent.action) {
                Reminders.FIRE -> {
                    val id = intent.data?.schemeSpecificPart ?: return
                    Reminders(context).fire(id, intent.getLongExtra("at", 0))
                }
                Holidays.FIRE -> Holidays(context).fire(intent.getLongExtra("at", 0))
            }
        } finally {
            pending.finish()
        }
    }
}
