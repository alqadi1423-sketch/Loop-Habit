@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Adhkar
import app.daab.habits.data.Db

private class Chapter(val category: Int, val part: Int, val name: String)

/** Every chapter of adhkar, to open and read whether or not a habit is tied to it. */
@Composable
fun AdhkarScreen(db: Db, version: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val chapters = remember {
        val all = Adhkar.categories(ctx)
        listOf(
            Chapter(Adhkar.MORNING_EVENING, Adhkar.PART_MORNING, "أذكار الصباح"),
            Chapter(Adhkar.MORNING_EVENING, Adhkar.PART_EVENING, "أذكار المساء")
        ) + all.filter { it.id != Adhkar.MORNING_EVENING }.map { Chapter(it.id, Adhkar.PART_ALL, it.name) }
    }
    val habits = remember(version) { db.habits() }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) chapters else chapters.filter { it.name.contains(query.trim()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("الأذكار") }) },
        bottomBar = { BottomNav(Screen.Adhkar, go) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث في الأبواب") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(shown) { c ->
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                            .clickable {
                                // A chapter that belongs to one of the habits is read as that habit, so it counts.
                                val habit = habits.firstOrNull { it.contentRef == c.category && it.contentPart == c.part }
                                go(if (habit != null) Screen.Reader(habit.id) else Screen.AdhkarRead(c.category, c.part))
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
