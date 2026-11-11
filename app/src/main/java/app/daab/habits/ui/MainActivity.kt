@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
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
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Group
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.Hijri
import app.daab.habits.logic.Phase
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.ReminderReceiver
import app.daab.habits.widget.HabitWidget
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Archived : Screen
    data class Edit(val id: Long, val quran: Boolean = false) : Screen
    data class Detail(val id: Long) : Screen
    data class Reader(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_READER = "reader_habit"
    }

    private var resumes = mutableIntStateOf(0)
    private var readerRequest = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.scheduleAll(this)
        readerRequest.longValue = intent?.getLongExtra(EXTRA_READER, 0L) ?: 0L
        setContent {
            DaabTheme { App(resumes.intValue, readerRequest.longValue) { readerRequest.longValue = 0L } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readerRequest.longValue = intent.getLongExtra(EXTRA_READER, 0L)
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        HabitWidget.updateAll(this)
    }
}

class HabitState(
    val habit: Habit, val dates: Set<Long>, val entries: Map<Long, Int>, val partials: Map<Long, Int>
)

fun loadStates(db: Db): List<HabitState> =
    db.habits().map { HabitState(it, db.dates(it.id), db.entries(it.id), db.partials(it.id)) }

val weekdayNames = listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

fun describe(s: HabitState, today: Long): String = when (s.habit.kind) {
    Kind.DAILY -> "كل يوم"
    Kind.WEEKDAYS -> weekdayNames.filterIndexed { i, _ -> (s.habit.weekdays shr i) and 1 == 1 }.joinToString("، ")
    Kind.PER_WEEK -> "هذا الأسبوع ${Schedule.doneInWeek(s.entries, today)} من ${s.habit.perWeek}"
    else -> "${s.dates.count { it >= today }} أيام محددة قادمة"
}

fun anchorLabel(kind: Int, value: Int, times: IntArray?, empty: String): String = when (kind) {
    Anchor.CLOCK -> timeText(value)
    Anchor.PRAYER -> {
        val i = value.coerceIn(0, 5)
        if (times == null) prayerNames[i] else prayerNames[i] + " " + timeText(times[i])
    }
    else -> empty
}

fun windowText(h: Habit, times: IntArray): String =
    "من " + anchorLabel(h.startKind, h.startValue, times, "أول اليوم") +
        " إلى " + anchorLabel(h.endKind, h.endValue, times, "آخر اليوم")

@Composable
fun App(resumes: Int, readerRequest: Long, readerHandled: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) }
    val changed = {
        version++
        HabitWidget.updateAll(ctx)
    }

    BackHandler(screen != Screen.Home) { screen = Screen.Home }

    LaunchedEffect(readerRequest) {
        if (readerRequest > 0L) {
            screen = Screen.Reader(readerRequest)
            readerHandled()
        }
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(db, version + resumes, changed, { screen = it })
        is Screen.Settings -> SettingsScreen({ changed(); screen = Screen.Home })
        is Screen.Archived -> ArchivedScreen(db, version, changed, { screen = Screen.Home })
        is Screen.Edit -> EditScreen(db, s.id, s.quran, { changed(); screen = Screen.Home }, { screen = Screen.Home })
        is Screen.Detail -> DetailScreen(db, s.id, version, changed, { screen = it })
        is Screen.Reader ->
            if (db.habit(s.id)?.contentRef == Quran.CONTENT) QuranScreen(db, s.id, changed, { screen = Screen.Home })
            else ReaderScreen(db, s.id, changed, { screen = Screen.Home })
    }
}

@Composable
fun HomeScreen(db: Db, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val today = Schedule.today()
    val now = Schedule.nowMinutes()
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minute++
        }
    }
    val states = remember(version, minute) { loadStates(db) }
    val groups = remember(version) { db.groups() }
    val times = remember(version, today) { PrayerTimes.forDay(ctx, today) }
    var menu by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<Group?>(null) }

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
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.exportCsv().toByteArray()) }
                Toast.makeText(ctx, "حُفظ الجدول", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "تعذر حفظ الجدول", Toast.LENGTH_LONG).show()
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
                title = {
                    Column {
                        Text("دَأب")
                        Text(
                            Hijri.text(today),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "القائمة") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("إضافة ورد القرآن") }, onClick = {
                            menu = false
                            go(Screen.Edit(0, quran = true))
                        })
                        DropdownMenuItem(text = { Text("إضافة مجموعة الصلوات الخمس") }, onClick = {
                            menu = false
                            db.addPrayerGroup(today)
                            changed()
                        })
                        DropdownMenuItem(text = { Text("إضافة مجموعة أذكار الصباح والمساء") }, onClick = {
                            menu = false
                            db.addAdhkarGroup(today)
                            changed()
                        })
                        DropdownMenuItem(text = { Text("مجموعة جديدة فارغة") }, onClick = {
                            menu = false
                            editGroup = Group(position = groups.size)
                        })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("المدينة ومواقيت الصلاة") }, onClick = {
                            menu = false
                            go(Screen.Settings)
                        })
                        DropdownMenuItem(text = { Text("العادات المؤرشفة") }, onClick = {
                            menu = false
                            go(Screen.Archived)
                        })
                        DropdownMenuItem(text = { Text("تصدير السجل جدولًا") }, onClick = {
                            menu = false
                            csvExporter.launch("daab-log.csv")
                        })
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
            if (states.isEmpty() && groups.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ابدأ بأول عادة", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اضغط علامة الإضافة، أو أضف مجموعة الصلوات من القائمة، ثم ضع المربع على شاشتك الرئيسية.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            fun isToday(s: HabitState) = Schedule.isDue(s.habit, s.dates, s.entries, today) || s.entries[today] != null
            val toggle = { s: HabitState ->
                if (s.habit.contentRef != 0) {
                    go(Screen.Reader(s.habit.id))
                } else {
                    db.tap(s.habit, today)
                    changed()
                }
            }
            val groupIds = groups.map { it.id }.toSet()
            val loose = states.filter { it.habit.groupId !in groupIds }
            val looseToday = loose.filter { isToday(it) }
            if (looseToday.isNotEmpty()) SectionTitle("اليوم")
            for (s in looseToday) HabitRow(s, today, times, now, true, { toggle(s) }, { go(Screen.Detail(s.habit.id)) })

            for (g in groups) {
                val members = states.filter { it.habit.groupId == g.id }
                val done = members.count { it.entries[today] == Mark.DONE }
                val dueCount = members.count { isToday(it) }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$done من $dueCount", style = MaterialTheme.typography.labelLarge, color = Color(g.color))
                    TextButton(onClick = { editGroup = g }) { Text("تعديل") }
                }
                if (members.isEmpty()) {
                    Text(
                        "أضف عادة ثم اختر هذه المجموعة لها.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                for (s in members.filter { isToday(it) }.sortedBy { Schedule.window(it.habit, times).first }) {
                    HabitRow(s, today, times, now, true, { toggle(s) }, { go(Screen.Detail(s.habit.id)) })
                }
            }

            val rest = states.filter { !isToday(it) }
            if (rest.isNotEmpty()) SectionTitle("ليست اليوم")
            for (s in rest) HabitRow(s, today, times, now, false, {}, { go(Screen.Detail(s.habit.id)) })
            Spacer(Modifier.height(96.dp))
        }
    }

    editGroup?.let { g ->
        var name by remember(g) { mutableStateOf(g.name) }
        var error by remember(g) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editGroup = null },
            title = { Text(if (g.id == 0L) "مجموعة جديدة" else "تعديل المجموعة") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text("اسم المجموعة") },
                    isError = error,
                    supportingText = { if (error) Text("اكتب اسم المجموعة") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) error = true
                    else {
                        db.saveGroup(g.copy(name = name.trim()))
                        editGroup = null
                        changed()
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                if (g.id != 0L) {
                    TextButton(onClick = {
                        db.deleteGroup(g.id)
                        editGroup = null
                        changed()
                    }) { Text("حذف المجموعة وإبقاء عاداتها") }
                } else {
                    TextButton(onClick = { editGroup = null }) { Text("إلغاء") }
                }
            }
        )
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
fun HabitRow(s: HabitState, today: Long, times: IntArray, now: Int, active: Boolean, toggle: () -> Unit, open: () -> Unit) {
    val color = Color(s.habit.color)
    val value = s.entries[today] ?: Mark.NONE
    val score = (Schedule.score(s.habit, s.dates, s.entries, today, s.partials) * 100).roundToInt()
    val readPercent = if (s.habit.contentRef != 0 && value == Mark.NONE) ((s.partials[today] ?: 0) / 10.0).roundToInt() else 0
    val counted = if (s.habit.target > 0 && value == Mark.NONE) (s.partials[today] ?: 0) * s.habit.target / 1000 else -1
    val windowed = Schedule.hasWindow(s.habit)
    val phase = Schedule.phase(s.habit, times, now)
    val missed = active && windowed && phase == Phase.CLOSED && value == Mark.NONE
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .then(
                    if (value == Mark.DONE) Modifier.background(color)
                    else Modifier.border(
                        2.dp,
                        if (missed) MaterialTheme.colorScheme.error
                        else if (active) color else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                )
                .clickable(enabled = active, onClick = toggle),
            contentAlignment = Alignment.Center
        ) {
            if (value == Mark.DONE) Icon(Icons.Default.Check, "منجزة", tint = Color.White)
            if (value == Mark.EXCUSED) Text("ع", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (readPercent > 0) Text("$readPercent", style = MaterialTheme.typography.labelMedium, color = color)
            if (counted >= 0) Text("$counted/${s.habit.target}", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Column(Modifier.weight(1f)) {
            Text(s.habit.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (windowed) windowText(s.habit, times)
                else if (s.habit.contentRef == Quran.CONTENT) Quran.describe(s.habit.contentUnit, s.habit.contentAmount)
                else describe(s, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active && windowed && value == Mark.NONE) {
                Text(
                    when (phase) {
                        Phase.BEFORE -> "لم يحن وقتها"
                        Phase.OPEN -> "وقتها الآن"
                        else -> "فائتة، اضغط الدائرة لتعديلها"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missed) MaterialTheme.colorScheme.error else color
                )
            }
        }
        Text("$score%", style = MaterialTheme.typography.labelLarge, color = color)
        Spacer(Modifier.width(2.dp))
    }
}

@Composable
fun ArchivedScreen(db: Db, version: Int, changed: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val archived = remember(version) { db.habits(true).filter { it.archived } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العادات المؤرشفة") },
                navigationIcon = { TextButton(onClick = back) { Text("رجوع") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (archived.isEmpty()) {
                Text(
                    "لا عادات مؤرشفة. تؤرشف العادة من صفحة تعديلها، وسجلها يبقى محفوظًا.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
            for (h in archived) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(h.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        db.save(h.copy(archived = false))
                        ReminderReceiver.schedule(ctx, h.copy(archived = false))
                        changed()
                    }) { Text("استرجاع") }
                }
                HorizontalDivider()
            }
        }
    }
}
