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
    val last = remember(version) { prefs.getInt("mushaf_page", -1) }
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
                                Text("صفحة " + arabicDigits(last + 1), style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { go(Screen.Mushaf(0, last)) }) { Text("تابع") }
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
                        Modifier.fillMaxWidth().clickable { go(Screen.Mushaf(s.start)) }.padding(vertical = 8.dp),
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
                    if (i % 8 == 0) {
                        Text(
                            "الجزء " + ordinals[(i / 8).coerceIn(0, 29)],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { go(Screen.Mushaf(start)) }.padding(vertical = 10.dp),
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

