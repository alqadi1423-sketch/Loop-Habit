@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daab.habits.R
import app.daab.habits.data.Adhkar
import app.daab.habits.data.Db
import app.daab.habits.data.Dhikr
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import kotlin.math.min
import kotlin.math.roundToInt

private const val VIEW_VERTICAL = 0
private const val VIEW_HORIZONTAL = 1
private const val VIEW_SESSION = 2

/**
 * Reading the adhkar of a habit with a counter for each. They can be shown as a list of
 * cards, one per page, or one at a time where a touch anywhere counts.
 */
@Composable
fun ReaderScreen(db: Db, habitId: Long, changed: () -> Unit, back: () -> Unit, free: Screen.AdhkarRead? = null) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val habit = remember(habitId) { if (free != null) null else db.habit(habitId) }
    // A chapter opened from the adhkar page keeps today's count under its own key and marks no habit.
    val key = if (free != null) -(free.category * 10L + free.part) - 1 else habitId
    val title = if (free != null) Adhkar.title(ctx, free.category, free.part) else habit?.name ?: ""
    val colorValue = habit?.color ?: 0xFF0F5C55.toInt()
    val items = remember(habitId, free) {
        when {
            free != null -> Adhkar.items(ctx, free.category, free.part)
            habit == null -> emptyList()
            habit.contentRef == Adhkar.CUSTOM -> db.customItems(habitId)
            else -> Adhkar.items(ctx, habit.contentRef, habit.contentPart)
        }
    }
    val today = remember { Schedule.today() }
    val counts = remember(habitId) {
        mutableStateMapOf<Int, Int>().apply { putAll(db.progress(key, today)) }
    }
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var view by remember { mutableIntStateOf(prefs.getInt("reader_view", VIEW_VERTICAL)) }
    var fontSize by remember { mutableIntStateOf(prefs.getInt("reader_font", 22)) }
    var naskh by remember { mutableStateOf(prefs.getBoolean("reader_naskh", true)) }
    var buzzEnd by remember { mutableStateOf(prefs.getBoolean("reader_buzz_end", true)) }
    var buzzTap by remember { mutableStateOf(prefs.getBoolean("reader_buzz_tap", false)) }
    var hideDone by remember { mutableStateOf(prefs.getBoolean("reader_hide_done", false)) }
    var tapText by remember { mutableStateOf(prefs.getBoolean("reader_tap_text", false)) }
    var confirmExit by remember { mutableStateOf(prefs.getBoolean("reader_confirm_exit", true)) }
    var options by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var current by remember(habitId) {
        mutableIntStateOf(items.indexOfFirst { (counts[it.id] ?: 0) < it.count }.let { if (it < 0) 0 else it })
    }

    if ((habit == null && free == null) || items.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
                )
            }
        ) { pad ->
            Text("لا يوجد محتوى لهذه العادة. اختر محتواها من تعديل العادة.", Modifier.padding(pad).padding(20.dp))
        }
        return
    }

    DisposableEffect(habitId) {
        onDispose { changed() }
    }

    val color = Color(colorValue)
    val tint = habitTint(colorValue)
    val family = if (naskh) remember { FontFamily(Font(R.font.amiri_regular)) } else MaterialTheme.typography.bodyLarge.fontFamily
    fun left(d: Dhikr) = (d.count - (counts[d.id] ?: 0)).coerceAtLeast(0)
    fun share(): Double = items.sumOf { min(1.0, (counts[it.id] ?: 0).toDouble() / it.count) } / items.size
    val finished = items.all { left(it) == 0 }
    val started = counts.values.any { it > 0 }

    fun leave() {
        if (confirmExit && started && !finished) asking = true else back()
    }
    BackHandler { leave() }

    fun count(d: Dhikr) {
        val now = (counts[d.id] ?: 0) + 1
        if (now > d.count) return
        counts[d.id] = now
        db.setProgress(key, today, d.id, now)
        if (habit != null) db.setPartial(habitId, today, (share() * 1000).roundToInt())
        val all = items.all { left(it) == 0 }
        if (now >= d.count) {
            if (buzzEnd) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val next = items.indexOfFirst { left(it) > 0 }
            if (next >= 0) current = next
        } else if (buzzTap) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (all && habit != null) {
            db.setEntry(habitId, today, Mark.DONE)
            changed()
        }
    }

    fun send(d: Dhikr) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, d.text)
        ctx.startActivity(Intent.createChooser(i, "مشاركة الذكر"))
    }

    /** One dhikr as a card: its text, then a bar that counts down as it is pressed. */
    @Composable
    fun Card(d: Dhikr, modifier: Modifier = Modifier, scroll: Boolean = false) {
        val shape = RoundedCornerShape(20.dp)
        val remaining = left(d)
        Column(
            modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { send(d) }) {
                    Icon(Icons.Default.Share, "مشاركة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${items.indexOf(d) + 1} من ${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val textModifier = Modifier.fillMaxWidth()
                .then(if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .then(
                    if (tapText && remaining > 0) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { count(d) } else Modifier
                )
                .padding(horizontal = 18.dp, vertical = 10.dp)
            Text(
                d.text,
                fontFamily = family,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.95f).sp,
                textAlign = TextAlign.Center,
                modifier = textModifier
            )
            Box(
                Modifier.fillMaxWidth().height(64.dp)
                    .background(if (remaining > 0) color else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = remaining > 0) { count(d) },
                contentAlignment = Alignment.Center
            ) {
                if (remaining > 0) {
                    Text(remaining.toString(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                } else {
                    Icon(Icons.Default.Check, "اكتمل", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = { IconButton(onClick = { options = true }) { Icon(Icons.Default.Settings, "خيارات العرض") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(
                progress = { share().toFloat() }, color = tint,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(5.dp).clip(RoundedCornerShape(50))
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    "${items.count { left(it) == 0 }} من ${items.size}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("${(share() * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = tint)
            }

            if (finished) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("أتممت $title", style = MaterialTheme.typography.headlineSmall, color = tint)
                    if (habit != null) Text("سُجلت العادة منجزة اليوم.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = back) { Text("رجوع") }
                }
            } else when (view) {
                VIEW_VERTICAL -> {
                    val shown = if (hideDone) items.filter { left(it) > 0 } else items
                    LazyColumn(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.id }) { d -> Card(d) }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
                VIEW_HORIZONTAL -> {
                    val pager = rememberPagerState(initialPage = current) { items.size }
                    LaunchedEffect(current) {
                        if (pager.currentPage != current) pager.animateScrollToPage(current)
                    }
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f), pageSpacing = 10.dp) { page ->
                        Card(items[page], Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxSize(), scroll = true)
                    }
                }
                else -> {
                    val d = items[current.coerceIn(0, items.size - 1)]
                    Column(
                        Modifier.weight(1f).fillMaxWidth().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { count(d) }.padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                            Text(
                                d.text,
                                fontFamily = family,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.95f).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        }
                        Text(left(d).toString(), style = MaterialTheme.typography.displayMedium, color = tint)
                        Text(
                            "المس أي مكان للعدّ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                }
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("لم تكتمل الأذكار") },
            text = { Text("ما قرأته محفوظ، وتكمل من حيث وقفت حين ترجع.") },
            confirmButton = { TextButton(onClick = { asking = false; back() }) { Text("خروج") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("متابعة القراءة") } }
        )
    }

    if (options) {
        ModalBottomSheet(onDismissRequest = { options = false }) {
            @Composable
            fun Toggle(title: String, detail: String, value: Boolean, key: String, set: (Boolean) -> Unit) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = value, onCheckedChange = {
                        set(it)
                        prefs.edit().putBoolean(key, it).apply()
                    })
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("خيارات العرض", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in listOf(VIEW_VERTICAL to "عمودي", VIEW_HORIZONTAL to "أفقي", VIEW_SESSION to "وضع الجلسة")) {
                        FilterChip(selected = view == value, onClick = {
                            view = value
                            prefs.edit().putInt("reader_view", value).apply()
                        }, label = { Text(label) })
                    }
                }
                Text(
                    when (view) {
                        VIEW_VERTICAL -> "كل الأذكار تحت بعض، وزر العدّ أسفل كل ذكر."
                        VIEW_HORIZONTAL -> "ذكر في كل صفحة، وتقلّب بينها."
                        else -> "ذكر واحد في كل مرة، واللمس في أي مكان يعدّ."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Text("حجم الخط $fontSize", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.roundToInt() },
                    onValueChangeFinished = { prefs.edit().putInt("reader_font", fontSize).apply() },
                    valueRange = 16f..40f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = naskh, onClick = {
                        naskh = true
                        prefs.edit().putBoolean("reader_naskh", true).apply()
                    }, label = { Text("خط نسخي") })
                    FilterChip(selected = !naskh, onClick = {
                        naskh = false
                        prefs.edit().putBoolean("reader_naskh", false).apply()
                    }, label = { Text("خط التطبيق") })
                }
                Spacer(Modifier.height(8.dp))
                Toggle("ارتجاج عند الصفر", "يرتج الجوال حين يكتمل عدّ الذكر.", buzzEnd, "reader_buzz_end") { buzzEnd = it }
                Toggle("ارتجاج عند كل ضغطة", "ارتجاج خفيف مع كل عدّة.", buzzTap, "reader_buzz_tap") { buzzTap = it }
                Toggle("إخفاء عند الصفر", "يختفي الذكر من القائمة حين يكتمل عدّه.", hideDone, "reader_hide_done") { hideDone = it }
                Toggle("العدّ بالضغط على الذكر", "تضغط على نص الذكر نفسه ليُعدّ.", tapText, "reader_tap_text") { tapText = it }
                Toggle("تأكيد الخروج", "يسألك قبل الخروج إن لم تكتمل الأذكار.", confirmExit, "reader_confirm_exit") { confirmExit = it }
            }
        }
    }
}
