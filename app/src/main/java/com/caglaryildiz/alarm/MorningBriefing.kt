package com.caglaryildiz.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Settings for the screen shown right after an alarm is turned off. */
internal class MorningBriefing(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("briefing", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    /** Read the briefing aloud (Premium). */
    var speak: Boolean
        get() = prefs.getBoolean("speak", false)
        set(value) { prefs.edit().putBoolean("speak", value).apply() }
}

/**
 * After the alarm: a greeting, the time and date, today's weather, the on-time streak and the wake check
 * reminder. Optionally read aloud with the phone's text-to-speech in the app language.
 */
class BriefingActivity : AlarmScreen() {
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private val weatherStore by lazy { runCatching { WeatherStore(applicationContext) }.getOrNull() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true) else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        render()
        val premium = PremiumAccess(this).unlocked
        if (MorningBriefing(this).speak && premium && savedInstanceState == null) speak()
        // Weather is cached; refresh it if it's old, then show the new numbers.
        weatherStore?.let { store ->
            if (store.place != null && store.stale) {
                runCatching { WeatherClient.refresh(applicationContext, store, false) { if (!isFinishing) render() } }
            }
        }
        handler.postDelayed({ finish() }, AUTO_CLOSE)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun greeting(hour: Int) = getString(when (hour) {
        in 4..11 -> R.string.briefing_morning
        in 12..17 -> R.string.briefing_afternoon
        else -> R.string.briefing_evening
    })

    /** Today's weather in one sentence, or null if no place is set or nothing is cached (e.g. phone still locked). */
    private fun weatherLine(): String? {
        val store = weatherStore ?: return null
        val place = runCatching { store.place }.getOrNull() ?: return null
        val forecast = runCatching { store.forecast() }.getOrNull() ?: return null
        val now = forecast.now
        val today = forecast.days.firstOrNull()
        val sky = getString(now.sky.labelRes)
        return if (today == null) getString(R.string.briefing_weather_now, place.name, "${now.temp.roundToInt()}°", sky)
        else getString(R.string.briefing_weather, place.name, "${now.temp.roundToInt()}°", sky,
            "${today.max.roundToInt()}°", "${today.min.roundToInt()}°")
    }

    private fun streakLine(): String? {
        val streak = WakeStats(this).summary().streak
        return if (streak > 0) resources.getQuantityString(R.plurals.stats_streak_status, streak, streak) else null
    }

    private fun wakeLine(): String? {
        val wake = WakeCheck(this)
        return if (PremiumAccess(this).unlocked && wake.enabled) getString(R.string.briefing_wake_check, wake.delay) else null
    }

    private fun render() {
        layout()
        val now = LocalDateTime.now()
        val hero = card()
        label(greeting(now.hour), 26f, hero, bold = true).apply {
            gravity = Gravity.CENTER
            accessibilityHeading()
        }
        timeLabel(now.format(clockFormat), 60f, hero).gravity = Gravity.CENTER
        label(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(appLocale())), 16f, hero,
            color = mutedColor).gravity = Gravity.CENTER
        hero.addView(AstralPlanet(this), android.widget.LinearLayout.LayoutParams(-1, dp(96)))

        weatherLine()?.let { line ->
            val box = card()
            label(getString(R.string.tab_weather), 15f, box, color = mutedColor)
            label(line, 17f, box)
        }
        listOfNotNull(streakLine(), wakeLine()).takeIf { it.isNotEmpty() }?.let { lines ->
            val box = card()
            lines.forEach { label(it, 16f, box) }
        }
        button(getString(R.string.briefing_close)) { finish() }
    }

    private fun speak() {
        val now = LocalDateTime.now()
        val text = listOfNotNull(
            "${greeting(now.hour)}.",
            getString(R.string.briefing_spoken_time, now.format(clockFormat)),
            weatherLine(),
            streakLine(),
            wakeLine()
        ).joinToString(" ")
        tts = TextToSpeech(applicationContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val language = engine.setLanguage(appLocale())
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return@TextToSpeech
            engine.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "briefing")
        }
    }

    private companion object {
        const val AUTO_CLOSE = 3 * 60_000L
    }
}
