package com.caglaryildiz.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepMissionTest {
    private fun mission(goal: Int = 30) = ExtraMission(AlarmTask.STEPS, "", "", goal)

    @Test fun counterCatchesStepsTheDetectorMissed() {
        val mission = mission()
        // The detector saw only 4 of 40 steps; the counter reports the full total a little later.
        repeat(4) { mission.step() }
        mission.stepTotal(10_000f)
        assertEquals(4, mission.progress)
        mission.stepTotal(10_026f)
        assertEquals(26, mission.progress)
        assertFalse(mission.complete)
        mission.stepTotal(10_040f)
        assertTrue(mission.complete)
        assertEquals(30, mission.progress)
    }

    @Test fun detectorAloneStillCompletes() {
        val mission = mission(20)
        repeat(19) { mission.step() }
        assertFalse(mission.complete)
        mission.step()
        assertTrue(mission.complete)
    }

    @Test fun progressNeverGoesBack() {
        val mission = mission()
        repeat(12) { mission.step() }
        mission.stepTotal(500f)
        mission.stepTotal(505f)
        assertEquals(12, mission.progress)
    }

    @Test fun otherMissionsIgnoreSteps() {
        val shake = ExtraMission(AlarmTask.SHAKE, "", "")
        repeat(50) { shake.step() }
        assertEquals(0, shake.progress)
        assertFalse(shake.complete)
    }
}
