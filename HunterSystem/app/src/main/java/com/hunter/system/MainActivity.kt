package com.hunter.system

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.system.data.PlayerEntity
import com.hunter.system.domain.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

internal val Bg = Color(0xFF03060F)
internal val PanelC = Color(0xFF0A1226)
internal val Blue = Color(0xFF3DA5FF)
internal val Glow = Color(0xFF1E6BFF)
internal val Txt = Color(0xFFD6E6FF)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, onPrimary = Bg, background = Bg, surface = PanelC,
                onBackground = Txt, onSurface = Txt, secondaryContainer = Glow.copy(alpha = .35f)
            )) { HunterApp(vm) }
        }
    }
}

@Composable
fun HunterApp(vm: MainViewModel) {
    val player by vm.player.collectAsState()
    val completed by vm.completed.collectAsState()
    val recs by vm.recommendations.collectAsState()
    val achievements by vm.achievements.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("STATUS", "QUESTS", "TRAIN", "GATES", "SKILLS", "MORE")
    val holder = rememberSaveableStateHolder()
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = PanelC) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i; vm.clearMessage() }, icon = {},
                        label = { Text(t, fontSize = 10.sp) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF071230))))) {
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> StatusScreen(player, completed, recs, achievements)
                    1 -> QuestsScreen(vm)
                    2 -> TrainScreen(vm)
                    3 -> DungeonScreen(vm, player)
                    4 -> SkillsScreen(vm, player)
                    else -> MoreScreen(vm, player)
                }
            }
        }
    }
}

@Composable
fun Panel(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(PanelC)
        .border(1.dp, Brush.linearGradient(listOf(Blue, Glow.copy(alpha = .2f))), shape).padding(14.dp)) {
        if (title != null) {
            Text(title, color = Blue, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
private fun StatRow(k: String, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(k, Modifier.weight(1f)); Text(v, color = Blue, fontWeight = FontWeight.Bold)
}

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val logs by vm.logs.collectAsState()
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    if (logs.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No training recorded yet.") }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logs, key = { it.id }) { l ->
            val ex = Exercise.valueOf(l.exercise)
            Panel(null) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(ex.label, fontWeight = FontWeight.Bold)
                        Text("${l.value.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it) }} ${ex.kind.unit} · diff ${l.difficulty}" +
                            if (l.pain) " · pain" else "", fontSize = 12.sp)
                        Text(fmt.format(Date(l.timestamp)), fontSize = 11.sp, color = Blue)
                    }
                    Text("+${l.xp} XP", color = Blue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SystemScreen(vm: MainViewModel, p: PlayerEntity?) {
    var name by rememberSaveable(p?.name) { mutableStateOf(p?.name ?: "") }
    var pending by remember { mutableStateOf<Uri?>(null) }
    val msg by vm.message.collectAsState()
    val voiceOn by vm.voiceEnabled.collectAsState()
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pending = it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("[ HUNTER NAME ]") {
            OutlinedTextField(name, { name = it.take(24) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ vm.rename(name) }, Modifier.padding(top = 8.dp)) { Text("SAVE NAME") }
        }
        Panel("[ SYSTEM VOICE ]") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice announcements", Modifier.weight(1f)); Switch(voiceOn, { vm.setVoice(it) })
            }
        }
        Panel("[ BACKUP & RESTORE ]") {
            Button({ exportL.launch("hunter-backup.json") }, Modifier.fillMaxWidth()) { Text("EXPORT BACKUP") }
            OutlinedButton({ importL.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("RESTORE FROM BACKUP") }
        }
        msg?.let { Panel("[ SYSTEM MESSAGE ]") { Text(it); TextButton({ vm.clearMessage() }) { Text("DISMISS") } } }
    }
    pending?.let { uri ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces ALL current progress with the backup's contents.") },
            confirmButton = { TextButton({ vm.restore(uri); pending = null }) { Text("RESTORE") } },
            dismissButton = { TextButton({ pending = null }) { Text("CANCEL") } })
    }
}
