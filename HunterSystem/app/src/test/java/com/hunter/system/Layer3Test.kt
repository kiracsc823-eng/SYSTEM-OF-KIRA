package com.hunter.system

import com.hunter.system.data.*
import com.hunter.system.domain.*
import org.junit.Assert.*
import org.junit.Test

class Layer3Test {
    @Test fun skillEffectsStackAndStayScoped() {
        assertEquals(1.10, Skills.effects(setOf("STR1", "STR2"), Exercise.PUSH_UPS).xpMult, 0.001)
        assertEquals(1.0, Skills.effects(setOf("STR1", "STR2"), Exercise.RUNNING).xpMult, 0.001)
        assertEquals(1.2, Skills.effects(setOf("RUN1"), Exercise.RUNNING).capMult, 0.001)
        assertEquals(40, Skills.effects(setOf("CON1"), Exercise.PLANK).streakCap)
    }
    @Test fun skillRules() {
        assertNotNull(Skills.canUnlock(Skills.byId("STR2")!!, 10, emptySet()))
        assertEquals(3, Skills.points(5, setOf("STR1")))
        assertNull(Skills.canUnlock(Skills.byId("STR1")!!, 2, emptySet()))
    }
    @Test fun skillBoostRaisesXp() {
        val a = XpEngine.calculate(XpInput(Exercise.PUSH_UPS, 50.0, 3, 0.0, 0, 0.0, xpMult = 1.1))
        assertEquals(46L, a.xp)
    }
    @Test fun dungeonScalesWithLevel() {
        val m = MissionDef(Exercise.PUSH_UPS, 40.0)
        assertEquals(30.0, Dungeons.target(m, 1), 0.001)
        assertEquals(80.0, Dungeons.target(m, 50), 0.001)
    }
    @Test fun painStopsRecommendations() {
        val l = WorkoutLog(exercise = "PUSH_UPS", value = 10.0, difficulty = 3, xp = 5, timestamp = 1, day = 10, pain = true)
        val r = Recommender.recommend(listOf(l), 11)
        assertEquals(1, r.size); assertTrue(r[0].warn)
    }
    @Test fun progressionIsGradual() {
        val l = WorkoutLog(exercise = "PUSH_UPS", value = 20.0, difficulty = 3, xp = 5, timestamp = 1, day = 10, pain = false)
        assertTrue(Recommender.suggest(Exercise.PUSH_UPS, listOf(l), false).title.contains("22"))
        val hard = l.copy(difficulty = 5)
        assertTrue(Recommender.suggest(Exercise.PUSH_UPS, listOf(hard), false).title.contains("20"))
    }
}
