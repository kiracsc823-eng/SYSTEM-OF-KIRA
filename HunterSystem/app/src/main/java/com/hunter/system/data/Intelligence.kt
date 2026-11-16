package com.hunter.system.data

import com.hunter.system.domain.Exercise
import kotlin.math.ceil

data class Recommendation(val title: String, val detail: String, val warn: Boolean = false)

/** Rules-based coach: respects pain and fatigue first, then suggests gradual progression. */
object Recommender {
    fun recommend(logs: List<WorkoutLog>, today: Long): List<Recommendation> {
        if (logs.isEmpty()) return listOf(Recommendation("Begin your awakening",
            "Start light: 10 push-ups or squats and 5 minutes of stretching. Log them in TRAIN."))
        if (logs.any { it.pain && it.day >= today - 2 }) return listOf(Recommendation("Pain reported recently",
            "Skip hard training. Gentle stretching only. If the pain continues, see a medical professional.", true))
        val trained = logs.filter { it.day in (today - 6) until today }.map { it.day }.distinct().size
        if (trained >= 5) return listOf(Recommendation("Rest day recommended",
            "You trained $trained of the last 6 days. Light stretching at most; recovery is how you grow.", true))
        val out = mutableListOf<Recommendation>()
        val gap = today - logs.maxOf { it.day }
        if (gap >= 3) out += Recommendation("Easy restart", "$gap days since your last session. Begin at about half your usual volume.")
        val lastSeen = Exercise.entries.filter { it != Exercise.STRETCHING && it != Exercise.CUSTOM }
            .associateWith { e -> logs.filter { it.exercise == e.name }.maxOfOrNull { it.day } }
        lastSeen.entries.sortedBy { it.value ?: Long.MIN_VALUE }.take(2).forEach { (e, _) ->
            out += suggest(e, logs.filter { it.exercise == e.name }.sortedBy { it.timestamp }, gap >= 3)
        }
        if (logs.none { it.exercise == Exercise.STRETCHING.name && it.day >= today - 3 })
            out += Recommendation("Mobility", "5 minutes of stretching keeps you recovered and injury-resistant.")
        return out
    }

    private fun fmt(t: Double, e: Exercise) = when (e.kind) {
        com.hunter.system.domain.Kind.REPS -> "${ceil(t - 1e-9).toInt()} reps"
        com.hunter.system.domain.Kind.SECONDS -> "${(Math.round(t / 5) * 5).toInt()} sec"
        com.hunter.system.domain.Kind.KM -> "${Math.round(t * 10) / 10.0} km"
    }

    internal fun suggest(e: Exercise, h: List<WorkoutLog>, easy: Boolean): Recommendation {
        val start = when (e) {
            Exercise.PUSH_UPS -> 10.0; Exercise.SQUATS -> 20.0; Exercise.SIT_UPS -> 15.0
            Exercise.PULL_UPS -> 3.0; Exercise.PLANK -> 30.0; Exercise.RUNNING -> 1.0; else -> 10.0
        }
        if (h.isEmpty()) return Recommendation("Try ${e.label}", "Start with ${fmt(start, e)} at a moderate effort.")
        val last = h.last(); val prev = h.getOrNull(h.size - 2)
        val (mult, why) = when {
            prev != null && last.value < prev.value * 0.8 -> 1.0 to "Your last session dipped, so hold steady."
            last.difficulty >= 5 -> 1.0 to "Last time was max effort, so repeat before progressing."
            last.difficulty <= 3 -> 1.10 to "Last session felt manageable: +10%."
            else -> 1.05 to "Steady progress: +5%."
        }
        var t = last.value * mult
        if (easy) t *= 0.5
        t = t.coerceIn(1.0, maxOf(1.0, e.dailyCap / 2))
        return Recommendation("${e.label}: ${fmt(t, e)}", why)
    }
}
