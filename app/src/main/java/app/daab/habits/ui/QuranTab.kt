@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daab.habits.R
import app.daab.habits.data.Db
import app.daab.habits.data.Quran

private val ordinals = listOf(
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
    "الثامن عشر", "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون",
    "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون",
    "التاسع والعشرون", "الثلاثون"
)

/** The Quran page: the surahs by part and the quarters, to open any place and read. */
@Composable
fun QuranTab(db: Db, version: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val quran = remember { Quran.text(ctx) }
    val wird = remember(version) { db.habits().firstOrNull { it.contentRef == Quran.CONTENT } }
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val last = remember(version) { prefs.getInt("quran_last", -1) }
    var quarters by remember { mutableStateOf(false) }
    fun juzOf(position: Int) = quran.rub.indexOfLast { it <= position } / 8

    Scaffold(
        topBar = { TopAppBar(title = { Text("القرآن") }) },
        bottomBar = { BottomNav(Screen.QuranTab, go) }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (wird != null) {
                item {
                    Panel(Modifier.padding(bottom = 10.dp)) {
                        Text(wird.name, style = MaterialTheme.typography.titleMedium, color = habitTint(wird.color))
                        Text(
                            Quran.describe(wird.contentUnit, wird.contentAmount),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { go(Screen.Reader(wird.id)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("افتح ورد اليوم")
                        }
                    }
                }
            }
            if (last >= 0) {
                item {
                    Panel(Modifier.padding(bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("آخر موضع قرأته", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(quran.label(last), style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = {
                                val s = quran.surahs[quran.surahAt(last)]
                                go(Screen.QuranRead(last, s.start + s.count))
                            }) { Text("تابع") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !quarters, onClick = { quarters = false }, label = { Text("السور") })
                    FilterChip(selected = quarters, onClick = { quarters = true }, label = { Text("الأرباع") })
                }
            }
            if (!quarters) {
                itemsIndexed(quran.surahs) { i, s ->
                    val juz = juzOf(s.start)
                    if (i == 0 || juzOf(quran.surahs[i - 1].start) != juz) {
                        Text(
                            "الجزء " + ordinals[juz.coerceIn(0, 29)],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { go(Screen.QuranRead(s.start, s.start + s.count)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Text((i + 1).toString(), style = MaterialTheme.typography.labelLarge) }
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text("آياتها ${s.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            } else {
                itemsIndexed(quran.rub.toList()) { i, start ->
                    val end = if (i + 1 < quran.rub.size) quran.rub[i + 1] else Quran.TOTAL
                    if (i % 8 == 0) {
                        Text(
                            "الجزء " + ordinals[(i / 8).coerceIn(0, 29)],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { go(Screen.QuranRead(start, end)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("الحزب ${i / 4 + 1}، الربع ${i % 4 + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(quran.label(start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Reading a stretch of the Quran that is not tied to a habit, opened from the Quran page. */
@Composable
fun QuranReadScreen(start: Int, end: Int, back: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val quran = remember { Quran.text(ctx) }
    val font = remember { FontFamily(Font(R.font.uthmanic_hafs)) }
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var fontSize by remember { mutableIntStateOf(prefs.getInt("quran_font", 26)) }
    // Each block is a run of ayat within one surah, with the surah's name when it opens there.
    val blocks = remember(start, end) {
        val out = ArrayList<Triple<Int, Int, Boolean>>()
        var i = start
        while (i < end) {
            val s = quran.surahs[quran.surahAt(i)]
            val limit = minOf(end, s.start + s.count, i + 8)
            out.add(Triple(i, limit, i == s.start))
            i = limit
        }
        out
    }
    val list = rememberLazyListState()
    val first by remember { derivedStateOf { list.firstVisibleItemIndex } }
    DisposableEffect(start, end) {
        onDispose {
            val at = blocks.getOrNull(list.firstVisibleItemIndex)?.first ?: start
            prefs.edit().putInt("quran_last", at).apply()
        }
    }
    val at = blocks.getOrNull(first)?.first ?: start
    val juz = quran.rub.indexOfLast { it <= at } / 8 + 1
    val hizb = quran.rub.indexOfLast { it <= at } / 4 + 1

    fun setFont(value: Int) {
        fontSize = value.coerceIn(18, 44)
        prefs.edit().putInt("quran_font", fontSize).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(quran.surahs[quran.surahAt(at)].name)
                        Text(
                            "الجزء $juz، الحزب $hizb",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = {
                    TextButton(onClick = { setFont(fontSize - 2) }) { Text("أ-") }
                    TextButton(onClick = { setFont(fontSize + 2) }) { Text("أ+") }
                }
            )
        }
    ) { pad ->
        LazyColumn(state = list, modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 18.dp)) {
            itemsIndexed(blocks) { _, b ->
                Column(Modifier.fillMaxWidth()) {
                    if (b.third) {
                        val s = quran.surahs[quran.surahAt(b.first)]
                        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "سورة ${s.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        )
                        if (s.basmala) {
                            Text(
                                quran.basmala(), fontFamily = font, fontSize = fontSize.sp, lineHeight = (fontSize * 2f).sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Text(
                        (b.first until b.second).joinToString(" ") { quran.ayat[it] },
                        fontFamily = font, fontSize = fontSize.sp, lineHeight = (fontSize * 2f).sp,
                        textAlign = TextAlign.Justify, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
            item {
                if (end < Quran.TOTAL) {
                    val next = quran.surahs[quran.surahAt(end)]
                    Button(
                        onClick = { go(Screen.QuranRead(end, next.start + next.count)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    ) { Text("تابع القراءة: " + quran.label(end)) }
                }
            }
        }
    }
}
