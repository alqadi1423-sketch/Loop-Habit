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
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Palette
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
            perWeek = perWeek, reminder = reminder
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
