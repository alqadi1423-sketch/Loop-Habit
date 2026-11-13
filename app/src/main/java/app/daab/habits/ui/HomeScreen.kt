@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.daab.habits.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.Group
import app.daab.habits.data.Mark
import app.daab.habits.logic.Hijri
import app.daab.habits.logic.Phase
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.logic.Settings
import kotlinx.coroutines.delay
import java.time.LocalDate

/** As many past days as fit beside a readable habit name: four on most phones, fewer on small ones. */
private val days: Int
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 216) / 46).coerceIn(2, 7)
private val CELL = 46.dp

/** All habits as a table: one row each, with a mark for today and the days before it. */
@Composable
fun HomeScreen(db: Db, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minute++
        }
    }
    val today = remember(version, minute) { Schedule.today() }
    val now = Schedule.nowMinutes()
    val all = remember(version, minute) { loadStates(db) }
    val groups = remember(version) { db.groups() }
    val counters = remember(version) { db.counters() }
    val counterTotals = remember(version) {
        val linked = db.habits(true).filter { it.counterId != 0L }
        counters.associate { c -> c.id to linked.filter { it.counterId == c.id }.sumOf { db.doneCount(it.id) * it.counterEffect } }
    }
    val times = remember(version, today) { PrayerTimes.forDay(ctx, today) }
    var menu by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<Group?>(null) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(version) {
        val reminds = all.any { it.habit.reminder >= 0 || it.habit.remindStart || it.habit.remindEnd > 0 }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (reminds && Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled()) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val sort = Settings.sort
    val hideDone = Settings.hideDone
    fun arrange(list: List<HabitState>): List<HabitState> {
        val shown = if (hideDone) list.filter { it.entries[today] != Mark.DONE } else list
        return when (sort) {
            Settings.SORT_NAME -> shown.sortedBy { it.habit.name }
            Settings.SORT_COLOR -> shown.sortedBy { it.habit.color }
            Settings.SORT_SCORE -> shown.sortedByDescending { it.score }
            Settings.SORT_STATUS -> shown.sortedBy { if (it.entries[today] == Mark.DONE) 1 else 0 }
            else -> shown
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
                    IconButton(onClick = { go(Screen.Edit(0)) }) { Icon(Icons.Default.Add, "عادة جديدة") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "القائمة") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val sorts = listOf("يدويًا", "حسب الاسم", "حسب اللون", "حسب القوة", "حسب الحالة")
                        for (i in sorts.indices) {
                            DropdownMenuItem(
                                text = { Text("الفرز " + sorts[i]) },
                                trailingIcon = { if (sort == i) Icon(Icons.Default.Check, null) },
                                onClick = {
                                    Settings.setSort(ctx, i)
                                    menu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("إخفاء المنجز اليوم") },
                            trailingIcon = { if (hideDone) Icon(Icons.Default.Check, null) },
                            onClick = {
                                Settings.setHideDone(ctx, !hideDone)
                                menu = false
                            }
                        )
                        HorizontalDivider()
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
                    }
                }
            )
        },
        bottomBar = { BottomNav(Screen.Home, go) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (all.isEmpty() && groups.isEmpty()) {
                Panel(Modifier.padding(top = 24.dp), padding = 20.dp) {
                    Text("ابدأ بأول عادة", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "اضغط علامة الإضافة في الأعلى، أو أضف مجموعة الصلوات من القائمة. وإن كان لك سجل في تطبيق آخر فانقله من الإعدادات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    for (i in 0 until days) {
                        val d = today - i
                        Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                weekdayNames[Schedule.weekdayIndex(d)],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                LocalDate.ofEpochDay(d).dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val groupIds = groups.map { it.id }.toSet()
            val loose = arrange(all.filter { it.habit.groupId !in groupIds })
            if (loose.isNotEmpty()) {
                Panel(padding = 0.dp) {
                    loose.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HabitLine(db, s, today, now, times, changed, go)
                    }
                }
            }
            for (g in groups) {
                val members = all.filter { it.habit.groupId == g.id }
                val done = members.count { it.entries[today] == Mark.DONE }
                val due = members.count { Schedule.isDue(it.habit, it.dates, it.entries, today) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.name, Modifier.weight(1f).padding(start = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("$done من $due", style = MaterialTheme.typography.labelMedium, color = habitTint(g.color))
                    TextButton(onClick = { editGroup = g }) { Text("تعديل") }
                }
                val shown = arrange(members.sortedBy { Schedule.window(it.habit, times).first })
                Panel(padding = 0.dp) {
                    if (members.isEmpty()) {
                        Text(
                            "أضف عادة ثم اختر هذه المجموعة لها.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    shown.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HabitLine(db, s, today, now, times, changed, go)
                    }
                    for (c in counters.filter { it.groupId == g.id }) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().clickable { go(Screen.Counters) }
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "عدّاد", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    c.name, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "\u200E" + (counterTotals[c.id] ?: 0),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            if (all.isNotEmpty()) {
                Text(
                    "اضغط العلامة لتغييرها، واضغط مطولًا لتسجيل اليوم معذورًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
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
private fun HabitLine(
    db: Db, s: HabitState, today: Long, now: Int, times: IntArray, changed: () -> Unit, go: (Screen) -> Unit
) {
    val h = s.habit
    val tint = habitTint(h.color)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val windowed = Schedule.hasWindow(h)
    val phase = Schedule.phase(h, times, now)
    val todayValue = s.entries[today] ?: Mark.NONE
    Row(
        Modifier.fillMaxWidth().clickable { go(Screen.Detail(h.id)) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ring(s.score / 100f, tint, 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                h.name, style = MaterialTheme.typography.titleMedium, color = tint,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (windowed && todayValue == Mark.NONE && Schedule.isDue(h, s.dates, s.entries, today)) {
                val (start, _) = Schedule.window(h, times)
                Text(
                    when (phase) {
                        Phase.BEFORE -> "تبدأ " + timeText(start)
                        Phase.OPEN -> "وقتها الآن"
                        else -> "فائتة"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (phase == Phase.CLOSED) MaterialTheme.colorScheme.error else muted,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
        }
        for (i in 0 until days) {
            val d = today - i
            val value = s.entries[d] ?: Mark.NONE
            val partial = s.partials[d] ?: 0
            val planned = d >= h.created &&
                (if (d == today) Schedule.isDue(h, s.dates, s.entries, d) else Schedule.isScheduled(h, s.dates, d))
            val reading = h.contentRef != 0
            Box(
                Modifier.size(CELL).combinedClickable(
                    enabled = d >= h.created,
                    onClick = {
                        if (d == today && reading) go(Screen.Reader(h.id))
                        else if (d == today && h.target > 0) {
                            db.tap(h, d)
                            changed()
                        } else {
                            db.setMark(h.id, d, if (value == Mark.DONE) Mark.NONE else Mark.DONE)
                            changed()
                        }
                    },
                    onLongClick = {
                        db.setMark(h.id, d, if (value == Mark.EXCUSED) Mark.NONE else Mark.EXCUSED)
                        changed()
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    value == Mark.DONE -> Icon(Icons.Default.Check, "منجزة", tint = tint)
                    value == Mark.EXCUSED -> Text("عذر", style = MaterialTheme.typography.labelSmall, color = muted)
                    d == today && h.target > 0 && planned ->
                        Text("${s.count}/${h.target}", style = MaterialTheme.typography.labelSmall, color = tint)
                    partial > 0 -> Text("${partial / 10}%", style = MaterialTheme.typography.labelSmall, color = tint)
                    !planned -> Text("·", color = muted, textAlign = TextAlign.Center)
                    d == today && !(windowed && phase == Phase.CLOSED) ->
                        Box(Modifier.size(18.dp).border(2.dp, tint, CircleShape))
                    else -> Icon(Icons.Default.Close, "فائتة", tint = muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
