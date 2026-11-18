package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ForecastParserTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun step(time: String, temp: Double, symbol: String?, precip: Double, hourly: Boolean = true): String {
        val period = if (hourly) "next_1_hours" else "next_6_hours"
        val next = if (symbol == null) "" else
            """, "$period": {"summary": {"symbol_code": "$symbol"}, "details": {"precipitation_amount": $precip}}"""
        return """{"time": "$time", "data": {"instant": {"details": {"air_temperature": $temp,
            "relative_humidity": 81.4, "wind_speed": 5.0}}$next}}"""
    }

    private fun forecast(vararg steps: String) =
        """{"properties": {"meta": {"updated_at": "2026-11-18T10:00:00Z"}, "timeseries": [${steps.joinToString()}]}}"""

    @Test
    fun mapsSymbolCodes() {
        assertEquals(Sky.PARTLY, Sky.fromSymbol("partlycloudy_day"))
        assertEquals(Sky.PARTLY, Sky.fromSymbol("fair_night"))
        assertEquals(Sky.CLOUDY, Sky.fromSymbol("cloudy"))
        assertEquals(Sky.RAIN, Sky.fromSymbol("heavyrainshowers_night"))
        assertEquals(Sky.THUNDER, Sky.fromSymbol("rainandthunder"))
        assertEquals(Sky.SLEET, Sky.fromSymbol("lightsleetshowers_day"))
        assertEquals(Sky.SNOW, Sky.fromSymbol("snow"))
        assertEquals(Sky.CLEAR, Sky.fromSymbol("clearsky_polartwilight"))
        assertEquals(null, Sky.fromSymbol("unknown"))
        assertTrue(Sky.isNight("clearsky_night"))
        assertFalse(Sky.isNight("clearsky_day"))
    }

    @Test
    fun startsAtTheCurrentHourAndConvertsUnits() {
        val json = forecast(
            step("2026-11-18T08:00:00Z", 10.0, "cloudy", 0.0),
            step("2026-11-18T09:00:00Z", 12.4, "clearsky_day", 0.0),
            step("2026-11-18T10:00:00Z", 13.0, "rain", 1.2),
            step("2026-11-18T11:00:00Z", 13.5, "rain", 0.4)
        )
        val result = ForecastParser.parse(json, zone, Instant.parse("2026-11-18T09:30:00Z"))
        assertEquals(12.4, result.now.temp, 0.0)
        assertEquals(Sky.CLEAR, result.now.sky)
        assertEquals(18, result.now.windKmh)
        assertEquals(81, result.now.humidity)
        assertEquals(3, result.hours.size)
        assertEquals(12, result.hours.first().time.hour)
        assertEquals(1.2, result.hours[1].precip, 0.0)
        assertEquals(10.0, result.days.first().min, 0.0)
        assertEquals(13.5, result.days.first().max, 0.0)
    }

    @Test
    fun groupsDaysByLocalDateAndUsesSixHourPrecipitation() {
        val json = forecast(
            // 20:00 and 21:00 UTC are 23:00 on the 18th and 00:00 on the 19th in Istanbul.
            step("2026-11-18T20:00:00Z", 9.0, "clearsky_night", 0.0),
            step("2026-11-18T21:00:00Z", 8.0, "rain", 0.0, hourly = false),
            step("2026-11-19T03:00:00Z", 6.0, "rain", 2.0, hourly = false),
            step("2026-11-19T09:00:00Z", 14.0, "lightrain", 1.0, hourly = false),
            step("2026-11-19T15:00:00Z", 11.0, null, 0.0, hourly = false)
        )
        val result = ForecastParser.parse(json, zone, Instant.parse("2026-11-18T20:10:00Z"))
        assertEquals(2, result.days.size)
        val tomorrow = result.days[1]
        assertEquals(LocalDate.of(2026, 11, 19), tomorrow.date)
        assertEquals(6.0, tomorrow.min, 0.0)
        assertEquals(14.0, tomorrow.max, 0.0)
        assertEquals(3.0, tomorrow.precip, 0.0)
        assertEquals(Sky.RAIN, tomorrow.sky)
    }

    @Test
    fun dryDaysShowTheirMostCommonSky() {
        val skies = listOf(Sky.CLEAR, Sky.PARTLY, Sky.PARTLY, Sky.RAIN)
        assertEquals(Sky.PARTLY, ForecastParser.daySky(skies, precip = 0.1))
        assertEquals(Sky.RAIN, ForecastParser.daySky(skies, precip = 2.0))
    }
}
