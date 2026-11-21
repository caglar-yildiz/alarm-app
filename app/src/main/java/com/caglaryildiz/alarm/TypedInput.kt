package com.caglaryildiz.alarm

import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/** Parsing for typed times and dates, kept free of Android so it can be unit-tested. */
internal object TypedInput {
    enum class Field { DAY, MONTH, YEAR }

    sealed class DateResult {
        data class Ok(val date: LocalDate) : DateResult()
        object Invalid : DateResult()
        object TooEarly : DateResult()
    }

    /** "7" and "07" are both fine; anything outside 0–23 / 0–59 is rejected. */
    fun time(hour: String, minute: String): LocalTime? {
        val h = hour.trim().toIntOrNull() ?: return null
        val m = minute.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    /** A two-digit year means this century ("27" is 2027). Dates before [earliest] are refused. */
    fun date(day: String, month: String, year: String, earliest: LocalDate): DateResult {
        val d = day.trim().toIntOrNull() ?: return DateResult.Invalid
        val m = month.trim().toIntOrNull() ?: return DateResult.Invalid
        var y = year.trim().ifEmpty { earliest.year.toString() }.toIntOrNull() ?: return DateResult.Invalid
        if (y in 0..99) y += 2000
        val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return DateResult.Invalid
        return if (date.isBefore(earliest)) DateResult.TooEarly else DateResult.Ok(date)
    }

    /** Day, month and year in the order people write them in [locale] (e.g. 21.11.2026, 11/21/2026, 2026/11/21). */
    fun dateOrder(locale: Locale): List<Field> {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        val positions = listOf(Field.DAY to pattern.indexOf('d'), Field.MONTH to pattern.indexOf('M'),
            Field.YEAR to pattern.indexOf('y'))
        if (positions.any { it.second < 0 }) return listOf(Field.DAY, Field.MONTH, Field.YEAR)
        return positions.sortedBy { it.second }.map { it.first }
    }
}
