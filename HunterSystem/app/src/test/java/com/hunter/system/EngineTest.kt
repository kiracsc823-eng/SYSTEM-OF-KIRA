package com.hunter.system

import com.hunter.system.domain.*
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    @Test fun dailyCapReducesXp() {
        val a = XpEngine.calculate(XpInput(Exercise.PUSH_UPS, 50.0, 3, 0.0, 0, 0.0))
        val b = XpEngine.calculate(XpInput(Exercise.PUSH_UPS, 50.0, 3, 150.0, 0, 0.0))
        assertEquals(42L, a.xp)
        assertTrue(b.xp < 10)
    }
    @Test fun prBonusApplies() {
        assertTrue(XpEngine.calculate(XpInput(Exercise.SQUATS, 60.0, 3, 0.0, 0, 40.0)).isPr)
    }
    @Test fun levelUp() { assertEquals(2 to 0L, Progression.applyXp(1, 0, 100)) }
    @Test fun rankNeedsHistory() {
        assertEquals("E", Progression.rankFor(10, 0, 0))
        assertEquals("D", Progression.rankFor(10, 14, 0))
    }
    @Test fun streakAllowsOneRestDay() {
        assertEquals(4, Progression.nextStreak(3, 10, 12))
        assertEquals(1, Progression.nextStreak(3, 10, 13))
    }
    @Test fun rankProgressTowardNext() {
        assertEquals("D", Progression.rankProgress(10, 14, 0, "E")!!.first)
        assertEquals(1.0, Progression.rankProgress(10, 14, 0, "E")!!.second, 0.001)
        assertNull(Progression.rankProgress(70, 365, 30, "S"))
    }
}
