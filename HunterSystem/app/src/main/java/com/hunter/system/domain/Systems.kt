package com.hunter.system.domain

import kotlin.math.min
import kotlin.math.round

enum class Cat { STRENGTH, CARDIO, RECOVERY, OTHER }

fun categoryOf(e: Exercise) = when (e) {
    Exercise.PUSH_UPS, Exercise.SQUATS, Exercise.SIT_UPS, Exercise.PULL_UPS, Exercise.PLANK -> Cat.STRENGTH
    Exercise.RUNNING -> Cat.CARDIO
    Exercise.STRETCHING -> Cat.RECOVERY
    Exercise.CUSTOM -> Cat.OTHER
}

data class Skill(
    val id: String, val name: String, val desc: String, val cost: Int, val minLevel: Int,
    val prereq: String? = null, val strengthXp: Double = 0.0, val cardioXp: Double = 0.0,
    val recoveryXp: Double = 0.0, val runCap: Double = 0.0, val streakCapBonus: Int = 0
)
data class SkillEffects(val xpMult: Double, val capMult: Double, val streakCap: Int)

/** Skills give modest, realistic bonuses: small XP boosts, a higher daily cap for running, a longer streak bonus. */
object Skills {
    val all = listOf(
        Skill("STR1", "Strength Development I", "+5% XP on strength exercises", 1, 2, strengthXp = 0.05),
        Skill("STR2", "Strength Development II", "+5% more XP on strength exercises", 2, 10, "STR1", strengthXp = 0.05),
        Skill("STR3", "Strength Development III", "+5% more XP on strength exercises", 3, 25, "STR2", strengthXp = 0.05),
        Skill("END1", "Endurance Mastery I", "+5% XP on running", 1, 3, cardioXp = 0.05),
        Skill("END2", "Endurance Mastery II", "+5% more XP on running", 2, 12, "END1", cardioXp = 0.05),
        Skill("RUN1", "Running Technique I", "Daily running XP cap +20%", 2, 5, runCap = 0.2),
        Skill("RUN2", "Running Technique II", "Daily running XP cap +20% more", 3, 20, "RUN1", runCap = 0.2),
        Skill("REC1", "Recovery Mastery I", "+10% XP on stretching", 1, 4, recoveryXp = 0.10),
        Skill("REC2", "Recovery Mastery II", "+10% more XP on stretching", 2, 15, "REC1", recoveryXp = 0.10),
        Skill("CON1", "Consistency I", "Streak bonus grows up to 40 days", 1, 5, streakCapBonus = 10),
        Skill("CON2", "Consistency II", "Streak bonus grows up to 50 days", 2, 18, "CON1", streakCapBonus = 10)
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun points(level: Int, unlocked: Set<String>): Int =
        ((level - 1) - all.filter { it.id in unlocked }.sumOf { it.cost }).coerceAtLeast(0)

    fun canUnlock(s: Skill, level: Int, unlocked: Set<String>): String? = when {
        s.id in unlocked -> "[SYSTEM] Already unlocked."
        level < s.minLevel -> "[SYSTEM] Requires level ${s.minLevel}."
        s.prereq != null && s.prereq !in unlocked -> "[SYSTEM] Requires ${byId(s.prereq)?.name}."
        points(level, unlocked) < s.cost -> "[SYSTEM] Not enough skill points."
        else -> null
    }

    fun effects(unlocked: Set<String>, ex: Exercise): SkillEffects {
        val cat = categoryOf(ex)
        var xp = 0.0; var cap = 0.0; var streak = 30
        all.filter { it.id in unlocked }.forEach { s ->
            xp += when (cat) { Cat.STRENGTH -> s.strengthXp; Cat.CARDIO -> s.cardioXp; Cat.RECOVERY -> s.recoveryXp; else -> 0.0 }
            if (ex == Exercise.RUNNING) cap += s.runCap
            streak += s.streakCapBonus
        }
        return SkillEffects(1 + xp, 1 + cap, streak)
    }
}

data class MissionDef(val ex: Exercise, val base: Double)
data class DungeonDef(val id: String, val name: String, val rank: String, val title: String, val missions: List<MissionDef>)

object Dungeons {
    val all = listOf(
        DungeonDef("E_GOBLIN", "Goblin Cave", "E", "Goblin Slayer", listOf(
            MissionDef(Exercise.PUSH_UPS, 40.0), MissionDef(Exercise.SQUATS, 60.0),
            MissionDef(Exercise.RUNNING, 2.0), MissionDef(Exercise.STRETCHING, 900.0))),
        DungeonDef("D_ORC", "Orc Fortress", "D", "Orc Breaker", listOf(
            MissionDef(Exercise.PUSH_UPS, 100.0), MissionDef(Exercise.SIT_UPS, 80.0),
            MissionDef(Exercise.RUNNING, 5.0), MissionDef(Exercise.PLANK, 300.0))),
        DungeonDef("C_ELF", "Ice Elf Keep", "C", "Frost Hunter", listOf(
            MissionDef(Exercise.PULL_UPS, 15.0), MissionDef(Exercise.PUSH_UPS, 200.0),
            MissionDef(Exercise.SQUATS, 250.0), MissionDef(Exercise.RUNNING, 8.0))),
        DungeonDef("B_DEMON", "Demon Castle", "B", "Demon Bane", listOf(
            MissionDef(Exercise.PULL_UPS, 30.0), MissionDef(Exercise.PUSH_UPS, 300.0),
            MissionDef(Exercise.PLANK, 600.0), MissionDef(Exercise.RUNNING, 12.0))),
        DungeonDef("A_DRAGON", "Dragon's Lair", "A", "Dragon Slayer", listOf(
            MissionDef(Exercise.PULL_UPS, 50.0), MissionDef(Exercise.SQUATS, 500.0),
            MissionDef(Exercise.PLANK, 900.0), MissionDef(Exercise.RUNNING, 20.0))),
        DungeonDef("S_THRONE", "Monarch's Throne", "S", "Monarch", listOf(
            MissionDef(Exercise.PULL_UPS, 80.0), MissionDef(Exercise.PUSH_UPS, 600.0),
            MissionDef(Exercise.PLANK, 1500.0), MissionDef(Exercise.RUNNING, 30.0)))
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
    const val DAYS = 7

    /** Targets scale with the player's level, which only rises through real training. */
    fun ability(level: Int) = (0.7 + 0.03 * level).coerceIn(0.7, 2.0)

    fun target(m: MissionDef, level: Int): Double {
        val raw = m.base * ability(level)
        val r = when (m.ex.kind) {
            Kind.REPS -> maxOf(5.0, round(raw / 5) * 5)
            Kind.KM -> maxOf(1.0, round(raw * 2) / 2)
            Kind.SECONDS -> maxOf(60.0, round(raw / 30) * 30)
        }
        return min(r, m.ex.dailyCap * 4)
    }
    fun clearXp(d: DungeonDef) = 100L * (Progression.RANKS.indexOf(d.rank) + 1)
}

object Achievements {
    private val fixed = mapOf(
        "FIRST_LOG" to "Awakened", "STREAK_7" to "Unbroken (7-day streak)", "STREAK_30" to "Iron Will (30-day streak)",
        "LEVEL_10" to "Rising Hunter (Level 10)", "LEVEL_25" to "Seasoned Hunter (Level 25)", "SKILL_FIRST" to "Skill Awakened"
    ) + Progression.RANKS.drop(1).associate { "RANK_$it" to "Rank $it Hunter" }

    fun title(id: String): String? = fixed[id] ?: if (id.startsWith("DUNGEON_"))
        Dungeons.byId(id.removePrefix("DUNGEON_"))?.let { "${it.title} (cleared ${it.name})" } else null
}
