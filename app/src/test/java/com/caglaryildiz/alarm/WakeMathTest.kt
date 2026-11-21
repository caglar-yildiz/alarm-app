package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WakeMathTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun entry(daysAgo: Long, snoozes: Int = 0, rerung: Boolean = false, missed: Boolean = false, seconds: Int = 30) =
        WakeEntry(today.minusDays(daysAgo), "a", snoozes, seconds, rerung, missed, 0)

    @Test fun streakCountsConsecutiveOnTimeDays() {
        assertEquals(3, WakeMath.streak(listOf(entry(0), entry(1), entry(2), entry(3, snoozes = 1)), today))
    }

    @Test fun daysWithoutAlarmsDoNotBreakTheStreak() {
        // Saturday and Sunday off: Friday and Monday still form one streak.
        assertEquals(2, WakeMath.streak(listOf(entry(3), entry(0)), today))
    }

    @Test fun todayWithoutAnAlarmYetKeepsYesterdaysStreak() {
        assertEquals(2, WakeMath.streak(listOf(entry(1), entry(2)), today))
    }

    @Test fun rerungOrMissedDaysAreLate() {
        assertEquals(0, WakeMath.streak(listOf(entry(1), entry(0, rerung = true)), today))
        assertEquals(DayResult.LATE, WakeMath.dayResult(listOf(entry(0, missed = true)), today))
    }

    @Test fun oneOnTimeAlarmMakesTheDayOnTime() {
        assertEquals(DayResult.ON_TIME, WakeMath.dayResult(listOf(entry(0, snoozes = 2), entry(0)), today))
    }

    @Test fun summaryCoversThirtyDaysAndTheLastWeek() {
        val list = listOf(entry(40), entry(5, snoozes = 2, seconds = 90), entry(1, seconds = 30), entry(0, missed = true))
        val summary = WakeMath.summary(list, today)
        assertEquals(1, summary.onTimeDays)
        assertEquals(3, summary.alarmDays)
        assertEquals(2, summary.snoozes)
        assertEquals(60, summary.averageSeconds)
        assertEquals(7, summary.week.size)
        assertEquals(today, summary.week.last().first)
        assertEquals(DayResult.LATE, summary.week.last().second)
        assertEquals(4, summary.total)
    }

    @Test fun bestStreakFindsTheLongestRun() {
        val list = listOf(entry(6), entry(5), entry(4), entry(3, snoozes = 1), entry(2), entry(1))
        assertEquals(3, WakeMath.bestStreak(list))
    }
}
