@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DetailScreen(db: Db, id: Long, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val habit = remember(id, version) { db.habit(id) }
    if (habit == null) {
        go(Screen.Home)
        return
    }
    val dates = remember(id, version) { db.dates(id) }
    val entries = remember(id, version) { db.entries(id) }
    val today = Schedule.today()
    val color = Color(habit.color)
    val score = Schedule.score(habit, dates, entries, today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit.name) },
                navigationIcon = {
                    IconButton(onClick = { go(Screen.Home) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = { IconButton(onClick = { go(Screen.Edit(id)) }) { Icon(Icons.Default.Edit, "تعديل") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("قوة العادة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(score * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, color = color)
            LinearProgressIndicator(
                progress = { score.toFloat() },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (habit.kind == Kind.PER_WEEK) "هذا الأسبوع ${Schedule.doneInWeek(entries, today)} من ${habit.perWeek}"
                else "السلسلة الحالية ${Schedule.streak(habit, dates, entries, today)} يومًا",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "السجل، آخر ستة أسابيع",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                for (n in weekdayNames) {
                    Text(
                        n, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val first = Schedule.weekStart(today) - 35
            for (week in 0..5) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val day = first + week * 7 + i
                        val value = entries[day] ?: Mark.NONE
                        val planned = Schedule.isScheduled(habit, dates, day) && day >= habit.created
                        val editable = day <= today
                        val shape = RoundedCornerShape(10.dp)
                        val base = Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(shape)
                        val look = when {
                            value == Mark.DONE -> base.background(color)
                            value == Mark.EXCUSED -> base.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
                            planned && editable -> base.background(MaterialTheme.colorScheme.surfaceVariant)
                            planned -> base.border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape)
                            else -> base
                        }
                        Box(
                            look.clickable(enabled = editable) {
                                val next = when (value) {
                                    Mark.NONE -> Mark.DONE
                                    Mark.DONE -> Mark.EXCUSED
                                    else -> Mark.NONE
                                }
                                db.setEntry(id, day, next)
                                changed()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (value == Mark.EXCUSED) "ع" else LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    value == Mark.DONE -> Color.White
                                    day == today -> color
                                    editable -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            Text(
                "اضغط اليوم لتغييره: منجز، ثم معذور (ع)، ثم فارغ. اليوم المعذور لا يضر قوة العادة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
