package com.caglaryildiz.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private val day = 24 * 60 * 60_000L
    private val now = 1_000 * day
    private fun summary(streak: Int, total: Int) =
        WakeSummary(streak, streak, streak, streak, 0, 30, emptyList(), total)

    @Test fun asksAfterAStreakOnAnEstablishedInstall() {
        assertTrue(ReviewPrompt.eligible(summary(3, 5), now - 10 * day, 0, now))
    }

    @Test fun waitsForStreakHistoryAndInstallAge() {
        assertFalse(ReviewPrompt.eligible(summary(2, 9), now - 10 * day, 0, now))
        assertFalse(ReviewPrompt.eligible(summary(3, 4), now - 10 * day, 0, now))
        assertFalse(ReviewPrompt.eligible(summary(5, 9), now - 2 * day, 0, now))
    }

    @Test fun asksAtMostEveryFewMonths() {
        assertFalse(ReviewPrompt.eligible(summary(5, 9), now - 300 * day, now - 30 * day, now))
        assertTrue(ReviewPrompt.eligible(summary(5, 9), now - 300 * day, now - 121 * day, now))
    }
}
