package com.hunter.system

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hunter.system.data.PlayerEntity
import com.hunter.system.data.Recommendation
import com.hunter.system.data.QuestRules
import com.hunter.system.domain.*
import java.time.LocalDate

@Composable
internal fun StatLine(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(k, Modifier.weight(1f)); Text(v, color = Blue, fontWeight = FontWeight.Bold)
}

@Composable
private fun Bar(p: Float, h: Int = 8, modifier: Modifier = Modifier) = LinearProgressIndicator(
    progress = { p.coerceIn(0f, 1f) },
    modifier = modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(4.dp)), color = Blue, trackColor = Bg)

@Composable
fun StatusScreen(p: PlayerEntity?, completed: Int, recs: List<Recommendation>, achievements: List<String>) {
    if (p == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("SYSTEM INITIALIZING...", color = Blue) }; return }
    val need = Progression.xpToNext(p.level)
    val today = LocalDate.now().toEpochDay()
    val streak = if (p.lastTrainingDay >= today - 2) p.streak else 0
    val rp = Progression.rankProgress(p.level, p.trainingDays, p.bestStreak, p.rank)
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        .55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("[ STATUS ]") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Level ${p.level}", color = Blue)
                }
                Text(p.rank, fontSize = 56.sp, fontWeight = FontWeight.Black, color = Blue, modifier = Modifier.alpha(pulse))
            }
            Spacer(Modifier.height(10.dp))
            Bar(p.xp.toFloat() / need)
            Text("${p.xp} / $need XP", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Panel("[ SYSTEM RECOMMENDS ]") {
            recs.forEachIndexed { i, r ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Text(r.title, fontWeight = FontWeight.Bold, color = if (r.warn) Color(0xFFFFB74D) else Blue)
                Text(r.detail, fontSize = 13.sp)
            }
        }
        Panel("[ RECORD ]") {
            StatLine("Total training days", "${p.trainingDays}")
            StatLine("Current streak", "$streak")
            StatLine("Best streak", "${p.bestStreak}")
            StatLine("Completed missions", "$completed")
        }
        Panel("[ OVERALL PROGRESS ]") {
            if (rp == null) Text("Maximum rank reached.") else {
                val r = Progression.RANK_REQS.getValue(rp.first)
                Text("Toward Rank ${rp.first}  ·  ${(rp.second * 100).toInt()}%", color = Blue)
                Spacer(Modifier.height(6.dp))
                Bar(rp.second.toFloat())
                Text("Needs: Level ${r.first} · ${r.second} training days" + (if (r.third > 0) " · best streak ${r.third}" else ""),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Panel("[ ACHIEVEMENTS ]") {
            if (achievements.isEmpty()) Text("None unlocked yet.", fontSize = 13.sp)
            achievements.forEach { Text("★ $it", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Panel("[ ATTRIBUTES ]") {
            listOf("Strength" to p.strength, "Endurance" to p.endurance, "Stamina" to p.stamina,
                "Agility" to p.agility, "Discipline" to p.discipline, "Recovery" to p.recovery).forEach { (n, v) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.width(96.dp), fontSize = 13.sp)
                    Bar((v / 100.0).toFloat(), 6, Modifier.weight(1f))
                    Text("%.1f".format(v), Modifier.width(52.dp).padding(start = 8.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TrainScreen(vm: MainViewModel) {
    var ex by rememberSaveable { mutableStateOf(Exercise.PUSH_UPS) }
    var text by rememberSaveable { mutableStateOf("") }
    var diff by rememberSaveable { mutableStateOf(3f) }
    var pain by rememberSaveable { mutableStateOf(false) }
    val msg by vm.message.collectAsState()
    val elapsed by vm.elapsedMs.collectAsState()
    val running by vm.timerRunning.collectAsState()
    val secs = elapsed / 1000
    val count = text.toDoubleOrNull()?.toInt() ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("[ SESSION ]") {
            Text("%02d:%02d".format(secs / 60, secs % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Blue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.timerToggle() }) { Text(if (running) "PAUSE" else "START") }
                OutlinedButton({ vm.timerReset() }) { Text("RESET") }
                if (ex.kind == Kind.SECONDS) OutlinedButton({ text = secs.toString() }, enabled = secs > 0) { Text("USE AS AMOUNT") }
            }
            if (ex.kind == Kind.REPS) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({ text = maxOf(0, count - 1).toString() }) { Text("−1") }
                    Button({ text = (count + 1).toString() }) { Text("+1 REP") }
                    OutlinedButton({ text = (count + 5).toString() }) { Text("+5") }
                    Text("Count: $count", color = Blue)
                }
            }
            Text("Warm up first. Stretching and recovery earn XP too.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Panel("[ LOG TRAINING ]") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Exercise.entries) { e -> FilterChip(selected = ex == e, onClick = { ex = e }, label = { Text(e.label) }) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(text, { text = it }, label = { Text("Amount (${ex.kind.unit})") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("Difficulty ${diff.toInt()} (1 easy – 5 max effort)", Modifier.padding(top = 12.dp), fontSize = 13.sp)
            Slider(diff, { diff = it }, valueRange = 1f..5f, steps = 3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(pain, { pain = it }); Text("I felt pain during this exercise")
            }
            Button({ vm.log(ex, text.toDoubleOrNull(), diff.toInt(), pain, secs); text = ""; pain = false },
                Modifier.fillMaxWidth()) { Text("LOG WORKOUT") }
        }
        msg?.let { Panel("[ SYSTEM MESSAGE ]") { Text(it); TextButton({ vm.clearMessage() }) { Text("DISMISS") } } }
    }
}

@Composable
fun QuestsScreen(vm: MainViewModel) {
    val quests by vm.quests.collectAsState()
    val msg by vm.message.collectAsState()
    var cat by rememberSaveable { mutableStateOf(QuestRules.DAILY) }
    var dialog by remember { mutableStateOf(false) }
    val shown = quests.filter { it.quest.category == cat }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QuestRules.DAILY to "Daily", QuestRules.WEEKLY to "Weekly", QuestRules.SPECIAL to "Special").forEach { (k, l) ->
                FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(l) })
            }
        }
        msg?.let { Panel(null) { Text(it); TextButton({ vm.clearMessage() }) { Text("DISMISS") } } }
        if (cat == QuestRules.DAILY && shown.isNotEmpty() && shown.all { it.quest.autoKey.endsWith(":REC") })
            Panel("[ REST DAY ]") { Text("Recovery only today. Rest is part of getting stronger.") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item { Text("No quests here yet.") }
            items(shown, key = { it.quest.id }) { QuestCard(it, vm) }
        }
        Button({ dialog = true }, Modifier.fillMaxWidth()) { Text("NEW CUSTOM QUEST") }
    }
    if (dialog) NewQuestDialog(cat, { dialog = false }) { c, e, m, t, title -> vm.createQuest(c, e, m, t, title); dialog = false }
}

@Composable
internal fun QuestCard(v: QuestView, vm: MainViewModel) {
    val q = v.quest
    val ex = q.exercise?.let { Exercise.valueOf(it) }
    val unit = if (q.mode == QuestRules.DAYS) "days" else ex?.kind?.unit ?: ""
    val done = q.completedAt > 0
    Panel(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(q.title, fontWeight = FontWeight.Bold, color = if (done) Blue else Txt)
                Text(if (done) "COMPLETED · +${q.xpReward} XP" else "Reward ${q.xpReward} XP", fontSize = 12.sp, color = Blue)
            }
            if (q.custom && !done) TextButton({ vm.deleteQuest(q.id) }) { Text("✕") }
        }
        Spacer(Modifier.height(6.dp))
        Bar((v.progress / q.target).toFloat(), 6)
        Text("${QuestRules.fmt(minOf(v.progress, q.target))} / ${QuestRules.fmt(q.target)} $unit", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NewQuestDialog(initialCat: String, onDismiss: () -> Unit, onCreate: (String, Exercise?, String, Double?, String) -> Unit) {
    var cat by remember { mutableStateOf(initialCat) }
    var ex by remember { mutableStateOf(Exercise.PUSH_UPS) }
    var mode by remember { mutableStateOf(QuestRules.VOLUME) }
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New custom quest") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(QuestRules.DAILY to "Daily", QuestRules.WEEKLY to "Weekly", QuestRules.SPECIAL to "Special")) { (k, l) ->
                        FilterChip(selected = cat == k, onClick = { cat = k }, label = { Text(l) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(QuestRules.VOLUME to "Total", QuestRules.BEST to "Best single log", QuestRules.DAYS to "Days trained")) { (k, l) ->
                        FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(l) })
                    }
                }
                if (mode != QuestRules.DAYS) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Exercise.entries) { e -> FilterChip(selected = ex == e, onClick = { ex = e }, label = { Text(e.label) }) }
                }
                OutlinedTextField(target, { target = it }, label = { Text("Target") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Title (optional)") }, singleLine = true)
                Text("Custom quests give half XP; max 3 per day.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton({ onCreate(cat, if (mode == QuestRules.DAYS) null else ex, mode, target.toDoubleOrNull(), title) }) { Text("CREATE") } },
        dismissButton = { TextButton(onDismiss) { Text("CANCEL") } }
    )
}
