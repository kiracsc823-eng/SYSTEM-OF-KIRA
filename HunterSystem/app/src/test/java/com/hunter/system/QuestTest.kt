package com.hunter.system

import com.hunter.system.data.*
import com.hunter.system.domain.Exercise
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QuestTest {
    private fun q(mode: String, ex: String?, target: Double, s: Long, e: Long) = QuestEntity(
        category = "DAILY", title = "t", exercise = ex, mode = mode, target = target, xpReward = 10,
        periodStart = s, periodEnd = e, completedAt = 0, custom = false, autoKey = "", sinceTs = 0)
    private fun l(ex: String, v: Double, day: Long) = WorkoutLog(exercise = ex, value = v, difficulty = 3,
        xp = 1, timestamp = 0, day = day, pain = false)

    @Test fun volumeSumsOnlyInPeriodAndExercise() {
        val logs = listOf(l("PUSH_UPS", 10.0, 5), l("PUSH_UPS", 15.0, 5), l("PUSH_UPS", 99.0, 6), l("SQUATS", 50.0, 5))
        assertEquals(25.0, QuestRules.progress(q("VOLUME", "PUSH_UPS", 30.0, 5, 5), logs), 0.001)
    }
    @Test fun daysCountsDistinctDays() {
        val logs = listOf(l("PUSH_UPS", 1.0, 5), l("SQUATS", 1.0, 5), l("PLANK", 1.0, 6))
        assertEquals(2.0, QuestRules.progress(q("DAYS", null, 4.0, 5, 11), logs), 0.001)
    }
    @Test fun bestUsesSingleLog() {
        val logs = listOf(l("PUSH_UPS", 30.0, 5), l("PUSH_UPS", 30.0, 5))
        assertEquals(30.0, QuestRules.progress(q("BEST", "PUSH_UPS", 50.0, 5, -1), logs), 0.001)
    }
    @Test fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), QuestRules.weekStart(LocalDate.of(2026, 11, 19).toEpochDay()))
    }
    @Test fun customValidation() {
        assertNotNull(QuestRules.validate("DAILY", Exercise.PUSH_UPS, "DAYS", 3.0))
        assertNotNull(QuestRules.validate("DAILY", Exercise.PUSH_UPS, "VOLUME", 5000.0))
        assertNull(QuestRules.validate("DAILY", Exercise.PUSH_UPS, "VOLUME", 50.0))
    }
}
