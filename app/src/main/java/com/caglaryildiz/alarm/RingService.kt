package com.caglaryildiz.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import java.util.UUID

internal data class Ring(val id: String, val math: Boolean, val token: String = UUID.randomUUID().toString())

class RingService : Service() {
    private val queue = ArrayDeque<Ring>()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val timeout = Runnable { advance() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DISMISS, SNOOZE -> {
                val ring = active ?: return START_NOT_STICKY
                if (intent.getStringExtra("token") != ring.token) return START_NOT_STICKY
                if (intent.action == SNOOZE) {
                    val alarms = Alarms(this)
                    alarms.all().firstOrNull { it.id == ring.id }?.let {
                        alarms.save(it.copy(snoozeAt = System.currentTimeMillis() + 300_000))
                    }
                }
                advance()
            }
            else -> {
                val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY
                val ring = Ring(id, intent.getBooleanExtra("math", true))
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
        val screen = PendingIntent.getActivity(this, 0,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm çalıyor")
            .setContentText("Alarmı açmak için dokun")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
        play()
        handler.postDelayed(timeout, 10 * 60_000L)
        notifyScreen()
    }

    private fun play() {
        val selected = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        for (uri in listOfNotNull(selected, Settings.System.DEFAULT_NOTIFICATION_URI).distinct()) {
            val media = MediaPlayer()
            try {
                media.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                media.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                media.setDataSource(this, uri)
                media.isLooping = true
                media.prepare()
                media.start()
                player = media
                return
            } catch (error: Exception) {
                android.util.Log.w("RingService", "Could not play alarm tone", error)
                media.release()
            }
        }
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
        internal val listeners = mutableSetOf<() -> Unit>()
        internal const val DISMISS = "com.caglaryildiz.alarm.DISMISS"
        internal const val SNOOZE = "com.caglaryildiz.alarm.SNOOZE_RING"
        internal var active: Ring? = null
            private set
    }
}
