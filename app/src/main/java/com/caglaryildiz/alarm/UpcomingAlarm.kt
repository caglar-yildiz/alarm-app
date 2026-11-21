package com.caglaryildiz.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import java.time.Instant
import java.time.ZoneId

/**
 * A quiet notification two hours before the next real alarm, with "Skip this time". That is when people
 * decide they don't need tomorrow's alarm, so the skip sits there instead of on every alarm card.
 */
internal class UpcomingAlarm(base: Context) {
    private val context = AppLanguage.wrap(base)
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("upcoming_alarm", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Plans the notice for the next alarm; called whenever alarms change. */
    fun reschedule() {
        val alarms = Alarms(context)
        val next = alarms.all().filterNot { it.quick }
            .mapNotNull { alarm -> alarms.next(alarm)?.let { alarm to it } }
            .minByOrNull { it.second }
        val shown = prefs.getString("shownId", null)
        val shownAt = prefs.getLong("shownAt", 0)
        // A notice already on screen goes away once its alarm no longer rings at that time (skipped, edited, off).
        if (shown != null && (next == null || next.first.id != shown || next.second != shownAt)) {
            notifications.cancel(NOTIFICATION_ID)
            prefs.edit().remove("shownId").remove("shownAt").apply()
        }
        manager.cancel(showOperation())
        if (next == null) return
        val (alarm, at) = next
        val showAt = at - LEAD
        // Too close already (e.g. an alarm set for an hour from now): no notice, it would only be noise.
        if (showAt <= System.currentTimeMillis()) return
        prefs.edit().putString("plannedId", alarm.id).putLong("plannedAt", at).apply()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, showOperation())
    }

    fun show() {
        val id = prefs.getString("plannedId", null) ?: return
        val at = prefs.getLong("plannedAt", 0)
        val alarms = Alarms(context)
        val alarm = alarms.all().firstOrNull { it.id == id } ?: return
        // Still the same ring? Otherwise plan again from the current alarms.
        if (alarms.next(alarm) != at || at <= System.currentTimeMillis()) {
            reschedule()
            return
        }
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.upcoming_channel), NotificationManager.IMPORTANCE_LOW))
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(clockFormat)
        val open = PendingIntent.getActivity(context, 5, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val skip = PendingIntent.getBroadcast(context, 0, Intent(context, UpcomingAlarmReceiver::class.java)
            .setAction(SKIP).setData(Uri.fromParts("beng-upcoming", id, null)).putExtra("at", at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.upcoming_title, time))
            .setContentText(alarm.label.ifBlank { context.getString(R.string.upcoming_text) })
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(at - System.currentTimeMillis())
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                context.getString(R.string.upcoming_skip), skip).build())
            .build())
        prefs.edit().putString("shownId", id).putLong("shownAt", at).apply()
    }

    /** "Skip this time": a repeating alarm skips that day, a one-time alarm is turned off. */
    fun skip(id: String, at: Long) {
        notifications.cancel(NOTIFICATION_ID)
        val alarms = Alarms(context)
        val alarm = alarms.all().firstOrNull { it.id == id } ?: return
        if (alarms.next(alarm) != at) return
        val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        alarms.save(if (alarm.days.isNotEmpty()) alarm.copy(skipDate = date) else alarm.copy(enabled = false))
    }

    private fun showOperation() = PendingIntent.getBroadcast(context, 0,
        Intent(context, UpcomingAlarmReceiver::class.java).setAction(SHOW), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val CHANNEL = "upcoming_alarm"
        private const val NOTIFICATION_ID = 5
        private const val LEAD = 2 * 60 * 60_000L
        private const val SHOW = "com.caglaryildiz.alarm.UPCOMING_SHOW"
        private const val SKIP = "com.caglaryildiz.alarm.UPCOMING_SKIP"

        internal fun handle(context: Context, intent: Intent) {
            val upcoming = UpcomingAlarm(context)
            when (intent.action) {
                SHOW -> upcoming.show()
                SKIP -> upcoming.skip(intent.data?.schemeSpecificPart ?: return, intent.getLongExtra("at", 0))
            }
        }
    }
}

class UpcomingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UpcomingAlarm.handle(context, intent)
}
