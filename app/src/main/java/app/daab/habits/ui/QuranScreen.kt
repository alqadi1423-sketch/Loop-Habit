@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daab.habits.R
import app.daab.habits.data.Db
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.logic.Schedule
import kotlin.math.roundToInt

private class Block(val start: Int, val end: Int, val surahName: String?, val basmala: Boolean)

/** Today's Quran portion of a habit. Progress follows how far the reader has scrolled. */
@Composable
fun QuranScreen(db: Db, habitId: Long, changed: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val habit = remember(habitId) { db.habit(habitId) }
    if (habit == null) {
        LaunchedEffect(habitId) { back() }
        return
    }
    val today = remember { Schedule.today() }
    val quran = remember { Quran.text(ctx) }
    val range = remember(habitId) { Quran.today(ctx, db, habit, today) }
    val font = remember { FontFamily(Font(R.font.uthmanic_hafs)) }
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fontSize by remember { mutableIntStateOf(prefs.getInt("quran_font", 26)) }
    var reached by remember(habitId) { mutableIntStateOf(range.reached) }
    var done by remember(habitId) { mutableStateOf(db.entries(habitId)[today] == Mark.DONE) }
    val color = Color(habit.color)

    val blocks = remember(habitId) {
        val out = ArrayList<Block>()
        var i = range.start
        while (i < range.end) {
            val s = quran.surahs[quran.surahAt(i)]
            val opens = i == s.start
            val limit = minOf(range.end, s.start + s.count, i + 8)
            out.add(Block(i, limit, if (opens) s.name else null, opens && s.basmala))
            i = limit
        }
        out
    }
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = blocks.indexOfFirst { reached < it.end }.coerceAtLeast(0)
    )
    val firstVisible by remember { derivedStateOf { list.firstVisibleItemIndex } }
    val size = (range.end - range.start).coerceAtLeast(1)

    LaunchedEffect(firstVisible) {
        val at = blocks.getOrNull(firstVisible)?.start ?: range.end
        if (!done && at > reached) {
            reached = at
            db.setQuranDay(habitId, today, range.start, range.end, reached)
            db.setPartial(habitId, today, ((reached - range.start) * 1000.0 / size).roundToInt().coerceAtMost(990))
        }
    }
    DisposableEffect(habitId) {
        onDispose { changed() }
    }

    fun setFont(value: Int) {
        fontSize = value.coerceIn(18, 44)
        prefs.edit().putInt("quran_font", fontSize).apply()
    }

    fun finish() {
        db.setQuranDay(habitId, today, range.start, range.end, range.end)
        db.setPartial(habitId, today, 1000)
        db.setEntry(habitId, today, Mark.DONE)
        val (position, planEnd) = db.quranState(habitId)
        if (position >= range.start && position < range.end) {
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
                title = { Text(habit.name) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    TextButton(onClick = { setFont(fontSize - 2) }) { Text("أ-") }
                    TextButton(onClick = { setFont(fontSize + 2) }) { Text("أ+") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(progress = { share }, color = color, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    "من ${quran.label(range.start)} إلى ${quran.label(range.end - 1)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = color)
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                itemsIndexed(blocks) { _, b ->
                    Column(Modifier.fillMaxWidth()) {
                        if (b.surahName != null) {
                            HorizontalDivider(Modifier.padding(top = 12.dp))
                            Text(
                                "سورة ${b.surahName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = color,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                            )
                            if (b.basmala) {
                                Text(
                                    quran.basmala(),
                                    fontFamily = font,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 2f).sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Text(
                            (b.start until b.end).joinToString(" ") { quran.ayat[it] },
                            fontFamily = font,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 2f).sp,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                        if (done) {
                            Text(
                                "أتممت ورد اليوم، وسُجلت العادة منجزة.",
                                color = color,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )
                            Button(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("رجوع") }
                        } else {
                            Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("أتممت الورد") }
                        }
                    }
                }
            }
        }
    }
}
