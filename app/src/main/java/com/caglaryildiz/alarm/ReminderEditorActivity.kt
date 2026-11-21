package com.caglaryildiz.alarm

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Creates or edits a reminder: what, when, how often, an early heads-up and whether it rings. */
class ReminderEditorActivity : AlarmScreen() {
    private val reminders by lazy { Reminders(this) }
    private val premium get() = PremiumAccess(this).unlocked
    private var original: Reminder? = null
    private lateinit var date: LocalDate
    private lateinit var time: LocalTime
    private var repeat = ReminderRepeat.ONCE
    private var days: Set<Int> = emptySet()
    private var leadDays = 0
    private var ring = false
    private lateinit var titleInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var repeatButton: Button
    private lateinit var leadButton: Button
    private lateinit var ringSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id")
        original = id?.let(reminders::get)
        if (id != null && original == null) {
            finish()
            return
        }
        val start = original ?: ZonedDateTime.now().plusHours(1).withMinute(0).let {
            Reminder(title = "", date = it.toLocalDate(), time = it.toLocalTime().withSecond(0).withNano(0))
        }
        date = savedInstanceState?.getString("date")?.let(LocalDate::parse) ?: start.date
        time = savedInstanceState?.getString("time")?.let(LocalTime::parse) ?: start.time
        repeat = savedInstanceState?.getString("repeat")?.let(ReminderRepeat::valueOf) ?: start.repeat
        days = savedInstanceState?.getIntArray("days")?.toSet() ?: start.days
        leadDays = savedInstanceState?.getInt("leadDays") ?: start.leadDays
        ring = savedInstanceState?.getBoolean("ring") ?: start.ring

        layout(getString(if (original == null) R.string.reminder_new else R.string.reminder_edit))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val what = card()
        titleInput = input(what, getString(R.string.reminder_title_hint), savedInstanceState?.getString("title") ?: start.title, 80, 20f)
        noteInput = input(what, getString(R.string.reminder_note_hint), savedInstanceState?.getString("note") ?: start.note, 200, 15f)

        val whenCard = section(getString(R.string.reminder_when))
        val row = LinearLayout(this).apply { isBaselineAligned = false }
        whenCard.addView(row)
        dateButton = button("", row, primary = false, action = ::pickDate).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.5f).apply { topMargin = dp(8); marginEnd = dp(6) }
        }
        timeButton = button("", row, primary = false, action = ::pickTime).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8) }
        }
        repeatButton = button("", whenCard, primary = false, action = ::pickRepeat)

        val extras = section(getString(R.string.reminder_extras))
        leadButton = button("", extras, primary = false, action = ::pickLead)
        ringSwitch = Switch(this).apply {
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            stylePlanetToggle()
            minHeight = dp(52)
            setOnCheckedChangeListener { view, on ->
                if (on && !premium) {
                    view.isChecked = false
                    askPremium()
                } else ring = on
            }
        }
        extras.addView(ringSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        label(getString(R.string.reminder_ring_hint), 12.5f, extras, color = mutedColor).setPadding(0, 0, 0, 0)

        button(getString(R.string.save), action = ::save)
        button(getString(R.string.cancel), primary = false) { finish() }
        original?.let { existing ->
            button(getString(R.string.reminder_delete), primary = false, destructive = true) {
                AlertDialog.Builder(this, R.style.AstralDialogTheme).setTitle(getString(R.string.reminder_delete_confirm))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        reminders.delete(existing.id)
                        finish()
                    }.show()
            }
        }
        update()
    }

    private fun input(parent: LinearLayout, hint: String, value: String, max: Int, size: Float) = EditText(this).apply {
        this.hint = hint
        contentDescription = hint
        setHintTextColor(0x99C0C4E1.toInt())
        setTextColor(android.graphics.Color.WHITE)
        textSize = size
        filters = arrayOf(InputFilter.LengthFilter(max))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        styleInput(this)
        setText(value)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    private fun section(title: String): LinearLayout = card().also {
        label(title, 18f, it, bold = true).accessibilityHeading()
    }

    private fun update() {
        dateButton.text = date.format(dateFormat())
        dateButton.isEnabled = repeat != ReminderRepeat.DAILY
        timeButton.text = time.format(clockFormat)
        repeatButton.text = getString(R.string.reminder_repeat_value, when (repeat) {
            ReminderRepeat.WEEKLY -> days.ifEmpty { setOf(date.dayOfWeek.value) }.sorted().joinToString(" · ") { weekdayName(it, short = true) }
            else -> getString(repeat.labelRes)
        })
        val lead = getString(when (leadDays) {
            1 -> R.string.reminder_lead_day
            7 -> R.string.reminder_lead_week
            else -> R.string.reminder_lead_none
        })
        leadButton.text = getString(R.string.reminder_lead_value, lead) + if (premium) "" else "  ✦"
        leadButton.visibility = if (repeat == ReminderRepeat.DAILY) android.view.View.GONE else android.view.View.VISIBLE
        val ringTitle = getString(R.string.reminder_ring)
        ringSwitch.text = if (premium) ringTitle else "$ringTitle  ✦"
        ringSwitch.isChecked = ring && premium
    }

    private fun pickDate() {
        val today = LocalDate.now()
        DatePickerDialog(this, R.style.AstralDialogTheme, { _, year, month, day ->
            date = LocalDate.of(year, month + 1, day)
            if (repeat == ReminderRepeat.WEEKLY && days.isEmpty()) days = setOf(date.dayOfWeek.value)
            update()
        }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.type_date)) { _, _ ->
                typeDate(date.coerceAtLeast(today), today) { typed ->
                    date = typed
                    if (repeat == ReminderRepeat.WEEKLY && days.isEmpty()) days = setOf(typed.dayOfWeek.value)
                    update()
                }
            }
        }.show()
    }

    private fun pickTime() {
        TimePickerDialog(this, R.style.AstralDialogTheme, { _, hour, minute ->
            time = LocalTime.of(hour, minute)
            update()
        }, time.hour, time.minute, true).show()
    }

    private fun pickRepeat() {
        val options = ReminderRepeat.entries
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.reminder_repeat))
            .setSingleChoiceItems(options.map { getString(it.labelRes) }.toTypedArray(), repeat.ordinal) { dialog, index ->
                dialog.dismiss()
                repeat = options[index]
                if (repeat == ReminderRepeat.DAILY) leadDays = 0
                if (repeat == ReminderRepeat.WEEKLY) pickDays() else update()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickDays() {
        val themed = ContextThemeWrapper(this, R.style.AstralDialogTheme)
        val box = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        val selected = days.ifEmpty { setOf(date.dayOfWeek.value) }
        val boxes = (1..7).map { day ->
            CheckBox(themed).apply {
                text = weekdayName(day)
                minHeight = dp(44)
                isChecked = day in selected
                box.addView(this)
            }
        }
        AlertDialog.Builder(themed)
            .setTitle(getString(R.string.editor_repeat_days))
            .setView(android.widget.ScrollView(themed).apply { addView(box) })
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> update() }
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                days = boxes.indices.filter { boxes[it].isChecked }.map { it + 1 }.toSet()
                    .ifEmpty { setOf(date.dayOfWeek.value) }
                update()
            }
            .show()
    }

    private fun pickLead() {
        if (!premium) {
            askPremium()
            return
        }
        val values = listOf(0, 1, 7)
        val names = listOf(R.string.reminder_lead_none, R.string.reminder_lead_day, R.string.reminder_lead_week).map(::getString)
        AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.reminder_lead))
            .setSingleChoiceItems(names.toTypedArray(), values.indexOf(leadDays).coerceAtLeast(0)) { dialog, index ->
                dialog.dismiss()
                leadDays = values[index]
                update()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun askPremium() {
        Toast.makeText(this, getString(R.string.reminder_premium_hint), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun save() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.reminder_title_required)
            titleInput.requestFocus()
            return
        }
        val reminder = (original ?: Reminder(title = title, date = date, time = time)).copy(
            title = title,
            note = noteInput.text.toString().trim(),
            date = date,
            time = time,
            repeat = repeat,
            days = if (repeat == ReminderRepeat.WEEKLY) days.ifEmpty { setOf(date.dayOfWeek.value) } else emptySet(),
            leadDays = if (premium && repeat != ReminderRepeat.DAILY) leadDays else 0,
            ring = ring && premium,
            enabled = true,
            snoozeAt = 0
        )
        if (reminder.nextEvent(ZonedDateTime.now()) == null) {
            Toast.makeText(this, getString(R.string.editor_future_required), Toast.LENGTH_LONG).show()
            return
        }
        reminders.save(reminder)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::titleInput.isInitialized) return
        outState.putString("title", titleInput.text.toString())
        outState.putString("note", noteInput.text.toString())
        outState.putString("date", date.toString())
        outState.putString("time", time.toString())
        outState.putString("repeat", repeat.name)
        outState.putIntArray("days", days.toIntArray())
        outState.putInt("leadDays", leadDays)
        outState.putBoolean("ring", ring)
    }
}
