package com.caglaryildiz.alarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.WindowManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class AlarmEditorActivity : AlarmScreen() {
    private val alarms by lazy { Alarms(this) }
    private var original: Alarm? = null
    private lateinit var time: LocalTime
    private var date: LocalDate? = null
    private var days: Set<Int> = emptySet()
    private var task = AlarmTask.MATH
    private var qrCode = ""
    private lateinit var qrButton: Button
    private var mathSettings = MathSettings()
    private var tone: String? = null
    private var preview: Ringtone? = null
    private lateinit var nameInput: EditText
    private lateinit var timeReadout: TextView
    private lateinit var orbit: OrbitTimeView
    private lateinit var hourButton: Button
    private lateinit var minuteButton: Button
    private lateinit var dateButton: Button
    private lateinit var repeatButton: Button
    private lateinit var toneButton: Button
    private lateinit var taskButton: Button
    private lateinit var difficultyButton: Button
    private lateinit var questionCountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id")
        original = alarms.all().firstOrNull { it.id == id }
        if (id != null && original == null) {
            finish()
            return
        }
        val alarm = original ?: Alarm(time = LocalTime.now().plusMinutes(2).withSecond(0).withNano(0))
        time = savedInstanceState?.getString("time")?.let(LocalTime::parse) ?: alarm.time
        date = if (savedInstanceState == null) alarm.date else
            savedInstanceState.getString("date")?.let(LocalDate::parse)
        days = savedInstanceState?.getIntArray("days")?.toSet() ?: alarm.days
        task = savedInstanceState?.getString("task")?.let(AlarmTask::valueOf) ?: alarm.task
        mathSettings = if (savedInstanceState == null) alarm.mathSettings else MathSettings.from(
            savedInstanceState.getString("mathDifficulty"), savedInstanceState.getInt("mathCount", 1))
        qrCode = savedInstanceState?.getString("qrCode") ?: alarm.qrCode
        tone = if (savedInstanceState == null) alarm.tone else savedInstanceState.getString("tone")

        layout()
        showNameField(savedInstanceState?.getString("label") ?: alarm.label)
        val schedule = card()
        timeReadout = timeLabel("", 56f, schedule).apply { gravity = Gravity.CENTER }
        orbit = OrbitTimeView(this) { picked ->
            time = picked
            updateFields()
        }
        orbit.selectingMinutes = savedInstanceState?.getBoolean("selectingMinutes") ?: false
        val tabs = LinearLayout(this)
        schedule.addView(tabs)
        hourButton = button("Saat", tabs, primary = false) {
            orbit.selectingMinutes = false
            updateTimeFields()
        }
        minuteButton = button("Dakika", tabs, primary = false) {
            orbit.selectingMinutes = true
            updateTimeFields()
        }
        for (tab in listOf(hourButton, minuteButton)) {
            tab.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(4)
                marginStart = dp(4)
            }
        }
        schedule.addView(orbit, LinearLayout.LayoutParams(-1, dp(268)))
        dateButton = button("", section("Tarih"), primary = false, action = ::pickDate)
        repeatButton = button("", section("Tekrar edilen günler"), primary = false, action = ::pickDays)
        toneButton = button("", section("Alarm sesi"), primary = false, action = ::pickTone)
        val taskSection = section("Kapatma görevi")
        taskButton = button("", taskSection, primary = false, action = ::pickTask)
        qrButton = button("", taskSection, primary = false, action = ::registerQrCode)
        difficultyButton = button("", taskSection, primary = false, action = ::pickDifficulty)
        questionCountButton = button("", taskSection, primary = false, action = ::pickQuestionCount)

        button("Kaydet", action = ::save)
        button("Vazgeç", primary = false) { finish() }
        original?.let { existing ->
            button("Alarmı sil", primary = false, destructive = true) {
                AlertDialog.Builder(this, R.style.AstralDialogTheme).setTitle("Alarm silinsin mi?")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Sil") { _, _ ->
                        alarms.delete(existing)
                        finish()
                    }.show()
            }
        }
        updateFields()
    }

    private fun showNameField(name: String) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        panel.isFocusableInTouchMode = true
        panel.requestFocus()
        nameInput = EditText(this).apply {
            hint = "Alarm adı gir"
            contentDescription = "Alarm adı"
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            textSize = 23f
            styleInput(this)
            filters = arrayOf(InputFilter.LengthFilter(60))
            setText(name)
        }
        panel.addView(nameInput, LinearLayout.LayoutParams(-1, -2))
    }

    private fun section(title: String): LinearLayout = card().also {
        label(title, 18f, it, bold = true).accessibilityHeading()
    }

    private fun updateTimeFields() {
        timeReadout.text = time.format(clockFormat)
        orbit.time = time
        hourButton.isSelected = !orbit.selectingMinutes
        minuteButton.isSelected = orbit.selectingMinutes
        hourButton.alpha = if (hourButton.isSelected) 1f else .66f
        minuteButton.alpha = if (minuteButton.isSelected) 1f else .66f
    }

    private fun updateFields() {
        updateTimeFields()
        dateButton.isEnabled = days.isEmpty()
        dateButton.text = when {
            days.isNotEmpty() -> "Tarih, tekrar günlerine göre belirlenir"
            date != null -> date?.format(dateFormat)
            else -> {
                val now = ZonedDateTime.now()
                val today = now.toLocalDate().atTime(time).atZone(now.zone).isAfter(now)
                if (today) "Otomatik · Bugün bir kez" else "Otomatik · Yarın bir kez"
            }
        }
        repeatButton.text = when (days.size) {
            0 -> "Tek sefer"
            7 -> "Her gün"
            else -> days.sorted().joinToString(" · ") { weekdays[it - 1].take(3) }
        }
        taskButton.text = task.label
        qrButton.visibility = if (task == AlarmTask.QR) android.view.View.VISIBLE else android.view.View.GONE
        qrButton.text = if (qrCode.isBlank()) "QR kodu kaydet" else "Kayıtlı QR kodu değiştir"
        val showMathSettings = if (task == AlarmTask.MATH) android.view.View.VISIBLE else android.view.View.GONE
        difficultyButton.visibility = showMathSettings
        questionCountButton.visibility = showMathSettings
        difficultyButton.text = "Zorluk · ${mathSettings.difficulty.label}"
        questionCountButton.text = "Soru sayısı · ${mathSettings.count}"
        toneButton.text = tone?.let {
            runCatching { RingtoneManager.getRingtone(this, Uri.parse(it))?.getTitle(this) }.getOrNull()
        } ?: "Cihazın varsayılan alarm sesi"
    }

    private fun pickDate() {
        val today = LocalDate.now()
        val selected = date?.coerceAtLeast(today) ?: today
        DatePickerDialog(this, R.style.AstralDialogTheme, { _, year, month, day ->
            date = LocalDate.of(year, month + 1, day)
            updateFields()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            setButton(AlertDialog.BUTTON_NEUTRAL, "Bugün veya yarın") { _, _ ->
                date = null
                updateFields()
            }
        }.show()
    }

    private fun pickDays() {
        val dialogContext = ContextThemeWrapper(this, R.style.AstralDialogTheme)
        val choices = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        val checkboxes = weekdays.mapIndexed { index, name ->
            CheckBox(dialogContext).apply {
                text = name
                minHeight = dp(44)
                isChecked = index + 1 in days
                choices.addView(this)
            }
        }
        val scroll = ScrollView(dialogContext).apply { addView(choices) }
        val dialog = AlertDialog.Builder(dialogContext)
            .setTitle("Tekrar edilen günler")
            .setView(scroll)
            .setNegativeButton("Vazgeç", null)
            .setNeutralButton("Tek sefer") { _, _ -> updateDays(emptySet()) }
            .setPositiveButton("Uygula") { _, _ ->
                updateDays(checkboxes.indices.filter { checkboxes[it].isChecked }.map { it + 1 }.toSet())
            }
            .create()

        button("Hepsini seç", choices, primary = false) {
            updateDays((1..7).toSet())
            dialog.dismiss()
        }
        button("Hafta sonu", choices, primary = false) {
            updateDays(setOf(6, 7))
            dialog.dismiss()
        }
        button("Hafta içi", choices, primary = false) {
            updateDays((1..5).toSet())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateDays(selection: Set<Int>) {
        days = selection
        updateFields()
    }

    private fun pickTask() {
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Kapatma görevi")
            .setSingleChoiceItems(AlarmTask.entries.map {
                if (it.premium) "${it.label} · Premium" else it.label
            }.toTypedArray(), task.ordinal) { dialog, index ->
                dialog.dismiss()
                selectTask(AlarmTask.entries[index])
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun selectTask(selected: AlarmTask) {
        if (selected == AlarmTask.SHAKE && !ExtraMission.hasAccelerometer(this)) {
            Toast.makeText(this, "Bu cihazda hareket sensörü yok.", Toast.LENGTH_SHORT).show()
            return
        }
        val premium = PremiumAccess(this)
        if (selected.premium && !premium.unlocked) {
            val prompt = AlertDialog.Builder(this, R.style.AstralDialogTheme)
                .setTitle("BENG Premium")
                .setNegativeButton("Vazgeç", null)
            if (BuildConfig.DEBUG) {
                prompt.setMessage("Bu test sürümünde Premium görevleri ücretsiz deneyebilirsin.")
                    .setPositiveButton("Premium’u dene") { _, _ ->
                        premium.enablePreview()
                        selectTask(selected)
                    }
            } else {
                prompt.setMessage("Bu görev Premium üyelik gerektirir.")
                    .setPositiveButton("Tamam", null)
            }
            prompt.show()
            return
        }
        task = selected
        updateFields()
    }

    @Suppress("DEPRECATION")
    private fun registerQrCode() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 20)
            return
        }
        startActivityForResult(android.content.Intent(this, QrScannerActivity::class.java), 21)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 20 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            registerQrCode()
        }
    }

    @Deprecated("Receives the locally scanned QR code.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 21) return
        if (resultCode == QrScannerActivity.RESULT_CAMERA_UNAVAILABLE) {
            Toast.makeText(this, "Kamera açılamadı.", Toast.LENGTH_SHORT).show()
        } else if (resultCode == RESULT_OK) {
            qrCode = data?.getStringExtra(QrScannerActivity.CODE).orEmpty()
            updateFields()
        }
    }

    private fun pickDifficulty() {
        val levels = MathDifficulty.entries
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Matematik zorluğu")
            .setSingleChoiceItems(levels.map { it.label }.toTypedArray(), mathSettings.difficulty.ordinal) { dialog, index ->
                mathSettings = mathSettings.copy(difficulty = levels[index])
                updateFields()
                dialog.dismiss()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun pickQuestionCount() {
        val counts = MathSettings.counts.toList()
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Soru sayısı")
            .setSingleChoiceItems(counts.map { "$it soru" }.toTypedArray(), counts.indexOf(mathSettings.count)) { dialog, index ->
                mathSettings = mathSettings.copy(count = counts[index])
                updateFields()
                dialog.dismiss()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun pickTone() {
        val defaultLabel = "Cihazın varsayılan alarm sesi"
        val options = linkedMapOf<Uri?, String>(null to defaultLabel)
        val manager = RingtoneManager(applicationContext).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        runCatching {
            manager.cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = manager.getRingtoneUri(cursor.position) ?: continue
                    options[uri] = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                }
            }
        }
        val savedTone = tone?.let(Uri::parse)
        if (savedTone != null && savedTone !in options) {
            options[savedTone] = runCatching {
                RingtoneManager.getRingtone(this, savedTone)?.getTitle(this)
            }.getOrNull() ?: "Kayıtlı alarm sesi"
        }

        val uris = options.keys.toList()
        var selected = uris.indexOf(savedTone).coerceAtLeast(0)
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Alarm sesi")
            .setSingleChoiceItems(options.values.toTypedArray(), selected) { _, index ->
                selected = index
                stopPreview()
                val uri = uris[index] ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                runCatching {
                    preview = RingtoneManager.getRingtone(this, uri)
                    preview?.play()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Uygula") { _, _ ->
                tone = uris[selected]?.toString()
                updateFields()
            }
            .setOnDismissListener { stopPreview() }
            .show()
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    private fun stopPreview() {
        preview?.stop()
        preview = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("time", time.toString())
        outState.putBoolean("selectingMinutes", orbit.selectingMinutes)
        outState.putString("date", date?.toString())
        outState.putIntArray("days", days.toIntArray())
        outState.putString("task", task.name)
        outState.putString("qrCode", qrCode)
        outState.putString("mathDifficulty", mathSettings.difficulty.name)
        outState.putInt("mathCount", mathSettings.count)
        outState.putString("tone", tone)
        outState.putString("label", nameInput.text.toString())
    }

    private fun save() {
        if (task == AlarmTask.QR && qrCode.isBlank()) {
            Toast.makeText(this, "Önce alarmı kapatacak QR kodunu kaydet.", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = original?.let { edited -> alarms.all().firstOrNull { it.id == edited.id } }
        if (original != null && existing == null) {
            finish()
            return
        }
        val updated = (existing ?: Alarm(time = time)).copy(
            time = time,
            date = date.takeIf { days.isEmpty() },
            days = days,
            task = task,
            qrCode = qrCode,
            mathSettings = mathSettings,
            label = nameInput.text.toString().trim(),
            tone = tone,
            enabled = intent.getBooleanExtra("enableOnSave", false) || existing?.enabled != false,
            snoozeAt = 0
        )
        if (updated.nextAfter(ZonedDateTime.now()) == null) {
            Toast.makeText(this, "Gelecekte bir tarih ve saat seç.", Toast.LENGTH_LONG).show()
            return
        }
        alarms.save(updated)
        finish()
    }
}
