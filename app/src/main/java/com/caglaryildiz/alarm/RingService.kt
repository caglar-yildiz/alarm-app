package com.caglaryildiz.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalTime
import java.util.UUID

internal data class Ring(
    val id: String,
    var math: MathMission?,
    var extra: ExtraMission?,
    val label: String,
    val tone: Uri?,
    val token: String = UUID.randomUUID().toString(),
    val startedAt: LocalTime = LocalTime.now()
)

class RingService : Service() {
    private val queue = ArrayDeque<Ring>()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val timeout = Runnable { advance() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DISMISS, SNOOZE, RESTORE_NOTIFICATION -> {
                val ring = active
                if (ring == null) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (intent.getStringExtra("token") != ring.token) return START_NOT_STICKY
                if (intent.action == RESTORE_NOTIFICATION) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, alarmNotification(ring, fullScreen = false))
                    return START_NOT_STICKY
                }
                if (intent.action == DISMISS && (ring.math?.question != null || ring.extra?.complete == false)) return START_NOT_STICKY
                if (intent.action == SNOOZE) {
                    val alarms = Alarms(this)
                    alarms.all().firstOrNull { it.id == ring.id }?.let {
                        alarms.save(it.copy(snoozeAt = System.currentTimeMillis() + 300_000))
                    }
                }
                if (intent.action == DISMISS) WakeCheck(this).schedule(ring.id)
                advance()
            }
            else -> {
                val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY
                val savedTask = AlarmTask.entries.firstOrNull { it.name == intent.getStringExtra("task") }
                    ?: AlarmTask.NONE
                val qrCode = intent.getStringExtra("qrCode").orEmpty()
                val needsFallback = (savedTask.premium && !PremiumAccess(this).unlocked) ||
                    (savedTask == AlarmTask.SHAKE && !ExtraMission.hasAccelerometer(this)) ||
                    (savedTask == AlarmTask.QR && (qrCode.isBlank() ||
                        checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED))
                val task = if (needsFallback) AlarmTask.MATH else savedTask
                val math = if (task == AlarmTask.MATH) {
                    MathMission(MathSettings.from(intent.getStringExtra("mathDifficulty"),
                        intent.getIntExtra("mathCount", 1)))
                } else null
                val extra = when (task) {
                    AlarmTask.MEMORY, AlarmTask.TYPING, AlarmTask.SHAKE, AlarmTask.QR, AlarmTask.SHAPES -> ExtraMission(task, qrCode)
                    else -> null
                }
                val ring = Ring(id, math, extra, intent.getStringExtra("label").orEmpty(),
                    intent.getStringExtra("tone")?.let(Uri::parse))
                if (active == null) show(ring) else queue.addLast(ring)
            }
        }
        return START_NOT_STICKY
    }

    private fun show(ring: Ring) {
        active = ring
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Çalan alarmlar",
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val notification = alarmNotification(ring, fullScreen = true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        play(ring.tone)
        handler.postDelayed(timeout, 10 * 60_000L)
        notifyScreen()
    }

    private fun alarmNotification(ring: Ring, fullScreen: Boolean): Notification {
        val screen = PendingIntent.getActivity(this, 0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val onSwipe = PendingIntent.getService(
            this, 0,
            Intent(this, RingService::class.java).setAction(RESTORE_NOTIFICATION)
                .setData(Uri.fromParts("beng-ring", ring.token, null))
                .putExtra("token", ring.token),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openAlarm = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
            "Alarmı aç",
            screen
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(ring.label.ifBlank { "Alarm çalıyor" })
            .setContentText("Kapatmak veya ertelemek için alarmı aç")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(screen)
            .setDeleteIntent(onSwipe)
            .addAction(openAlarm)
            .apply {
                if (fullScreen) setFullScreenIntent(screen, true)
                if (Build.VERSION.SDK_INT >= 31) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
            .apply { flags = flags or Notification.FLAG_NO_CLEAR }
    }

    private fun play(tone: Uri?) {
        player = playAlarmTone(this, tone, keepAwake = true)
    }

    private fun advance() {
        handler.removeCallbacks(timeout)
        player?.release()
        player = null
        val next = queue.removeFirstOrNull()
        if (next != null) {
            show(next)
        } else {
            active = null
            notifyScreen()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyScreen() = listeners.toList().forEach { it() }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.release()
        if (active != null) {
            active = null
            notifyScreen()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val RESTORE_NOTIFICATION = "com.caglaryildiz.alarm.RESTORE_RING_NOTIFICATION"
        internal val listeners = mutableSetOf<() -> Unit>()
        internal const val DISMISS = "com.caglaryildiz.alarm.DISMISS"
        internal const val SNOOZE = "com.caglaryildiz.alarm.SNOOZE_RING"
        internal var active: Ring? = null
            private set
    }
}
