package com.caglaryildiz.alarm

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The Reminders tab: the user's own reminders and the country's public holidays and special days. */
abstract class ReminderScreen : WeatherScreen() {
    private val reminders by lazy { Reminders(this) }
    private val holidays by lazy { Holidays(this) }
    private var holidaysLoading = false
    private var holidaysFailed = false
    private var showAllHolidays = false

    /** Called when the Reminders tab should be drawn again. */
    protected abstract fun onRemindersChanged()

    protected fun showReminders() {
        loadHolidays(force = false)
        button(getString(R.string.reminder_add)) { openReminder(null) }
        if (!Reminders.canNotify(this)) {
            val warning = card()
            label(getString(R.string.reminder_notifications_off), 14f, warning, color = mutedColor)
            button(getString(R.string.notification_settings), warning, primary = false) {
                startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }

        val now = ZonedDateTime.now()
        val list = reminders.all().sortedWith(compareBy<Reminder> { !it.enabled }
            .thenBy { it.nextEvent(now)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE })
        label(getString(R.string.reminder_mine), 22f, bold = true).apply {
            setPadding(0, dp(18), 0, dp(2))
            accessibilityHeading()
        }
        if (list.isEmpty()) label(getString(R.string.reminder_empty), 14.5f, card(), color = mutedColor)
        list.forEach { reminderCard(it, now) }

        showHolidayCard()
        showUpcomingHolidays()
    }

    // region Own reminders

    private fun reminderCard(reminder: Reminder, now: ZonedDateTime) {
        val item = card().apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            setOnClickListener { openReminder(reminder) }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        item.addView(row)
        iconBadge(R.drawable.ic_reminder, row, 42, 0xFF5B4BC4.toInt(), 0xFF2B3C86.toInt())
        val texts = column()
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        val color = if (reminder.enabled) Color.WHITE else mutedColor
        label(reminder.title, 17f, texts, bold = true, color = color).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(2))
        }
        val next = reminder.nextEvent(now)
        label(next?.let { whenText(it.toLocalDate(), now.toLocalDate()) + " · " + it.format(clockFormat) }
            ?: getString(R.string.reminder_done), 14f, texts, color = mutedColor).setPadding(0, 0, 0, 0)
        row.addView(Switch(this).apply {
            contentDescription = reminder.title
            stylePlanetToggle()
            minWidth = dp(48)
            minHeight = dp(48)
            isChecked = reminder.enabled && next != null
            setOnCheckedChangeListener { _, on ->
                if (on && reminder.copy(enabled = true).nextEvent(ZonedDateTime.now()) == null) {
                    openReminder(reminder)
                } else {
                    reminders.save(reminder.copy(enabled = on, snoozeAt = 0))
                }
                onRemindersChanged()
            }
        })
        val tags = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        item.addView(tags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        pill(repeatText(reminder), tags)
        if (reminder.leadDays > 0) pill(getString(if (reminder.leadDays == 1) R.string.reminder_lead_day
            else R.string.reminder_lead_week), tags).spaced()
        if (reminder.ring) pill(getString(R.string.reminder_ring_tag), tags, goldColor, 0x30FFD98C, 0x88FFD98C.toInt()).spaced()
        if (reminder.note.isNotBlank()) label(reminder.note, 13.5f, item, color = 0xFFD9DCF5.toInt()).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun TextView.spaced() = apply {
        (layoutParams as LinearLayout.LayoutParams).marginStart = dp(6)
    }

    private fun repeatText(reminder: Reminder): String = when (reminder.repeat) {
        ReminderRepeat.WEEKLY -> reminder.days.ifEmpty { setOf(reminder.date.dayOfWeek.value) }.sorted()
            .joinToString(" · ") { weekdayName(it, short = true) }
        else -> getString(reminder.repeat.labelRes)
    }

    private fun whenText(date: LocalDate, today: LocalDate): String = when (date) {
        today -> getString(R.string.weather_today)
        today.plusDays(1) -> getString(R.string.weather_tomorrow)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()))
    }

    private fun openReminder(reminder: Reminder?) {
        startActivity(Intent(this, ReminderEditorActivity::class.java).putExtra("id", reminder?.id))
    }

    /** The soonest reminder or holiday notice, for the home screen; null when there is none. */
    protected fun nextReminderSummary(): String? {
        val now = ZonedDateTime.now()
        val own = reminders.all().mapNotNull { r -> r.nextEvent(now)?.let { it to r.title } }.minByOrNull { it.first }
        val holiday = if (holidays.enabled) holidays.upcoming().firstOrNull { !it.date.isBefore(now.toLocalDate()) } else null
        val holidayTime = holiday?.date?.atTime(holidays.time)?.atZone(now.zone)
        val (time, title) = when {
            own != null && (holidayTime == null || own.first.isBefore(holidayTime)) -> own
            holidayTime != null && holiday != null -> holidayTime to holidays.displayName(holiday)
            else -> return null
        }
        return getString(R.string.reminder_next_summary, title,
            whenText(time.toLocalDate(), now.toLocalDate()) + " " + time.format(clockFormat))
    }

    // endregion

    // region Public holidays

    private fun loadHolidays(force: Boolean) {
        if (holidaysLoading) return
        val started = holidays.refresh(force) { ok ->
            holidaysLoading = false
            holidaysFailed = !ok
            if (!isFinishing && !isDestroyed) onRemindersChanged()
        }
        if (started) {
            holidaysLoading = true
            holidaysFailed = false
            if (force) onRemindersChanged()
        }
    }

    private fun countryName(code: String) =
        Locale("", code).getDisplayCountry(appLocale()).ifBlank { code }

    private fun showHolidayCard() {
        val premium = premiumAccess.unlocked
        val box = card().apply {
            background = glass(0xEE2E3F6E.toInt(), 0xEE33285E.toInt(), 22)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        box.addView(header)
        iconBadge(R.drawable.ic_flag, header, 44, 0xFFE0A14F.toInt(), 0xFF7A3F8F.toInt())
        val titles = column()
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        label(getString(R.string.holiday_title), 18f, titles, bold = true).apply {
            setPadding(0, 0, 0, 0)
            accessibilityHeading()
        }
        label(getString(R.string.holiday_subtitle), 13.5f, titles, color = mutedColor).setPadding(0, dp(2), 0, 0)

        box.addView(Switch(this).apply {
            text = getString(R.string.holiday_toggle)
            textSize = 16f
            setTextColor(Color.WHITE)
            stylePlanetToggle()
            minHeight = dp(52)
            isChecked = holidays.enabled
            setOnCheckedChangeListener { _, on ->
                holidays.enabled = on
                onRemindersChanged()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val settings = LinearLayout(this).apply { isBaselineAligned = false }
        box.addView(settings)
        button(getString(R.string.holiday_country, countryName(holidays.country)), settings, primary = false) {
            pickCountry()
        }.layoutParams = LinearLayout.LayoutParams(0, -2, 1.4f).apply {
            topMargin = dp(6)
            marginEnd = dp(6)
        }
        button(holidays.time.format(clockFormat), settings, primary = false) {
            android.app.TimePickerDialog(this, R.style.AstralDialogTheme, { _, hour, minute ->
                holidays.time = java.time.LocalTime.of(hour, minute)
                onRemindersChanged()
            }, holidays.time.hour, holidays.time.minute, true).show()
        }.apply {
            contentDescription = getString(R.string.holiday_time_description, holidays.time.format(clockFormat))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6) }
        }

        premiumSwitch(box, getString(R.string.holiday_day_before), holidays.dayBefore, premium) { holidays.dayBefore = it }
        premiumSwitch(box, getString(R.string.holiday_special), holidays.specialDays, premium) { holidays.specialDays = it }
        if (holidays.specialDays || !premium) {
            label(getString(R.string.holiday_special_hint), 12.5f, box, color = mutedColor).setPadding(0, 0, 0, 0)
        }

        val status = when {
            holidaysLoading && !holidays.hasData() -> getString(R.string.holiday_loading)
            holidays.unsupported() -> getString(R.string.holiday_unsupported, countryName(holidays.country))
            holidaysFailed && !holidays.hasData() -> getString(R.string.holiday_failed)
            else -> null
        }
        if (status != null) label(status, 13.5f, box, color = goldColor).setPadding(0, dp(8), 0, 0)
        if (holidaysFailed && !holidays.hasData()) {
            button(getString(R.string.weather_refresh), box, primary = false) { loadHolidays(force = true) }
        }
    }

    /** A switch that only Premium (or the free trial) can turn on; otherwise it leads to the Premium tab. */
    private fun premiumSwitch(parent: LinearLayout, title: String, checked: Boolean, premium: Boolean, change: (Boolean) -> Unit) {
        parent.addView(Switch(this).apply {
            text = if (premium) title else "$title  ✦"
            textSize = 15f
            setTextColor(if (premium) Color.WHITE else 0xFFE9E2FF.toInt())
            contentDescription = if (premium) title else getString(R.string.premium_feature_description, title)
            stylePlanetToggle()
            minHeight = dp(48)
            isChecked = checked && premium
            setOnCheckedChangeListener { view, on ->
                if (on && !premium) {
                    view.isChecked = false
                    android.widget.Toast.makeText(this@ReminderScreen, getString(R.string.reminder_premium_hint),
                        android.widget.Toast.LENGTH_SHORT).show()
                    openPremiumTab()
                } else {
                    change(on)
                    onRemindersChanged()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
    }

    /** Opens the Premium tab; MainActivity knows its tabs. */
    protected abstract fun openPremiumTab()

    private fun pickCountry() {
        val known = holidays.countries.ifEmpty { listOf(holidays.country) }
        val codes = (known + holidays.country).distinct().sortedBy { countryName(it) }
        val names = codes.map { countryName(it) }.toTypedArray()
        android.app.AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.holiday_country_title))
            .setSingleChoiceItems(names, codes.indexOf(holidays.country)) { dialog, index ->
                dialog.dismiss()
                holidays.country = codes[index]
                showAllHolidays = false
                loadHolidays(force = false)
                onRemindersChanged()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showUpcomingHolidays() {
        val today = LocalDate.now()
        val all = holidays.upcoming(today)
        if (all.isEmpty()) return
        label(getString(R.string.holiday_upcoming, countryName(holidays.country)), 20f, bold = true).apply {
            setPadding(0, dp(18), 0, dp(2))
            accessibilityHeading()
        }
        val shown = if (showAllHolidays) all else all.take(5)
        val list = card().apply { setPadding(dp(14), dp(6), dp(14), dp(6)) }
        shown.forEachIndexed { index, holiday ->
            if (index > 0) list.addView(View(this).apply { setBackgroundColor(0x338C8CD8) }, LinearLayout.LayoutParams(-1, dp(1)))
            holidayRow(holiday, today, list)
        }
        if (all.size > shown.size) {
            button(getString(R.string.holiday_show_all, all.size), primary = false) {
                showAllHolidays = true
                onRemindersChanged()
            }
        }
    }

    private fun holidayRow(holiday: Holiday, today: LocalDate, parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        parent.addView(row)
        val locale = appLocale()
        val dateBox = column().apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (holiday.special) 0x30FFD98C else 0x404E407B)
                setStroke(dp(1), if (holiday.special) 0x88FFD98C.toInt() else 0x668C8CD8)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        row.addView(dateBox, LinearLayout.LayoutParams(dp(54), dp(54)))
        label(holiday.date.dayOfMonth.toString(), 19f, dateBox, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
        }
        label(holiday.date.format(DateTimeFormatter.ofPattern("MMM", locale)).uppercase(locale), 11f, dateBox,
            color = mutedColor).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
            includeFontPadding = false
        }
        val texts = column()
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        label(holidays.displayName(holiday), 15.5f, texts, bold = true).apply {
            setPadding(0, 0, 0, dp(2))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val parts = mutableListOf(
            if (holiday.date == today || holiday.date == today.plusDays(1)) whenText(holiday.date, today)
            else holiday.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
                .replaceFirstChar { it.titlecase(locale) }
        )
        if (holiday.days > 1) parts += resources.getQuantityString(R.plurals.day_count, holiday.days, holiday.days)
        if (holiday.special) parts += getString(R.string.holiday_special_tag)
        if (holiday.tentative) parts += getString(R.string.holiday_tentative)
        label(parts.joinToString(" · "), 13f, texts, color = mutedColor).setPadding(0, 0, 0, 0)
        row.contentDescription = listOf(holidays.displayName(holiday),
            holiday.date.format(dateFormat()), parts.joinToString(", ")).joinToString(", ")
    }

    // endregion
}
