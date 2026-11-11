package com.caglaryildiz.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val weekdays = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

abstract class AlarmScreen : Activity() {
    protected lateinit var panel: LinearLayout

    protected fun layout(title: String) {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        setContentView(ScrollView(this).apply {
            clipToPadding = false
            addView(panel)
        })
        label(title, 36f).setTextColor(Color.rgb(185, 245, 107))
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    protected fun label(text: String, size: Float = 16f) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(12), 0, dp(8))
        panel.addView(this)
    }

    protected fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        panel.addView(this)
    }
}

class MainActivity : AlarmScreen() {
    private val alarms by lazy { Alarms(this) }

    override fun onResume() {
        super.onResume()
        alarms.syncAll()
        render()
    }

    private fun render() {
        layout("Uyan")
        if (RingService.active != null) {
            button("Çalan alarmı aç") { startActivity(Intent(this, RingActivity::class.java)) }
        }
        if (!alarms.canSchedule()) {
            label("Alarmların saatinde çalması için Alarmlar ve hatırlatıcılar izni gerekiyor.")
            button("Alarm iznini aç") { openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
        }
        val notifications = getSystemService(NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) {
            button("Alarm bildirimlerini aç") {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !getPreferences(MODE_PRIVATE).getBoolean("notificationRequested", false)) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("notificationRequested", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()) {
            button("Kilit ekranında alarmı göster") {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
        button("+ Alarm ekle") { pickTime(null) }
        val all = alarms.all().sortedBy { it.time }
        if (all.isEmpty()) label("Henüz alarm eklenmedi.")
        all.forEach { alarm ->
            val time = alarm.time.format(clockFormat)
            val repeat = if (alarm.days.isEmpty()) "Bir kez"
                else alarm.days.sorted().joinToString(", ") { weekdays[it - 1] }
            panel.addView(Switch(this).apply {
                text = "$time · $repeat"
                textSize = 18f
                setPadding(0, dp(12), 0, dp(4))
                isChecked = alarm.enabled
                setOnCheckedChangeListener { _, enabled ->
                    alarms.save(alarm.copy(enabled = enabled, snoozeAt = 0))
                    render()
                }
            })
            if (alarm.snoozeAt > 0) {
                label("5 dakika ertelendi")
                button("Ertelemeyi iptal et") {
                    alarms.save(alarm.copy(snoozeAt = 0))
                    render()
                }
            }
            button("$time alarmını düzenle") { pickTime(alarm) }
        }
    }

    private fun openSettings(action: String) {
        startActivity(Intent(action, Uri.fromParts("package", packageName, null)))
    }

    private fun pickTime(alarm: Alarm?) {
        val time = alarm?.time ?: LocalTime.now().plusMinutes(1)
        TimePickerDialog(this, { _, hour, minute ->
            editOptions(alarm, LocalTime.of(hour, minute))
        }, time.hour, time.minute, true).show()
    }

    private fun editOptions(alarm: Alarm?, time: LocalTime) {
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val days = weekdays.mapIndexed { index, name ->
            CheckBox(this).apply {
                text = name
                isChecked = alarm?.days?.contains(index + 1) == true
                options.addView(this)
            }
        }
        val math = CheckBox(this).apply {
            text = "Kapatmak için matematik sorusu"
            isChecked = alarm?.math ?: true
            options.addView(this)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("${time.format(clockFormat)} · Tekrar günleri")
            .setView(ScrollView(this).apply { addView(options) })
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val chosen = days.mapIndexedNotNull { index, box -> (index + 1).takeIf { box.isChecked } }.toSet()
                alarms.save((alarm ?: Alarm(time = time)).copy(time = time, days = chosen,
                    enabled = true, math = math.isChecked, snoozeAt = 0))
                render()
            }
        if (alarm != null) builder.setNeutralButton("Sil") { _, _ ->
            alarms.delete(alarm)
            render()
        }
        builder.show()
    }
}

class RingActivity : AlarmScreen() {
    private var token: String? = null
    private var first = 0
    private var second = 0
    private val onRingChanged: () -> Unit = ::render

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        token = savedInstanceState?.getString("token")
        first = savedInstanceState?.getInt("first") ?: 0
        second = savedInstanceState?.getInt("second") ?: 0
    }

    override fun onStart() {
        super.onStart()
        RingService.listeners.add(onRingChanged)
        render()
    }

    override fun onStop() {
        RingService.listeners.remove(onRingChanged)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("token", token)
        outState.putInt("first", first)
        outState.putInt("second", second)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val ring = RingService.active
        if (ring == null) {
            finish()
            return
        }
        if (token != ring.token) {
            token = ring.token
            first = Random.nextInt(12, 50)
            second = Random.nextInt(8, 40)
        }
        layout("Alarm çalıyor")
        if (ring.math) {
            label("$first + $second = ?", 28f)
            val answer = EditText(this).apply {
                hint = "Cevap"
                inputType = InputType.TYPE_CLASS_NUMBER
                panel.addView(this)
            }
            button("Alarmı kapat") {
                if (answer.text.toString().toIntOrNull() == first + second) {
                    send(RingService.DISMISS, ring)
                } else {
                    answer.error = "Tekrar dene"
                }
            }
        } else {
            button("Alarmı kapat") { send(RingService.DISMISS, ring) }
        }
        if (Alarms(this).canSchedule()) button("5 dakika ertele") { send(RingService.SNOOZE, ring) }
    }

    private fun send(action: String, ring: Ring) {
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("token", ring.token))
    }
}
