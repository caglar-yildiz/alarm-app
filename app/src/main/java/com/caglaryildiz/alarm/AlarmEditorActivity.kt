package com.caglaryildiz.alarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private var tone: String? = null
    private var preview: Ringtone? = null
    private lateinit var nameInput: EditText
    private lateinit var nameAction: Button
    private var editingName = false
    private lateinit var timeReadout: TextView
    private lateinit var orbit: OrbitTimeView
    private lateinit var hourButton: Button
    private lateinit var minuteButton: Button
    private lateinit var dateButton: Button
    private lateinit var repeatButton: Button
    private lateinit var toneButton: Button
    private lateinit var taskButton: Button

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
        tone = if (savedInstanceState == null) alarm.tone else savedInstanceState.getString("tone")

        layout()
        showNameField(savedInstanceState?.getString("label") ?: alarm.label)
        if (savedInstanceState?.getBoolean("editingName") == true) setNameEditing(true)
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
        taskButton = button("", section("Kapatma görevi"), primary = false, action = ::pickTask)

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
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            panel.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        nameInput = EditText(this).apply {
            hint = "Alarm adı gir"
            contentDescription = "Alarm adı"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 23f
            styleInput(this)
            filters = arrayOf(InputFilter.LengthFilter(60))
            setText(name)
            setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                setNameEditing(false)
                true
            }
        }
        row.addView(nameInput, LinearLayout.LayoutParams(0, -2, 1f))
        nameAction = button("✎", row, primary = false) { setNameEditing(!editingName) }
        nameAction.layoutParams = LinearLayout.LayoutParams(dp(54), -2).apply { marginStart = dp(8) }
        setNameEditing(false)
    }

    private fun setNameEditing(editing: Boolean) {
        editingName = editing
        nameInput.isFocusableInTouchMode = editing
        nameInput.isFocusable = editing
        nameInput.isCursorVisible = editing
        nameInput.isClickable = editing
        nameInput.isLongClickable = editing
        nameAction.text = if (editing) "✓" else "✎"
        nameAction.contentDescription = if (editing) "Alarm adını onayla" else "Alarm adını düzenle"
        val keyboard = getSystemService(InputMethodManager::class.java)
        if (editing) {
            nameInput.requestFocus()
            nameInput.setSelection(nameInput.length())
            nameInput.post { if (editingName) keyboard.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT) }
        } else {
            nameInput.clearFocus()
            keyboard.hideSoftInputFromWindow(nameInput.windowToken, 0)
        }
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
        val selected = BooleanArray(weekdays.size) { it + 1 in days }
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Tekrar edilen günler")
            .setMultiChoiceItems(weekdays, selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton("Vazgeç", null)
            .setNeutralButton("Tek sefer") { _, _ ->
                days = emptySet()
                updateFields()
            }
            .setPositiveButton("Uygula") { _, _ ->
                days = selected.indices.filter { selected[it] }.map { it + 1 }.toSet()
                updateFields()
            }
            .show()
    }

    private fun pickTask() {
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle("Kapatma görevi")
            .setSingleChoiceItems(AlarmTask.entries.map { it.label }.toTypedArray(), task.ordinal) { dialog, index ->
                task = AlarmTask.entries[index]
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
        outState.putString("tone", tone)
        outState.putString("label", nameInput.text.toString())
        outState.putBoolean("editingName", editingName)
    }

    private fun save() {
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
