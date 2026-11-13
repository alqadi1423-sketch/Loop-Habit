@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.daab.habits.data.Mushaf
import app.daab.habits.data.MushafData
import app.daab.habits.data.Quran
import app.daab.habits.data.QuranText
import kotlinx.coroutines.Dispatchers
import app.daab.habits.audio.QuranAudio
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.withContext

/** The mushaf data, loaded away from the main thread; null until ready. */
@Composable
fun rememberMushaf(): Pair<MushafData, QuranText>? {
    val ctx = LocalContext.current
    val state by produceState<Pair<MushafData, QuranText>?>(null) {
        value = withContext(Dispatchers.IO) { Mushaf.load(ctx) to Quran.text(ctx) }
    }
    return state
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Free reading of the mushaf page by page, opened at an ayah or at a page. */
@Composable
fun MushafScreen(ayah: Int, page: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    val loaded = rememberMushaf()
    if (loaded == null) {
        Loading(Modifier.fillMaxSize())
        return
    }
    val (data, quran) = loaded
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val start = remember { if (page >= 0) page else data.pageOf(ayah) }
    val pager = rememberPagerState(initialPage = start) { Mushaf.PAGES }
    LaunchedEffect(pager.currentPage) { prefs.edit().putInt("mushaf_page", pager.currentPage).apply() }
    val first = data.firstAyah(pager.currentPage)
    val current by QuranAudio.current.collectAsState()
    val word by QuranAudio.word.collectAsState()
    // The page follows the recitation.
    LaunchedEffect(current) {
        if (current >= 0) {
            val p = data.pageOf(current)
            if (p != pager.currentPage) pager.animateScrollToPage(p)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("سورة " + quran.surahs[quran.surahAt(first)].name)
                        Text(
                            juzName(quran, first) + "، صفحة " + arabicDigits(pager.currentPage + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = { AudioMenu(quran, first) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mushafPaper())
            )
        },
        bottomBar = { AudioBar(quran, { data.firstAyah(pager.currentPage) }, Quran.TOTAL) },
        containerColor = mushafPaper()
    ) { pad ->
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.padding(pad).fillMaxSize()) { p ->
            MushafPage(data, quran, p, Modifier.fillMaxSize(), highlight = current, word = word, header = false)
        }
    }
}
