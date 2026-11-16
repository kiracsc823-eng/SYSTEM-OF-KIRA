package com.hunter.system.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hunter.system.domain.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Entity(tableName = "player")
data class PlayerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Hunter",
    val level: Int = 1, val xp: Long = 0, val rank: String = "E",
    val trainingDays: Int = 0, val streak: Int = 0, val bestStreak: Int = 0,
    val lastTrainingDay: Long = -1,
    val strength: Double = 1.0, val endurance: Double = 1.0, val stamina: Double = 1.0,
    val agility: Double = 1.0, val discipline: Double = 1.0, val recovery: Double = 1.0
)

@Entity(tableName = "workout_log")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercise: String, val value: Double, val difficulty: Int,
    val xp: Long, val timestamp: Long, val day: Long, val pain: Boolean,
    @ColumnInfo(defaultValue = "0") val durationSec: Long = 0
)

@Entity(tableName = "quest")
data class QuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, val title: String, val exercise: String?, val mode: String,
    val target: Double, val xpReward: Long, val periodStart: Long, val periodEnd: Long,
    val completedAt: Long, val custom: Boolean, val autoKey: String, val sinceTs: Long
)

object QuestRules {
    const val DAILY = "DAILY"; const val WEEKLY = "WEEKLY"; const val SPECIAL = "SPECIAL"
    const val VOLUME = "VOLUME"; const val DAYS = "DAYS"; const val BEST = "BEST"

    fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
    fun weekStart(day: Long): Long = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)

    fun progress(q: QuestEntity, logs: List<WorkoutLog>): Double {
        val l = logs.filter {
            it.day >= q.periodStart && (q.periodEnd < 0 || it.day <= q.periodEnd) &&
                it.timestamp >= q.sinceTs && (q.exercise == null || it.exercise == q.exercise)
        }
        return when (q.mode) {
            DAYS -> l.map { it.day }.distinct().size.toDouble()
            BEST -> l.maxOfOrNull { it.value } ?: 0.0
            else -> l.sumOf { it.value }
        }
    }

    fun reward(ex: Exercise?, mode: String, target: Double, cat: String): Long {
        val rate = ex?.xpPerUnit ?: 0.3
        val base = when (mode) { DAYS -> target * 15.0; BEST -> target * rate * 0.8; else -> target * rate * 0.6 }
        return (base * (if (cat == SPECIAL) 1.5 else 1.0)).roundToLong().coerceIn(10, 500)
    }

    fun autoTitle(ex: Exercise?, mode: String, target: Double) = when (mode) {
        DAYS -> "Train ${fmt(target)} days"
        BEST -> "${ex?.label}: ${fmt(target)} ${ex?.kind?.unit} in one log"
        else -> "${fmt(target)} ${ex?.kind?.unit} ${ex?.label}"
    }

    fun validate(cat: String, ex: Exercise?, mode: String, target: Double): String? {
        if (target.isNaN() || target <= 0.0) return "[SYSTEM] Target must be above zero."
        if (mode == DAYS) {
            if (cat == DAILY) return "[SYSTEM] Daily quests can't count days."
            if (target > (if (cat == WEEKLY) 7 else 365)) return "[SYSTEM] Too many days for this quest type."
        } else {
            val e = ex ?: return "[SYSTEM] Pick an exercise."
            val limit = if (mode == BEST) e.dailyCap * 2 else
                e.dailyCap * when (cat) { DAILY -> 1.0; WEEKLY -> 7.0; else -> 30.0 }
            if (target > limit) return "[SYSTEM] Target too high to be realistic (max ${fmt(limit)} ${e.kind.unit})."
        }
        return null
    }
}

@Dao
interface HunterDao {
    @Query("SELECT * FROM player WHERE id = 1") fun observePlayer(): Flow<PlayerEntity?>
    @Query("SELECT * FROM player WHERE id = 1") suspend fun getPlayer(): PlayerEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlayerIfAbsent(p: PlayerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlayer(p: PlayerEntity)
    @Insert suspend fun insertLog(l: WorkoutLog): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLogs(l: List<WorkoutLog>)
    @Query("SELECT * FROM workout_log ORDER BY timestamp DESC") fun observeLogs(): Flow<List<WorkoutLog>>
    @Query("SELECT * FROM workout_log ORDER BY timestamp") suspend fun allLogs(): List<WorkoutLog>
    @Query("SELECT COALESCE(SUM(value),0) FROM workout_log WHERE exercise = :e AND day = :d")
    suspend fun sumForDay(e: String, d: Long): Double
    @Query("SELECT COALESCE(MAX(value),0) FROM workout_log WHERE exercise = :e")
    suspend fun best(e: String): Double
    @Query("DELETE FROM workout_log") suspend fun clearLogs()

    @Insert suspend fun insertQuest(q: QuestEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuests(q: List<QuestEntity>)
    @Update suspend fun updateQuest(q: QuestEntity)
    @Query("SELECT * FROM quest WHERE periodStart <= :today AND (periodEnd < 0 OR periodEnd >= :today) ORDER BY completedAt > 0, id")
    fun observeQuests(today: Long): Flow<List<QuestEntity>>
    @Query("SELECT * FROM quest WHERE completedAt = 0 AND periodStart <= :today AND (periodEnd < 0 OR periodEnd >= :today)")
    suspend fun openQuests(today: Long): List<QuestEntity>
    @Query("SELECT COUNT(*) FROM quest WHERE autoKey LIKE :prefix") suspend fun countKey(prefix: String): Int
    @Query("SELECT COUNT(*) FROM quest WHERE custom = 1 AND sinceTs >= :ts") suspend fun customSince(ts: Long): Int
    @Query("SELECT COUNT(*) FROM quest WHERE completedAt > 0") fun observeCompleted(): Flow<Int>
    @Query("SELECT * FROM quest WHERE completedAt > 0") fun observeCompletedQuests(): Flow<List<QuestEntity>>
    @Query("SELECT * FROM quest") suspend fun allQuests(): List<QuestEntity>
    @Query("DELETE FROM quest") suspend fun clearQuests()
    @Query("DELETE FROM quest WHERE id = :id AND custom = 1 AND completedAt = 0") suspend fun deleteCustom(id: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_log ADD COLUMN durationSec INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quest` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`category` TEXT NOT NULL, `title` TEXT NOT NULL, `exercise` TEXT, `mode` TEXT NOT NULL, " +
                "`target` REAL NOT NULL, `xpReward` INTEGER NOT NULL, `periodStart` INTEGER NOT NULL, " +
                "`periodEnd` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `custom` INTEGER NOT NULL, " +
                "`autoKey` TEXT NOT NULL, `sinceTs` INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [PlayerEntity::class, WorkoutLog::class, QuestEntity::class, SkillUnlock::class, AchievementEntity::class, DungeonRun::class],
    version = 3, exportSchema = false
)
abstract class HunterDatabase : RoomDatabase() {
    abstract fun dao(): HunterDao
    abstract fun dao3(): ExtraDao
    companion object {
        @Volatile private var inst: HunterDatabase? = null
        fun get(c: Context): HunterDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, HunterDatabase::class.java, "hunter.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { inst = it }
        }
    }
}

data class LogResult(
    val xp: Long, val questXp: Long, val quests: List<String>, val isPr: Boolean,
    val leveledUp: Boolean, val level: Int, val rankChanged: Boolean, val rank: String, val pain: Boolean,
    val achievements: List<String>, val dungeon: String?
)

class HunterRepository(private val db: HunterDatabase) {
    private val dao = db.dao()
    private val extra = db.dao3()
    val player = dao.observePlayer()
    val logs = dao.observeLogs()
    val completed = dao.observeCompleted()
    val doneQuests = dao.observeCompletedQuests()
    val skills = extra.observeSkills()
    val achievements = extra.observeAchievements()
    val activeRun = extra.observeActiveRun()
    val clearedCount = extra.observeCleared()
    suspend fun hasOpenDaily(day: Long) = dao.openQuests(day).any { it.category == QuestRules.DAILY }
    fun observeQuests(today: Long) = dao.observeQuests(today)

    suspend fun ensurePlayer() = dao.insertPlayerIfAbsent(PlayerEntity())
    suspend fun ensureQuests(today: Long) = db.withTransaction { genQuests(today) }
    suspend fun deleteQuest(id: Long) = dao.deleteCustom(id)

    suspend fun rename(name: String) {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return
        db.withTransaction { dao.getPlayer()?.let { dao.upsertPlayer(it.copy(name = clean)) } }
    }

    private fun auto(cat: String, key: String, title: String, ex: Exercise?, mode: String, target: Double, s: Long, e: Long) =
        QuestEntity(category = cat, title = title, exercise = ex?.name, mode = mode, target = target,
            xpReward = QuestRules.reward(ex, mode, target, cat), periodStart = s, periodEnd = e,
            completedAt = 0, custom = false, autoKey = key, sinceTs = 0)

    /** Generates today's / this week's quests once. Adapts to ability, pain and fatigue. */
    private suspend fun genQuests(today: Long) {
        extra.expireRuns(today)
        val logs = dao.allLogs()
        if (dao.countKey("D:$today:%") == 0) {
            val recentPain = logs.any { it.pain && it.day >= today - 2 }
            val trained = logs.filter { it.day in (today - 6) until today }.map { it.day }.distinct().size
            if (recentPain || trained >= 5) {
                dao.insertQuest(auto(QuestRules.DAILY, "D:$today:REC", "Recovery session: 10 min stretching",
                    Exercise.STRETCHING, QuestRules.VOLUME, 600.0, today, today))
            } else {
                val s = listOf(Exercise.PUSH_UPS, Exercise.SQUATS, Exercise.SIT_UPS)[(today % 3).toInt()]
                val bestS = logs.filter { it.exercise == s.name }.maxOfOrNull { it.value } ?: 0.0
                val startT = when (s) { Exercise.PUSH_UPS -> 10.0; Exercise.SQUATS -> 20.0; else -> 15.0 }
                val tS = if (bestS > 0) (bestS * 0.9).roundToInt().coerceIn(5, (s.dailyCap / 2).toInt()).toDouble() else startT
                dao.insertQuest(auto(QuestRules.DAILY, "D:$today:STR", "Daily training: ${QuestRules.fmt(tS)} ${s.label}",
                    s, QuestRules.VOLUME, tS, today, today))
                val bestR = logs.filter { it.exercise == Exercise.RUNNING.name }.maxOfOrNull { it.value } ?: 0.0
                val km = if (bestR > 0) (Math.round(bestR * 0.8 * 2) / 2.0).coerceIn(1.0, 5.0) else 1.0
                dao.insertQuest(auto(QuestRules.DAILY, "D:$today:RUN", "Daily run: ${QuestRules.fmt(km)} km",
                    Exercise.RUNNING, QuestRules.VOLUME, km, today, today))
                dao.insertQuest(auto(QuestRules.DAILY, "D:$today:MOB", "Mobility warm-up: 5 min stretching",
                    Exercise.STRETCHING, QuestRules.VOLUME, 300.0, today, today))
            }
        }
        val ws = QuestRules.weekStart(today)
        if (dao.countKey("W:$ws:%") == 0) {
            val prev = logs.filter { it.exercise == Exercise.PUSH_UPS.name && it.day in (ws - 7) until ws }.sumOf { it.value }
            val pu = if (prev > 0) (prev * 1.1).roundToInt().coerceIn(50, 600).toDouble() else 100.0
            val e = ws + 6
            dao.insertQuest(auto(QuestRules.WEEKLY, "W:$ws:DAYS", "Consistency: train 4 days this week", null, QuestRules.DAYS, 4.0, ws, e))
            dao.insertQuest(auto(QuestRules.WEEKLY, "W:$ws:RUN", "Run 5 km this week", Exercise.RUNNING, QuestRules.VOLUME, 5.0, ws, e))
            dao.insertQuest(auto(QuestRules.WEEKLY, "W:$ws:STR", "Strength: ${QuestRules.fmt(pu)} push-ups this week", Exercise.PUSH_UPS, QuestRules.VOLUME, pu, ws, e))
        }
        if (dao.countKey("S:%") == 0) {
            dao.insertQuest(auto(QuestRules.SPECIAL, "S:PU50", "Hunter challenge: 50 push-ups in one log", Exercise.PUSH_UPS, QuestRules.BEST, 50.0, today, -1))
            dao.insertQuest(auto(QuestRules.SPECIAL, "S:RUN5", "Hunter challenge: run 5 km in one go", Exercise.RUNNING, QuestRules.BEST, 5.0, today, -1))
            dao.insertQuest(auto(QuestRules.SPECIAL, "S:DAYS30", "Milestone: train on 30 different days", null, QuestRules.DAYS, 30.0, today, -1))
        }
    }

    /** Returns an error message, or null on success. Max 3 custom quests per day; rewards are halved. */
    suspend fun createCustomQuest(cat: String, ex: Exercise?, mode: String, target: Double, title: String, today: Long): String? {
        QuestRules.validate(cat, ex, mode, target)?.let { return it }
        return db.withTransaction {
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (dao.customSince(startOfDay) >= 3) return@withTransaction "[SYSTEM] Limit: 3 custom quests per day."
            val e = if (mode == QuestRules.DAYS) null else ex
            val s = if (cat == QuestRules.WEEKLY) QuestRules.weekStart(today) else today
            val end = when (cat) { QuestRules.DAILY -> today; QuestRules.WEEKLY -> s + 6; else -> -1L }
            dao.insertQuest(QuestEntity(category = cat, title = title.trim().ifEmpty { QuestRules.autoTitle(e, mode, target) }.take(60),
                exercise = e?.name, mode = mode, target = target,
                xpReward = (QuestRules.reward(e, mode, target, cat) / 2).coerceIn(5, 300),
                periodStart = s, periodEnd = end, completedAt = 0, custom = true, autoKey = "",
                sinceTs = System.currentTimeMillis()))
            null
        }
    }

    suspend fun logWorkout(
        ex: Exercise, value: Double, difficulty: Int, pain: Boolean, durationSec: Long,
        today: Long = LocalDate.now().toEpochDay()
    ): LogResult = db.withTransaction {
        genQuests(today)
        val p = dao.getPlayer() ?: PlayerEntity()
        val eff = Skills.effects(extra.skillIds().toSet(), ex)
        val firstToday = p.lastTrainingDay != today
        val streak = if (firstToday) Progression.nextStreak(p.streak, p.lastTrainingDay, today) else p.streak
        val res = XpEngine.calculate(XpInput(ex, value, difficulty, dao.sumForDay(ex.name, today), streak,
            dao.best(ex.name), eff.xpMult, eff.capMult, eff.streakCap))
        val now = System.currentTimeMillis()
        dao.insertLog(WorkoutLog(exercise = ex.name, value = value, difficulty = difficulty, xp = res.xp,
            timestamp = now, day = today, pain = pain, durationSec = durationSec))
        val logs = dao.allLogs()
        var bonus = 0L
        val done = mutableListOf<String>()
        dao.openQuests(today).forEach { q ->
            if (QuestRules.progress(q, logs) >= q.target - 1e-9) {
                dao.updateQuest(q.copy(completedAt = now)); bonus += q.xpReward; done += q.title
            }
        }
        var dungeon: String? = null
        val newIds = mutableListOf<String>()
        extra.activeRun()?.let { run ->
            val ms = extra.questsByKey("G:${run.id}:%")
            if (ms.isNotEmpty() && ms.all { it.completedAt > 0 }) {
                extra.updateRun(run.copy(status = "CLEARED")); bonus += run.clearXp; dungeon = run.name
                newIds += "DUNGEON_${run.defId}"
            }
        }
        var np = p
        ex.weights.forEach { (a, w) -> np = np.addAttr(a, res.xp * w * 0.02) }
        if (firstToday) np = np.addAttr(Attr.DISCIPLINE, 0.5)
        val (lvl, xp) = Progression.applyXp(p.level, p.xp, res.xp + bonus)
        val days = p.trainingDays + if (firstToday) 1 else 0
        val best = maxOf(p.bestStreak, streak)
        val computed = Progression.rankFor(lvl, days, best)
        val rank = if (Progression.RANKS.indexOf(computed) > Progression.RANKS.indexOf(p.rank)) computed else p.rank
        newIds += "FIRST_LOG"
        if (best >= 7) newIds += "STREAK_7"
        if (best >= 30) newIds += "STREAK_30"
        if (lvl >= 10) newIds += "LEVEL_10"
        if (lvl >= 25) newIds += "LEVEL_25"
        Progression.RANKS.drop(1).take(Progression.RANKS.indexOf(rank)).forEach { newIds += "RANK_$it" }
        val have = extra.achievementIds().toSet()
        val fresh = newIds.distinct().filter { it !in have }
        fresh.forEach { extra.insertAchievement(AchievementEntity(it, now)) }
        dao.upsertPlayer(np.copy(level = lvl, xp = xp, rank = rank, trainingDays = days,
            streak = streak, bestStreak = best, lastTrainingDay = today))
        LogResult(res.xp, bonus, done, res.isPr, lvl > p.level, lvl, rank != p.rank, rank, pain,
            fresh.mapNotNull { Achievements.title(it) }, dungeon)
    }

    suspend fun unlockSkill(id: String): String? = db.withTransaction {
        val s = Skills.byId(id) ?: return@withTransaction "[SYSTEM] Unknown skill."
        val p = dao.getPlayer() ?: return@withTransaction "[SYSTEM] No player data."
        val have = extra.skillIds().toSet()
        Skills.canUnlock(s, p.level, have)?.let { return@withTransaction it }
        val now = System.currentTimeMillis()
        extra.insertSkill(SkillUnlock(id, now))
        extra.insertAchievement(AchievementEntity("SKILL_FIRST", now))
        null
    }

    suspend fun enterDungeon(defId: String, today: Long): String? = db.withTransaction {
        genQuests(today)
        val def = Dungeons.byId(defId) ?: return@withTransaction "[SYSTEM] Unknown dungeon."
        val p = dao.getPlayer() ?: return@withTransaction "[SYSTEM] No player data."
        if (extra.activeRun() != null) return@withTransaction "[SYSTEM] Clear or abandon your current dungeon first."
        if (Progression.RANKS.indexOf(p.rank) < Progression.RANKS.indexOf(def.rank))
            return@withTransaction "[SYSTEM] Requires Hunter Rank ${def.rank}."
        if (dao.allLogs().any { it.pain && it.day >= today - 2 })
            return@withTransaction "[SYSTEM] Pain reported recently. Recover before entering a dungeon."
        val now = System.currentTimeMillis()
        val end = today + Dungeons.DAYS - 1
        val id = extra.insertRun(DungeonRun(defId = def.id, name = def.name, enteredAt = now, startDay = today,
            endDay = end, status = "ACTIVE", clearXp = Dungeons.clearXp(def)))
        def.missions.forEachIndexed { i, m ->
            val t = Dungeons.target(m, p.level)
            dao.insertQuest(QuestEntity(category = "DUNGEON", title = QuestRules.autoTitle(m.ex, QuestRules.VOLUME, t),
                exercise = m.ex.name, mode = QuestRules.VOLUME, target = t,
                xpReward = QuestRules.reward(m.ex, QuestRules.VOLUME, t, "DUNGEON"),
                periodStart = today, periodEnd = end, completedAt = 0, custom = false, autoKey = "G:$id:$i", sinceTs = now))
        }
        null
    }

    suspend fun abandonDungeon() = db.withTransaction {
        val run = extra.activeRun() ?: return@withTransaction
        extra.updateRun(run.copy(status = "ABANDONED"))
        extra.deleteOpenByKey("G:${run.id}:%")
    }

    private fun PlayerEntity.addAttr(a: Attr, d: Double) = when (a) {
        Attr.STRENGTH -> copy(strength = strength + d)
        Attr.ENDURANCE -> copy(endurance = endurance + d)
        Attr.STAMINA -> copy(stamina = stamina + d)
        Attr.AGILITY -> copy(agility = agility + d)
        Attr.DISCIPLINE -> copy(discipline = discipline + d)
        Attr.RECOVERY -> copy(recovery = recovery + d)
    }

    suspend fun exportJson(): String = db.withTransaction {
        Backup.toJson(BackupData(dao.getPlayer() ?: PlayerEntity(), dao.allLogs(), dao.allQuests(),
            extra.allSkills(), extra.allAchievements(), extra.allRuns()))
    }

    /** Parses first; only touches the database if the backup is valid. */
    suspend fun restoreJson(json: String) {
        val b = Backup.fromJson(json)
        db.withTransaction {
            dao.clearLogs(); dao.clearQuests(); extra.clearSkills(); extra.clearAchievements(); extra.clearRuns()
            dao.upsertPlayer(b.player); dao.insertLogs(b.logs); dao.insertQuests(b.quests)
            extra.insertSkills(b.skills); extra.insertAchievements(b.achievements); extra.insertRuns(b.runs)
        }
    }
}

data class BackupData(
    val player: PlayerEntity, val logs: List<WorkoutLog>, val quests: List<QuestEntity>,
    val skills: List<SkillUnlock>, val achievements: List<AchievementEntity>, val runs: List<DungeonRun>
)

object Backup {
    fun toJson(d: BackupData): String = JSONObject().apply {
        val p = d.player
        put("version", 3)
        put("player", JSONObject().apply {
            put("name", p.name); put("level", p.level); put("xp", p.xp); put("rank", p.rank)
            put("trainingDays", p.trainingDays); put("streak", p.streak); put("bestStreak", p.bestStreak)
            put("lastTrainingDay", p.lastTrainingDay)
            put("strength", p.strength); put("endurance", p.endurance); put("stamina", p.stamina)
            put("agility", p.agility); put("discipline", p.discipline); put("recovery", p.recovery)
        })
        put("logs", JSONArray().also { a ->
            d.logs.forEach { l -> a.put(JSONObject().apply {
                put("id", l.id); put("exercise", l.exercise); put("value", l.value)
                put("difficulty", l.difficulty); put("xp", l.xp); put("timestamp", l.timestamp)
                put("day", l.day); put("pain", l.pain); put("durationSec", l.durationSec)
            }) }
        })
        put("quests", JSONArray().also { a ->
            d.quests.forEach { q -> a.put(JSONObject().apply {
                put("id", q.id); put("category", q.category); put("title", q.title)
                put("exercise", q.exercise ?: JSONObject.NULL); put("mode", q.mode); put("target", q.target)
                put("xpReward", q.xpReward); put("periodStart", q.periodStart); put("periodEnd", q.periodEnd)
                put("completedAt", q.completedAt); put("custom", q.custom); put("autoKey", q.autoKey)
                put("sinceTs", q.sinceTs)
            }) }
        })
        put("skills", JSONArray().also { a -> d.skills.forEach { a.put(JSONObject().put("id", it.skillId).put("at", it.unlockedAt)) } })
        put("achievements", JSONArray().also { a -> d.achievements.forEach { a.put(JSONObject().put("id", it.id).put("at", it.unlockedAt)) } })
        put("runs", JSONArray().also { a ->
            d.runs.forEach { r -> a.put(JSONObject().apply {
                put("id", r.id); put("defId", r.defId); put("name", r.name); put("enteredAt", r.enteredAt)
                put("startDay", r.startDay); put("endDay", r.endDay); put("status", r.status); put("clearXp", r.clearXp)
            }) }
        })
    }.toString()

    private fun arr(o: JSONObject, k: String) = o.optJSONArray(k) ?: JSONArray()

    fun fromJson(s: String): BackupData {
        val o = JSONObject(s)
        require(o.getInt("version") in 1..3) { "Unsupported backup version" }
        val j = o.getJSONObject("player")
        val p = PlayerEntity(
            name = j.getString("name"), level = j.getInt("level"), xp = j.getLong("xp"),
            rank = j.getString("rank"), trainingDays = j.getInt("trainingDays"),
            streak = j.getInt("streak"), bestStreak = j.getInt("bestStreak"),
            lastTrainingDay = j.getLong("lastTrainingDay"),
            strength = j.getDouble("strength"), endurance = j.getDouble("endurance"),
            stamina = j.getDouble("stamina"), agility = j.getDouble("agility"),
            discipline = j.getDouble("discipline"), recovery = j.getDouble("recovery")
        )
        val a = o.getJSONArray("logs")
        val logs = (0 until a.length()).map {
            val l = a.getJSONObject(it)
            WorkoutLog(l.getLong("id"), l.getString("exercise"), l.getDouble("value"), l.getInt("difficulty"),
                l.getLong("xp"), l.getLong("timestamp"), l.getLong("day"), l.getBoolean("pain"), l.optLong("durationSec", 0))
        }
        val qa = arr(o, "quests")
        val quests = (0 until qa.length()).map {
            val q = qa.getJSONObject(it)
            QuestEntity(q.getLong("id"), q.getString("category"), q.getString("title"),
                if (q.isNull("exercise")) null else q.getString("exercise"), q.getString("mode"),
                q.getDouble("target"), q.getLong("xpReward"), q.getLong("periodStart"), q.getLong("periodEnd"),
                q.getLong("completedAt"), q.getBoolean("custom"), q.getString("autoKey"), q.getLong("sinceTs"))
        }
        val sa = arr(o, "skills"); val aa = arr(o, "achievements"); val ra = arr(o, "runs")
        return BackupData(p, logs, quests,
            (0 until sa.length()).map { sa.getJSONObject(it).let { x -> SkillUnlock(x.getString("id"), x.getLong("at")) } },
            (0 until aa.length()).map { aa.getJSONObject(it).let { x -> AchievementEntity(x.getString("id"), x.getLong("at")) } },
            (0 until ra.length()).map { ra.getJSONObject(it).let { r ->
                DungeonRun(r.getLong("id"), r.getString("defId"), r.getString("name"), r.getLong("enteredAt"),
                    r.getLong("startDay"), r.getLong("endDay"), r.getString("status"), r.getLong("clearXp")) } })
    }
}
