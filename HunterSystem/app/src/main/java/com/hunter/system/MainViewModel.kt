package com.hunter.system

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hunter.system.data.*
import com.hunter.system.domain.*
import com.hunter.system.voice.SystemVoice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class QuestView(val quest: QuestEntity, val progress: Double)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HunterRepository(HunterDatabase.get(app))
    private val voice = SystemVoice(app)
    private fun <T> Flow<T>.hot(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val player = repo.player.hot(null)
    val logs = repo.logs.hot(emptyList())
    val today = MutableStateFlow(LocalDate.now().toEpochDay())
    val quests = combine(today.flatMapLatest { repo.observeQuests(it) }, repo.logs) { qs, ls ->
        qs.map { QuestView(it, QuestRules.progress(it, ls)) }
    }.hot(emptyList())
    val completed = repo.completed.hot(0)
    val skills = repo.skills.map { l -> l.map { it.skillId }.toSet() }.hot(emptySet())
    val achievements = repo.achievements.map { l -> l.mapNotNull { Achievements.title(it.id) } }.hot(emptyList())
    val activeRun = repo.activeRun.hot(null)
    val clearedCount = repo.clearedCount.hot(0)
    val recommendations = combine(repo.logs, today) { ls, d -> Recommender.recommend(ls, d) }.hot(emptyList())
    val insights = combine(repo.logs, repo.doneQuests, today) { ls, qs, d -> Analytics.build(ls, qs, d) }
        .flowOn(Dispatchers.Default).hot(null)
    val message = MutableStateFlow<String?>(null)
    val voiceEnabled = MutableStateFlow(voice.enabled)

    val elapsedMs = MutableStateFlow(0L)
    val timerRunning = MutableStateFlow(false)
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            try { repo.ensurePlayer(); repo.ensureQuests(today.value) }
            catch (e: Exception) { message.value = "[SYSTEM] Startup error: ${e.message}" }
            voice.speak("System initialized.")
            announceDaily(today.value)
            while (true) {
                delay(30_000)
                val d = LocalDate.now().toEpochDay()
                if (d != today.value) {
                    try { repo.ensureQuests(d); today.value = d; announceDaily(d) } catch (e: Exception) { /* retry next tick */ }
                }
            }
        }
    }

    private suspend fun announceDaily(day: Long) {
        try {
            if (voice.lastDailyDay != day && repo.hasOpenDaily(day)) { voice.lastDailyDay = day; voice.speak("Daily Quest Available.") }
        } catch (e: Exception) { }
    }

    fun setVoice(on: Boolean) { voice.enabled = on; voiceEnabled.value = on }

    fun timerToggle() {
        if (timerRunning.value) { timerJob?.cancel(); timerRunning.value = false; return }
        val base = elapsedMs.value
        val t0 = System.nanoTime()
        timerRunning.value = true
        timerJob = viewModelScope.launch {
            while (true) { elapsedMs.value = base + (System.nanoTime() - t0) / 1_000_000; delay(200) }
        }
    }
    fun timerReset() { timerJob?.cancel(); timerRunning.value = false; elapsedMs.value = 0 }

    fun log(ex: Exercise, value: Double?, difficulty: Int, pain: Boolean, durationSec: Long) = viewModelScope.launch {
        if (value == null || value <= 0.0) { message.value = "[SYSTEM] Enter a valid amount."; return@launch }
        if (value > ex.dailyCap * 3) { message.value = "[SYSTEM] Unrealistic amount rejected."; return@launch }
        try {
            val r = repo.logWorkout(ex, value, difficulty, pain, if (ex.kind == Kind.SECONDS && durationSec == 0L) value.toLong() else durationSec)
            timerReset()
            message.value = buildString {
                append("[SYSTEM] +${r.xp} XP")
                if (r.isPr) append("\nNew personal record!")
                r.quests.forEach { append("\nQUEST COMPLETED: $it") }
                if (r.questXp > 0) append("\nQuest reward: +${r.questXp} XP")
                r.dungeon?.let { append("\nDUNGEON CLEARED: $it") }
                r.achievements.forEach { append("\nACHIEVEMENT UNLOCKED: $it") }
                if (r.leveledUp) append("\nLEVEL UP → ${r.level} (+1 skill point)")
                if (r.rankChanged) append("\nHUNTER RANK UPDATED → ${r.rank}")
                if (r.pain) append("\nPain reported. Rest and avoid pushing through it; see a professional if it persists.")
            }
            if (r.quests.isNotEmpty()) voice.speak("Quest completed.")
            if (r.dungeon != null) voice.speak("Dungeon cleared.")
            if (r.leveledUp) voice.speak("Level up.")
            if (r.rankChanged) voice.speak("Hunter rank updated.")
            if (r.achievements.isNotEmpty()) voice.speak("New achievement unlocked.")
        } catch (e: Exception) { message.value = "[SYSTEM] Could not save: ${e.message}" }
    }

    fun createQuest(cat: String, ex: Exercise?, mode: String, target: Double?, title: String) = viewModelScope.launch {
        if (target == null) { message.value = "[SYSTEM] Enter a valid target."; return@launch }
        try { message.value = repo.createCustomQuest(cat, ex, mode, target, title, today.value) ?: "[SYSTEM] Quest created." }
        catch (e: Exception) { message.value = "[SYSTEM] Could not create quest: ${e.message}" }
    }

    fun unlockSkill(id: String) = viewModelScope.launch {
        try { message.value = repo.unlockSkill(id) ?: "[SYSTEM] Skill unlocked." }
        catch (e: Exception) { message.value = "[SYSTEM] Could not unlock: ${e.message}" }
    }

    fun enterDungeon(id: String) = viewModelScope.launch {
        try { message.value = repo.enterDungeon(id, today.value) ?: "[SYSTEM] Dungeon entered. You have ${Dungeons.DAYS} days." }
        catch (e: Exception) { message.value = "[SYSTEM] Could not enter: ${e.message}" }
    }

    fun abandonDungeon() = viewModelScope.launch {
        try { repo.abandonDungeon(); message.value = "[SYSTEM] Dungeon abandoned." }
        catch (e: Exception) { message.value = "[SYSTEM] Failed: ${e.message}" }
    }

    fun deleteQuest(id: Long) = viewModelScope.launch { repo.deleteQuest(id) }
    fun rename(name: String) = viewModelScope.launch { repo.rename(name); message.value = "[SYSTEM] Name updated." }

    fun backup(uri: Uri) = viewModelScope.launch {
        try {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
            message.value = "[SYSTEM] Backup saved."
        } catch (e: Exception) { message.value = "[SYSTEM] Backup failed: ${e.message}" }
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        try {
            val json = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            }
            repo.restoreJson(json)
            repo.ensureQuests(today.value)
            message.value = "[SYSTEM] Progress restored."
        } catch (e: Exception) { message.value = "[SYSTEM] Restore failed, nothing changed: ${e.message}" }
    }

    fun clearMessage() { message.value = null }
    override fun onCleared() { voice.shutdown(); super.onCleared() }
}
