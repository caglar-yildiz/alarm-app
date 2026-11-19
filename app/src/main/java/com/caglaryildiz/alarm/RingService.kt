package com.caglaryildiz.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalTime
import java.util.UUID
import kotlin.math.ceil

internal data class Ring(
    val id: String,
    var math: MathMission?,
    var extra: ExtraMission?,
    val label: String,
    val tone: Uri?,
    val crescendo: Boolean = true,
    val token: String = UUID.randomUUID().toString(),
    val startedAt: LocalTime = LocalTime.now()
)

class RingService : Service() {
    private val queue = ArrayDeque<Ring>()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val timeout = Runnable { advance() }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var originalAlarmVolume = -1
    private var lockedAlarmVolume = -1
    private var volumeReceiverRegistered = false
    private val volumeGuard = object : Runnable {
        override fun run() {
            enforceAlarmVolume()
            handler.postDelayed(this, VOLUME_GUARD_INTERVAL)
        }
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_ALARM) == AudioManager.STREAM_ALARM) {
                enforceAlarmVolume()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

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
                val reminderId = ring.id.removePrefix(Reminders.REMINDER_PREFIX).takeIf { it != ring.id }
                if (intent.action == SNOOZE) {
                    if (reminderId != null) Reminders(this).snooze(reminderId)
                    val alarms = Alarms(this)
                    alarms.all().firstOrNull { it.id == ring.id }?.let {
                        alarms.save(it.copy(snoozeAt = System.currentTimeMillis() + 300_000))
                    }
                }
                // The wake check follows real alarms only, not reminders that ring.
                if (intent.action == DISMISS && reminderId == null) WakeCheck(this).schedule(ring.id)
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
                    AlarmTask.MEMORY, AlarmTask.TYPING, AlarmTask.SHAKE, AlarmTask.QR, AlarmTask.SHAPES -> ExtraMission.create(this, task, qrCode)
                    else -> null
                }
                val rawTone = intent.getStringExtra("tone")?.let(Uri::parse)
                val builtIn = BuiltInTones.fromUri(rawTone)
                val tone = if (builtIn?.premium == true && !PremiumAccess(this).unlocked) {
                    BuiltInTones.toUri(BuiltInTones.list.first())
                } else {
                    rawTone
                }
                val crescendo = intent.getBooleanExtra("crescendo", true)
                val ring = Ring(id, math, extra, intent.getStringExtra("label").orEmpty(), tone, crescendo)
                if (active == null) show(ring) else queue.addLast(ring)
            }
        }
        return START_NOT_STICKY
    }

    private fun show(ring: Ring) {
        active = ring
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_ringing),
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
        lockAlarmVolume()
        play(ring.tone, ring.crescendo)
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
            getString(R.string.notification_open_alarm),
            screen
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(ring.label.ifBlank { getString(R.string.alarm_ringing) })
            .setContentText(getString(R.string.notification_ringing_text))
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

    private var crescendoRunnable: Runnable? = null

    private fun play(tone: Uri?, crescendo: Boolean) {
        stopCrescendo()
        player = playAlarmTone(this, tone, keepAwake = true)
        if (crescendo) {
            startCrescendo()
        }
    }

    private fun startCrescendo() {
        val p = player ?: return
        var currentVol = 0.08f
        p.setVolume(currentVol, currentVol)
        val step = (1.0f - 0.08f) / 30f
        val runnable = object : Runnable {
            override fun run() {
                val currentPlayer = player ?: return
                currentVol = (currentVol + step).coerceAtMost(1.0f)
                runCatching { currentPlayer.setVolume(currentVol, currentVol) }
                if (currentVol < 1.0f) {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        crescendoRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    /**
     * While an alarm rings, the alarm stream must not be turned down or muted from the
     * volume keys, the volume panel or another app. The level at ring start (raised to a
     * safe minimum) is held until the last queued alarm ends, then the user's level returns.
     */
    private fun lockAlarmVolume() {
        if (originalAlarmVolume < 0) {
            originalAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val floor = ceil(max * MIN_ALARM_VOLUME_RATIO).toInt().coerceIn(1, max)
        lockedAlarmVolume = maxOf(originalAlarmVolume, floor)
        enforceAlarmVolume()
        if (!volumeReceiverRegistered) {
            val filter = IntentFilter(VOLUME_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(volumeReceiver, filter)
            }
            volumeReceiverRegistered = true
        }
        handler.removeCallbacks(volumeGuard)
        handler.postDelayed(volumeGuard, VOLUME_GUARD_INTERVAL)
    }

    private fun enforceAlarmVolume() {
        if (lockedAlarmVolume < 0) return
        runCatching {
            if (audio.isStreamMute(AudioManager.STREAM_ALARM)) {
                audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
            }
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < lockedAlarmVolume) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, lockedAlarmVolume, 0)
            }
        }
    }

    private fun unlockAlarmVolume() {
        handler.removeCallbacks(volumeGuard)
        if (volumeReceiverRegistered) {
            runCatching { unregisterReceiver(volumeReceiver) }
            volumeReceiverRegistered = false
        }
        if (originalAlarmVolume >= 0) {
            val restore = originalAlarmVolume
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, restore, 0) }
        }
        originalAlarmVolume = -1
        lockedAlarmVolume = -1
    }

    private fun stopCrescendo() {
        crescendoRunnable?.let { handler.removeCallbacks(it) }
        crescendoRunnable = null
    }

    private fun advance() {
        handler.removeCallbacks(timeout)
        stopCrescendo()
        player?.release()
        player = null
        val next = queue.removeFirstOrNull()
        if (next != null) {
            show(next)
        } else {
            active = null
            unlockAlarmVolume()
            notifyScreen()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyScreen() = listeners.toList().forEach { it() }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopCrescendo()
        player?.release()
        unlockAlarmVolume()
        if (active != null) {
            active = null
            notifyScreen()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val VOLUME_GUARD_INTERVAL = 400L
        private const val MIN_ALARM_VOLUME_RATIO = .6f
        private const val RESTORE_NOTIFICATION = "com.caglaryildiz.alarm.RESTORE_RING_NOTIFICATION"
        internal val listeners = mutableSetOf<() -> Unit>()
        internal const val DISMISS = "com.caglaryildiz.alarm.DISMISS"
        internal const val SNOOZE = "com.caglaryildiz.alarm.SNOOZE_RING"
        internal var active: Ring? = null
            private set
    }
}
