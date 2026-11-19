package com.caglaryildiz.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.Executors

/** A public holiday or special day. Multi-day holidays such as Kurban Bayramı are one entry with [days]. */
internal data class Holiday(
    val date: LocalDate,
    val localName: String,
    val name: String,
    val days: Int = 1,
    val tentative: Boolean = false,
    val special: Boolean = false
) {
    val end: LocalDate get() = date.plusDays(days - 1L)
}

/** Reads the Nager.Date `PublicHolidays` response: nationwide public holidays only, multi-day holidays merged. */
internal object HolidayParser {
    private val tentative = Regex("""\s*\(Tentative Date\)""", RegexOption.IGNORE_CASE)
    private val dayWord = Regex("""\s+(First|Second|Third|Fourth|Fifth|\d+(st|nd|rd|th)?)\s+Day$""", RegexOption.IGNORE_CASE)
    private val localDay = Regex("""\s*\d+\.\s*(Gün|Tag|Jour|Día|Dia|Giorno|Dag)$""", RegexOption.IGNORE_CASE)

    fun parse(json: String): List<Holiday> {
        val items = JSONArray(json)
        val raw = List(items.length()) { items.getJSONObject(it) }
            .filter { item ->
                val types = item.optJSONArray("types")
                val public = types == null || (0 until types.length()).any { types.optString(it) == "Public" }
                item.optBoolean("global", true) && public
            }
            .mapNotNull { item ->
                val date = runCatching { LocalDate.parse(item.getString("date")) }.getOrNull() ?: return@mapNotNull null
                val local = item.optString("localName")
                val english = item.optString("name")
                Holiday(
                    date = date,
                    localName = local.replace(tentative, "").trim(),
                    name = english.replace(tentative, "").trim(),
                    tentative = tentative.containsMatchIn(local) || tentative.containsMatchIn(english)
                )
            }
            .sortedBy { it.date }
        return merge(raw)
    }

    /** Joins consecutive days of the same holiday ("Eid al-Fitr First/Second/Third Day") into one entry. */
    fun merge(list: List<Holiday>): List<Holiday> {
        val result = mutableListOf<Holiday>()
        for (holiday in list.sortedBy { it.date }) {
            val base = holiday.name.replace(dayWord, "")
            // Another holiday can share a date ("Kurban Bayramı 4. Gün" on 19 May), so look past the last entry.
            val index = result.indexOfLast { !it.special && it.name == base && it.end.plusDays(1) == holiday.date }
            if (index >= 0) {
                val previous = result[index]
                result[index] = previous.copy(days = previous.days + 1, tentative = previous.tentative || holiday.tentative)
            } else {
                result += holiday.copy(name = base, localName = holiday.localName.replace(localDay, "").trim())
            }
        }
        return result
    }

    /**
     * Türkiye's religious holidays move with the lunar calendar and Nager.Date only estimates them
     * (it lists Ramazan Bayramı 2027 a day late). Years with dates published by Diyanet use those instead.
     */
    fun correctTurkey(year: Int, list: List<Holiday>): List<Holiday> {
        val official = TURKEY_BAYRAMS[year] ?: return list
        val kept = list.filterNot { it.name.startsWith("Eid al-", ignoreCase = true) || "Bayramı Arife" in it.localName }
        return (kept + official).sortedBy { it.date }
    }

    private val TURKEY_BAYRAMS = mapOf(
        2026 to listOf(
            Holiday(LocalDate.of(2026, 3, 19), "Ramazan Bayramı Arifesi", "Eid al-Fitr Eve"),
            Holiday(LocalDate.of(2026, 3, 20), "Ramazan Bayramı", "Eid al-Fitr", days = 3),
            Holiday(LocalDate.of(2026, 5, 26), "Kurban Bayramı Arifesi", "Eid al-Adha Eve"),
            Holiday(LocalDate.of(2026, 5, 27), "Kurban Bayramı", "Eid al-Adha", days = 4)
        ),
        2027 to listOf(
            Holiday(LocalDate.of(2027, 3, 8), "Ramazan Bayramı Arifesi", "Eid al-Fitr Eve"),
            Holiday(LocalDate.of(2027, 3, 9), "Ramazan Bayramı", "Eid al-Fitr", days = 3),
            Holiday(LocalDate.of(2027, 5, 15), "Kurban Bayramı Arifesi", "Eid al-Adha Eve"),
            Holiday(LocalDate.of(2027, 5, 16), "Kurban Bayramı", "Eid al-Adha", days = 4)
        )
    )
}

/** Days people celebrate without a day off. Dates follow each country's own custom. */
internal object SpecialDays {
    enum class Kind(val nameRes: Int) {
        VALENTINES(R.string.special_valentines),
        MOTHERS(R.string.special_mothers),
        FATHERS(R.string.special_fathers),
        PARENTS(R.string.special_parents),
        TEACHERS(R.string.special_teachers)
    }

    fun forCountry(country: String, year: Int): List<Pair<LocalDate, Kind>> {
        val days = mutableListOf(LocalDate.of(year, 2, 14) to Kind.VALENTINES)
        mothersDay(country, year)?.let { days += it to (if (country == "KR") Kind.PARENTS else Kind.MOTHERS) }
        fathersDay(country, year)?.let { days += it to Kind.FATHERS }
        if (country == "TR") days += LocalDate.of(year, 11, 24) to Kind.TEACHERS
        return days.sortedBy { it.first }
    }

    private fun nthSunday(year: Int, month: Int, n: Int) =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.SUNDAY))

    private fun lastSunday(year: Int, month: Int) =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))

    internal fun mothersDay(country: String, year: Int): LocalDate? = when (country) {
        "TR", "US", "CA", "AU", "NZ", "DE", "AT", "CH", "IT", "NL", "BE", "JP", "BR", "HK", "TW", "DK", "FI" -> nthSunday(year, 5, 2)
        "FR" -> lastSunday(year, 5).let { if (it == easter(year).plusDays(49)) nthSunday(year, 6, 1) else it }
        "ES", "PT" -> nthSunday(year, 5, 1)
        "MX" -> LocalDate.of(year, 5, 10)
        "GB", "IE" -> easter(year).minusDays(21)
        "KR" -> LocalDate.of(year, 5, 8)
        "TH" -> LocalDate.of(year, 8, 12)
        "SA", "AE", "EG", "JO", "LB", "QA", "KW", "BH", "OM" -> LocalDate.of(year, 3, 21)
        "ID" -> LocalDate.of(year, 12, 22)
        else -> null
    }

    internal fun fathersDay(country: String, year: Int): LocalDate? = when (country) {
        "TR", "US", "CA", "GB", "IE", "FR", "NL", "JP", "HK", "MX" -> nthSunday(year, 6, 3)
        "DE" -> easter(year).plusDays(39)
        "IT", "ES", "PT" -> LocalDate.of(year, 3, 19)
        "BR" -> nthSunday(year, 8, 2)
        "AU", "NZ" -> nthSunday(year, 9, 1)
        "AT", "BE" -> nthSunday(year, 6, 2)
        "CH" -> nthSunday(year, 6, 1)
        "TW" -> LocalDate.of(year, 8, 8)
        "TH" -> LocalDate.of(year, 12, 5)
        "ID" -> LocalDate.of(year, 11, 12)
        else -> null
    }

    /** Western (Gregorian) Easter Sunday, the anchor for Mothering Sunday, Pentecost and Ascension. */
    internal fun easter(year: Int): LocalDate {
        // Meeus/Jones/Butcher algorithm.
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val n = h + l - 7 * m + 114
        return LocalDate.of(year, n / 31, n % 31 + 1)
    }
}

/**
 * Public holiday reminders: settings, an on-device cache of Nager.Date (free, no key) and one scheduled
 * notice for the next holiday. The yearly lists are refreshed when the Reminders tab opens.
 */
internal class Holidays(context: Context) {
    private val app = AppLanguage.wrap(context.applicationContext)
    private val prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("holidays", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(AlarmManager::class.java)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply(); schedule() }
    var country: String
        get() = prefs.getString("country", null) ?: defaultCountry()
        set(value) { prefs.edit().putString("country", value).apply(); schedule() }
    var time: LocalTime
        get() = LocalTime.of(prefs.getInt("hour", 9), prefs.getInt("minute", 0))
        set(value) { prefs.edit().putInt("hour", value.hour).putInt("minute", value.minute).apply(); schedule() }
    /** Premium: also a heads-up the day before. */
    var dayBefore: Boolean
        get() = prefs.getBoolean("dayBefore", false)
        set(value) { prefs.edit().putBoolean("dayBefore", value).apply(); schedule() }
    /** Premium: Mother's Day, Father's Day, Valentine's Day and similar. */
    var specialDays: Boolean
        get() = prefs.getBoolean("special", false)
        set(value) { prefs.edit().putBoolean("special", value).apply(); schedule() }

    private fun defaultCountry(): String {
        val locale = app.appLocale().country.ifEmpty { Locale.getDefault().country }
        return locale.ifEmpty { "TR" }.uppercase(Locale.ROOT)
    }

    /** Countries Nager.Date covers, as ISO codes; empty until the list was loaded once. */
    val countries: List<String>
        get() = prefs.getString("countries", null)?.split(',')?.filter { it.length == 2 }.orEmpty()

    /** True when the service answered that it has no data for this country. */
    fun unsupported(code: String = country) = prefs.getBoolean("unsupported:$code", false)

    fun hasData(code: String = country, year: Int = LocalDate.now().year) = prefs.contains("cache:$code:$year")

    /** Holidays from today on, for this and next year, with special days when they are on and unlocked. */
    fun upcoming(today: LocalDate = LocalDate.now(), includeSpecial: Boolean = specialDays && PremiumAccess(app).unlocked): List<Holiday> {
        val code = country
        val years = listOf(today.year, today.year + 1)
        val list = years.flatMap { year ->
            val cached = prefs.getString("cache:$code:$year", null)
            val parsed = cached?.let { runCatching { HolidayParser.parse(it) }.getOrNull() }.orEmpty()
            val holidays = if (code == "TR") HolidayParser.correctTurkey(year, parsed) else parsed
            val special = if (includeSpecial) SpecialDays.forCountry(code, year).map { (date, kind) ->
                val name = app.getString(kind.nameRes)
                Holiday(date, name, name, special = true)
            } else emptyList()
            holidays + special
        }
        return list.filter { !it.end.isBefore(today) }.sortedBy { it.date }
    }

    /** A holiday's name as people in the app's language know it: the local name, or English abroad. */
    fun displayName(holiday: Holiday): String {
        if (holiday.special) return holiday.localName
        val language = app.appLocale().language
        val local = Locale.getAvailableLocales().filter { it.country == country }.map { it.language }.toSet()
        return if (language in local || holiday.name.isBlank()) holiday.localName else holiday.name
    }

    // region Scheduling

    private data class Notice(val at: Long, val holiday: Holiday, val early: Boolean)

    private fun notices(now: ZonedDateTime): List<Notice> {
        if (!enabled) return emptyList()
        val premium = PremiumAccess(app).unlocked
        return upcoming(now.toLocalDate().minusDays(1)).flatMap { holiday ->
            val onDay = holiday.date.atTime(time).atZone(now.zone)
            listOfNotNull(
                Notice(onDay.toInstant().toEpochMilli(), holiday, early = false),
                if (dayBefore && premium) Notice(onDay.minusDays(1).toInstant().toEpochMilli(), holiday, early = true) else null
            )
        }.filter { it.at > now.toInstant().toEpochMilli() }.sortedBy { it.at }
    }

    fun schedule() {
        val next = notices(ZonedDateTime.now()).firstOrNull()
        if (next == null) {
            manager.cancel(operation())
            return
        }
        Reminders.scheduleExact(manager, next.at, operation(next.at))
    }

    fun fire(at: Long) {
        val notice = notices(ZonedDateTime.now().minusSeconds(90)).firstOrNull { kotlin.math.abs(it.at - at) <= 60_000 }
        if (notice != null) notify(notice)
        schedule()
    }

    private fun notify(notice: Notice) {
        val notifications = app.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.channel_holidays),
            NotificationManager.IMPORTANCE_DEFAULT))
        if (!Reminders.canNotify(app)) return
        val name = displayName(notice.holiday)
        val text = when {
            notice.early -> app.getString(R.string.holiday_notice_tomorrow)
            notice.holiday.days > 1 -> app.resources.getQuantityString(R.plurals.holiday_notice_days,
                notice.holiday.days, notice.holiday.days)
            else -> app.getString(R.string.holiday_notice_today)
        }
        val open = PendingIntent.getActivity(app, 3, Intent(app, MainActivity::class.java).putExtra("remindersTab", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        notifications.notify("holiday", NOTIFICATION_ID, Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(name)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_EVENT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun operation(at: Long = 0) = PendingIntent.getBroadcast(app, 1,
        Intent(app, ReminderReceiver::class.java).setAction(FIRE).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or if (at > 0) PendingIntent.FLAG_UPDATE_CURRENT else 0)

    // endregion

    // region Loading

    /**
     * Downloads what is missing or older than a month: the country list and this and next year's holidays.
     * Returns false, without calling [done], when there is nothing to load or a recent attempt failed.
     */
    fun refresh(force: Boolean = false, done: (Boolean) -> Unit): Boolean {
        val code = country
        val year = LocalDate.now().year
        val now = System.currentTimeMillis()
        val stale = { key: String -> force || now - prefs.getLong("fetched:$key", 0) > 30L * 24 * 3600_000 }
        val years = listOf(year, year + 1).filter { stale("$code:$it") }
        val needCountries = countries.isEmpty() || stale("countries")
        if (years.isEmpty() && !needCountries) return false
        // Offline: try again at most every two minutes instead of on every screen update.
        if (!force && now - lastAttempt < 2 * 60_000L) return false
        lastAttempt = now
        val main = Handler(Looper.getMainLooper())
        executor.execute {
            var ok = true
            if (needCountries) runCatching {
                val (status, body) = get("https://date.nager.at/api/v3/AvailableCountries")
                if (status == 200) {
                    val items = JSONArray(body)
                    val codes = List(items.length()) { items.getJSONObject(it).getString("countryCode") }
                    prefs.edit().putString("countries", codes.joinToString(",")).putLong("fetched:countries", now).apply()
                }
            }
            for (y in years) {
                try {
                    val (status, body) = get("https://date.nager.at/api/v3/PublicHolidays/$y/$code")
                    when (status) {
                        200 -> {
                            HolidayParser.parse(body)
                            prefs.edit().putString("cache:$code:$y", body).putLong("fetched:$code:$y", now)
                                .putBoolean("unsupported:$code", false).apply()
                        }
                        204, 404 -> prefs.edit().putString("cache:$code:$y", "[]").putLong("fetched:$code:$y", now)
                            .putBoolean("unsupported:$code", true).apply()
                        else -> ok = false
                    }
                } catch (_: IOException) {
                    ok = false
                } catch (_: Exception) {
                    ok = false
                }
            }
            main.post {
                schedule()
                done(ok)
            }
        }
        return true
    }

    private fun get(address: String): Pair<Int, String> {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            status to if (status == 200) connection.inputStream.bufferedReader().use { it.readText() } else ""
        } finally {
            connection.disconnect()
        }
    }

    // endregion

    companion object {
        const val FIRE = "com.caglaryildiz.alarm.HOLIDAY"
        private const val CHANNEL = "holidays"
        private const val NOTIFICATION_ID = 4
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var lastAttempt = 0L
    }
}
