package com.hunter.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.system.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private fun dateStr(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM"))
private fun amount(e: com.hunter.system.domain.Exercise, v: Double) = "${QuestRules.fmt(v)} ${e.kind.unit}"
private fun pace(minPerKm: Double): String { val s = (minPerKm * 60).roundToInt(); return "%d:%02d /km".format(s / 60, s % 60) }
private fun delta(n: Long) = if (n >= 0) "+$n" else "$n"

@Composable
fun LineChart(values: List<Float>, modifier: Modifier = Modifier.fillMaxWidth().height(140.dp)) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull() ?: 0f
        val min = values.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * 0.95f - (v - min) / range * size.height * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Blue, style = Stroke(width = 4f))
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val slot = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, v ->
                val h = v / max * size.height
                drawRect(Blue, topLeft = Offset(i * slot + slot * 0.15f, size.height - h), size = Size(slot * 0.7f, h))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, modifier = Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun ReportsScreen(vm: MainViewModel) {
    val ins by vm.insights.collectAsState()
    var pi by rememberSaveable { mutableStateOf(1) }
    val d = ins
    if (d == null || d.exercises.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Log a workout to generate reports.") }
        return
    }
    val r = d.periods[pi]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Daily", "Weekly", "Monthly").forEachIndexed { i, l -> FilterChip(selected = pi == i, onClick = { pi = i }, label = { Text(l) }) }
        }
        Panel("[ ${r.label.uppercase()} ]") {
            StatLine("Sessions logged", "${r.sessions}")
            StatLine("Days trained", "${r.daysTrained} (${delta((r.daysTrained - r.prevDays).toLong())} vs ${r.prevLabel})")
            StatLine("XP earned", "${r.xp} (${delta(r.xp - r.prevXp)} vs ${r.prevLabel})")
            StatLine("Training time", "${r.minutes} min")
            if (r.byExercise.isNotEmpty()) Spacer(Modifier.height(6.dp))
            r.byExercise.forEach { (e, v) -> StatLine(e.label, amount(e, v)) }
            if (r.sessions == 0) Text("Nothing logged in this period yet.", fontSize = 13.sp)
        }
        Panel("[ XP GROWTH · 30 DAYS ]") {
            Text("+${d.xpGain} XP earned", color = Blue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LineChart(d.xpSeries.map { it.toFloat() })
            Text("Cumulative workout and quest XP. Dungeon clear bonuses are not included.", fontSize = 11.sp)
        }
        val c = d.consistency
        Panel("[ CONSISTENCY ]") {
            StatLine("Recent days trained", "${c.pct28}%")
            StatLine("Average per week", "%.1f days".format(c.avgPerWeek))
            Spacer(Modifier.height(6.dp))
            BarChart(c.weeklyDays.map { it.toFloat() }, c.weekLabels)
            Text("Days trained per week (week starting dates)", fontSize = 11.sp)
        }
        Panel("[ MISSED WORKOUTS ]") {
            val m = d.missed
            if (m == null) Text("Needs at least 7 days of history.", fontSize = 13.sp) else {
                StatLine("Days without training", "${m.missed} of ${m.window}")
                StatLine("Longest gap", "${m.longestGap} days")
                m.worstWeekday?.let { StatLine("Most missed day", it) }
                Text(m.verdict, fontSize = 13.sp, color = Blue, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val run = d.running
        Panel("[ RUNNING ]") {
            if (run.totalKm == 0.0) Text("No runs logged yet.", fontSize = 13.sp) else {
                StatLine("Total distance", "${QuestRules.fmt(Math.round(run.totalKm * 10) / 10.0)} km")
                StatLine("Longest run", "${QuestRules.fmt(run.longest)} km")
                run.bestPaceMinPerKm?.let { StatLine("Best pace", pace(it)) }
                Spacer(Modifier.height(6.dp))
                BarChart(run.weeklyKm.map { it.toFloat() }, run.weekLabels)
                Text("Kilometres per week", fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                run.recent.forEach {
                    Text("${dateStr(it.day)} · ${QuestRules.fmt(it.km)} km" +
                        (if (it.durationSec > 0) " · ${it.durationSec / 60} min · ${pace(it.durationSec / 60.0 / it.km)}" else ""),
                        fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        Panel("[ EXERCISE STATISTICS ]") {
            d.exercises.forEach {
                Text(it.ex.label, fontWeight = FontWeight.Bold)
                Text("${it.sessions} sessions · total ${amount(it.ex, it.total)} · avg ${amount(it.ex, Math.round(it.avg * 10) / 10.0)} · last ${dateStr(it.lastDay)}",
                    fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Panel("[ PERSONAL RECORDS ]") {
            d.exercises.forEach { StatLine(it.ex.label, "${amount(it.ex, it.best)}  ·  ${dateStr(it.bestDay)}") }
        }
    }
}
