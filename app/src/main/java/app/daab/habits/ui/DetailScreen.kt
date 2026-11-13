@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import java.time.LocalDate
import kotlin.math.roundToInt

private val monthNames = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

@Composable
private fun Heading(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("\u200E" + value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun signed(points: Int): String = (if (points > 0) "+" else "") + points + "%"

/** Everything about one habit: its strength over time, its record by year, a calendar to edit, and its runs. */
@Composable
fun DetailScreen(db: Db, id: Long, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val habit = remember(id, version) { db.habit(id) }
    if (habit == null) {
        LaunchedEffect(id) { go(Screen.Home) }
        return
    }
    val dates = remember(id, version) { db.dates(id) }
    val entries = remember(id, version) { db.entries(id) }
    val partials = remember(id, version) { db.partials(id) }
    val today = Schedule.today()
    val tint = habitTint(habit.color)
    val solid = Color(habit.color)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val series = remember(id, version, today) { Schedule.scoreSeries(habit, dates, entries, today, partials) }
    val score = series.last()
    fun scoreAgo(days: Int) = series.getOrElse(series.size - 1 - days) { 0.0 }
    val total = remember(id, version) { entries.values.count { it == Mark.DONE } }
    val streaks = remember(id, version, today) { Schedule.streaks(habit, dates, entries, today) }
    val counter = remember(id, version) { if (habit.counterId == 0L) null else db.counters().firstOrNull { it.id == habit.counterId } }
    val counterTotal = remember(id, version) {
        if (counter == null) 0
        else db.habits(true).filter { it.counterId == counter.id }.sumOf { db.doneCount(it.id) * it.counterEffect }
    }
    var byMonth by remember { mutableStateOf(false) }
    var monthOffset by remember { mutableIntStateOf(0) }

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
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when {
                    Schedule.hasWindow(habit) -> windowText(habit, PrayerTimes.forDay(ctx, today))
                    habit.contentRef == Quran.CONTENT -> Quran.describe(habit.contentUnit, habit.contentAmount)
                    else -> describe(HabitState(habit, dates, entries, partials, 0, 0), today)
                } + if (habit.reminder >= 0) "  ·  تذكير " + timeText(habit.reminder) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (habit.contentRef != 0) {
                Button(onClick = { go(Screen.Reader(id)) }, modifier = Modifier.fillMaxWidth()) { Text("افتح القراءة") }
            }

            if (counter != null) {
                Panel {
                    Heading("العدّاد: " + counter.name, tint)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Stat("\u200E$counterTotal", "مجموع العدّاد", tint, Modifier.weight(1f))
                        Stat("\u200E" + (if (habit.counterEffect > 0) "+" else "") + habit.counterEffect, "أثر كل إنجاز", tint, Modifier.weight(1f))
                        Stat("\u200E" + total * habit.counterEffect, "نصيب هذه العادة", tint, Modifier.weight(1f))
                    }
                }
            }

            Panel {
                Heading("نظرة عامة", tint)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Ring(score.toFloat(), tint, 52.dp, 5.dp)
                    Stat("${(score * 100).roundToInt()}%", "القوة", tint, Modifier.weight(1f))
                    Stat(signed(((score - scoreAgo(30)) * 100).roundToInt()), "شهر", tint, Modifier.weight(1f))
                    Stat(signed(((score - scoreAgo(365)) * 100).roundToInt()), "سنة", tint, Modifier.weight(1f))
                    Stat(total.toString(), "المجموع", tint, Modifier.weight(1f))
                }
                if (habit.contentRef == Quran.CONTENT) {
                    val position = db.quranState(id).first
                    Text(
                        "الختمة الحالية ${(position * 100.0 / Quran.TOTAL).roundToInt()}%، والختمات المكتملة ${db.khatmas(id)}",
                        color = muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    LinearProgressIndicator(progress = { position.toFloat() / Quran.TOTAL }, color = tint, modifier = Modifier.fillMaxWidth())
                }
            }

            Panel {
                Heading("القوة في آخر ثلاثة أشهر", tint)
                val points = remember(series) {
                    val n = 13
                    List(n) { i -> series.getOrElse(series.size - 1 - (n - 1 - i) * 7) { 0.0 }.toFloat() }
                }
                val grid = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    for (g in 0..4) {
                        val y = size.height * g / 4f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    val step = size.width / (points.size - 1)
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        // Oldest on the right, as the page reads right to left.
                        val x = size.width - i * step
                        val y = size.height * (1f - p)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, tint, style = Stroke(5f, cap = StrokeCap.Round))
                    points.forEachIndexed { i, p ->
                        drawCircle(tint, 7f, Offset(size.width - i * step, size.height * (1f - p)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("قبل 12 أسبوعًا", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
                    Text("اليوم", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("السجل", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = tint)
                    FilterChip(selected = !byMonth, onClick = { byMonth = false }, label = { Text("سنة") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = byMonth, onClick = { byMonth = true }, label = { Text("شهر") })
                }
                val bars = remember(id, version, byMonth, today) {
                    val now = LocalDate.ofEpochDay(today)
                    val done = entries.filterValues { it == Mark.DONE }.keys.map { LocalDate.ofEpochDay(it) }
                    if (byMonth) {
                        List(8) { i ->
                            val m = now.minusMonths((7 - i).toLong())
                            monthNames[m.monthValue - 1] to done.count { it.year == m.year && it.monthValue == m.monthValue }
                        }
                    } else {
                        val first = maxOf(LocalDate.ofEpochDay(habit.created).year, now.year - 6)
                        (first..now.year).map { y -> y.toString() to done.count { it.year == y } }
                    }
                }
                val top = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    for ((label, count) in bars) {
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = tint)
                            Box(
                                Modifier.padding(vertical = 4.dp).width(18.dp)
                                    .fillMaxHeight((count.toFloat() / top * 0.72f).coerceAtLeast(0.01f))
                                    .clip(RoundedCornerShape(5.dp)).background(tint)
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        }
                    }
                }
            }

            Panel {
                val month = LocalDate.ofEpochDay(today).withDayOfMonth(1).plusMonths(monthOffset.toLong())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("التقويم", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = tint)
                    TextButton(onClick = { monthOffset-- }) { Text("السابق") }
                    Text("${monthNames[month.monthValue - 1]} ${month.year}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) { Text("التالي") }
                }
                Row(Modifier.fillMaxWidth()) {
                    for (n in weekOrder()) {
                        Text(n, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
                val first = Schedule.weekStart(month.toEpochDay())
                val last = month.plusMonths(1).minusDays(1).toEpochDay()
                var week = first
                while (week <= last) {
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0..6) {
                            val day = week + i
                            val inMonth = LocalDate.ofEpochDay(day).monthValue == month.monthValue
                            val value = entries[day] ?: Mark.NONE
                            val planned = Schedule.isScheduled(habit, dates, day) && day >= habit.created
                            val editable = day <= today && inMonth
                            val shape = RoundedCornerShape(9.dp)
                            val base = Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(shape)
                            val look = when {
                                !inMonth -> base
                                value == Mark.DONE -> base.background(solid)
                                value == Mark.EXCUSED -> base.border(1.5.dp, muted, shape)
                                (partials[day] ?: 0) > 0 -> base.background(solid.copy(alpha = 0.4f))
                                planned && day <= today -> base.background(MaterialTheme.colorScheme.surfaceVariant)
                                else -> base
                            }
                            Box(
                                look.clickable(enabled = editable) {
                                    val next = when (value) {
                                        Mark.NONE -> Mark.DONE
                                        Mark.DONE -> Mark.EXCUSED
                                        else -> Mark.NONE
                                    }
                                    db.setMark(id, day, next)
                                    changed()
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                if (inMonth) {
                                    Text(
                                        if (value == Mark.EXCUSED) "عذر" else LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            value == Mark.DONE -> Color.White
                                            day == today -> tint
                                            day > today -> muted
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                    week += 7
                }
                Text(
                    "اضغط اليوم لتغييره: منجز، ثم معذور، ثم فارغ. اليوم المعذور لا يضر القوة.",
                    style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (habit.kind != Kind.PER_WEEK && streaks.isNotEmpty()) {
                Panel {
                    Heading("أطول السلاسل", tint)
                    val longest = streaks.first().third.coerceAtLeast(1)
                    for ((start, end, length) in streaks.take(4)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalDate.ofEpochDay(start).toString(), Modifier.width(86.dp), style = MaterialTheme.typography.labelSmall, color = muted)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.fillMaxWidth((length.toFloat() / longest).coerceAtLeast(0.18f)).height(26.dp)
                                        .clip(RoundedCornerShape(7.dp)).background(solid),
                                    contentAlignment = Alignment.Center
                                ) { Text(length.toString(), style = MaterialTheme.typography.labelLarge, color = Color.White) }
                            }
                            Text(
                                LocalDate.ofEpochDay(end).toString(), Modifier.width(86.dp), textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall, color = muted
                            )
                        }
                    }
                }
            }

            Panel {
                Heading("التردد في أيام الأسبوع", tint)
                val counts = remember(id, version) {
                    IntArray(7).also { c -> entries.filterValues { it == Mark.DONE }.keys.forEach { c[Schedule.weekdayIndex(it)]++ } }
                }
                val top = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom) {
                    for (i in 0..6) {
                        val index = (app.daab.habits.logic.Settings.weekStart + i) % 7
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(counts[index].toString(), style = MaterialTheme.typography.labelSmall, color = tint)
                            Box(
                                Modifier.padding(vertical = 4.dp).width(16.dp)
                                    .fillMaxHeight((counts[index].toFloat() / top * 0.62f).coerceAtLeast(0.01f))
                                    .clip(RoundedCornerShape(5.dp)).background(tint)
                            )
                            Text(weekdayNames[index], style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
