package app.daab.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daab.habits.R
import app.daab.habits.data.MushafData
import app.daab.habits.data.MushafLine
import app.daab.habits.data.QuranText

private val ordinalsShort = listOf(
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
    "الثامن عشر", "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون",
    "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون",
    "التاسع والعشرون", "الثلاثون"
)

fun arabicDigits(n: Int): String = n.toString().map { if (it.isDigit()) '٠' + (it - '0') else it }.joinToString("")

/** The paper colour of the page. */
@Composable
fun mushafPaper(): Color = if (isDark()) Color(0xFF1B2A2E) else Color(0xFFFCF9F1)

/**
 * One page of the mushaf, laid out on its fifteen lines. Full lines are stretched edge to edge
 * as in print; short ones are centred. Words outside [keep] are faded, for a daily portion.
 */
@Composable
fun MushafPage(data: MushafData, quran: QuranText, page: Int, modifier: Modifier = Modifier, keep: IntRange? = null) {
    val font = remember { FontFamily(Font(R.font.uthmanic_hafs)) }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = data.pages[page]
    val first = data.firstAyah(page)
    val surah = quran.surahs[quran.surahAt(first)].name
    val juz = quran.rub.indexOfLast { it <= first } / 8
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Column(modifier.background(mushafPaper()).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("سورة $surah", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = muted)
            Text("الجزء " + ordinalsShort[juz.coerceIn(0, 29)], style = MaterialTheme.typography.labelMedium, color = muted)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat()
            val lineH = maxHeight / 15
            // One size for the page: the longest full line just fits the width, and no line is taller than its slot.
            val base = 20f
            val natural = remember(page, data) {
                lines.map { l ->
                    if (l.kind != MushafLine.TEXT) 0f
                    else measurer.measure(
                        (l.a..l.b).joinToString(" ") { data.words[it] },
                        TextStyle(fontFamily = font, fontSize = base.sp)
                    ).size.width.toFloat()
                }
            }
            val widest = natural.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            // Lines may be pressed by up to 15% to meet the edge, as the calligrapher does by hand.
            val byWidth = base * widthPx * 1.15f / widest
            val byHeight = with(density) { (lineH * 0.46f).toSp().value }
            val size = minOf(byWidth, byHeight)
            val style = TextStyle(fontFamily = font, fontSize = size.sp, color = ink, lineHeight = (size * 1.6f).sp)
            val short = page < 2

            Column(Modifier.fillMaxSize(), verticalArrangement = if (short) Arrangement.Center else Arrangement.Top) {
                lines.forEachIndexed { i, l ->
                    when (l.kind) {
                        MushafLine.HEADER -> Box(Modifier.fillMaxWidth().height(lineH), contentAlignment = Alignment.Center) {
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).height(lineH - 6.dp)
                                    .border(1.5.dp, accent.copy(alpha = 0.7f), shape)
                                    .padding(2.dp).border(0.75.dp, accent.copy(alpha = 0.35f), shape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "سورة " + quran.surahs[(l.a - 1).coerceIn(0, 113)].name,
                                    style = MaterialTheme.typography.titleMedium, color = accent
                                )
                            }
                        }
                        MushafLine.BASMALA -> Box(Modifier.fillMaxWidth().height(lineH), contentAlignment = Alignment.Center) {
                            Text(quran.basmala(), style = style, softWrap = false, maxLines = 1)
                        }
                        else -> {
                            val w = natural[i] * size / base
                            // Every line reaches both edges, as in print: the letters are widened or
                            // pressed a little and the rest goes between the words. Only the opening
                            // pages and a lone short line stay centred.
                            val stretch = !short && (l.b > l.a) && w >= widthPx * 0.45f
                            val squeeze = if (stretch) (widthPx / w).coerceIn(0.85f, 1.08f) else 1f
                            val rowWidth = with(density) { (widthPx / squeeze).toDp() }
                            Box(Modifier.fillMaxWidth().height(lineH), contentAlignment = Alignment.Center) {
                            Row(
                                (if (stretch) Modifier.requiredWidth(rowWidth).graphicsLayer { scaleX = squeeze } else Modifier.fillMaxWidth()),
                                horizontalArrangement = if (stretch) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (w in l.a..l.b) {
                                    val faded = keep != null && data.ayahOf[w] !in keep
                                    Text(
                                        data.words[w],
                                        style = if (faded) style.copy(color = ink.copy(alpha = 0.28f)) else style,
                                        softWrap = false, maxLines = 1
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
        Text(
            arabicDigits(page + 1),
            Modifier.fillMaxWidth().padding(top = 4.dp),
            style = MaterialTheme.typography.labelLarge, color = muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
