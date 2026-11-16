package com.hunter.system.data

import com.hunter.system.domain.Exercise
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class PeriodReport(
    val label: String, val sessions: Int, val daysTrained: Int, val xp: Long, val minutes: Long,
    val byExercise: List<Pair<Exercise, Double>>, val prevDays: Int, val prevXp: Long, val prevLabel: String
)
data class ExerciseStat(val ex: Exercise, val sessions: Int, val total: Double, val avg: Double, val best: Double, val bestDay: Long, val lastDay: Long)
data class RunSummary(val day: Long, val km: Double, val durationSec: Long)
data class RunningStats(val weeklyKm: List<Double>, val weekLabels: List<String>, val totalKm: Double,
                        val longest: Double, val recent: List<RunSummary>, val bestPaceMinPerKm: Double?)
data class ConsistencyStats(val pct28: Int, val weeklyDays: List<Int>, val weekLabels: List<String>, val avgPerWeek: Double)
data class MissedStats(val window: Int, val missed: Int, val longestGap: Int, val worstWeekday: String?, val verdict: String)
data class Insights(
    val periods: List<PeriodReport>, val xpSeries: List<Long>, val xpGain: Long,
    val consistency: ConsistencyStats, val missed: MissedStats?, val running: RunningStats,
    val exercises: List<ExerciseStat>
)

/** Every report is computed from real logs and completed quests; nothing is stored or invented. */
object Analytics {
    fun build(logs: List<WorkoutLog>, doneQuests: List<QuestEntity>, today: Long, zone: ZoneId = ZoneId.systemDefault()): Insights {
        val xpByDay = HashMap<Long, Long>()
        logs.forEach { xpByDay[it.day] = (xpByDay[it.day] ?: 0L) + it.xp }
        doneQuests.filter { it.completedAt > 0 }.forEach { q ->
            val d = Instant.ofEpochMilli(q.completedAt).atZone(zone).toLocalDate().toEpochDay()
            xpByDay[d] = (xpByDay[d] ?: 0L) + q.xpReward
        }
        fun xpIn(a: Long, b: Long) = (a..b).sumOf { xpByDay[it] ?: 0L }

        fun period(label: String, prevLabel: String, from: Long, to: Long, pf: Long, pt: Long): PeriodReport {
            val cur = logs.filter { it.day in from..to }
            val prev = logs.filter { it.day in pf..pt }
            val byEx = cur.groupBy { it.exercise }.mapNotNull { (k, v) ->
                Exercise.entries.firstOrNull { it.name == k }?.let { e -> e to v.sumOf { l -> l.value } }
            }.sortedByDescending { it.second }
            return PeriodReport(label, cur.size, cur.map { it.day }.distinct().size, xpIn(from, to),
                cur.sumOf { it.durationSec } / 60, byEx, prev.map { it.day }.distinct().size, xpIn(pf, pt), prevLabel)
        }

        val date = LocalDate.ofEpochDay(today)
        val ws = today - (date.dayOfWeek.value - 1)
        val monthStart = date.withDayOfMonth(1)
        val prevMonthStart = monthStart.minusMonths(1)
        val prevMonthEnd = monthStart.minusDays(1).toEpochDay()
        val elapsedM = today - monthStart.toEpochDay() + 1
        val periods = listOf(
            period("Today", "yesterday", today, today, today - 1, today - 1),
            period("This week", "same days last week", ws, today, ws - 7, ws - 7 + (today - ws)),
            period("This month", "same days last month", monthStart.toEpochDay(), today,
                prevMonthStart.toEpochDay(), minOf(prevMonthStart.toEpochDay() + elapsedM - 1, prevMonthEnd))
        )

        val start30 = today - 29
        var run = xpByDay.filterKeys { it < start30 }.values.sum()
        val series = (start30..today).map { run += xpByDay[it] ?: 0L; run }

        val first = logs.minOfOrNull { it.day }
        val trained = logs.map { it.day }.toSet()
        val weekStarts = (7 downTo 0).map { ws - 7L * it }
        fun lbl(d: Long) = LocalDate.ofEpochDay(d).let { "${it.dayOfMonth}/${it.monthValue}" }
        val weeklyDays = weekStarts.map { w -> (w..w + 6).count { it in trained } }
        val span = if (first == null) 0 else minOf(28L, today - first + 1).toInt()
        val pct = if (span == 0) 0 else ((today - span + 1..today).count { it in trained } * 100) / span
        val fullWeeks = weekStarts.filter { first != null && it + 6 < today && it + 6 >= first }
        val avg = if (fullWeeks.isEmpty()) weeklyDays.last().toDouble()
        else fullWeeks.map { w -> (w..w + 6).count { it in trained } }.average()

        val runs = logs.filter { it.exercise == Exercise.RUNNING.name }
        val running = RunningStats(
            weekStarts.map { w -> runs.filter { it.day in w..w + 6 }.sumOf { it.value } }, weekStarts.map(::lbl),
            runs.sumOf { it.value }, runs.maxOfOrNull { it.value } ?: 0.0,
            runs.sortedByDescending { it.timestamp }.take(8).map { RunSummary(it.day, it.value, it.durationSec) },
            runs.filter { it.durationSec > 0 && it.value >= 1.0 }.minOfOrNull { it.durationSec / 60.0 / it.value }
        )

        val stats = Exercise.entries.mapNotNull { e ->
            val l = logs.filter { it.exercise == e.name }
            if (l.isEmpty()) null else {
                val best = l.maxOf { it.value }
                val bestLog = l.filter { it.value == best }.minByOrNull { it.timestamp }!!
                ExerciseStat(e, l.size, l.sumOf { it.value }, l.sumOf { it.value } / l.size, best, bestLog.day, l.maxOf { it.day })
            }
        }
        return Insights(periods, series, xpIn(start30, today), ConsistencyStats(pct, weeklyDays, weekStarts.map(::lbl), avg),
            missed(trained, first, today), running, stats)
    }

    /** Only days since the first workout count, and today is excluded because it isn't over yet. */
    fun missed(trained: Set<Long>, first: Long?, today: Long): MissedStats? {
        if (first == null) return null
        val start = maxOf(today - 27, first); val end = today - 1
        val window = (end - start + 1).toInt()
        if (window < 7) return null
        val missedDays = (start..end).filter { it !in trained }
        var longest = 0; var cur = 0
        (start..end).forEach { if (it in trained) cur = 0 else { cur++; if (cur > longest) longest = cur } }
        val worst = missedDays.groupBy { LocalDate.ofEpochDay(it).dayOfWeek }.maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= 3 }?.key?.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val ratio = missedDays.size.toDouble() / window
        val verdict = when {
            ratio <= 2.0 / 7 + 1e-9 -> "Healthy rhythm. Rest days are part of training."
            ratio <= 4.0 / 7 -> "Some gaps. Aim for at least 3 sessions a week."
            else -> "Many missed days. Start small with a short daily session; consistency beats intensity."
        }
        return MissedStats(window, missedDays.size, longest, worst, verdict)
    }
}
