package app.daab.habits.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.audio.QuranAudio
import app.daab.habits.audio.QuranAudioService
import app.daab.habits.data.QuranText

/** The recitation controls under a mushaf page, and download progress when one runs. */
@Composable
fun AudioBar(quran: QuranText, startAt: () -> Int, end: Int) {
    val ctx = LocalContext.current
    val current by QuranAudio.current.collectAsState()
    val playing by QuranAudio.playing.collectAsState()
    val download by QuranAudio.download.collectAsState()
    val result by QuranAudio.lastResult.collectAsState()
    LaunchedEffect(result) {
        result?.let {
            val text = if (it.failed == 0) "اكتمل تنزيل ${it.title}" else "نُزّل ${it.title} وتعذر ${it.failed} ملفا، أعد التنزيل لإكمالها"
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
            QuranAudio.lastResult.value = null
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            download?.let { d ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تنزيل ${d.title}: ${d.done} من ${d.total}", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { d.done.toFloat() / d.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                    TextButton(onClick = { QuranAudio.cancel() }) { Text("إلغاء") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (current >= 0) QuranAudioService.toggle(ctx) else QuranAudioService.play(ctx, startAt(), end)
                }) { Icon(if (playing) PauseIcon else Icons.Filled.PlayArrow, if (playing) "إيقاف مؤقت" else "استماع") }
                Column(Modifier.weight(1f)) {
                    Text(QuranAudio.reciter(ctx).name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (current >= 0) quran.label(current) else "استمع من أول الصفحة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (current >= 0) IconButton(onClick = { QuranAudioService.stop(ctx) }) { Icon(Icons.Filled.Close, "إنهاء التلاوة") }
            }
        }
    }
}

/** The recitation menu: choose the reciter, and download a surah or a part to listen without internet. */
@Composable
fun AudioMenu(quran: QuranText, ayah: Int) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(DownloadIcon, "التلاوة والتنزيل") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        val s = quran.surahAt(ayah)
        val surah = quran.surahs[s]
        val rub = quran.rub.indexOfLast { it <= ayah }
        val juz = rub / 8
        DropdownMenuItem(text = { Text("القارئ: " + QuranAudio.reciter(ctx).name) }, onClick = { open = false; pick = true })
        DropdownMenuItem(text = { Text("تنزيل سورة ${surah.name}") }, onClick = {
            open = false
            QuranAudio.downloadAyat(ctx, "سورة ${surah.name}", withBasmala((1..surah.count).map { s + 1 to it }))
        })
        DropdownMenuItem(text = { Text("تنزيل الجزء ${juz + 1}") }, onClick = {
            open = false
            val from = quran.rub[juz * 8]
            val to = if (juz * 8 + 8 < quran.rub.size) quran.rub[juz * 8 + 8] else quran.ayat.size
            QuranAudio.downloadAyat(ctx, "الجزء ${juz + 1}", withBasmala((from until to).map {
                val si = quran.surahAt(it)
                si + 1 to it - quran.surahs[si].start + 1
            }))
        })
    }
    if (pick) {
        var chosen by remember { mutableStateOf(QuranAudio.reciter(ctx).folder) }
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text("القارئ") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for (r in QuranAudio.reciters) {
                        Row(Modifier.fillMaxWidth().clickable { chosen = r.folder }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen == r.folder, onClick = { chosen = r.folder })
                            Text(r.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    QuranAudio.setReciter(ctx, chosen)
                    pick = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("إلغاء") } }
        )
    }
}

/** The basmala file is played before every surah, so it is downloaded with them. */
private fun withBasmala(list: List<Pair<Int, Int>>) = listOf(1 to 1) + list
