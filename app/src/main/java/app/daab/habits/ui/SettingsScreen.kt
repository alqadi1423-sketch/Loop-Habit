@file:OptIn(ExperimentalMaterial3Api::class)

package app.daab.habits.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.data.LoopImport
import java.io.File
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.City
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.logic.Settings
import app.daab.habits.notify.ReminderReceiver
import app.daab.habits.widget.HabitWidget
import org.json.JSONObject

@Composable
private fun SettingRow(title: String, value: String = "", detail: String = "", onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun SettingsScreen(db: Db, changed: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var city by remember { mutableStateOf(PrayerTimes.city(ctx).name) }
    val today = Schedule.today()
    val times = remember(city, today) { PrayerTimes.forDay(ctx, today) }
    var pickCity by remember { mutableStateOf(false) }

    fun refresh() {
        ReminderReceiver.scheduleAll(ctx)
        changed()
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val c = PrayerTimes.city(ctx)
                val settings = JSONObject().put("city", c.name).put("lat", c.lat).put("lng", c.lng)
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.exportJson(settings).toByteArray()) }
                Toast.makeText(ctx, "حُفظت النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "تعذر حفظ النسخة", Toast.LENGTH_LONG).show()
            }
        }
    }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(db.exportCsv().toByteArray()) }
                Toast.makeText(ctx, "حُفظ الجدول", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "تعذر حفظ الجدول", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                db.importJson(text ?: "")?.let { st ->
                    if (st.has("city")) {
                        PrayerTimes.setCity(ctx, City(st.getString("city"), st.getDouble("lat"), st.getDouble("lng")))
                        city = st.getString("city")
                    }
                }
                refresh()
                Toast.makeText(ctx, "استُعيدت النسخة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "الملف ليس نسخة احتياطية صالحة", Toast.LENGTH_LONG).show()
            }
        }
    }

    val loopImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val copy = File(ctx.cacheDir, "loop-import.db")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                val result = LoopImport.run(db, copy, Schedule.today())
                refresh()
                Toast.makeText(ctx, "نُقلت ${result.habits} عادة و${result.days} يومًا من سجلها", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "الملف ليس نسخة احتياطية كاملة من ذلك التطبيق", Toast.LENGTH_LONG).show()
            } finally {
                copy.delete()
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("الإعدادات") }) },
        bottomBar = { BottomNav(Screen.Settings, go) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            SectionLabel("المواقيت")
            Panel(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    for (i in prayerNames.indices) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                prayerNames[i], style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(timeText(times[i]).substringBefore(' '), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Divider()
                SettingRow("المدينة", city, "تُحسب المواقيت داخل الجوال بطريقة أم القرى") { pickCity = true }
            }

            SectionLabel("اليوم والأسبوع")
            Panel(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تمديد اليوم بعد منتصف الليل", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "يبقى اليوم نفسه حتى 3 صباحًا، فما تنجزه بعد منتصف الليل يُسجل لليوم السابق.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = Settings.extendDay, onCheckedChange = {
                        Settings.setExtendDay(ctx, it)
                        refresh()
                    })
                }
                Divider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("أول يوم في الأسبوع", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, label) in listOf(6 to "السبت", 0 to "الأحد", 1 to "الاثنين")) {
                            FilterChip(selected = Settings.weekStart == value, onClick = {
                                Settings.setWeekStart(ctx, value)
                                refresh()
                            }, label = { Text(label) })
                        }
                    }
                }
            }

            SectionLabel("المظهر")
            Panel(padding = 14.dp) {
                Text("ألوان التطبيق", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf(
                        Settings.THEME_SYSTEM to "يتبع الجوال", Settings.THEME_LIGHT to "فاتح", Settings.THEME_DARK to "داكن"
                    )
                    for ((value, label) in themes) {
                        FilterChip(selected = Settings.theme == value, onClick = { Settings.setTheme(ctx, value) }, label = { Text(label) })
                    }
                }
            }

            SectionLabel("البيانات")
            Panel(padding = 0.dp) {
                SettingRow("حفظ نسخة احتياطية", detail = "ملف فيه كل عاداتك وسجلها وإعداداتك") { exporter.launch("daab-backup.json") }
                Divider()
                SettingRow("استعادة نسخة احتياطية", detail = "تستبدل كل ما في التطبيق بما في الملف") {
                    importer.launch(arrayOf("application/json", "*/*"))
                }
                Divider()
                SettingRow("تصدير السجل جدولًا", detail = "ملف يُفتح في برامج الجداول، ولا يُستعاد منه") { csvExporter.launch("daab-log.csv") }
                Divider()
                SettingRow("العادات المؤرشفة") { go(Screen.Archived) }
            }

            SectionLabel("نقل السجل من تطبيق آخر")
            Panel(padding = 0.dp) {
                SettingRow(
                    "استيراد نسخة Loop",
                    detail = "في ذلك التطبيق: الإعدادات ثم «صدر نسخة احتياطية كاملة»، ثم اختر الملف هنا. تُضاف عاداته وسجلها كله، ولا يُحذف شيء مما عندك."
                ) { loopImporter.launch(arrayOf("*/*")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickCity) {
        AlertDialog(
            onDismissRequest = { pickCity = false },
            title = { Text("المدينة") },
            text = {
                LazyColumn {
                    items(PrayerTimes.cities) { c ->
                        TextButton(onClick = {
                            PrayerTimes.setCity(ctx, c)
                            city = c.name
                            pickCity = false
                            ReminderReceiver.scheduleAll(ctx)
                            HabitWidget.updateAll(ctx)
                        }) { Text(c.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCity = false }) { Text("إلغاء") } }
        )
    }
}
