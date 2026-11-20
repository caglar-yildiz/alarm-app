package com.caglaryildiz.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reminds the user to go to bed so they get their sleep goal before the next alarm.
 * Rescheduled whenever alarms change; an inexact alarm is enough for a reminder and needs no permission.
 */
internal class Bedtime(base: Context) {
    private val context = AppLanguage.wrap(base)
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("bedtime", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            reschedule()
        }

    /** Sleep goal in minutes. */
    var goal: Int
        get() = prefs.getInt("goal", 8 * 60).takeIf { it in GOALS } ?: (8 * 60)
        set(value) {
            prefs.edit().putInt("goal", value).apply()
            reschedule()
        }

    /** How long before bedtime the reminder comes, in minutes. */
    var lead: Int
        get() = prefs.getInt("lead", 30).takeIf { it in LEADS } ?: 30
        set(value) {
            prefs.edit().putInt("lead", value).apply()
            reschedule()
        }

    /** When the next reminder is due, or 0 if none is planned. */
    val nextReminder: Long get() = prefs.getLong("remindAt", 0).takeIf { it > System.currentTimeMillis() } ?: 0

    /** The next alarm that is not a snooze: bedtime is planned around real alarms only. */
    fun nextAlarm(now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val alarms = Alarms(context)
        return alarms.all().mapNotNull { alarms.next(it, now) }.minOrNull()
    }

    fun reschedule() {
        val pending = operation()
        manager.cancel(pending)
        prefs.edit().remove("remindAt").remove("alarmAt").apply()
        if (!enabled) return
        val alarmAt = nextAlarm() ?: return
        val remindAt = reminderTime(alarmAt, goal, lead)
        if (remindAt <= System.currentTimeMillis()) return
        prefs.edit().putLong("remindAt", remindAt).putLong("alarmAt", alarmAt).apply()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
    }

    fun remind() {
        val alarmAt = prefs.getLong("alarmAt", 0)
        prefs.edit().remove("remindAt").apply()
        if (!enabled || alarmAt <= System.currentTimeMillis()) return
        val zone = ZoneId.systemDefault()
        val bedtime = Instant.ofEpochMilli(alarmAt - goal * 60_000L).atZone(zone).format(clockFormat)
        val alarm = Instant.ofEpochMilli(alarmAt).atZone(zone).format(clockFormat)
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.bedtime_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val text = context.getString(R.string.bedtime_notification_text, bedtime, sleepDuration(context, goal), alarm)
        notifications.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bedtime)
            .setContentTitle(context.getString(R.string.bedtime_notification_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(lead * 60_000L + 60 * 60_000L)
            .build())
    }

    private fun operation() = PendingIntent.getBroadcast(context, 0,
        Intent(context, BedtimeReceiver::class.java).setAction(REMIND), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        val GOALS = listOf(7 * 60, 7 * 60 + 30, 8 * 60, 8 * 60 + 30, 9 * 60)
        val LEADS = listOf(15, 30, 60)
        private const val CHANNEL = "bedtime"
        private const val NOTIFICATION_ID = 4
        private const val REMIND = "com.caglaryildiz.alarm.BEDTIME"

        fun reminderTime(alarmAt: Long, goalMinutes: Int, leadMinutes: Int) =
            alarmAt - (goalMinutes + leadMinutes) * 60_000L
    }
}

/** "7 h 30 min", in the app language. */
internal fun sleepDuration(context: Context, minutes: Int): String =
    if (minutes % 60 == 0) context.getString(R.string.duration_hours, minutes / 60)
    else context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Bedtime(context).remind()
    }
}
