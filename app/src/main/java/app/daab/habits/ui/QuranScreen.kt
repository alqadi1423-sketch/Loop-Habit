@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.logic.Schedule
import kotlin.math.roundToInt
import app.daab.habits.audio.QuranAudio
import androidx.compose.runtime.collectAsState

/** Today's Quran portion of a habit, on the mushaf pages that hold it. Progress follows the page reached. */
@Composable
fun QuranScreen(db: Db, habitId: Long, changed: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val habit = remember(habitId) { db.habit(habitId) }
    if (habit == null) {
        LaunchedEffect(habitId) { back() }
        return
    }
    val loaded = rememberMushaf()
    if (loaded == null) {
        Loading(Modifier.fillMaxSize())
        return
    }
    val (data, quran) = loaded
    val today = remember { Schedule.today() }
    val range = remember(habitId) { Quran.today(ctx, db, habit, today) }
    var reached by remember(habitId) { mutableIntStateOf(range.reached) }
    var done by remember(habitId) { mutableStateOf(db.entries(habitId)[today] == Mark.DONE) }
    val color = Color(habit.color)
    val firstPage = remember(habitId) { data.pageOf(range.start) }
    val lastPage = remember(habitId) { data.pageOf(range.end - 1) }
    val count = lastPage - firstPage + 1
    val pager = rememberPagerState(initialPage = (data.pageOf(reached.coerceAtMost(range.end - 1)) - firstPage).coerceIn(0, count - 1)) { count }
    val size = (range.end - range.start).coerceAtLeast(1)
    val current by QuranAudio.current.collectAsState()
    val word by QuranAudio.word.collectAsState()
    LaunchedEffect(current) {
        if (current in range.start until range.end) {
            val p = data.pageOf(current) - firstPage
            if (p != pager.currentPage && p in 0 until count) pager.animateScrollToPage(p)
        }
    }

    LaunchedEffect(pager.currentPage) {
        // Reaching a page counts what came before it as read.
        val at = data.firstAyah(firstPage + pager.currentPage).coerceIn(range.start, range.end)
        if (!done && at > reached) {
            reached = at
            db.setQuranDay(habitId, today, range.start, range.end, reached)
            db.setPartial(habitId, today, ((reached - range.start) * 1000.0 / size).roundToInt().coerceAtMost(990))
        }
    }
    DisposableEffect(habitId) {
        onDispose { changed() }
    }

    fun finish() {
        db.setQuranDay(habitId, today, range.start, range.end, range.end)
        db.setPartial(habitId, today, 1000)
        db.setEntry(habitId, today, Mark.DONE)
        val (position, planEnd) = db.quranState(habitId)
        if (habit.contentUnit != Quran.UNIT_SURAH && position >= range.start && position < range.end) {
            if (range.end >= Quran.TOTAL) db.setQuranState(habitId, 0, 0, addKhatma = true)
            else db.setQuranState(habitId, range.end, planEnd)
        }
        done = true
        reached = range.end
        changed()
    }

    val share = if (done) 1f else (reached - range.start).toFloat() / size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(habit.name)
                        Text(
                            "من ${quran.label(range.start)} إلى ${quran.label(range.end - 1)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = { AudioMenu(quran, range.start) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mushafPaper())
            )
        },
        bottomBar = {
          Column {
            AudioBar(quran, { data.firstAyah(firstPage + pager.currentPage).coerceIn(range.start, range.end - 1) }, range.end)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { share }, color = color, modifier = Modifier.weight(1f))
                        Text(
                            "  ${(share * 100).roundToInt()}%  ·  صفحة ${pager.currentPage + 1} من $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (done) {
                        OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("أتممت ورد اليوم، رجوع")
                        }
                    } else {
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("أتممت الورد") }
                    }
                }
            }
          }
        },
        containerColor = mushafPaper()
    ) { pad ->
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.padding(pad).fillMaxSize()) { i ->
            MushafPage(data, quran, firstPage + i, Modifier.fillMaxSize(), keep = range.start until range.end, highlight = current, word = word)
        }
    }
}
