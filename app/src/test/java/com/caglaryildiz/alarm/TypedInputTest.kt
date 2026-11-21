package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class TypedInputTest {
    private val today = LocalDate.of(2026, 11, 21)

    @Test fun acceptsShortAndPaddedTimes() {
        assertEquals(LocalTime.of(7, 5), TypedInput.time("7", "05"))
        assertEquals(LocalTime.of(23, 59), TypedInput.time("23", "59"))
        assertEquals(LocalTime.of(6, 0), TypedInput.time("06", ""))
    }

    @Test fun rejectsImpossibleTimes() {
        assertNull(TypedInput.time("24", "00"))
        assertNull(TypedInput.time("12", "60"))
        assertNull(TypedInput.time("", "30"))
    }

    @Test fun parsesDatesAndTwoDigitYears() {
        assertEquals(TypedInput.DateResult.Ok(LocalDate.of(2027, 1, 3)), TypedInput.date("3", "1", "27", today))
        assertEquals(TypedInput.DateResult.Ok(LocalDate.of(2026, 12, 24)), TypedInput.date("24", "12", "", today))
    }

    @Test fun rejectsMissingAndPastDates() {
        assertEquals(TypedInput.DateResult.Invalid, TypedInput.date("31", "2", "2027", today))
        assertEquals(TypedInput.DateResult.Invalid, TypedInput.date("x", "2", "2027", today))
        assertEquals(TypedInput.DateResult.TooEarly, TypedInput.date("20", "11", "2026", today))
        assertEquals(TypedInput.DateResult.Ok(today), TypedInput.date("21", "11", "2026", today))
    }

    @Test fun followsTheLocaleFieldOrder() {
        val dmy = listOf(TypedInput.Field.DAY, TypedInput.Field.MONTH, TypedInput.Field.YEAR)
        assertEquals(dmy, TypedInput.dateOrder(Locale.forLanguageTag("tr")))
        assertEquals(dmy, TypedInput.dateOrder(Locale.GERMANY))
        assertEquals(listOf(TypedInput.Field.MONTH, TypedInput.Field.DAY, TypedInput.Field.YEAR), TypedInput.dateOrder(Locale.US))
        assertEquals(listOf(TypedInput.Field.YEAR, TypedInput.Field.MONTH, TypedInput.Field.DAY), TypedInput.dateOrder(Locale.JAPAN))
    }
}
