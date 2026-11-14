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
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class Countdown(
    val id: String,
    val duration: Long,
    val remaining: Long = duration,
    val deadline: Long = 0
) {
    fun remainingAt(now: Long) = if (deadline == 0L) remaining else (deadline - now).coerceAtLeast(0)
}

internal class TimeTools(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("time_tools", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)

    init {
        val boot = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (prefs.getInt("boot", boot) != boot) {
            write(timers().map { it.copy(deadline = 0) })
            prefs.edit().remove("stopwatchStart").apply()
        }
        prefs.edit().putInt("boot", boot).apply()
    }

    val stopwatchRunning get() = prefs.contains("stopwatchStart")

    fun stopwatchElapsed(now: Long): Long = prefs.getLong("stopwatchElapsed", 0) +
        if (stopwatchRunning) (now - prefs.getLong("stopwatchStart", now)).coerceAtLeast(0) else 0

    fun toggleStopwatch() {
        val now = SystemClock.elapsedRealtime()
        val edit = prefs.edit()
        if (stopwatchRunning) edit.putLong("stopwatchElapsed", stopwatchElapsed(now)).remove("stopwatchStart")
        else edit.putLong("stopwatchStart", now)
        edit.apply()
    }

    fun resetStopwatch() {
        prefs.edit().remove("stopwatchStart").remove("stopwatchElapsed").apply()
    }

    fun timers(): List<Countdown> {
        val items = JSONArray(prefs.getString("timers", "[]"))
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Countdown(item.getString("id"), item.getLong("duration"), item.getLong("remaining"), item.getLong("deadline"))
        }
    }

    fun add(duration: Long) {
        val timer = Countdown(UUID.randomUUID().toString(), duration, deadline = SystemClock.elapsedRealtime() + duration)
        write(timers() + timer)
        schedule(timer)
    }

    fun toggle(id: String) {
        val timer = timers().firstOrNull { it.id == id } ?: return
        val now = SystemClock.elapsedRealtime()
        if (timer.deadline != 0L) {
            if (finishIfDue(id)) return
            replace(timer.copy(remaining = timer.remainingAt(now), deadline = 0))
            alarms.cancel(operation(id))
        } else {
            val remaining = timer.remaining.takeIf { it > 0 } ?: timer.duration
            val started = timer.copy(remaining = remaining, deadline = now + remaining)
            notifications.cancel(id, NOTIFICATION_ID)
            replace(started)
            schedule(started)
        }
    }

    fun reset(id: String) {
        val timer = timers().firstOrNull { it.id == id } ?: return
        cancel(id)
        replace(timer.copy(remaining = timer.duration, deadline = 0))
    }

    fun remove(id: String) {
        cancel(id)
        write(timers().filterNot { it.id == id })
    }

    fun notificationsBlocked(): Boolean = !notifications.areNotificationsEnabled() ||
        notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE

    fun notificationSettings(): Intent {
        if (notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
    }

    fun finishIfDue(id: String): Boolean {
        val timer = timers().firstOrNull { it.id == id } ?: return false
        if (timer.deadline == 0L || timer.deadline > SystemClock.elapsedRealtime()) return false
        alarms.cancel(operation(id))
        replace(timer.copy(remaining = 0, deadline = 0))
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Sayaçlar", NotificationManager.IMPORTANCE_HIGH))
        if (notifications.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            val open = Intent(app, MainActivity::class.java).putExtra("timersTab", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val content = PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(id, NOTIFICATION_ID, Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_time_tools)
                .setContentTitle("Sayaç tamamlandı")
                .setContentText("${formatTime(timer.duration)} süren geri sayım bitti.")
                .setContentIntent(content)
                .setAutoCancel(true)
                .build())
        }
        return true
    }

    private fun schedule(timer: Countdown) {
        val pending = operation(timer.id)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadline, pending)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadline, pending)
        }
    }

    private fun cancel(id: String) {
        alarms.cancel(operation(id))
        notifications.cancel(id, NOTIFICATION_ID)
    }

    private fun operation(id: String) = PendingIntent.getBroadcast(app, 0,
        Intent(app, CountdownReceiver::class.java).setData(Uri.fromParts("beng-timer", id, null)),
        PendingIntent.FLAG_IMMUTABLE)

    private fun replace(timer: Countdown) = write(timers().map { if (it.id == timer.id) timer else it })

    private fun write(items: List<Countdown>) {
        val json = JSONArray()
        for (item in items) {
            json.put(JSONObject().put("id", item.id).put("duration", item.duration)
                .put("remaining", item.remaining).put("deadline", item.deadline))
        }
        prefs.edit().putString("timers", json.toString()).apply()
    }

    private companion object {
        const val CHANNEL = "countdowns"
        const val NOTIFICATION_ID = 1
    }
}

class CountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.schemeSpecificPart ?: return
        TimeTools(context).finishIfDue(id)
    }
}

internal fun formatTime(millis: Long, tenths: Boolean = false): String {
    val seconds = millis / 1000
    val time = "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
    return if (tenths) "$time.${millis / 100 % 10}" else time
}
