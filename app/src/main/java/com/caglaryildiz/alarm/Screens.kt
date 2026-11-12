package com.caglaryildiz.alarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

internal val weekdays = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
internal val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private val midnight = 0xFF0B1021.toInt()
internal val mutedColor = 0xFFC0C4E1.toInt()
private val accentColor = 0xFFC5BAFF.toInt()
internal val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val nextAlarmFormat = DateTimeFormatter.ofPattern("EEEE · HH:mm", Locale.forLanguageTag("tr-TR"))

abstract class AlarmScreen : Activity() {
    protected lateinit var panel: LinearLayout
    private var scroll: ScrollView? = null

    protected fun layout(title: String? = null) {
        val previousScroll = scroll?.scrollY ?: 0
        panel = column()
        val container = ScrollView(this).apply {
            background = Starfield()
            isFillViewport = true
            clipToPadding = false
            addView(panel)
            setOnApplyWindowInsetsListener { _, insets ->
                applyScreenInsets(insets)
                insets
            }
        }
        scroll = container
        setContentView(container)
        container.requestApplyInsets()
        container.post { container.scrollTo(0, previousScroll) }
        if (title == null) return
        label(title, 32f, bold = true).apply {
            accessibilityHeading()
            if (title == getString(R.string.app_name)) {
                setTextColor(accentColor)
                letterSpacing = .12f
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyScreenInsets(insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 35) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            panel.setPadding(dp(20) + bars.left, dp(20) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom)
        } else {
            panel.setPadding(dp(20), dp(20), dp(20), dp(24))
        }
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    protected fun View.accessibilityHeading() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    protected fun card() = column().apply {
        background = glass(0xDD242F58.toInt(), 0xD137204F.toInt(), 20)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        panel.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun glass(from: Int, to: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0x748C8CD8)
        }

    protected fun timeLabel(text: String, size: Float, parent: LinearLayout = panel, color: Int = Color.WHITE) =
        label(text, size, parent, color = color).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = .055f
        }

    protected fun styleInput(input: EditText) {
        input.background = glass(0xDA11192F.toInt(), 0xD6231C37.toInt(), 12)
        input.setPadding(dp(12), dp(9), dp(12), dp(9))
        input.minHeight = dp(48)
    }

    protected fun Switch.stylePlanetToggle() {
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        trackTintList = ColorStateList(checkedStates, intArrayOf(0xFF6969BD.toInt(), 0xFF494264.toInt()))
        val lit = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFF4FFFF.toInt(), 0xFF91D7FF.toInt(), 0xFF7566EA.toInt())).apply {
            shape = GradientDrawable.OVAL
            setSize(dp(22), dp(22))
            setStroke(dp(1), 0xFFC5ECFF.toInt())
        }
        val dim = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFD3CCE9.toInt(), 0xFF74658F.toInt())).apply {
            shape = GradientDrawable.OVAL
            setSize(dp(22), dp(22))
        }
        thumbDrawable = StateListDrawable().apply {
            addState(checkedStates[0], lit)
            addState(checkedStates[1], dim)
        }
    }

    protected fun label(
        text: String,
        size: Float = 16f,
        parent: LinearLayout = panel,
        bold: Boolean = false,
        color: Int = Color.WHITE
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(5), 0, dp(5))
        parent.addView(this)
    }

    protected fun button(
        text: String,
        parent: LinearLayout = panel,
        primary: Boolean = true,
        destructive: Boolean = false,
        action: () -> Unit
    ) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        val textColor = when {
            destructive -> 0xFFFFC4D1.toInt()
            primary -> midnight
            else -> accentColor
        }
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(mutedColor, textColor)
        ))
        background = RippleDrawable(
            ColorStateList.valueOf(0x35FFFFFF),
            when {
                destructive -> glass(0xE65E213E.toInt(), 0xE6461B36.toInt(), 18)
                primary -> glass(0xFF9BDFFF.toInt(), 0xFFC3A0FC.toInt(), 18)
                else -> glass(0xA6253152.toInt(), 0xA42F2149.toInt(), 18)
            },
            null
        )
        minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}

class MainActivity : AlarmScreen() {
    private val alarms by lazy { Alarms(this) }
    private val onRingChanged: () -> Unit = ::render

    override fun onStart() {
        super.onStart()
        RingService.listeners.add(onRingChanged)
    }

    override fun onStop() {
        RingService.listeners.remove(onRingChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        alarms.syncAll()
        render()
    }

    private fun render() {
        layout(getString(R.string.app_name))
        RingService.active?.let { ring ->
            val ringing = card()
            label("Alarm çalıyor", 20f, ringing, bold = true).accessibilityHeading()
            if (ring.label.isNotBlank()) label(ring.label, parent = ringing)
            button("Çalan alarmı aç", ringing) {
                startActivity(Intent(this, RingActivity::class.java))
            }
        }
        val all = alarms.all().sortedBy { it.time }
        showNextAlarm(all)
        button("+  Alarm ekle") { openEditor(null) }
        showMissingPermissions()
        label("Alarmlarım", 22f, bold = true).apply {
            setPadding(0, dp(24), 0, dp(2))
            accessibilityHeading()
        }
        if (all.isEmpty()) label("Henüz alarm eklenmedi.", parent = card(), color = mutedColor)
        all.forEach(::showAlarm)
    }

    private fun showNextAlarm(all: List<Alarm>) {
        val now = ZonedDateTime.now()
        val next = all.mapNotNull { alarm ->
            listOfNotNull(
                alarm.snoozeAt.takeIf { it > now.toInstant().toEpochMilli() },
                if (alarm.enabled) alarm.nextAfter(now) else null
            ).minOrNull()
        }.minOrNull()
        val hero = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(row)
        val details = column()
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        label("Sonraki alarm", 15f, details, color = mutedColor)
        val summary = when {
            next == null -> "Alarm planlanmadı"
            !alarms.canSchedule() -> "Alarm izni gerekli"
            else -> Instant.ofEpochMilli(next).atZone(now.zone).format(nextAlarmFormat)
        }
        label(summary, 22f, details, bold = true)
        row.addView(AstralPlanet(this), LinearLayout.LayoutParams(dp(112), dp(96)).apply { marginStart = dp(4) })
    }

    private fun showMissingPermissions() {
        val notifications = getSystemService(NotificationManager::class.java)
        val exactAlarmMissing = !alarms.canSchedule()
        val notificationsMissing = !notifications.areNotificationsEnabled()
        val fullScreenMissing = Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()
        if (!exactAlarmMissing && !notificationsMissing && !fullScreenMissing) return

        val permissions = card()
        label("Eksik izinler", 18f, permissions, bold = true).apply { accessibilityHeading() }
        if (exactAlarmMissing) {
            label("Alarmların tam saatinde çalması için zamanlama izni gerekli.", 14f, permissions, color = mutedColor)
            button("Alarm iznini aç", permissions, primary = false) {
                openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        if (notificationsMissing) {
            label("Alarmı bildirimden açabilmek için bildirim izni gerekli.", 14f, permissions, color = mutedColor)
            button("Bildirimlere izin ver", permissions, primary = false) { requestNotifications() }
        }
        if (fullScreenMissing) {
            label("Alarm ekranının kilitliyken açılması için tam ekran izni gerekli.", 14f, permissions, color = mutedColor)
            button("Kilit ekranı iznini aç", permissions, primary = false) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
    }

    private fun requestNotifications() {
        val preferences = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!preferences.getBoolean("notificationRequested", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            preferences.edit().putBoolean("notificationRequested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) render()
    }

    private fun showAlarm(alarm: Alarm) {
        val time = alarm.time.format(clockFormat)
        val repeat = when {
            alarm.days.isEmpty() -> alarm.date?.format(dateFormat) ?: "Bir kez"
            alarm.days.size == 7 -> "Her gün"
            else -> alarm.days.sorted().joinToString(" · ") { weekdays[it - 1].take(3) }
        }
        val alarmCard = card().apply {
            isFocusable = true
            setOnClickListener { openEditor(alarm) }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        alarmCard.addView(row)
        val details = column()
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        timeLabel(time, 38f, details, color = if (alarm.enabled) Color.WHITE else mutedColor)
        label(repeat, 15f, details, color = mutedColor)
        if (alarm.label.isNotBlank()) label(alarm.label, 16f, details, bold = true)
        if (alarm.task != AlarmTask.NONE) label(alarm.task.label, 14f, details, color = mutedColor)
        val dateExpired = alarm.date != null && alarm.nextAfter(ZonedDateTime.now()) == null
        if (dateExpired && alarm.enabled) label("Alarm tarihi geçti", 14f, details, color = mutedColor)
        row.addView(Switch(this).apply {
            contentDescription = "$time alarmı"
            stylePlanetToggle()
            minWidth = dp(48)
            minHeight = dp(48)
            isChecked = alarm.enabled && !dateExpired
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && dateExpired) {
                    render()
                    openEditor(alarm, enableOnSave = true)
                } else {
                    alarms.save(alarm.copy(enabled = enabled, snoozeAt = 0))
                    render()
                }
            }
        })
        if (alarm.snoozeAt > 0) {
            button("Ertelemeyi iptal et", alarmCard, primary = false) {
                alarms.save(alarm.copy(snoozeAt = 0))
                render()
            }
        }
    }

    private fun openSettings(action: String) {
        startActivity(Intent(action, Uri.fromParts("package", packageName, null)))
    }

    private fun openEditor(alarm: Alarm?, enableOnSave: Boolean = false) {
        startActivity(Intent(this, AlarmEditorActivity::class.java)
            .putExtra("id", alarm?.id)
            .putExtra("enableOnSave", enableOnSave))
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
        layout(getString(R.string.app_name))
        val header = card()
        label("Alarm çalıyor", 22f, header, bold = true).gravity = Gravity.CENTER
        if (ring.label.isNotBlank()) label(ring.label, 16f, header, color = mutedColor).gravity = Gravity.CENTER
        timeLabel(ring.startedAt.format(clockFormat), 60f, header).gravity = Gravity.CENTER
        header.addView(AstralPlanet(this), LinearLayout.LayoutParams(-1, dp(112)))
        val controls = card()
        if (ring.math) {
            label("$first + $second = ?", 32f, controls, bold = true).gravity = Gravity.CENTER
            val answer = EditText(this).apply {
                hint = "Cevap"
                inputType = InputType.TYPE_CLASS_NUMBER
                styleInput(this)
                controls.addView(this)
            }
            button("Alarmı kapat", controls) {
                if (answer.text.toString().toIntOrNull() == first + second) {
                    send(RingService.DISMISS, ring)
                } else {
                    answer.error = "Tekrar dene"
                }
            }
        } else {
            button("Alarmı kapat", controls) { send(RingService.DISMISS, ring) }
        }
        if (Alarms(this).canSchedule()) button("5 dakika ertele", controls, primary = false) { send(RingService.SNOOZE, ring) }
    }

    private fun send(action: String, ring: Ring) {
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("token", ring.token))
    }
}
