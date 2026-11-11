@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Quran
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.Schedule
import app.daab.habits.logic.Settings
import app.daab.habits.notify.ReminderReceiver
import app.daab.habits.widget.HabitWidget
import kotlin.math.roundToInt

sealed interface Screen {
    data object Home : Screen
    data object Counters : Screen
    data object Adhkar : Screen
    data object QuranTab : Screen
    data class AdhkarRead(val category: Int, val part: Int) : Screen
    data class QuranRead(val start: Int, val end: Int) : Screen
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
        intent?.removeExtra(EXTRA_READER)
        setContent {
            DaabTheme { App(resumes.intValue, readerRequest.longValue) { readerRequest.longValue = 0L } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readerRequest.longValue = intent.getLongExtra(EXTRA_READER, 0L)
        intent.removeExtra(EXTRA_READER)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        HabitWidget.updateAll(this)
    }
}

class HabitState(
    val habit: Habit, val dates: Set<Long>, val entries: Map<Long, Int>, val partials: Map<Long, Int>,
    val count: Int, val score: Int
)

fun loadStates(db: Db): List<HabitState> {
    val today = Schedule.today()
    return db.habits().map {
        val dates = db.dates(it.id)
        val entries = db.entries(it.id)
        val partials = db.partials(it.id)
        HabitState(
            it, dates, entries, partials,
            if (it.target > 0) db.progress(it.id, today)[0] ?: 0 else 0,
            (Schedule.score(it, dates, entries, today, partials) * 100).roundToInt()
        )
    }
}

/** Weekday names with Sunday first, matching how weekdays are stored. */
val weekdayNames = listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

/** Weekday names in the order the user's week runs. */
fun weekOrder(): List<String> = List(7) { weekdayNames[(Settings.weekStart + it) % 7] }

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
    var tab by remember { mutableStateOf<Screen>(Screen.Home) }
    var version by remember { mutableIntStateOf(0) }
    val changed = {
        version++
        HabitWidget.updateAll(ctx)
    }
    val go = { next: Screen ->
        if (next == Screen.Home || next == Screen.Settings ||
            next == Screen.Adhkar || next == Screen.QuranTab
        ) tab = next
        screen = next
    }

    BackHandler(screen != Screen.Home) { go(if (screen == tab) Screen.Home else tab) }

    LaunchedEffect(readerRequest) {
        if (readerRequest > 0L) {
            screen = Screen.Reader(readerRequest)
            readerHandled()
        }
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(db, version + resumes, changed, go)
        is Screen.Counters -> CountersScreen(db, version, changed, { go(tab) })
        is Screen.Adhkar -> AdhkarScreen(db, version, go)
        is Screen.QuranTab -> QuranTab(db, version, go)
        is Screen.AdhkarRead -> ReaderScreen(db, 0, changed, { go(tab) }, s)
        is Screen.QuranRead -> QuranReadScreen(s.start, s.end, { changed(); go(tab) }, go)
        is Screen.Settings -> SettingsScreen(db, changed, go)
        is Screen.Archived -> ArchivedScreen(db, version, changed, { go(Screen.Settings) })
        is Screen.Edit -> EditScreen(db, s.id, s.quran, { changed(); go(tab) }, { go(tab) })
        is Screen.Detail -> DetailScreen(db, s.id, version, changed, { go(if (it == Screen.Home) tab else it) })
        is Screen.Reader ->
            if (db.habit(s.id)?.contentRef == Quran.CONTENT) QuranScreen(db, s.id, changed, { go(tab) })
            else ReaderScreen(db, s.id, changed, { go(tab) })
    }
}

/** The bar at the bottom of the three main pages. */
@Composable
fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = current == Screen.Home, onClick = { go(Screen.Home) },
            icon = { Icon(Icons.Default.Home, null) }, label = { Text("العادات") }
        )
        NavigationBarItem(
            selected = current == Screen.Adhkar, onClick = { go(Screen.Adhkar) },
            icon = { Icon(BeadsIcon, null) }, label = { Text("الأذكار") }
        )
        NavigationBarItem(
            selected = current == Screen.QuranTab, onClick = { go(Screen.QuranTab) },
            icon = { Icon(BookIcon, null) }, label = { Text("القرآن") }
        )
        NavigationBarItem(
            selected = current == Screen.Settings, onClick = { go(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") }
        )
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
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
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
