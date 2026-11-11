@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import app.daab.habits.data.Adhkar
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Palette
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.ReminderReceiver
import java.time.LocalDate

fun timeText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "ص" else "م")
}

@Composable
fun EditScreen(db: Db, id: Long, saved: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val original = remember(id) { if (id == 0L) null else db.habit(id) }
    var name by remember { mutableStateOf(original?.name ?: "") }
    var color by remember { mutableIntStateOf(original?.color ?: Palette.colors[0]) }
    var kind by remember { mutableIntStateOf(original?.kind ?: Kind.DAILY) }
    var weekdays by remember { mutableIntStateOf(original?.weekdays ?: 127) }
    var perWeek by remember { mutableIntStateOf(original?.perWeek ?: 3) }
    var reminder by remember { mutableIntStateOf(original?.reminder ?: -1) }
    var dates by remember { mutableStateOf(if (id == 0L) emptySet() else db.dates(id)) }
    val groups = remember { db.groups() }
    var groupId by remember { mutableStateOf(original?.groupId ?: 0L) }
    var startKind by remember { mutableIntStateOf(original?.startKind ?: Anchor.NONE) }
    var startValue by remember { mutableIntStateOf(original?.startValue ?: 0) }
    var endKind by remember { mutableIntStateOf(original?.endKind ?: Anchor.NONE) }
    var endValue by remember { mutableIntStateOf(original?.endValue ?: 0) }
    var contentRef by remember { mutableIntStateOf(original?.contentRef ?: 0) }
    var contentPart by remember { mutableIntStateOf(original?.contentPart ?: 0) }
    var pickContent by remember { mutableStateOf(false) }
    var pickAnchor by remember { mutableIntStateOf(0) }
    var pickAnchorClock by remember { mutableIntStateOf(0) }
    var nameError by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun save() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        val h = (original ?: Habit(created = Schedule.today(), position = db.habits(true).size)).copy(
            name = name.trim(), color = color, kind = kind,
            weekdays = if (weekdays == 0) 127 else weekdays,
            perWeek = perWeek, reminder = reminder,
            groupId = groupId, startKind = startKind, startValue = startValue,
            endKind = endKind, endValue = endValue,
            contentRef = contentRef, contentPart = contentPart
        )
        val newId = db.save(h)
        db.setDates(newId, if (kind == Kind.DATES) dates else emptySet())
        ReminderReceiver.schedule(ctx, h.copy(id = newId))
        saved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "عادة جديدة" else "تعديل العادة") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("اسم العادة") },
                isError = nameError,
                supportingText = { if (nameError) Text("اكتب اسم العادة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("اللون", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in Palette.colors) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = c }
                    )
                }
            }

            Text("الجدول", style = MaterialTheme.typography.labelLarge)
            val kinds = listOf(
                Kind.DAILY to "كل يوم",
                Kind.WEEKDAYS to "أيام معينة من الأسبوع",
                Kind.PER_WEEK to "عدد مرات في الأسبوع",
                Kind.DATES to "أيام أختارها من التقويم"
            )
            for ((k, label) in kinds) {
                Row(Modifier.fillMaxWidth().clickable { kind = k }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == k, onClick = { kind = k })
                    Text(label)
                }
            }

            if (kind == Kind.WEEKDAYS) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..6) {
                        val on = (weekdays shr i) and 1 == 1
                        FilterChip(selected = on, onClick = { weekdays = weekdays xor (1 shl i) }, label = { Text(weekdayNames[i]) })
                    }
                }
            }
            if (kind == Kind.PER_WEEK) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { if (perWeek > 1) perWeek-- }) { Text("-") }
                    Text("$perWeek مرات في الأسبوع")
                    OutlinedButton(onClick = { if (perWeek < 7) perWeek++ }) { Text("+") }
                }
            }
            if (kind == Kind.DATES) {
                for (d in dates.sorted()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val date = LocalDate.ofEpochDay(d)
                        Text("${weekdayNames[Schedule.weekdayIndex(d)]} $date", Modifier.weight(1f))
                        IconButton(onClick = { dates = dates - d }) { Icon(Icons.Default.Close, "إزالة") }
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) { Text("إضافة يوم") }
            }

            if (groups.isNotEmpty()) {
                Text("المجموعة", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = groupId == 0L, onClick = { groupId = 0L }, label = { Text("بلا مجموعة") })
                    for (g in groups) {
                        FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                    }
                }
            }

            Text("ما يفتحه الضغط على العادة", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { pickContent = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (contentRef == 0) "لا شيء، تُعلَّم بضغطة" else Adhkar.title(ctx, contentRef, contentPart))
            }

            Text("وقت الظهور في اليوم", style = MaterialTheme.typography.labelLarge)
            Text(
                "تظهر العادة في المربع داخل هذا الوقت فقط، وبعده تُسجل فائتة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickAnchor = 1 }, modifier = Modifier.weight(1f)) {
                    Text("من: " + anchorLabel(startKind, startValue, null, "أول اليوم"))
                }
                OutlinedButton(onClick = { pickAnchor = 2 }, modifier = Modifier.weight(1f)) {
                    Text("إلى: " + anchorLabel(endKind, endValue, null, "آخر اليوم"))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تذكير", style = MaterialTheme.typography.labelLarge)
                    if (reminder >= 0) TextButton(onClick = { pickTime = true }) { Text(timeText(reminder)) }
                }
                Switch(checked = reminder >= 0, onCheckedChange = {
                    if (it) {
                        reminder = 8 * 60
                        pickTime = true
                        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else reminder = -1
                })
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dates = dates + (it / 86_400_000L) }
                    pickDate = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } }
        ) { DatePicker(state = state) }
    }

    if (pickTime) {
        val start = if (reminder >= 0) reminder else 8 * 60
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    reminder = state.hour * 60 + state.minute
                    pickTime = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("إلغاء") } },
            text = { TimePicker(state = state) }
        )
    }

    if (pickContent) {
        val categories = remember { Adhkar.categories(ctx) }
        fun set(ref: Int, part: Int) {
            contentRef = ref
            contentPart = part
            pickContent = false
        }
        AlertDialog(
            onDismissRequest = { pickContent = false },
            title = { Text("اختر الأذكار") },
            text = {
                LazyColumn {
                    item { TextButton(onClick = { set(0, 0) }) { Text("لا شيء، تُعلَّم بضغطة") } }
                    item { TextButton(onClick = { set(Adhkar.MORNING_EVENING, Adhkar.PART_MORNING) }) { Text("أذكار الصباح") } }
                    item { TextButton(onClick = { set(Adhkar.MORNING_EVENING, Adhkar.PART_EVENING) }) { Text("أذكار المساء") } }
                    items(categories.filter { it.id != Adhkar.MORNING_EVENING }) { c ->
                        TextButton(onClick = { set(c.id, Adhkar.PART_ALL) }) { Text(c.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickContent = false }) { Text("إلغاء") } }
        )
    }

    if (pickAnchor != 0) {
        val isStart = pickAnchor == 1
        fun set(kind: Int, value: Int) {
            if (isStart) {
                startKind = kind
                startValue = value
            } else {
                endKind = kind
                endValue = value
            }
            pickAnchor = 0
        }
        AlertDialog(
            onDismissRequest = { pickAnchor = 0 },
            title = { Text(if (isStart) "يبدأ وقتها" else "ينتهي وقتها") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { set(Anchor.NONE, 0) }) { Text(if (isStart) "أول اليوم" else "آخر اليوم") }
                    for (i in prayerNames.indices) {
                        TextButton(onClick = { set(Anchor.PRAYER, i) }) { Text(prayerNames[i]) }
                    }
                    TextButton(onClick = {
                        pickAnchorClock = pickAnchor
                        pickAnchor = 0
                    }) { Text("ساعة أحددها") }
                }
            },
            confirmButton = { TextButton(onClick = { pickAnchor = 0 }) { Text("إلغاء") } }
        )
    }

    if (pickAnchorClock != 0) {
        val isStart = pickAnchorClock == 1
        val state = rememberTimePickerState(initialHour = if (isStart) 6 else 12, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickAnchorClock = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    if (isStart) {
                        startKind = Anchor.CLOCK
                        startValue = minutes
                    } else {
                        endKind = Anchor.CLOCK
                        endValue = minutes
                    }
                    pickAnchorClock = 0
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickAnchorClock = 0 }) { Text("إلغاء") } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف العادة") },
            text = { Text("سيُحذف سجل «${original.name}» كله ولا يمكن استرجاعه.") },
            confirmButton = {
                TextButton(onClick = {
                    ReminderReceiver.cancel(ctx, original.id)
                    db.delete(original.id)
                    saved()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}
