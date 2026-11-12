package com.caglaryildiz.alarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.view.Gravity
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
    private lateinit var nameInput: EditText
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

        layout(if (original == null) "Yeni alarm" else "Alarmı düzenle")
        val schedule = card()
        timeReadout = timeLabel("", 56f, schedule).apply { gravity = Gravity.CENTER }
        orbit = OrbitTimeView(this) { picked ->
            time = picked
            updateTimeFields()
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
        button("Standart saat seçici", schedule, primary = false, action = ::pickTime)
        dateButton = button("", section("Tarih"), primary = false, action = ::pickDate)

        val identity = section("Alarm adı")
        nameInput = EditText(this).apply {
            hint = "Örn. Hafta içi"
            contentDescription = "Alarm adı"
            setSingleLine()
            styleInput(this)
            filters = arrayOf(InputFilter.LengthFilter(60))
            setText(savedInstanceState?.getString("label") ?: alarm.label)
            identity.addView(this)
        }

        repeatButton = button("", section("Tekrar"), primary = false, action = ::pickDays)
        toneButton = button("", section("Alarm sesi"), primary = false, action = ::pickTone)
        taskButton = button("", section("Kapatma görevi"), primary = false, action = ::pickTask)

        button("Kaydet", action = ::save)
        button("Vazgeç", primary = false) { finish() }
        original?.let { existing ->
            button("Alarmı sil", primary = false) {
                AlertDialog.Builder(this).setTitle("Alarm silinsin mi?")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Sil") { _, _ ->
                        alarms.delete(existing)
                        finish()
                    }.show()
            }
        }
        updateFields()
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
        dateButton.text = if (days.isNotEmpty()) "Tekrar günleri geçerli"
            else date?.format(dateFormat) ?: "Sonraki uygun tarih"
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

    private fun pickTime() {
        TimePickerDialog(this, { _, hour, minute ->
            time = LocalTime.of(hour, minute)
            updateTimeFields()
        }, time.hour, time.minute, true).show()
    }

    private fun pickDate() {
        val today = LocalDate.now()
        val selected = date?.coerceAtLeast(today) ?: today
        DatePickerDialog(this, { _, year, month, day ->
            date = LocalDate.of(year, month + 1, day)
            updateFields()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            setButton(AlertDialog.BUTTON_NEUTRAL, "Sonraki uygun tarih") { _, _ ->
                date = null
                updateFields()
            }
        }.show()
    }

    private fun pickDays() {
        val selected = BooleanArray(weekdays.size) { it + 1 in days }
        AlertDialog.Builder(this)
            .setTitle("Haftalık tekrar günleri")
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
        AlertDialog.Builder(this)
            .setTitle("Kapatma görevi")
            .setSingleChoiceItems(AlarmTask.entries.map { it.label }.toTypedArray(), task.ordinal) { dialog, index ->
                task = AlarmTask.entries[index]
                updateFields()
                dialog.dismiss()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickTone() {
        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                tone?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
        try {
            startActivityForResult(picker, PICK_TONE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Cihazın alarm sesi seçicisi açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Handles the platform ringtone picker result.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_TONE || resultCode != RESULT_OK) return
        val picked = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        tone = picked?.takeUnless(RingtoneManager::isDefault)?.toString()
        updateFields()
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

    private companion object {
        const val PICK_TONE = 1
    }
}
