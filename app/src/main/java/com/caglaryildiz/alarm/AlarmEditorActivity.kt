package com.caglaryildiz.alarm

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.media.MediaPlayer
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
    private var crescendo: Boolean = true
    private var preview: MediaPlayer? = null
    private lateinit var nameInput: EditText
    private lateinit var timeReadout: TextView
    private lateinit var orbit: OrbitTimeView
    private lateinit var hourButton: Button
    private lateinit var minuteButton: Button
    private lateinit var dateButton: Button
    private lateinit var repeatButton: Button
    private lateinit var toneButton: Button
    private lateinit var crescendoButton: Button
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
        crescendo = if (savedInstanceState == null) alarm.crescendo else savedInstanceState.getBoolean("crescendo", true)

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
        hourButton = button(getString(R.string.editor_hour), tabs, primary = false) {
            orbit.selectingMinutes = false
            updateTimeFields()
        }
        minuteButton = button(getString(R.string.editor_minute), tabs, primary = false) {
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
        dateButton = button("", section(getString(R.string.editor_date)), primary = false, action = ::pickDate)
        repeatButton = button("", section(getString(R.string.editor_repeat_days)), primary = false, action = ::pickDays)
        val soundSection = section(getString(R.string.editor_sound))
        toneButton = button("", soundSection, primary = false, action = ::pickTone)
        crescendoButton = button("", soundSection, primary = false, action = ::toggleCrescendo)
        val taskSection = section(getString(R.string.editor_task))
        taskButton = button("", taskSection, primary = false, action = ::pickTask)
        qrButton = button("", taskSection, primary = false, action = ::registerQrCode)
        difficultyButton = button("", taskSection, primary = false, action = ::pickDifficulty)
        questionCountButton = button("", taskSection, primary = false, action = ::pickQuestionCount)

        button(getString(R.string.editor_preview), taskSection, primary = false, action = ::openPreview)

        button(getString(R.string.save), action = ::save)
        button(getString(R.string.cancel), primary = false) { finish() }
        original?.let { existing ->
            button(getString(R.string.editor_delete), primary = false, destructive = true) {
                AlertDialog.Builder(this, R.style.AstralDialogTheme).setTitle(getString(R.string.editor_delete_confirm))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
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
            hint = getString(R.string.editor_name_hint)
            contentDescription = getString(R.string.editor_name)
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
            days.isNotEmpty() -> getString(R.string.editor_date_by_days)
            date != null -> date?.format(dateFormat())
            else -> {
                val now = ZonedDateTime.now()
                val today = now.toLocalDate().atTime(time).atZone(now.zone).isAfter(now)
                getString(if (today) R.string.editor_auto_today else R.string.editor_auto_tomorrow)
            }
        }
        repeatButton.text = when (days.size) {
            0 -> getString(R.string.repeat_once)
            7 -> getString(R.string.repeat_every_day)
            else -> days.sorted().joinToString(" · ") { weekdayName(it, short = true) }
        }
        val taskLabel = getString(task.labelRes)
        taskButton.text = if (task.premium) "$taskLabel  ✦" else taskLabel
        taskButton.contentDescription = if (task.premium) getString(R.string.premium_feature_description, taskLabel) else taskLabel
        qrButton.visibility = if (task == AlarmTask.QR) android.view.View.VISIBLE else android.view.View.GONE
        qrButton.text = getString(if (qrCode.isBlank()) R.string.editor_qr_save else R.string.editor_qr_change)
        val showMathSettings = if (task == AlarmTask.MATH) android.view.View.VISIBLE else android.view.View.GONE
        difficultyButton.visibility = showMathSettings
        questionCountButton.visibility = showMathSettings
        difficultyButton.text = getString(R.string.editor_difficulty_value, getString(mathSettings.difficulty.labelRes))
        questionCountButton.text = getString(R.string.editor_question_count_value, mathSettings.count)
        toneButton.text = getString(R.string.editor_tone_value, resolveToneTitle(this, tone))
        crescendoButton.text = getString(if (crescendo) R.string.editor_crescendo_on else R.string.editor_crescendo_off)
    }

    private fun toggleCrescendo() {
        crescendo = !crescendo
        updateFields()
    }

    private fun pickDate() {
        val today = LocalDate.now()
        val selected = date?.coerceAtLeast(today) ?: today
        DatePickerDialog(this, R.style.AstralDialogTheme, { _, year, month, day ->
            date = LocalDate.of(year, month + 1, day)
            updateFields()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.editor_today_or_tomorrow)) { _, _ ->
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
        val checkboxes = (1..7).mapIndexed { index, day ->
            CheckBox(dialogContext).apply {
                text = weekdayName(day)
                minHeight = dp(44)
                isChecked = index + 1 in days
                choices.addView(this)
            }
        }
        val scroll = ScrollView(dialogContext).apply { addView(choices) }
        val dialog = AlertDialog.Builder(dialogContext)
            .setTitle(getString(R.string.editor_repeat_days))
            .setView(scroll)
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.repeat_once)) { _, _ -> updateDays(emptySet()) }
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                updateDays(checkboxes.indices.filter { checkboxes[it].isChecked }.map { it + 1 }.toSet())
            }
            .create()

        button(getString(R.string.editor_select_all), choices, primary = false) {
            updateDays((1..7).toSet())
            dialog.dismiss()
        }
        button(getString(R.string.repeat_weekend), choices, primary = false) {
            updateDays(setOf(6, 7))
            dialog.dismiss()
        }
        button(getString(R.string.repeat_weekdays), choices, primary = false) {
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
            .setTitle(getString(R.string.editor_task))
            .setSingleChoiceItems(AlarmTask.entries.map {
                if (it.premium) "${getString(it.labelRes)}  ✦" else getString(it.labelRes)
            }.toTypedArray(), task.ordinal) { dialog, index ->
                dialog.dismiss()
                selectTask(AlarmTask.entries[index])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openPreview() {
        if (task == AlarmTask.QR && qrCode.isBlank()) {
            Toast.makeText(this, getString(R.string.editor_qr_preview_needs_code), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(android.content.Intent(this, MissionPreviewActivity::class.java)
            .putExtra("task", task.name)
            .putExtra("mathDifficulty", mathSettings.difficulty.name)
            .putExtra("mathCount", mathSettings.count)
            .putExtra("qrCode", qrCode)
            .putExtra("label", nameInput.text.toString())
            .putExtra("tone", tone))
    }

    private fun selectTask(selected: AlarmTask) {
        if (selected == AlarmTask.SHAKE && !ExtraMission.hasAccelerometer(this)) {
            Toast.makeText(this, getString(R.string.editor_no_motion_sensor), Toast.LENGTH_SHORT).show()
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

    @Deprecated("Receives the locally scanned QR code and picked audio.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21) {
            if (resultCode == QrScannerActivity.RESULT_CAMERA_UNAVAILABLE) {
                Toast.makeText(this, getString(R.string.camera_failed), Toast.LENGTH_SHORT).show()
            } else if (resultCode == RESULT_OK) {
                qrCode = data?.getStringExtra(QrScannerActivity.CODE).orEmpty()
                updateFields()
            }
        } else if (requestCode == REQUEST_PICK_AUDIO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            tone = uri.toString()
            updateFields()
            stopPreview()
            if (RingService.active == null) {
                preview = playAlarmTone(this, uri, loop = false)
            }
        }
    }

    private fun pickDifficulty() {
        val levels = MathDifficulty.entries
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.editor_math_difficulty))
            .setSingleChoiceItems(levels.map { getString(it.labelRes) }.toTypedArray(), mathSettings.difficulty.ordinal) { dialog, index ->
                mathSettings = mathSettings.copy(difficulty = levels[index])
                updateFields()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickQuestionCount() {
        val counts = MathSettings.counts.toList()
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.editor_question_count))
            .setSingleChoiceItems(counts.map { resources.getQuantityString(R.plurals.question_count, it, it) }.toTypedArray(), counts.indexOf(mathSettings.count)) { dialog, index ->
                mathSettings = mathSettings.copy(count = counts[index])
                updateFields()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickTone() {
        val options = linkedMapOf<Uri?, String>()
        options[null] = getString(R.string.tone_device_default)

        for (builtIn in BuiltInTones.list) {
            val badge = if (builtIn.premium) " ✦" else ""
            options[BuiltInTones.toUri(builtIn)] = "${getString(builtIn.titleRes)}$badge · ${getString(builtIn.descriptionRes)}"
        }

        val savedToneUri = tone?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (savedToneUri != null && savedToneUri !in options) {
            val label = resolveToneTitle(this, tone)
            options[savedToneUri] = getString(R.string.tone_current_choice, label)
        }

        val manager = RingtoneManager(applicationContext).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        runCatching {
            manager.cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = manager.getRingtoneUri(cursor.position) ?: continue
                    if (uri !in options) {
                        options[uri] = getString(R.string.tone_system, cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX))
                    }
                }
            }
        }

        val uris = options.keys.toList()
        val titles = options.values.toMutableList()
        titles.add(getString(R.string.tone_pick_file))

        var selected = uris.indexOf(savedToneUri).coerceAtLeast(0)
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.editor_sound))
            .setSingleChoiceItems(titles.toTypedArray(), selected) { dialog, index ->
                if (index == titles.lastIndex) {
                    stopPreview()
                    dialog.dismiss()
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    }
                    runCatching {
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_PICK_AUDIO)
                    }.onFailure {
                        Toast.makeText(this, getString(R.string.tone_picker_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@setSingleChoiceItems
                }
                selected = index
                stopPreview()
                if (RingService.active == null) preview = playAlarmTone(this, uris[index], loop = false)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                if (selected < uris.size) {
                    val chosenUri = uris[selected]
                    val builtIn = BuiltInTones.fromUri(chosenUri)
                    if (builtIn?.premium == true && !PremiumAccess(this).unlocked) {
                        Toast.makeText(this, getString(R.string.tone_premium_only), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, PremiumActivity::class.java))
                        return@setPositiveButton
                    }
                    tone = chosenUri?.toString()
                    updateFields()
                }
            }
            .setOnDismissListener { stopPreview() }
            .show()
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    private fun stopPreview() {
        preview?.release()
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
        outState.putBoolean("crescendo", crescendo)
        outState.putString("label", nameInput.text.toString())
    }

    private fun save() {
        if (task.premium && !PremiumAccess(this).unlocked) {
            startActivity(Intent(this, PremiumActivity::class.java))
            return
        }
        val selectedBuiltIn = tone?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(BuiltInTones::fromUri)
        if (selectedBuiltIn?.premium == true && !PremiumAccess(this).unlocked) {
            Toast.makeText(this, getString(R.string.tone_selected_premium_only), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PremiumActivity::class.java))
            return
        }
        if (task == AlarmTask.QR && qrCode.isBlank()) {
            Toast.makeText(this, getString(R.string.editor_qr_required), Toast.LENGTH_SHORT).show()
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
            crescendo = crescendo,
            enabled = true,
            snoozeAt = 0
        )
        if (updated.nextAfter(ZonedDateTime.now()) == null) {
            Toast.makeText(this, getString(R.string.editor_future_required), Toast.LENGTH_LONG).show()
            return
        }
        alarms.save(updated)
        finish()
    }

    companion object {
        private const val REQUEST_PICK_AUDIO = 2002
    }
}
