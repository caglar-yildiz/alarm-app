package com.caglaryildiz.alarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Home-screen widget: the next alarm at a glance, plus one-tap nap and "alarm soon" buttons. */
class NextAlarmWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    companion object {
        private const val QUICK = "com.caglaryildiz.alarm.WIDGET_QUICK_ALARM"
        private const val EXTRA_MINUTES = "minutes"
        private const val NAP_MINUTES = 20
        private const val SOON_MINUTES = 30

        /** Redraws every placed widget; called whenever alarms change. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, NextAlarmWidget::class.java)) }
                .getOrNull() ?: return
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(base: Context, manager: AppWidgetManager, ids: IntArray) {
            val context = AppLanguage.wrap(base)
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            val next = runCatching { Alarms(context).nextRing() }.getOrNull()
            views.setTextViewText(R.id.widget_title, context.getString(R.string.next_alarm))
            if (next == null) {
                views.setTextViewText(R.id.widget_time, "––:––")
                views.setTextViewText(R.id.widget_detail, context.getString(R.string.no_alarm_planned))
            } else {
                val (alarm, at) = next
                val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                views.setTextViewText(R.id.widget_time, time.format(clockFormat))
                val day = context.weekdayName(time.dayOfWeek.value)
                views.setTextViewText(R.id.widget_detail, if (alarm.label.isBlank()) day else "$day · ${alarm.label}")
            }
            views.setTextViewText(R.id.widget_nap, context.getString(R.string.widget_nap, NAP_MINUTES))
            views.setTextViewText(R.id.widget_soon, context.getString(R.string.widget_soon, SOON_MINUTES))
            views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_nap, quickIntent(context, NAP_MINUTES))
            views.setOnClickPendingIntent(R.id.widget_soon, quickIntent(context, SOON_MINUTES))
            manager.updateAppWidget(ids, views)
        }

        private fun quickIntent(context: Context, minutes: Int) = PendingIntent.getBroadcast(context, minutes,
            Intent(context, QuickAlarmReceiver::class.java).setAction(QUICK).putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        internal fun handleQuick(base: Context, intent: Intent) {
            if (intent.action != QUICK) return
            val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
            if (minutes != NAP_MINUTES && minutes != SOON_MINUTES) return
            val context = AppLanguage.wrap(base)
            val alarms = Alarms(context)
            if (!alarms.canSchedule()) {
                // Exact alarm access is missing: the app's home screen shows how to allow it.
                Toast.makeText(context, context.getString(R.string.alarm_permission_needed), Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return
            }
            // Whole minutes, rounded up, so "20 min" never rings early.
            val at = ZonedDateTime.now().plusMinutes(minutes.toLong()).plusSeconds(59).truncatedTo(ChronoUnit.MINUTES)
            val label = context.getString(if (minutes == NAP_MINUTES) R.string.widget_nap_label else R.string.widget_soon_label)
            alarms.save(Alarm(time = at.toLocalTime(), date = at.toLocalDate(), label = label, quick = true))
            Toast.makeText(context, context.getString(R.string.widget_set, at.format(clockFormat)), Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Handles the widget's nap buttons. Kept apart from the widget provider, which must be exported for the
 * launcher: this one is not, so other apps cannot create alarms through it.
 */
class QuickAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = NextAlarmWidget.handleQuick(context, intent)
}
