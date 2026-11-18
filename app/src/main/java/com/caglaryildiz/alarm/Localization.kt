package com.caglaryildiz.alarm

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * In-app language choice. Android 13+ stores it as the per-app language, so the
 * system settings page and the app stay in sync. Older versions keep it in
 * device-protected preferences and wrap each context with the chosen locale.
 */
internal object AppLanguage {
    /** Empty tag means "follow the device language". */
    val supported = listOf("", "tr", "en")

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("language", Context.MODE_PRIVATE)

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) "" else locales[0].language
        }
        return prefs(context).getString("tag", "").orEmpty()
    }

    fun set(activity: Activity, tag: String) {
        prefs(activity).edit().putString("tag", tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.recreate()
        }
    }

    /** Returns a context whose resources use the chosen language on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = prefs(base).getString("tag", "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    fun displayName(context: Context, tag: String): String {
        if (tag.isEmpty()) return context.getString(R.string.language_system)
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

internal fun Context.appLocale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

/** Localized weekday name for ISO day 1 (Monday) … 7 (Sunday). */
internal fun Context.weekdayName(day: Int, short: Boolean = false): String =
    DayOfWeek.of(day).getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, appLocale())
        .replaceFirstChar { it.titlecase(appLocale()) }

internal fun Context.dateFormat(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(appLocale())

internal fun Context.nextAlarmFormat(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE · HH:mm", appLocale())
