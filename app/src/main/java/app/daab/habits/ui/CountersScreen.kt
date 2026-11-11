@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import app.daab.habits.data.Counter
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** A signed number written left to right so the sign sits where it is read. */
@Composable
private fun Signed(value: Int, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(if (value > 0) "+$value" else value.toString(), style = style, color = color)
    }
}

/** Running totals. Each counter adds up its habits, every completion counting by the habit's effect. */
@Composable
fun CountersScreen(db: Db, version: Int, changed: () -> Unit, go: (Screen) -> Unit) {
    val counters = remember(version) { db.counters() }
    val habits = remember(version) { db.habits(true) }
    val done = remember(version) { habits.associate { it.id to db.doneCount(it.id) } }
    var edit by remember { mutableStateOf<Counter?>(null) }
    var linkTo by remember { mutableStateOf<Counter?>(null) }
    var effectOf by remember { mutableStateOf<Habit?>(null) }
    var effectCounter by remember { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العدّادات") },
                actions = {
                    IconButton(onClick = { edit = Counter(position = counters.size) }) { Icon(Icons.Default.Add, "عدّاد جديد") }
                }
            )
        },
        bottomBar = { BottomNav(Screen.Counters, go) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (counters.isEmpty()) {
                Panel(Modifier.padding(top = 12.dp), padding = 20.dp) {
                    Text("لا عدّادات بعد", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "العدّاد رصيد تراكمي يُحسب وحده من عادات تربطها به. مثال: عدّاد باسم «الصيام»، تربط به عادة أثرها 3 فتضيف ثلاثة في كل مرة تنجزها، وعادة أثرها 0 فتُسجل ولا تغيّره، وعادة أثرها ‎-1 فتنقص واحدًا. ابدأ بعلامة الإضافة في الأعلى وسمِّ العدّاد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            for (c in counters) {
                val members = habits.filter { it.counterId == c.id }
                val total = members.sumOf { (done[it.id] ?: 0) * it.counterEffect }
                Column(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.name, Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "تعديل",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.clickable { edit = c }.padding(8.dp)
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            total.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "المجموع الكلي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Panel(Modifier.padding(top = 8.dp), padding = 0.dp) {
                    if (members.isEmpty()) {
                        Text(
                            "لا عادات مربوطة بهذا العدّاد بعد. اضغط «ربط عادة» واختر العادة وأثرها.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    members.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val times = done[h.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                effectCounter = c.id
                                effectOf = h
                            }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Signed(h.counterEffect, MaterialTheme.typography.labelLarge, habitTint(h.color)) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(h.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "أُنجزت $times مرة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Signed(times * h.counterEffect, MaterialTheme.typography.titleMedium, habitTint(h.color))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = { linkTo = c }, modifier = Modifier.padding(horizontal = 6.dp)) { Text("ربط عادة") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    edit?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var error by remember(c) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (c.id == 0L) "عدّاد جديد" else "تعديل العدّاد") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text("اسم العدّاد") },
                    placeholder = { Text("مثل: الصيام") },
                    isError = error,
                    supportingText = {
                        Text(if (error) "اكتب اسم العدّاد" else "هذا اسم العدّاد لا رقمه. المجموع يُحسب وحده من العادات المربوطة به.")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) error = true
                    else {
                        db.saveCounter(c.copy(name = name.trim()))
                        edit = null
                        changed()
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                if (c.id != 0L) {
                    TextButton(onClick = {
                        db.deleteCounter(c.id)
                        edit = null
                        changed()
                    }) { Text("حذف العدّاد وإبقاء عاداته") }
                } else {
                    TextButton(onClick = { edit = null }) { Text("إلغاء") }
                }
            }
        )
    }

    linkTo?.let { c ->
        val free = habits.filter { it.counterId != c.id && !it.archived }
        AlertDialog(
            onDismissRequest = { linkTo = null },
            title = { Text("اختر العادة") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (free.isEmpty()) Text("لا عادات أخرى. أضف عادة من صفحة العادات أولًا.")
                    for (h in free) {
                        TextButton(onClick = {
                            effectCounter = c.id
                            effectOf = h
                            linkTo = null
                        }) { Text(h.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkTo = null }) { Text("إلغاء") } }
        )
    }

    effectOf?.let { h ->
        val linked = h.counterId == effectCounter
        var text by remember(h) { mutableStateOf(if (linked) h.counterEffect.toString() else "1") }
        AlertDialog(
            onDismissRequest = { effectOf = null },
            title = { Text(h.name) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        val sign = if (v.trim().startsWith("-")) "-" else ""
                        text = sign + v.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("أثرها في كل إنجاز") },
                    supportingText = { Text("رقم فقط. مثل 3 لتضيف ثلاثة، أو 0 بلا أثر، أو ‎-1 لتنقص واحدًا.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.save(h.copy(counterId = effectCounter, counterEffect = (text.toIntOrNull() ?: 0).coerceIn(-9999, 9999)))
                    effectOf = null
                    changed()
                }) { Text("حفظ") }
            },
            dismissButton = {
                if (linked) {
                    TextButton(onClick = {
                        db.save(h.copy(counterId = 0, counterEffect = 0))
                        effectOf = null
                        changed()
                    }) { Text("فك الربط") }
                } else {
                    TextButton(onClick = { effectOf = null }) { Text("إلغاء") }
                }
            }
        )
    }
}
