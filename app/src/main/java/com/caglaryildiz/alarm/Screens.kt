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
import android.text.TextUtils
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

    protected fun layout(title: String? = null, footer: View? = null) {
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
        if (footer == null) {
            setContentView(container)
        } else {
            val root = column().apply {
                setBackgroundColor(midnight)
                addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(footer)
            }
            root.setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT >= 35) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    footer.setPadding(bars.left, 0, bars.right, bars.bottom)
                }
                insets
            }
            setContentView(root)
        }
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

class MainActivity : PremiumScreen() {
    private enum class Tab(val title: String, val icon: Int) {
        ALARMS("Alarmlar", R.drawable.ic_beng_launcher),
        WAKE("Uyanıklık", android.R.drawable.ic_menu_day),
        PREMIUM("Premium", android.R.drawable.star_on)
    }

    private var tab = Tab.ALARMS
    private val wake by lazy { WakeCheck(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = if (intent.getBooleanExtra("wakeTab", false)) Tab.WAKE
            else savedInstanceState?.getString("tab")?.let(Tab::valueOf) ?: Tab.ALARMS
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("wakeTab", false)) {
            tab = Tab.WAKE
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPremiumChanged() {
        if (tab != Tab.ALARMS) render()
    }

    private fun navigation(): LinearLayout = LinearLayout(this).apply {
        setBackgroundColor(0xFF161B34.toInt())
        for (item in Tab.entries) {
            val selected = item == tab
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(64)
                isSelected = selected
                isFocusable = true
                contentDescription = item.title
                if (selected) setBackgroundColor(0xFF29264C.toInt())
                addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageResource(item.icon)
                    if (item != Tab.ALARMS) setColorFilter(if (selected) accentColor else mutedColor)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
                label(item.title, 12f, this, bold = selected, color = if (selected) accentColor else mutedColor)
                    .importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnClickListener {
                    tab = item
                    render()
                    if (tab == Tab.PREMIUM) refreshPremium()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun showWakeSettings() {
        val settings = card()
        label("Alarmı kapattıktan sonra uyanık olduğunu onayla. 60 saniye içinde onay vermezsen alarm yeniden çalar.",
            16f, settings)
        for (id in wake.activeIds()) {
            val alarm = alarms.all().firstOrNull { it.id == id } ?: continue
            button("Uyanığım · ${alarm.label.ifBlank { alarm.time.format(clockFormat) }}", settings) {
                wake.confirm(id)
                render()
            }
        }
        settings.addView(Switch(this).apply {
            text = "Uyanıklık kontrolü"
            minHeight = dp(48)
            isChecked = wake.enabled
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && !premiumAccess.unlocked) {
                    tab = Tab.PREMIUM
                    render()
                    refreshPremium()
                } else {
                    wake.enabled = enabled
                    render()
                }
            }
        })
        button("Kontrol gecikmesi · ${wake.delay} dk", settings, primary = false) {
            val delays = listOf(1, 3, 5, 10)
            android.app.AlertDialog.Builder(this, R.style.AstralDialogTheme)
                .setTitle("Kontrol gecikmesi")
                .setSingleChoiceItems(delays.map { "$it dakika" }.toTypedArray(), delays.indexOf(wake.delay)) { dialog, index ->
                    wake.delay = delays[index]
                    dialog.dismiss()
                    render()
                }.setNegativeButton("Vazgeç", null).show()
        }
        if (!wake.canPrompt()) showMissingPermissions()
    }
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
        refreshPremium()
    }

    private fun render() {
        layout(if (tab == Tab.ALARMS) getString(R.string.app_name) else tab.title, navigation())
        RingService.active?.let { ring ->
            val ringing = card()
            label("Alarm çalıyor", 20f, ringing, bold = true).accessibilityHeading()
            if (ring.label.isNotBlank()) label(ring.label, parent = ringing)
            button("Çalan alarmı aç", ringing) {
                startActivity(Intent(this, RingActivity::class.java))
            }
        }
        when (tab) {
            Tab.PREMIUM -> { showPremium(); return }
            Tab.WAKE -> { showWakeSettings(); return }
            Tab.ALARMS -> Unit
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
            alarm.days == setOf(6, 7) -> "Hafta sonu"
            alarm.days == (1..5).toSet() -> "Hafta içi"
            else -> "${alarm.days.size} gün"
        }
        val alarmCard = card().apply {
            setPadding(dp(14), dp(6), dp(14), dp(9))
            isFocusable = true
            setOnClickListener { openEditor(alarm) }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        alarmCard.addView(row)
        timeLabel(time, 35f, row, color = if (alarm.enabled) Color.WHITE else mutedColor).apply {
            setPadding(0, 0, 0, 0)
            setSingleLine()
            if (alarm.label.isBlank()) layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        if (alarm.label.isNotBlank()) {
            label(alarm.label, 16f, row, bold = true, color = if (alarm.enabled) Color.WHITE else mutedColor).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(6)
                }
            }
        }
        val dateExpired = alarm.date != null && alarm.nextAfter(ZonedDateTime.now()) == null
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
        val tags = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        alarmCard.addView(tags)
        addAlarmTag(tags, repeat, description = alarm.days.takeIf { it.isNotEmpty() }
            ?.sorted()?.joinToString(", ") { weekdays[it - 1] })
        if (alarm.task != AlarmTask.NONE) addAlarmTag(tags, alarm.task.label)
        if (dateExpired && alarm.enabled) label("Alarm tarihi geçti", 13f, alarmCard, color = mutedColor)
        if (alarm.snoozeAt > 0) {
            button("Ertelemeyi iptal et", alarmCard, primary = false) {
                alarms.save(alarm.copy(snoozeAt = 0))
                render()
            }
        }
    }

    private fun addAlarmTag(
        row: LinearLayout,
        text: String,
        description: String? = null
    ) {
        val tag = TextView(this).apply {
            this.text = text
            if (description != null) contentDescription = description
            textSize = 12f
            setTextColor(0xFFE7DFFF.toInt())
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(0x604E407B)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0x668C8CD8)
            }
        }
        tag.maxWidth = (resources.displayMetrics.widthPixels - dp(78)).coerceAtLeast(0) / 2
        row.addView(tag, LinearLayout.LayoutParams(-2, -2).apply {
            if (row.childCount > 0) marginStart = dp(5)
        })
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
    private val sensorManager by lazy { getSystemService(android.hardware.SensorManager::class.java) }
    private var shakeListener: android.hardware.SensorEventListener? = null
    private val memoryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var failedQrToken: String? = null
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
    }

    override fun onStart() {
        super.onStart()
        RingService.listeners.add(onRingChanged)
        render()
    }

    override fun onStop() {
        RingService.listeners.remove(onRingChanged)
        clearMissionCallbacks()
        super.onStop()
    }

    private fun render() {
        clearMissionCallbacks()
        val ring = RingService.active
        if (ring == null) {
            finish()
            return
        }
        layout(getString(R.string.app_name))
        val header = card()
        label("Alarm çalıyor", 22f, header, bold = true).gravity = Gravity.CENTER
        if (ring.label.isNotBlank()) label(ring.label, 16f, header, color = mutedColor).gravity = Gravity.CENTER
        timeLabel(ring.startedAt.format(clockFormat), 60f, header).gravity = Gravity.CENTER
        header.addView(AstralPlanet(this), LinearLayout.LayoutParams(-1, dp(112)))
        val controls = card()
        val mission = ring.math
        val extra = ring.extra
        if (extra != null) {
            showExtraMission(ring, extra, controls)
        } else if (mission == null) {
            button("Alarmı kapat", controls) { send(RingService.DISMISS, ring) }
        } else {
            val progress = label("", 15f, controls, color = mutedColor)
            val question = label("", 32f, controls, bold = true).apply { gravity = Gravity.CENTER }
            val answer = EditText(this).apply {
                hint = "Cevap"
                inputType = InputType.TYPE_CLASS_NUMBER
                styleInput(this)
                controls.addView(this)
            }
            fun showQuestion() {
                progress.text = mission.progress
                question.text = mission.question?.let { "${it.expression} = ?" }
                answer.text.clear()
                answer.error = null
            }
            showQuestion()
            button("Cevabı kontrol et", controls) {
                when (mission.submit(answer.text.toString().toIntOrNull())) {
                    MathResult.INCORRECT -> answer.error = "Tekrar dene"
                    MathResult.NEXT -> showQuestion()
                    MathResult.COMPLETE -> send(RingService.DISMISS, ring)
                }
            }
        }
        if (Alarms(this).canSchedule()) button("5 dakika ertele", controls, primary = false) { send(RingService.SNOOZE, ring) }
    }

    private fun clearMissionCallbacks() {
        memoryHandler.removeCallbacksAndMessages(null)
        shakeListener?.let { sensorManager.unregisterListener(it) }
        shakeListener = null
    }

    private fun showExtraMission(ring: Ring, mission: ExtraMission, controls: LinearLayout) {
        label(mission.task.label, 20f, controls, bold = true)
        when (mission.task) {
            AlarmTask.MEMORY -> {
                val display = label("Diziyi göster, sonra sırayı tekrarla.", 17f, controls)
                val row = LinearLayout(this)
                controls.addView(row)
                val keys = (1..4).map { number ->
                    button(number.toString(), row, primary = false) {
                        val correct = mission.tap(number)
                        display.text = if (correct) "${mission.progress} / ${mission.sequence.size}"
                            else "Yanlış sıra. Baştan dene veya diziyi yeniden göster."
                        if (mission.complete) send(RingService.DISMISS, ring)
                    }.apply {
                        isEnabled = false
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                }
                button("Diziyi göster", controls, primary = false) {
                    memoryHandler.removeCallbacksAndMessages(null)
                    keys.forEach { it.isEnabled = false }
                    for ((index, number) in mission.sequence.withIndex()) {
                        memoryHandler.postDelayed({ display.text = number.toString() }, index * 800L)
                        memoryHandler.postDelayed({ display.text = "•" }, index * 800L + 550L)
                    }
                    memoryHandler.postDelayed({
                        display.text = "Sırayı tekrarla · ${mission.progress} / ${mission.sequence.size}"
                        keys.forEach { it.isEnabled = true }
                    }, mission.sequence.size * 800L)
                }
            }
            AlarmTask.TYPING -> {
                label(mission.phrase, 24f, controls)
                val input = EditText(this).apply {
                    hint = "Cümleyi yaz"
                    setSingleLine()
                    styleInput(this)
                    controls.addView(this)
                }
                button("Metni kontrol et", controls) {
                    if (mission.type(input.text.toString())) send(RingService.DISMISS, ring)
                    else input.error = "Cümleyi ekrandaki gibi yaz."
                }
            }
            AlarmTask.SHAKE -> showShakeMission(ring, mission, controls)
            AlarmTask.QR -> {
                label("Bu alarm için kaydettiğin QR kodunu okut.", parent = controls)
                button("Kamerayı aç", controls) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(this, QrScannerActivity::class.java).putExtra("ringToken", ring.token), 22)
                }
                if (failedQrToken == ring.token) {
                    label("Kamera kullanılamıyor.", parent = controls)
                    button("Matematikle devam et", controls, primary = false) { useMathFallback(ring) }
                }
            }
            else -> error("Unsupported mission: ${mission.task}")
        }
    }

    private fun showShakeMission(ring: Ring, mission: ExtraMission, controls: LinearLayout) {
        val progress = label("${mission.progress} / ${ExtraMission.SHAKE_COUNT}", 24f, controls)
        val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            private var lastShake = 0L
            private var aboveThreshold = false

            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val (x, y, z) = event.values
                val high = x * x + y * y + z * z >= 23f * 23f
                val now = android.os.SystemClock.elapsedRealtime()
                if (high && !aboveThreshold && now - lastShake >= 300 && !mission.complete) {
                    lastShake = now
                    mission.shake()
                    progress.text = "${mission.progress} / ${ExtraMission.SHAKE_COUNT}"
                    if (mission.complete) send(RingService.DISMISS, ring)
                }
                aboveThreshold = high
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null && sensorManager.registerListener(listener, sensor,
                android.hardware.SensorManager.SENSOR_DELAY_GAME)) {
            shakeListener = listener
        } else {
            button("Matematikle devam et", controls, primary = false) { useMathFallback(ring) }
        }
    }

    private fun useMathFallback(ring: Ring) {
        ring.extra = null
        ring.math = MathMission(MathSettings())
        render()
    }

    @Deprecated("Receives the locally scanned QR code.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 22) return
        val ring = RingService.active ?: return
        val mission = ring.extra?.takeIf { it.task == AlarmTask.QR } ?: return
        if (data?.getStringExtra("ringToken") != ring.token) return
        when (resultCode) {
            QrScannerActivity.RESULT_CAMERA_UNAVAILABLE -> {
                failedQrToken = ring.token
                if (hasWindowFocus()) render()
            }
            RESULT_OK -> {
                if (mission.scan(data.getStringExtra(QrScannerActivity.CODE).orEmpty())) {
                    send(RingService.DISMISS, ring)
                } else {
                    android.widget.Toast.makeText(this, "Bu alarmın kayıtlı QR kodu değil.",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun send(action: String, ring: Ring) {
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("token", ring.token))
    }
}
