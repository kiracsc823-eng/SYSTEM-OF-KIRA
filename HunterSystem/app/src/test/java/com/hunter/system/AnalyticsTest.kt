package com.hunter.system

import com.hunter.system.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {
    private val today = LocalDate.of(2026, 11, 19).toEpochDay() // Thursday; week starts 16 Nov
    private fun l(ex: String, v: Double, day: Long, xp: Long = 10, dur: Long = 0) = WorkoutLog(exercise = ex, value = v,
        difficulty = 3, xp = xp, timestamp = day * 1000, day = day, pain = false, durationSec = dur)
    private fun build(logs: List<WorkoutLog>) = Analytics.build(logs, emptyList(), today)

    @Test fun xpSeriesIsCumulative() {
        val s = build(listOf(l("PUSH_UPS", 10.0, today - 1, 10), l("PUSH_UPS", 10.0, today, 5))).xpSeries
        assertEquals(30, s.size); assertEquals(10L, s[28]); assertEquals(15L, s[29])
    }
    @Test fun weeklyReportComparesSameElapsedDays() {
        val ins = build(listOf(l("PUSH_UPS", 10.0, today - 3), l("SQUATS", 20.0, today - 2), l("PUSH_UPS", 10.0, today - 10)))
        val w = ins.periods[1]
        assertEquals(2, w.daysTrained); assertEquals(1, w.prevDays); assertEquals(20L, w.xp)
    }
    @Test fun missedDaysCountedFromFirstWorkout() {
        val logs = listOf(10, 8, 6, 4, 2).map { l("PUSH_UPS", 10.0, today - it) }
        val m = build(logs).missed!!
        assertEquals(10, m.window); assertEquals(5, m.missed); assertEquals(1, m.longestGap)
    }
    @Test fun tooLittleHistoryGivesNoMissedAnalysis() {
        assertNull(build(listOf(l("PUSH_UPS", 10.0, today - 2))).missed)
    }
    @Test fun runningWeeklyAndPace() {
        val r = build(listOf(l("RUNNING", 3.0, today - 1, 50, 1500), l("RUNNING", 2.0, today, 40))).running
        assertEquals(5.0, r.weeklyKm.last(), 0.001); assertEquals(3.0, r.longest, 0.001)
        assertEquals(8.333, r.bestPaceMinPerKm!!, 0.01)
    }
    @Test fun personalRecordKeepsFirstDayOfBest() {
        val st = build(listOf(l("PUSH_UPS", 20.0, today - 5), l("PUSH_UPS", 30.0, today - 3), l("PUSH_UPS", 30.0, today - 1))).exercises[0]
        assertEquals(30.0, st.best, 0.001); assertEquals(today - 3, st.bestDay)
    }
}
