@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule

@Composable
fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var city by remember { mutableStateOf(PrayerTimes.city(ctx).name) }
    val times = remember(city) { PrayerTimes.forDay(ctx, Schedule.today()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المدينة ومواقيت الصلاة") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(
                "مواقيت اليوم في $city",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in prayerNames.indices) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(prayerNames[i], Modifier.weight(1f))
                        Text(timeText(times[i]))
                    }
                }
            }
            Text(
                "تُحسب داخل الجوال بتقويم أم القرى وبلا إنترنت. قارنها بمواقيت مسجدك.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            for (c in PrayerTimes.cities) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        PrayerTimes.setCity(ctx, c)
                        city = c.name
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = city == c.name, onClick = {
                        PrayerTimes.setCity(ctx, c)
                        city = c.name
                    })
                    Text(c.name)
                }
            }
        }
    }
}
