package com.caglaryildiz.alarm

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ShapeMissionTest {
    @Test fun everyGeneratedBoardIsSolvableWithFourDistinctTargets() {
        repeat(100) { seed ->
            val mission = ShapeMission(Random(seed))
            assertEquals(16, mission.board.size)
            assertEquals(setOf(0, 1, 2, 3), mission.targets.toSet())
            for (shape in 0..3) assertEquals(4, mission.board.count { it == shape })
            for (target in mission.targets) {
                assertTrue(mission.pick(mission.board.indexOf(target)))
            }
            assertTrue(mission.complete)
            assertEquals(4, mission.progress)
            assertFalse(mission.pick(0))
        }
    }

    @Test fun incorrectAndRepeatedTapsDoNotAdvanceTheMission() {
        val mission = ShapeMission(Random(7))
        assertFalse(mission.pick(-1))
        assertFalse(mission.pick(16))
        assertFalse(mission.pick(mission.board.indexOfFirst { it != mission.targets.first() }))
        assertEquals(0, mission.progress)
        val first = mission.board.indexOf(mission.targets.first())
        assertTrue(mission.pick(first))
        assertFalse(mission.pick(first))
        assertEquals(1, mission.progress)
    }
}
