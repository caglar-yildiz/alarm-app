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

internal class WakeCheck(private val context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("wake_checks", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            if (!value) ids().forEach(::cancel)
        }
    var delay: Int
        get() = prefs.getInt("delay", 3)
        set(value) { prefs.edit().putInt("delay", value).apply() }

    fun activeIds(): List<String> {
        val now = System.currentTimeMillis()
        return ids().filter { now >= prefs.getLong("prompt:$it", Long.MAX_VALUE) &&
            now < prefs.getLong("deadline:$it", 0) }
    }

    fun schedule(id: String) {
        if (!enabled || !PremiumAccess(context).unlocked || !canPrompt()) return
        cancel(id)
        val promptAt = System.currentTimeMillis() + delay * 60_000L
        val deadline = promptAt + 60_000L
        prefs.edit().putLong("prompt:$id", promptAt).putLong("deadline:$id", deadline).apply()
        if (!scheduleExact(id, PROMPT, promptAt) || !scheduleExact(id, RING, deadline)) cancel(id)
    }

    fun restore() {
        val now = System.currentTimeMillis()
        for (id in ids()) {
            val deadline = prefs.getLong("deadline:$id", 0)
            if (!enabled || !PremiumAccess(context).unlocked || deadline <= now || !canPrompt()) {
                cancel(id)
                continue
            }
            val at = maxOf(now + 1000L, prefs.getLong("prompt:$id", now))
            if (at < deadline) scheduleExact(id, PROMPT, at)
            if (!scheduleExact(id, RING, deadline)) cancel(id)
        }
    }

    fun isChannelBlocked() =
        notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE

    fun channelSettingsIntent() = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL)

    fun canPrompt(): Boolean {
        if (!notifications.areNotificationsEnabled() ||
            isChannelBlocked() ||
            !Alarms(context).canSchedule()) return false
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun confirm(id: String) = cancel(id)

    fun handle(action: String?, id: String, deadline: Long) {
        if (deadline == 0L || deadline != prefs.getLong("deadline:$id", 0)) return
        if (action == CONFIRM) {
            if (System.currentTimeMillis() < deadline) confirm(id)
            return
        }
        if (!enabled || !PremiumAccess(context).unlocked || !canPrompt()) {
            cancel(id)
            return
        }
        val alarm = Alarms(context).all().firstOrNull { it.id == id }
        if (alarm == null) {
            cancel(id)
            return
        }
        when (action) {
            PROMPT -> {
                if (System.currentTimeMillis() >= deadline) return
                notifications.createNotificationChannel(NotificationChannel(
                    CHANNEL, "Uyanıklık kontrolü", NotificationManager.IMPORTANCE_HIGH))
                val open = PendingIntent.getActivity(context, 1,
                    Intent(context, MainActivity::class.java).putExtra("wakeTab", true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_wake_check)
                    .setContentTitle("Uyanık mısın?")
                    .setContentText("Onay vermezsen alarm yeniden çalacak.")
                    .setContentIntent(open)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setTimeoutAfter((deadline - System.currentTimeMillis()).coerceAtLeast(1))
                    .addAction(Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_agenda),
                        "Uyanığım", operation(id, CONFIRM, deadline)).build())
                    .build()
                notifications.notify(id, NOTIFICATION_ID, notification)
            }
            RING -> {
                cancel(id)
                context.startForegroundService(alarm.ringIntent(context))
            }
        }
    }

    private fun ids() = prefs.all.keys.filter { it.startsWith("deadline:") }.map { it.removePrefix("deadline:") }

    private fun cancel(id: String) {
        prefs.edit().remove("prompt:$id").remove("deadline:$id").apply()
        alarms.cancel(operation(id, PROMPT))
        alarms.cancel(operation(id, RING))
        notifications.cancel(id, NOTIFICATION_ID)
    }

    private fun scheduleExact(id: String, action: String, at: Long): Boolean {
        val deadline = prefs.getLong("deadline:$id", 0)
        return try {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), operation(id, action, deadline))
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun operation(id: String, action: String, deadline: Long = 0) = PendingIntent.getBroadcast(
        context, 0, Intent(context, WakeCheckReceiver::class.java).setAction(action)
            .setData(Uri.fromParts("beng-wake", id, null)).putExtra("deadline", deadline),
        PendingIntent.FLAG_IMMUTABLE or if (deadline > 0) PendingIntent.FLAG_UPDATE_CURRENT else 0
    )

    private companion object {
        const val CHANNEL = "wake_check"
        const val NOTIFICATION_ID = 2
        const val PROMPT = "com.caglaryildiz.alarm.WAKE_PROMPT"
        const val CONFIRM = "com.caglaryildiz.alarm.WAKE_CONFIRM"
        const val RING = "com.caglaryildiz.alarm.WAKE_RING"
    }
}

class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.schemeSpecificPart ?: return
        WakeCheck(context).handle(intent.action, id, intent.getLongExtra("deadline", 0))
    }
}
