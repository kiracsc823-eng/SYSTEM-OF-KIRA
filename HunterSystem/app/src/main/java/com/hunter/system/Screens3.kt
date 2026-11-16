package com.hunter.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.system.data.PlayerEntity
import com.hunter.system.data.QuestRules
import com.hunter.system.domain.*

@Composable
fun DungeonScreen(vm: MainViewModel, p: PlayerEntity?) {
    val run by vm.activeRun.collectAsState()
    val quests by vm.quests.collectAsState()
    val cleared by vm.clearedCount.collectAsState()
    val msg by vm.message.collectAsState()
    val today by vm.today.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val level = p?.level ?: 1
    val rankIdx = Progression.RANKS.indexOf(p?.rank ?: "E")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        msg?.let { Panel(null) { Text(it); TextButton({ vm.clearMessage() }) { Text("DISMISS") } } }
        Text("Dungeons cleared: $cleared", color = Blue, fontWeight = FontWeight.Bold)
        run?.let { r ->
            Panel("[ ACTIVE: ${r.name} ]") {
                Text("${(r.endDay - today + 1).coerceAtLeast(0)} days left · clear bonus ${r.clearXp} XP", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                quests.filter { it.quest.autoKey.startsWith("G:${r.id}:") }.forEach {
                    QuestCard(it, vm); Spacer(Modifier.height(8.dp))
                }
                TextButton({ confirm = true }) { Text("ABANDON DUNGEON") }
            }
        }
        Dungeons.all.forEach { d ->
            val locked = rankIdx < Progression.RANKS.indexOf(d.rank)
            Panel("[ RANK ${d.rank} ]  ${d.name}") {
                d.missions.forEach { m ->
                    Text("• " + QuestRules.autoTitle(m.ex, QuestRules.VOLUME, Dungeons.target(m, level)), fontSize = 13.sp)
                }
                Text("Clear: ${Dungeons.clearXp(d)} XP + title \"${d.title}\" · ${Dungeons.DAYS} days",
                    fontSize = 12.sp, color = Blue, modifier = Modifier.padding(vertical = 6.dp))
                Button({ vm.enterDungeon(d.id) }, enabled = !locked && run == null) {
                    Text(if (locked) "LOCKED (Rank ${d.rank})" else "ENTER")
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Abandon dungeon?") }, text = { Text("Unfinished missions are removed. Rewards already earned stay.") },
        confirmButton = { TextButton({ vm.abandonDungeon(); confirm = false }) { Text("ABANDON") } },
        dismissButton = { TextButton({ confirm = false }) { Text("CANCEL") } })
}

@Composable
fun SkillsScreen(vm: MainViewModel, p: PlayerEntity?) {
    val unlocked by vm.skills.collectAsState()
    val msg by vm.message.collectAsState()
    val level = p?.level ?: 1
    val pts = Skills.points(level, unlocked)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("[ SKILL POINTS ]") {
            Text("$pts available", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Blue)
            Text("You earn 1 point for every level gained.", fontSize = 12.sp)
        }
        msg?.let { Panel(null) { Text(it); TextButton({ vm.clearMessage() }) { Text("DISMISS") } } }
        Skills.all.forEach { s ->
            val have = s.id in unlocked
            Panel(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Bold, color = if (have) Blue else Txt)
                        Text(s.desc, fontSize = 13.sp)
                        Text("Cost ${s.cost} · Level ${s.minLevel}" + (s.prereq?.let { " · needs ${Skills.byId(it)?.name}" } ?: ""),
                            fontSize = 11.sp, color = Blue)
                    }
                    if (have) Text("UNLOCKED", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else Button({ vm.unlockSkill(s.id) }, enabled = Skills.canUnlock(s, level, unlocked) == null) { Text("UNLOCK") }
                }
            }
        }
    }
}

@Composable
fun MoreScreen(vm: MainViewModel, p: PlayerEntity?) {
    var sub by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Reports", "History", "System").forEachIndexed { i, l ->
                FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(l) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (sub) { 0 -> ReportsScreen(vm); 1 -> HistoryScreen(vm); else -> SystemScreen(vm, p) }
        }
    }
}
