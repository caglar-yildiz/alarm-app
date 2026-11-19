package com.caglaryildiz.alarm

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The free Weather tab: a city picker, today's sky, the next 24 hours and the week ahead. */
abstract class WeatherScreen : PremiumScreen() {
    private val weatherStore by lazy { WeatherStore(applicationContext) }
    private var weatherQuery = ""
    private var weatherResults: List<WeatherPlace>? = null
    private var weatherSearching = false
    private var weatherSearchFailed = false
    private var weatherPicking = false
    private var weatherFailure: WeatherClient.Failure? = null

    /** Called when weather data or the picker state changed and the tab should be drawn again. */
    protected abstract fun onWeatherChanged()

    private val locale: Locale get() = resources.configuration.locales[0]

    protected fun showWeather() {
        val place = weatherStore.place
        if (place == null || weatherPicking) {
            showPlacePicker(place != null)
            return
        }
        requestWeather(force = false)
        val forecast = weatherStore.forecast()
        if (forecast == null) {
            val status = card()
            when {
                WeatherClient.loading || weatherFailure == null ->
                    label(getString(R.string.weather_loading), 16f, status, color = mutedColor)
                else -> {
                    label(getString(if (weatherFailure == WeatherClient.Failure.OFFLINE) R.string.weather_failed_offline
                        else R.string.weather_failed), 16f, status)
                    button(getString(R.string.weather_refresh), status) { requestWeather(force = true) }
                }
            }
            button(getString(R.string.weather_change_city), primary = false) { startPicking() }
            return
        }
        showWeatherHero(place, forecast)
        showWeatherTip(forecast)
        showWeatherStats(forecast)
        showHours(forecast)
        showDays(forecast)
        showWeatherFooter()
    }

    private fun requestWeather(force: Boolean) {
        WeatherClient.refresh(applicationContext, weatherStore, force) { failure ->
            weatherFailure = failure
            if (!isFinishing && !isDestroyed) onWeatherChanged()
        }
        if (force && WeatherClient.loading) onWeatherChanged()
    }

    private fun startPicking() {
        weatherPicking = true
        weatherResults = null
        weatherSearchFailed = false
        onWeatherChanged()
    }

    // region City picker

    private var resultsBox: LinearLayout? = null
    private var introViews = emptyList<View>()

    private fun updateIntroVisibility() {
        val visibility = if (weatherQuery.isBlank()) View.VISIBLE else View.GONE
        introViews.forEach { it.visibility = visibility }
    }
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val worldSearch = Runnable { searchWorldwide() }

    private fun showPlacePicker(canCancel: Boolean) {
        val intro = card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
        }
        val glyph = WeatherGlyph(this, Sky.PARTLY, night = false, floating = true)
        intro.addView(glyph, LinearLayout.LayoutParams(dp(112), dp(112)))
        val title = label(getString(R.string.weather_intro_title), 21f, intro, bold = true).apply {
            gravity = Gravity.CENTER
            accessibilityHeading()
        }
        val body = label(getString(R.string.weather_intro_body), 14.5f, intro, color = mutedColor).apply {
            gravity = Gravity.CENTER
        }
        // While typing, the introduction steps aside so results stay above the keyboard.
        introViews = listOf(glyph, title, body)
        updateIntroVisibility()

        val input = EditText(this).apply {
            setText(weatherQuery)
            setSelection(text.length)
            hint = getString(R.string.weather_search_hint)
            setHintTextColor(0x99C0C4E1.toInt())
            setTextColor(android.graphics.Color.WHITE)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            styleInput(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString().orEmpty()
                    if (query == weatherQuery) return
                    weatherQuery = query
                    onQueryChanged()
                }
            })
        }
        intro.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        input.setOnEditorActionListener { view, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
                searchWorldwide()
                true
            } else false
        }

        resultsBox = column().also { panel.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        renderResults()

        if (canCancel) button(getString(R.string.cancel), primary = false) {
            searchHandler.removeCallbacks(worldSearch)
            weatherPicking = false
            weatherResults = null
            weatherQuery = ""
            onWeatherChanged()
        }
        if (!canCancel) input.requestFocus()
    }

    /** Local results update on every keystroke; the worldwide geocoder only runs when they fall short. */
    private fun onQueryChanged() {
        searchHandler.removeCallbacks(worldSearch)
        weatherResults = null
        weatherSearchFailed = false
        val query = weatherQuery.trim()
        val local = localMatches(query)
        updateIntroVisibility()
        renderResults(local)
        if (query.length >= 3 && local.size < 3) searchHandler.postDelayed(worldSearch, 700)
    }

    private fun localMatches(query: String): List<LocalPlace> =
        if (query.isEmpty()) emptyList() else PlaceSearch.search(PlaceSearch.places(this), query)

    private fun renderResults(local: List<LocalPlace> = localMatches(weatherQuery.trim())) {
        val box = resultsBox ?: return
        box.removeAllViews()
        val query = weatherQuery.trim()
        // Worldwide results that are not just a repeat of a bundled place.
        val world = weatherResults.orEmpty().filter { found ->
            local.none { kotlin.math.abs(it.lat - found.lat) < .05 && kotlin.math.abs(it.lon - found.lon) < .05 }
        }
        if (local.isNotEmpty() || world.isNotEmpty()) {
            val list = column().apply {
                background = glass(0xDD242F58.toInt(), 0xD137204F.toInt(), 20)
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            box.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            local.forEach { place ->
                val country = PlaceSearch.toWeatherPlace(place, locale).region
                val subtitle = getString(if (place.isProvince) R.string.weather_place_province
                    else R.string.weather_place_district, country)
                placeRow(place.title, subtitle, PlaceSearch.toWeatherPlace(place, locale), list)
            }
            world.forEach { placeRow(it.name, it.region, it, list) }
        }
        val status = when {
            weatherSearching -> getString(R.string.weather_searching)
            weatherSearchFailed && local.isEmpty() -> getString(R.string.weather_search_failed)
            query.length >= 2 && local.isEmpty() && weatherResults?.isEmpty() == true -> getString(R.string.weather_no_results)
            else -> null
        }
        if (status != null) label(status, 14.5f, box, color = mutedColor).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
    }

    private fun placeRow(title: String, subtitle: String, place: WeatherPlace, parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            minimumHeight = dp(56)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(", ")
            background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF), null,
                GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(14).toFloat() })
            setOnClickListener { choosePlace(place) }
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        label(title, 16.5f, row, bold = true).apply {
            setPadding(0, 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (subtitle.isNotBlank()) label(subtitle, 13.5f, row, color = mutedColor).apply {
            setPadding(0, dp(2), 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    /** Asks the platform geocoder, for places outside the bundled list (e.g. other countries). */
    private fun searchWorldwide() {
        searchHandler.removeCallbacks(worldSearch)
        val query = weatherQuery.trim()
        if (query.length < 2 || weatherSearching) return
        weatherSearching = true
        weatherSearchFailed = false
        renderResults()
        WeatherClient.search(applicationContext, query, locale) { places ->
            weatherSearching = false
            if (isFinishing || isDestroyed) return@search
            if (weatherQuery.trim() == query) {
                weatherSearchFailed = places == null
                weatherResults = places
            }
            renderResults()
        }
    }

    private fun choosePlace(place: WeatherPlace) {
        searchHandler.removeCallbacks(worldSearch)
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(panel.windowToken, 0)
        weatherStore.savePlace(place)
        weatherPicking = false
        weatherResults = null
        weatherQuery = ""
        weatherFailure = null
        requestWeather(force = true)
        onWeatherChanged()
    }

    // endregion

    // region Forecast

    private fun showWeatherHero(place: WeatherPlace, forecast: Forecast) {
        val now = forecast.now
        val colors = when {
            now.sky == Sky.THUNDER -> intArrayOf(0xFF3E2F78.toInt(), 0xFF241D52.toInt(), 0xFF14122E.toInt())
            now.sky.wet -> intArrayOf(0xFF34548A.toInt(), 0xFF27306A.toInt(), 0xFF161D40.toInt())
            now.sky == Sky.CLOUDY || now.sky == Sky.FOG -> intArrayOf(0xFF4A5486.toInt(), 0xFF2F3566.toInt(), 0xFF1A1F42.toInt())
            now.night -> intArrayOf(0xFF33307E.toInt(), 0xFF211F57.toInt(), 0xFF111430.toInt())
            else -> intArrayOf(0xFF3F7DD6.toInt(), 0xFF5547C0.toInt(), 0xFF2A2F78.toInt())
        }
        val hero = card().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), 0x748C8CD8)
            }
            setPadding(dp(20), dp(18), dp(16), dp(20))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.TOP }
        hero.addView(top, LinearLayout.LayoutParams(-1, -2))
        val heading = column()
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        label(place.name, 22f, heading, bold = true).apply {
            setPadding(0, dp(2), 0, 0)
            maxLines = 2
            accessibilityHeading()
        }
        val today = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(LocalDate.now())
        label(today.replaceFirstChar { it.titlecase(locale) }, 13.5f, heading, color = 0xFFD9DCF5.toInt())
            .setPadding(0, dp(2), 0, 0)
        top.addView(WeatherGlyph(this, now.sky, now.night, floating = true),
            LinearLayout.LayoutParams(dp(112), dp(112)).apply { marginStart = dp(8) })

        val temp = "${now.temp.roundToInt()}°"
        timeLabel(temp, 78f, hero).apply {
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
        }
        val condition = getString(now.sky.labelRes)
        label(condition, 19f, hero, bold = true).setPadding(0, dp(4), 0, dp(2))
        forecast.days.firstOrNull()?.takeIf { it.max.roundToInt() != it.min.roundToInt() }?.let { day ->
            label(getString(R.string.weather_high_low, "${day.max.roundToInt()}°", "${day.min.roundToInt()}°"),
                14.5f, hero, color = 0xFFD9DCF5.toInt())
        }
        hero.contentDescription = "${place.name}, $temp, $condition"
    }

    private fun showWeatherTip(forecast: Forecast) {
        val today = LocalDate.now()
        val hours = forecast.hours.filter { it.time.toLocalDate() == today }.ifEmpty { forecast.hours.take(6) }
        val worst = (hours.map { it.sky } + forecast.now.sky).max()
        val rain = hours.sumOf { it.precip }
        val high = (hours.map { it.temp } + forecast.now.temp).max()
        val low = (hours.map { it.temp } + forecast.now.temp).min()
        val tip = when {
            worst == Sky.THUNDER -> R.string.weather_tip_thunder
            worst == Sky.SNOW || worst == Sky.SLEET -> R.string.weather_tip_snow
            worst.wet || rain >= 0.5 -> R.string.weather_tip_rain
            high >= 30 -> R.string.weather_tip_hot
            low <= 3 -> R.string.weather_tip_cold
            forecast.now.windKmh >= 40 -> R.string.weather_tip_wind
            worst == Sky.CLEAR || worst == Sky.PARTLY -> R.string.weather_tip_clear
            else -> R.string.weather_tip_calm
        }
        val card = card().apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x40FFD98C, 0x1AFFD98C)).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x80FFD98C.toInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        label(getString(R.string.weather_today_for), 13f, card, bold = true, color = goldColor).apply {
            letterSpacing = .06f
            setPadding(0, 0, 0, dp(2))
        }
        label(getString(tip), 16f, card)
    }

    private fun showWeatherStats(forecast: Forecast) {
        val row = LinearLayout(this).apply { isBaselineAligned = false }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val rain = forecast.days.firstOrNull()?.precip ?: 0.0
        val stats = listOf(
            getString(R.string.weather_wind) to getString(R.string.weather_wind_value, forecast.now.windKmh),
            getString(R.string.weather_humidity) to getString(R.string.weather_percent, forecast.now.humidity),
            getString(R.string.weather_precip) to formatPrecip(rain)
        )
        stats.forEachIndexed { index, (title, value) ->
            val tile = column().apply {
                background = glass(0xDD242F58.toInt(), 0xD137204F.toInt(), 18)
                setPadding(dp(12), dp(10), dp(12), dp(12))
                contentDescription = "$title, $value"
            }
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
            label(title, 12.5f, tile, color = mutedColor).apply {
                setPadding(0, 0, 0, dp(2))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            label(value, 18f, tile, bold = true).apply {
                setPadding(0, 0, 0, 0)
                setSingleLine()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    }

    private fun sectionTitle(text: String) = label(text, 19f, bold = true).apply {
        setPadding(0, dp(20), 0, dp(2))
        accessibilityHeading()
    }

    private fun showHours(forecast: Forecast) {
        if (forecast.hours.size < 2) return
        sectionTitle(getString(R.string.weather_next_hours))
        val holder = card().apply { setPadding(0, dp(12), 0, dp(6)) }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(4), 0, dp(4), 0)
            val format = DateTimeFormatter.ofPattern("HH:mm")
            contentDescription = forecast.hours.joinToString("; ") { hour ->
                "${format.format(hour.time)}, ${hour.temp.roundToInt()}°, ${getString(hour.sky.labelRes)}"
            }
        }
        scroller.addView(HourlyChart(this, forecast.hours))
        holder.addView(scroller, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showDays(forecast: Forecast) {
        val days = forecast.days
        if (days.isEmpty()) return
        sectionTitle(getString(R.string.weather_days))
        val list = card().apply { setPadding(dp(14), dp(6), dp(14), dp(6)) }
        val weekLow = days.minOf { it.min }
        val weekHigh = days.maxOf { it.max }
        val today = LocalDate.now()
        days.forEachIndexed { index, day ->
            if (index > 0) list.addView(View(this).apply { setBackgroundColor(0x338C8CD8) },
                LinearLayout.LayoutParams(-1, dp(1)))
            val name = when (day.date) {
                today -> getString(R.string.weather_today)
                today.plusDays(1) -> getString(R.string.weather_tomorrow)
                else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(54)
                contentDescription = "$name, ${getString(day.sky.labelRes)}, " +
                    getString(R.string.weather_high_low, "${day.max.roundToInt()}°", "${day.min.roundToInt()}°")
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            row.addView(dayText(name, 15.5f, android.graphics.Color.WHITE, bold = index == 0),
                LinearLayout.LayoutParams(dp(92), -2))
            row.addView(WeatherGlyph(this, day.sky, night = false), LinearLayout.LayoutParams(dp(34), dp(34)))
            row.addView(dayText(if (day.precip >= 0.5) formatPrecip(day.precip) else "", 12f, 0xFF8FD0FF.toInt()).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(58), -2))
            row.addView(dayText("${day.min.roundToInt()}°", 15f, mutedColor).apply { gravity = Gravity.END },
                LinearLayout.LayoutParams(dp(34), -2))
            row.addView(TemperatureRange(this, weekLow, weekHigh, day.min, day.max),
                LinearLayout.LayoutParams(0, dp(20), 1f).apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                })
            row.addView(dayText("${day.max.roundToInt()}°", 15f, android.graphics.Color.WHITE, bold = true),
                LinearLayout.LayoutParams(dp(34), -2))
        }
    }

    private fun dayText(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun showWeatherFooter() {
        val updated = Instant.ofEpochMilli(weatherStore.fetchedAt).atZone(ZoneId.systemDefault())
        val status = when {
            WeatherClient.loading -> getString(R.string.weather_updating)
            weatherFailure == WeatherClient.Failure.OFFLINE -> getString(R.string.weather_offline)
            weatherFailure == WeatherClient.Failure.SERVICE -> getString(R.string.weather_failed)
            else -> null
        }
        if (status != null) label(status, 13.5f, color = goldColor).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        label(getString(R.string.weather_updated, clockFormat.format(updated)), 12.5f, color = mutedColor).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(if (status == null) 16 else 4), 0, dp(4))
        }
        val actions = LinearLayout(this).apply { isBaselineAligned = false }
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))
        button(getString(R.string.weather_change_city), actions, primary = false) { startPicking() }
            .layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = dp(8)
                marginEnd = dp(5)
            }
        button(getString(R.string.weather_refresh), actions, primary = false) { requestWeather(force = true) }
            .apply { isEnabled = !WeatherClient.loading }
            .layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = dp(8)
                marginStart = dp(5)
            }
    }

    // endregion
}
