package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)
    private fun day(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(zone)

    @Test fun oneTimeReminderFiresOnceAndThenStops() {
        val reminder = Reminder(title = "Dentist", date = LocalDate.parse("2026-12-03"), time = LocalTime.of(14, 30))
        assertEquals(at("2026-12-03", "14:30"), day(reminder.nextNotice(at("2026-12-01", "08:00"))!!.at))
        assertNull(reminder.nextNotice(at("2026-12-03", "15:00")))
    }

    @Test fun earlyNoticeComesBeforeTheEvent() {
        val reminder = Reminder(title = "Birthday", date = LocalDate.parse("2026-12-10"), time = LocalTime.of(9, 0),
            repeat = ReminderRepeat.YEARLY, leadDays = 7)
        val first = reminder.nextNotice(at("2026-12-01", "08:00"))!!
        assertTrue(first.early)
        assertEquals(at("2026-12-03", "09:00"), day(first.at))
        val second = reminder.nextNotice(at("2026-12-03", "09:01"))!!
        assertFalse(second.early)
        assertEquals(at("2026-12-10", "09:00"), day(second.at))
    }

    @Test fun monthlyAndYearlyKeepTheDayOrUseTheMonthsLastDay() {
        val monthly = Reminder(title = "Rent", date = LocalDate.parse("2026-01-31"), time = LocalTime.NOON, repeat = ReminderRepeat.MONTHLY)
        assertEquals(LocalDate.parse("2026-02-28"), monthly.nextEvent(at("2026-02-01", "00:00"))!!.toLocalDate())
        assertEquals(LocalDate.parse("2026-03-31"), monthly.nextEvent(at("2026-03-01", "00:00"))!!.toLocalDate())
        val leap = Reminder(title = "Leap", date = LocalDate.parse("2028-02-29"), time = LocalTime.NOON, repeat = ReminderRepeat.YEARLY)
        assertEquals(LocalDate.parse("2029-02-28"), leap.nextEvent(at("2028-03-01", "00:00"))!!.toLocalDate())
    }

    @Test fun weeklyUsesTheChosenDays() {
        val reminder = Reminder(title = "Gym", date = LocalDate.parse("2026-11-16"), time = LocalTime.of(18, 0),
            repeat = ReminderRepeat.WEEKLY, days = setOf(1, 3))
        // Wednesday 18 November 2026 at 19:00 → next is Monday 23 November.
        assertEquals(at("2026-11-23", "18:00"), reminder.nextEvent(at("2026-11-18", "19:00")))
    }

    @Test fun disabledRemindersHaveNoNotice() {
        val reminder = Reminder(title = "Off", date = LocalDate.parse("2030-01-01"), time = LocalTime.NOON, enabled = false)
        assertNull(reminder.nextNotice(at("2026-01-01", "00:00")))
    }

    @Test fun remindersSurviveJsonRoundTrip() {
        val reminder = Reminder(title = "Pay \"bills\"", note = "Electricity", date = LocalDate.parse("2026-12-01"),
            time = LocalTime.of(7, 5), repeat = ReminderRepeat.WEEKLY, days = setOf(2, 5), leadDays = 1, ring = true)
        assertEquals(reminder, Reminders.fromJson(org.json.JSONObject(Reminders.toJson(reminder).toString())))
    }
}

class HolidaysTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun multiDayHolidaysAreMergedAndTentativeIsMarked() {
        val holidays = HolidayParser.parse(resource("nager_tr_2027.json"))
        val kurban = holidays.single { it.name == "Eid al-Adha" }
        assertEquals(LocalDate.parse("2027-05-16"), kurban.date)
        assertEquals(4, kurban.days)
        assertTrue(kurban.tentative)
        assertEquals("Kurban Bayramı", kurban.localName)
        // Atatürk Day shares 19 May with the fourth day of Kurban Bayramı and stays its own entry.
        assertTrue(holidays.any { it.localName.startsWith("Atatürk") && it.date == LocalDate.parse("2027-05-19") })
    }

    @Test fun turkeyUsesDiyanetDatesWherePublished() {
        val corrected = HolidayParser.correctTurkey(2027, HolidayParser.parse(resource("nager_tr_2027.json")))
        val ramazan = corrected.single { it.localName == "Ramazan Bayramı" }
        assertEquals(LocalDate.parse("2027-03-09"), ramazan.date)
        assertEquals(3, ramazan.days)
        assertFalse(ramazan.tentative)
        assertTrue(corrected.any { it.localName == "Kurban Bayramı Arifesi" && it.date == LocalDate.parse("2027-05-15") })
        assertEquals(1, corrected.count { it.localName == "Kurban Bayramı" })
    }

    @Test fun onlyNationwideHolidaysAreKept() {
        val holidays = HolidayParser.parse(resource("nager_de_2026.json"))
        assertTrue(holidays.any { it.localName == "Tag der Deutschen Einheit" })
        // Regional days such as Heilige Drei Könige are left out.
        assertFalse(holidays.any { it.localName == "Heilige Drei Könige" })
    }

    @Test fun specialDaysFollowEachCountry() {
        assertEquals(LocalDate.parse("2026-04-05"), SpecialDays.easter(2026))
        assertEquals(LocalDate.parse("2027-03-28"), SpecialDays.easter(2027))
        assertEquals(LocalDate.parse("2030-04-21"), SpecialDays.easter(2030))
        assertEquals(LocalDate.parse("2026-05-10"), SpecialDays.mothersDay("TR", 2026))
        assertEquals(LocalDate.parse("2026-06-21"), SpecialDays.fathersDay("TR", 2026))
        assertEquals(LocalDate.parse("2026-03-15"), SpecialDays.mothersDay("GB", 2026))
        assertEquals(LocalDate.parse("2026-05-14"), SpecialDays.fathersDay("DE", 2026))
        assertEquals(LocalDate.parse("2026-05-31"), SpecialDays.mothersDay("FR", 2026))
        assertTrue(SpecialDays.forCountry("TR", 2026).any { it.second == SpecialDays.Kind.TEACHERS })
    }
}
