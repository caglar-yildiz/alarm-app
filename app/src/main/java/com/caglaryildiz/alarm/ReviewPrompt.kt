package com.caglaryildiz.alarm

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Asks for a Google Play rating at a good moment: after several mornings in a row of getting up on time,
 * never during the first days of use, and at most once every few months. Google Play decides whether the
 * rating sheet is actually shown, and it never says whether the user rated.
 */
internal object ReviewPrompt {
    private const val MIN_STREAK = 3
    private const val MIN_ENTRIES = 5
    private const val MIN_INSTALL_AGE = 4 * 24 * 60 * 60_000L
    private const val INTERVAL = 120L * 24 * 60 * 60_000L

    fun eligible(summary: WakeSummary, installedAt: Long, lastAsked: Long, now: Long) =
        summary.streak >= MIN_STREAK && summary.total >= MIN_ENTRIES &&
            now - installedAt >= MIN_INSTALL_AGE && now - lastAsked >= INTERVAL

    fun maybeAsk(activity: Activity) {
        val prefs = activity.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val installedAt = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).firstInstallTime
        }.getOrDefault(now)
        if (!eligible(WakeStats(activity).summary(), installedAt, prefs.getLong("lastAsked", 0), now)) return
        prefs.edit().putLong("lastAsked", now).apply()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                manager.launchReviewFlow(activity, request.result)
            }
        }
    }
}
