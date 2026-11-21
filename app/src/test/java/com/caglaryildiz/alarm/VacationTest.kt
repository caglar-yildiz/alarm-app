package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class VacationTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    // Saturday 21 November 2026, 22:00.
    private val now = ZonedDateTime.of(2026, 11, 21, 22, 0, 0, 0, zone)
    private val weekdays = Alarm(time = LocalTime.of(7, 0), days = (1..5).toSet())
    private fun date(millis: Long?) = Instant.ofEpochMilli(millis!!).atZone(zone).toLocalDate()

    @Test fun noVacationRingsOnTheNextWeekday() {
        assertEquals(LocalDate.of(2026, 11, 23), date(nextOutside(weekdays, now, null)))
    }

    @Test fun aVacationStartingLaterLeavesTheNextRingAlone() {
        val vacation = Vacation(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 30))
        assertEquals(LocalDate.of(2026, 11, 23), date(nextOutside(weekdays, now, vacation)))
    }

    @Test fun ringsAgainOnTheFirstWeekdayAfterTheVacation() {
        val vacation = Vacation(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27))
        assertEquals(LocalDate.of(2026, 11, 30), date(nextOutside(weekdays, now, vacation)))
    }

    @Test fun oneTimeAlarmsStillRingDuringAVacation() {
        val once = Alarm(time = LocalTime.of(9, 0), date = LocalDate.of(2026, 11, 24))
        val vacation = Vacation(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27))
        assertEquals(LocalDate.of(2026, 11, 24), date(nextOutside(once, now, vacation)))
    }

    @Test fun skipAndVacationTogether() {
        // Monday is skipped, Tuesday to Thursday are vacation: Friday is next.
        val skipped = weekdays.copy(skipDate = LocalDate.of(2026, 11, 23))
        val vacation = Vacation(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 26))
        assertEquals(LocalDate.of(2026, 11, 27), date(nextOutside(skipped, now, vacation)))
    }
}
