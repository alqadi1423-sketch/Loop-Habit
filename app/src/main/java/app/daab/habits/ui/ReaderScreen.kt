@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daab.habits.data.Adhkar
import app.daab.habits.data.Db
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import kotlin.math.min
import kotlin.math.roundToInt

/** Reading the adhkar of a habit one at a time, with a counter that marks the habit when all are done. */
@Composable
fun ReaderScreen(db: Db, habitId: Long, changed: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val habit = remember(habitId) { db.habit(habitId) }
    val items = remember(habitId) {
        when {
            habit == null -> emptyList()
            habit.contentRef == Adhkar.CUSTOM -> db.customItems(habitId)
            else -> Adhkar.items(ctx, habit.contentRef, habit.contentPart)
        }
    }
    val today = remember { Schedule.today() }
    val counts = remember(habitId) {
        mutableStateMapOf<Int, Int>().apply { putAll(db.progress(habitId, today)) }
    }
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fontSize by remember { mutableIntStateOf(prefs.getInt("reader_font", 22)) }
    var index by remember(habitId) {
        mutableIntStateOf(items.indexOfFirst { (counts[it.id] ?: 0) < it.count }.let { if (it < 0) 0 else it })
    }

    if (habit == null || items.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(habit?.name ?: "") },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
                )
            }
        ) { pad ->
            Text("لا يوجد محتوى لهذه العادة. اختر محتواها من تعديل العادة.", Modifier.padding(pad).padding(20.dp))
        }
        return
    }

    val color = Color(habit.color)
    fun share(): Double = items.sumOf { min(1.0, (counts[it.id] ?: 0).toDouble() / it.count) } / items.size
    val finished = items.all { (counts[it.id] ?: 0) >= it.count }

    fun setFont(size: Int) {
        fontSize = size.coerceIn(16, 40)
        prefs.edit().putInt("reader_font", fontSize).apply()
    }

    fun count() {
        val item = items[index]
        val now = (counts[item.id] ?: 0) + 1
        if (now > item.count) return
        counts[item.id] = now
        db.setProgress(habitId, today, item.id, now)
        db.setPartial(habitId, today, (share() * 1000).roundToInt())
        if (items.all { (counts[it.id] ?: 0) >= it.count }) {
            db.setEntry(habitId, today, Mark.DONE)
        } else if (now >= item.count) {
            val next = items.indexOfFirst { (counts[it.id] ?: 0) < it.count }
            if (next >= 0) index = next
        }
        changed()
    }

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
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
            LinearProgressIndicator(progress = { share().toFloat() }, color = color, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("الذكر ${index + 1} من ${items.size}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(share() * 100).roundToInt()}%", color = color)
            }

            if (finished) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("أتممت ${habit.name}", style = MaterialTheme.typography.headlineSmall, color = color)
                    Text("سُجلت العادة منجزة اليوم.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("رجوع") }
            } else {
                val item = items[index]
                val done = counts[item.id] ?: 0
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(
                        item.text,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.9f).sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (done >= item.count) MaterialTheme.colorScheme.surfaceVariant else color)
                        .clickable(enabled = done < item.count) { count() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$done من ${item.count}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (done >= item.count) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        )
                        if (done < item.count) Text("اضغط للعدّ", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("السابق") }
                    TextButton(onClick = { if (index < items.size - 1) index++ }, enabled = index < items.size - 1) { Text("التالي") }
                }
            }
        }
    }
}
