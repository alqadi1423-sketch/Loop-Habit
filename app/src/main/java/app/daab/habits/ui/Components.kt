package app.daab.habits.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import app.daab.habits.data.Quran
import app.daab.habits.data.QuranText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Data read from the database for a screen. The first read happens at once, so the screen never
 * opens empty; later reads, when [keys] change, run off the main thread and the last result stays
 * on screen until the new one is ready.
 */
@Composable
fun <T> rememberLoaded(vararg keys: Any?, load: () -> T): T {
    val state = remember { mutableStateOf(load()) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(*keys) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        state.value = withContext(Dispatchers.IO) { load() }
    }
    return state.value
}

/** The Quran text, parsed off the main thread the first time; null until ready. */
@Composable
fun rememberQuranText(): QuranText? {
    val ctx = LocalContext.current
    return produceState<QuranText?>(Quran.cached()) {
        if (value == null) value = withContext(Dispatchers.IO) { Quran.text(ctx) }
    }.value
}

/** A rounded panel that holds one block of content. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

/** A ring whose arc shows a share from 0 to 1, with optional content in the middle. */
@Composable
fun Ring(share: Float, color: Color, size: Dp, stroke: Dp = 3.dp, content: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(
                color, -90f, 360f * share.coerceIn(0f, 1f), false, Offset(inset, inset), arc,
                style = Stroke(w, cap = StrokeCap.Round)
            )
        }
        content()
    }
}
