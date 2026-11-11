@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.ReminderReceiver
import app.daab.habits.widget.HabitWidget
import kotlin.math.roundToInt

sealed interface Screen {
    data object Home : Screen
    data class Edit(val id: Long) : Screen
    data class Detail(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {

    private var resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.scheduleAll(this)
        setContent {
            DaabTheme { App(resumes.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }
}

class HabitState(val habit: Habit, val dates: Set<Long>, val entries: Map<Long, Int>)

fun loadStates(db: Db): List<HabitState> = db.habits().map { HabitState(it, db.dates(it.id), db.entries(it.id)) }

val weekdayNames = listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

fun describe(s: HabitState, today: Long): String = when (s.habit.kind) {
    Kind.DAILY -> "كل يوم"
    Kind.WEEKDAYS -> weekdayNames.filterIndexed { i, _ -> (s.habit.weekdays shr i) and 1 == 1 }.joinToString("، ")
    Kind.PER_WEEK -> "هذا الأسبوع ${Schedule.doneInWeek(s.entries, today)} من ${s.habit.perWeek}"
    else -> "${s.dates.count { it >= today }} أيام محددة قادمة"
}

@Composable
fun App(resumes: Int) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) }
    val changed = {
        version++
        HabitWidget.updateAll(ctx)
    }

    BackHandler(screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(db, version + resumes, changed, { screen = it })
        is Screen.Edit -> EditScreen(db, s.id, { changed(); screen = Screen.Home }, { screen = Screen.Home })
        is Screen.Detail -> DetailScreen(db, s.id, version, changed, { screen = it })
    }
}

@Composable
fun HomeScreen(db: Db, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val today = Schedule.today()
    val states = remember(version) { loadStates(db) }
    var menu by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.exportJson().toByteArray()) }
                Toast.makeText(ctx, "حُفظت النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "تعذر حفظ النسخة", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                db.importJson(text ?: "")
                ReminderReceiver.scheduleAll(ctx)
                changed()
                Toast.makeText(ctx, "استُعيدت النسخة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "الملف ليس نسخة احتياطية صالحة", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دَأب") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "القائمة") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("حفظ نسخة احتياطية") }, onClick = {
                            menu = false
                            exporter.launch("daab-backup.json")
                        })
                        DropdownMenuItem(text = { Text("استعادة نسخة احتياطية") }, onClick = {
                            menu = false
                            importer.launch(arrayOf("application/json", "*/*"))
                        })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { go(Screen.Edit(0)) }) { Icon(Icons.Default.Add, "عادة جديدة") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (states.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ابدأ بأول عادة", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اضغط علامة الإضافة، ثم ضع المربع على شاشتك الرئيسية.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val due = states.filter { Schedule.isDue(it.habit, it.dates, it.entries, today) || it.entries[today] != null }
            val rest = states - due.toSet()
            if (due.isNotEmpty()) SectionTitle("اليوم")
            for (s in due) HabitRow(s, today, true, {
                val v = s.entries[today] ?: Mark.NONE
                db.setEntry(s.habit.id, today, if (v == Mark.DONE) Mark.NONE else Mark.DONE)
                changed()
            }, { go(Screen.Detail(s.habit.id)) })
            if (rest.isNotEmpty()) SectionTitle("ليست اليوم")
            for (s in rest) HabitRow(s, today, false, {}, { go(Screen.Detail(s.habit.id)) })
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun HabitRow(s: HabitState, today: Long, active: Boolean, toggle: () -> Unit, open: () -> Unit) {
    val color = Color(s.habit.color)
    val value = s.entries[today] ?: Mark.NONE
    val score = (Schedule.score(s.habit, s.dates, s.entries, today) * 100).roundToInt()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .then(
                    if (value == Mark.DONE) Modifier.background(color)
                    else Modifier.border(2.dp, if (active) color else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
                .clickable(enabled = active, onClick = toggle),
            contentAlignment = Alignment.Center
        ) {
            if (value == Mark.DONE) Icon(Icons.Default.Check, "منجزة", tint = Color.White)
            if (value == Mark.EXCUSED) Text("ع", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(s.habit.name, style = MaterialTheme.typography.titleMedium)
            Text(describe(s, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$score%", style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(2.dp))
    }
}
