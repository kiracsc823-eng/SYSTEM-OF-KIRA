package com.hunter.system.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Kind(val unit: String) { REPS("reps"), SECONDS("sec"), KM("km") }
enum class Attr { STRENGTH, ENDURANCE, STAMINA, AGILITY, DISCIPLINE, RECOVERY }

enum class Exercise(
    val label: String, val kind: Kind, val xpPerUnit: Double, val dailyCap: Double,
    val weights: Map<Attr, Double>
) {
    PUSH_UPS("Push-ups", Kind.REPS, 0.6, 150.0, mapOf(Attr.STRENGTH to 0.7, Attr.ENDURANCE to 0.3)),
    SQUATS("Squats", Kind.REPS, 0.4, 200.0, mapOf(Attr.STRENGTH to 0.5, Attr.STAMINA to 0.3, Attr.AGILITY to 0.2)),
    SIT_UPS("Sit-ups", Kind.REPS, 0.4, 150.0, mapOf(Attr.STRENGTH to 0.5, Attr.STAMINA to 0.5)),
    PULL_UPS("Pull-ups", Kind.REPS, 1.5, 60.0, mapOf(Attr.STRENGTH to 0.8, Attr.ENDURANCE to 0.2)),
    PLANK("Plank", Kind.SECONDS, 0.1, 600.0, mapOf(Attr.STAMINA to 0.5, Attr.ENDURANCE to 0.3, Attr.STRENGTH to 0.2)),
    RUNNING("Running", Kind.KM, 40.0, 15.0, mapOf(Attr.ENDURANCE to 0.5, Attr.STAMINA to 0.4, Attr.AGILITY to 0.1)),
    STRETCHING("Stretching", Kind.SECONDS, 0.03, 1800.0, mapOf(Attr.RECOVERY to 0.7, Attr.AGILITY to 0.3)),
    CUSTOM("Custom", Kind.REPS, 0.3, 100.0, mapOf(Attr.DISCIPLINE to 1.0))
}

data class XpInput(
    val exercise: Exercise, val value: Double, val difficulty: Int,
    val doneTodayUnits: Double, val streak: Int, val personalBest: Double,
    val xpMult: Double = 1.0, val capMult: Double = 1.0, val streakCap: Int = 30
)
data class XpResult(val xp: Long, val isPr: Boolean)

object XpEngine {
    /** Units beyond the exercise's daily cap earn only 10% (anti-farming). */
    fun calculate(i: XpInput): XpResult {
        val room = max(0.0, i.exercise.dailyCap * i.capMult - i.doneTodayUnits)
        val full = min(i.value, room)
        val effective = full + (i.value - full) * 0.1
        val diff = 0.8 + 0.2 * i.difficulty.coerceIn(1, 5)
        val consistency = 1.0 + min(i.streak, i.streakCap) * 0.01
        val pr = i.personalBest > 0 && i.value > i.personalBest
        val raw = effective * i.exercise.xpPerUnit * diff * consistency * i.xpMult * (if (pr) 1.15 else 1.0)
        val floor = if (i.value > 0) 1L else 0L
        return XpResult(raw.roundToLong().coerceAtLeast(floor), pr)
    }
}

object Progression {
    val RANKS = listOf("E", "D", "C", "B", "A", "S")
    fun xpToNext(level: Int): Long = (100 * level.toDouble().pow(1.4)).toLong()

    fun applyXp(level: Int, xp: Long, gained: Long): Pair<Int, Long> {
        var l = level; var x = xp + gained
        while (x >= xpToNext(l)) { x -= xpToNext(l); l++ }
        return l to x
    }

    /** Rank needs level AND real training history, not XP alone. */
    fun rankFor(level: Int, trainingDays: Int, bestStreak: Int): String = when {
        level >= 70 && trainingDays >= 365 && bestStreak >= 30 -> "S"
        level >= 50 && trainingDays >= 200 && bestStreak >= 21 -> "A"
        level >= 35 && trainingDays >= 100 && bestStreak >= 14 -> "B"
        level >= 20 && trainingDays >= 45 && bestStreak >= 7 -> "C"
        level >= 10 && trainingDays >= 14 -> "D"
        else -> "E"
    }

    /** One rest day is allowed without breaking the streak. */
    fun nextStreak(prev: Int, lastDay: Long, today: Long): Int =
        if (lastDay < 0) 1 else if (today - lastDay <= 2) max(prev, 0) + 1 else 1

    val RANK_REQS = mapOf(
        "D" to Triple(10, 14, 0), "C" to Triple(20, 45, 7), "B" to Triple(35, 100, 14),
        "A" to Triple(50, 200, 21), "S" to Triple(70, 365, 30)
    )

    /** Progress (0..1) toward the next rank's three requirements; null at max rank. */
    fun rankProgress(level: Int, days: Int, bestStreak: Int, rank: String): Pair<String, Double>? {
        val next = RANKS.getOrNull(RANKS.indexOf(rank) + 1) ?: return null
        val r = RANK_REQS.getValue(next)
        val parts = listOf(level.toDouble() / r.first, days.toDouble() / r.second,
            if (r.third > 0) bestStreak.toDouble() / r.third else 1.0).map { min(it, 1.0) }
        return next to parts.average()
    }
}
