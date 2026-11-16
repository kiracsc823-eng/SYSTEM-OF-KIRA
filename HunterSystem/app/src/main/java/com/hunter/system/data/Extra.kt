package com.hunter.system.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "skill_unlock") data class SkillUnlock(@PrimaryKey val skillId: String, val unlockedAt: Long)
@Entity(tableName = "achievement") data class AchievementEntity(@PrimaryKey val id: String, val unlockedAt: Long)
@Entity(tableName = "dungeon_run")
data class DungeonRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val defId: String, val name: String,
    val enteredAt: Long, val startDay: Long, val endDay: Long, val status: String, val clearXp: Long
)

@Dao
interface ExtraDao {
    @Query("SELECT * FROM skill_unlock") fun observeSkills(): Flow<List<SkillUnlock>>
    @Query("SELECT skillId FROM skill_unlock") suspend fun skillIds(): List<String>
    @Query("SELECT * FROM skill_unlock") suspend fun allSkills(): List<SkillUnlock>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSkill(s: SkillUnlock)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSkills(l: List<SkillUnlock>)
    @Query("DELETE FROM skill_unlock") suspend fun clearSkills()

    @Query("SELECT * FROM achievement ORDER BY unlockedAt") fun observeAchievements(): Flow<List<AchievementEntity>>
    @Query("SELECT id FROM achievement") suspend fun achievementIds(): List<String>
    @Query("SELECT * FROM achievement") suspend fun allAchievements(): List<AchievementEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAchievement(a: AchievementEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAchievements(l: List<AchievementEntity>)
    @Query("DELETE FROM achievement") suspend fun clearAchievements()

    @Query("SELECT * FROM dungeon_run WHERE status = 'ACTIVE' LIMIT 1") fun observeActiveRun(): Flow<DungeonRun?>
    @Query("SELECT * FROM dungeon_run WHERE status = 'ACTIVE' LIMIT 1") suspend fun activeRun(): DungeonRun?
    @Insert suspend fun insertRun(r: DungeonRun): Long
    @Update suspend fun updateRun(r: DungeonRun)
    @Query("UPDATE dungeon_run SET status = 'FAILED' WHERE status = 'ACTIVE' AND endDay < :today") suspend fun expireRuns(today: Long)
    @Query("SELECT COUNT(*) FROM dungeon_run WHERE status = 'CLEARED'") fun observeCleared(): Flow<Int>
    @Query("SELECT * FROM dungeon_run") suspend fun allRuns(): List<DungeonRun>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRuns(l: List<DungeonRun>)
    @Query("DELETE FROM dungeon_run") suspend fun clearRuns()
    @Query("SELECT * FROM quest WHERE autoKey LIKE :prefix") suspend fun questsByKey(prefix: String): List<QuestEntity>
    @Query("DELETE FROM quest WHERE autoKey LIKE :prefix AND completedAt = 0") suspend fun deleteOpenByKey(prefix: String)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `skill_unlock` (`skillId` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`skillId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `achievement` (`id` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dungeon_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`defId` TEXT NOT NULL, `name` TEXT NOT NULL, `enteredAt` INTEGER NOT NULL, `startDay` INTEGER NOT NULL, " +
                "`endDay` INTEGER NOT NULL, `status` TEXT NOT NULL, `clearXp` INTEGER NOT NULL)"
        )
    }
}
