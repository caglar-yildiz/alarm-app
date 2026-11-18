package com.caglaryildiz.alarm

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.Executors

/** Simplified sky conditions, ordered by how much they matter for planning the day. */
internal enum class Sky(val labelRes: Int) {
    CLEAR(R.string.sky_clear),
    PARTLY(R.string.sky_partly),
    CLOUDY(R.string.sky_cloudy),
    FOG(R.string.sky_fog),
    RAIN(R.string.sky_rain),
    SLEET(R.string.sky_sleet),
    SNOW(R.string.sky_snow),
    THUNDER(R.string.sky_thunder);

    val wet get() = ordinal >= RAIN.ordinal

    companion object {
        /** Maps a MET Norway symbol code such as `lightrainshowers_night` to a [Sky]. */
        fun fromSymbol(symbol: String?): Sky? {
            val code = symbol?.substringBefore('_')?.lowercase() ?: return null
            return when {
                code.isEmpty() -> null
                "thunder" in code -> THUNDER
                "sleet" in code -> SLEET
                "snow" in code -> SNOW
                "rain" in code -> RAIN
                "fog" in code -> FOG
                code == "partlycloudy" || code == "fair" -> PARTLY
                "cloudy" in code -> CLOUDY
                code == "clearsky" -> CLEAR
                else -> null
            }
        }

        fun isNight(symbol: String?) = symbol?.endsWith("_night") == true
    }
}

internal data class WeatherPlace(val name: String, val region: String, val lat: Double, val lon: Double)

internal data class WeatherNow(
    val temp: Double,
    val sky: Sky,
    val night: Boolean,
    val humidity: Int,
    val windKmh: Int
)

internal data class WeatherHour(
    val time: ZonedDateTime,
    val temp: Double,
    val sky: Sky,
    val night: Boolean,
    val precip: Double
)

internal data class WeatherDay(
    val date: LocalDate,
    val min: Double,
    val max: Double,
    val sky: Sky,
    val precip: Double
)

internal data class Forecast(
    val updated: Instant,
    val now: WeatherNow,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>
)

/** Reads the MET Norway `locationforecast/2.0/compact` response. */
internal object ForecastParser {
    private class Step(
        val time: Instant,
        val temp: Double,
        val humidity: Double,
        val wind: Double,
        val symbol1: String?,
        val precip1: Double?,
        val symbol6: String?,
        val precip6: Double?,
        val symbol12: String?
    ) {
        val symbol get() = symbol1 ?: symbol6 ?: symbol12
    }

    fun parse(json: String, zone: ZoneId, now: Instant): Forecast {
        val properties = JSONObject(json).getJSONObject("properties")
        val updated = Instant.parse(properties.getJSONObject("meta").getString("updated_at"))
        val series = properties.getJSONArray("timeseries")
        val steps = List(series.length()) { index ->
            val item = series.getJSONObject(index)
            val data = item.getJSONObject("data")
            val instant = data.getJSONObject("instant").getJSONObject("details")
            Step(
                time = Instant.parse(item.getString("time")),
                temp = instant.getDouble("air_temperature"),
                humidity = instant.optDouble("relative_humidity", Double.NaN),
                wind = instant.optDouble("wind_speed", Double.NaN),
                symbol1 = data.symbol("next_1_hours"),
                precip1 = data.precipitation("next_1_hours"),
                symbol6 = data.symbol("next_6_hours"),
                precip6 = data.precipitation("next_6_hours"),
                symbol12 = data.symbol("next_12_hours")
            )
        }
        // The forecast starts at the current hour: keep the latest step that is not in the future.
        val start = steps.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)
        val upcoming = steps.drop(start)
        require(upcoming.isNotEmpty()) { "Empty forecast" }

        fun precipitation(list: List<Step>, index: Int): Double {
            val step = list[index]
            val next = list.getOrNull(index + 1) ?: return 0.0
            val hours = (next.time.epochSecond - step.time.epochSecond) / 3600
            val amount = if (hours <= 1) step.precip1 else step.precip6 ?: step.precip1
            return amount ?: 0.0
        }

        val current = upcoming.first()
        val currentSymbol = current.symbol
        val weatherNow = WeatherNow(
            temp = current.temp,
            sky = Sky.fromSymbol(currentSymbol) ?: Sky.CLOUDY,
            night = Sky.isNight(currentSymbol),
            humidity = if (current.humidity.isNaN()) 0 else current.humidity.toInt(),
            windKmh = if (current.wind.isNaN()) 0 else (current.wind * 3.6).toInt()
        )

        val hours = upcoming.withIndex()
            .takeWhile { it.value.symbol1 != null }
            .take(25)
            .map { (index, step) ->
                WeatherHour(
                    time = step.time.atZone(zone),
                    temp = step.temp,
                    sky = Sky.fromSymbol(step.symbol1) ?: Sky.CLOUDY,
                    night = Sky.isNight(step.symbol1),
                    precip = precipitation(upcoming, index)
                )
            }

        // Days use every step of the response, so today's high and low include the hours already passed.
        val today = now.atZone(zone).toLocalDate()
        val byDate = LinkedHashMap<LocalDate, MutableList<Int>>()
        steps.indices.forEach { index ->
            val date = steps[index].time.atZone(zone).toLocalDate()
            if (!date.isBefore(today)) byDate.getOrPut(date) { mutableListOf() }.add(index)
        }
        val days = byDate.entries
            .filterIndexed { position, entry -> position == 0 || entry.value.size >= 2 }
            .take(7)
            .map { (date, indices) ->
                val temps = indices.map { steps[it].temp }
                val precip = indices.sumOf { precipitation(steps, it) }
                val daytime = indices.filter { steps[it].time.atZone(zone).hour in 6..20 }.ifEmpty { indices }
                val skies = daytime.mapNotNull { Sky.fromSymbol(steps[it].symbol1 ?: steps[it].symbol6) }
                WeatherDay(date, temps.min(), temps.max(), daySky(skies, precip), precip)
            }

        return Forecast(updated, weatherNow, hours, days)
    }

    /** A wet day shows its strongest precipitation, a dry day its most common sky. */
    internal fun daySky(skies: List<Sky>, precip: Double): Sky {
        if (skies.isEmpty()) return Sky.CLOUDY
        if (precip >= 0.5) skies.filter { it.wet }.maxOrNull()?.let { return it }
        val dry = skies.filterNot { it.wet }.ifEmpty { skies }
        return dry.groupingBy { it }.eachCount().entries
            .maxWith(compareBy<Map.Entry<Sky, Int>> { it.value }.thenBy { it.key.ordinal }).key
    }

    private fun JSONObject.symbol(key: String) =
        optJSONObject(key)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotEmpty() }

    private fun JSONObject.precipitation(key: String): Double? {
        val details = optJSONObject(key)?.optJSONObject("details") ?: return null
        return if (details.has("precipitation_amount")) details.getDouble("precipitation_amount") else null
    }
}

/** The chosen city and the last forecast response, kept on the device. */
internal class WeatherStore(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    val place: WeatherPlace?
        get() {
            val name = prefs.getString("name", null) ?: return null
            if (!prefs.contains("lat") || !prefs.contains("lon")) return null
            return WeatherPlace(
                name,
                prefs.getString("region", "").orEmpty(),
                java.lang.Double.longBitsToDouble(prefs.getLong("lat", 0)),
                java.lang.Double.longBitsToDouble(prefs.getLong("lon", 0))
            )
        }

    val json: String? get() = prefs.getString("json", null)
    val lastModified: String? get() = prefs.getString("lastModified", null)
    val fetchedAt: Long get() = prefs.getLong("fetchedAt", 0)
    private val expiresAt: Long get() = prefs.getLong("expiresAt", 0)
    val stale get() = json == null || System.currentTimeMillis() >= expiresAt

    fun savePlace(place: WeatherPlace) {
        prefs.edit()
            .clear()
            .putString("name", place.name)
            .putString("region", place.region)
            .putLong("lat", java.lang.Double.doubleToRawLongBits(place.lat))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(place.lon))
            .apply()
    }

    fun saveResponse(json: String, lastModified: String?, expiresAt: Long, place: WeatherPlace) {
        if (this.place != place) return
        prefs.edit()
            .putString("json", json)
            .putString("lastModified", lastModified)
            .putLong("expiresAt", expiresAt)
            .putLong("fetchedAt", System.currentTimeMillis())
            .apply()
    }

    fun markFresh(expiresAt: Long, place: WeatherPlace) {
        if (this.place != place) return
        prefs.edit().putLong("expiresAt", expiresAt).putLong("fetchedAt", System.currentTimeMillis()).apply()
    }

    fun forecast(zone: ZoneId = ZoneId.systemDefault()): Forecast? {
        val parsed = json?.let {
            try {
                ForecastParser.parse(it, zone, Instant.now())
            } catch (_: Exception) {
                null
            }
        } ?: return null
        return rememberToday(parsed)
    }

    /**
     * Each response starts at the current hour, so today's earlier hours are missing from it.
     * Keep the lowest and highest values seen today so the day's range stays complete.
     */
    private fun rememberToday(forecast: Forecast): Forecast {
        val today = forecast.days.firstOrNull() ?: return forecast
        val key = today.date.toString()
        var low = today.min
        var high = today.max
        if (prefs.getString("dayDate", null) == key) {
            low = minOf(low, java.lang.Double.longBitsToDouble(prefs.getLong("dayMin", 0)))
            high = maxOf(high, java.lang.Double.longBitsToDouble(prefs.getLong("dayMax", 0)))
        }
        prefs.edit()
            .putString("dayDate", key)
            .putLong("dayMin", java.lang.Double.doubleToRawLongBits(low))
            .putLong("dayMax", java.lang.Double.doubleToRawLongBits(high))
            .apply()
        return forecast.copy(days = listOf(today.copy(min = low, max = high)) + forecast.days.drop(1))
    }
}

/**
 * Talks to MET Norway (free, also for commercial apps, CC BY 4.0) and the platform geocoder.
 * MET asks clients to identify themselves, to cache until `Expires` and to send `If-Modified-Since`.
 */
internal object WeatherClient {
    enum class Failure { OFFLINE, SERVICE }

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val AUTO_RETRY_MS = 2 * 60 * 1000L

    @Volatile var loading = false
        private set
    private var lastAttempt = 0L
    private var queued = false

    /** Refreshes the stored forecast. Without [force] it only asks when the cache expired. */
    fun refresh(context: Context, store: WeatherStore, force: Boolean, done: (Failure?) -> Unit) {
        val place = store.place ?: return
        val now = System.currentTimeMillis()
        if (loading) {
            // A new city was picked while the old one was loading: fetch again once it finishes.
            if (force) queued = true
            return
        }
        if (!force && (!store.stale || now - lastAttempt < AUTO_RETRY_MS)) return
        loading = true
        lastAttempt = now
        val agent = userAgent(context)
        val cached = store.json != null
        val lastModified = store.lastModified
        executor.execute {
            val failure = try {
                fetch(place, agent, if (cached) lastModified else null, store)
                null
            } catch (_: IOException) {
                Failure.OFFLINE
            } catch (_: Exception) {
                Failure.SERVICE
            }
            main.post {
                loading = false
                if (queued) {
                    queued = false
                    refresh(context, store, true, done)
                }
                done(failure)
            }
        }
    }

    private fun fetch(place: WeatherPlace, agent: String, lastModified: String?, store: WeatherStore) {
        val url = URL(String.format(Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", place.lat, place.lon))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", agent)
            connection.setRequestProperty("Accept", "application/json")
            if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified)
            val code = connection.responseCode
            val fallbackExpiry = System.currentTimeMillis() + 30 * 60 * 1000L
            val expires = connection.getHeaderFieldDate("Expires", fallbackExpiry)
                .coerceIn(System.currentTimeMillis() + 60_000L, System.currentTimeMillis() + 6 * 3600_000L)
            when (code) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_AUTHORITATIVE -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    ForecastParser.parse(body, ZoneId.systemDefault(), Instant.now())
                    store.saveResponse(body, connection.getHeaderField("Last-Modified"), expires, place)
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> store.markFresh(expires, place)
                else -> error("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Finds cities by name with the platform geocoder; `null` means the search failed. */
    fun search(context: Context, query: String, locale: Locale, done: (List<WeatherPlace>?) -> Unit) {
        if (!Geocoder.isPresent()) {
            done(null)
            return
        }
        val geocoder = Geocoder(context, locale)
        val deliver: (List<android.location.Address>?) -> Unit = { addresses ->
            val places = addresses?.mapNotNull(::toPlace)?.distinctBy { it.name to it.region }
            main.post { done(places) }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocationName(query, 6, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) = deliver(addresses)
                override fun onError(errorMessage: String?) = deliver(null)
            })
        } else {
            executor.execute {
                val addresses = try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 6)
                } catch (_: Exception) {
                    null
                }
                deliver(addresses)
            }
        }
    }

    private fun toPlace(address: android.location.Address): WeatherPlace? {
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val name = listOf(address.locality, address.subAdminArea, address.adminArea, address.featureName)
            .firstOrNull { !it.isNullOrBlank() } ?: return null
        val region = listOf(address.adminArea, address.countryName)
            .filter { !it.isNullOrBlank() && it != name }
            .distinct()
            .joinToString(", ")
        return WeatherPlace(name, region, address.latitude, address.longitude)
    }

    private fun userAgent(context: Context): String {
        val version = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: "dev"
        return "BENG/$version (${context.packageName}; +https://github.com/caglar-yildiz/alarm-app)"
    }
}
