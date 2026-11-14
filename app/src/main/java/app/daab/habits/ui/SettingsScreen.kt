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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import app.daab.habits.data.Backup
import app.daab.habits.data.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import app.daab.habits.data.Mushaf
import app.daab.habits.audio.QuranAudio
import app.daab.habits.data.LoopImport
import java.io.File
import app.daab.habits.data.prayerNames
import app.daab.habits.logic.City
import app.daab.habits.logic.Hijri
import app.daab.habits.logic.Locate
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
    var timesVersion by remember { mutableStateOf(0) }
    val times = remember(city, today, timesVersion) { PrayerTimes.forDay(ctx, today) }
    var adjust by remember { mutableStateOf(-1) }
    var pickMethod by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var pickCity by remember { mutableStateOf(false) }

    fun refresh() {
        ReminderReceiver.scheduleAll(ctx)
        changed()
    }

    fun prayerChanged() {
        timesVersion++
        ReminderReceiver.scheduleAll(ctx)
        HabitWidget.updateAll(ctx)
        changed()
    }

    fun useLocation() {
        locating = true
        Locate.once(ctx) { loc ->
            locating = false
            if (loc == null) {
                Toast.makeText(ctx, "تعذر معرفة موقعك. فعّل الموقع في الجوال أو اختر مدينة.", Toast.LENGTH_LONG).show()
            } else {
                val name = "موقعي (" + "%.2f".format(java.util.Locale.US, loc.latitude) + "، " + "%.2f".format(java.util.Locale.US, loc.longitude) + ")"
                PrayerTimes.setCity(ctx, City(name, loc.latitude, loc.longitude))
                city = name
                pickCity = false
                prayerChanged()
            }
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useLocation()
        else Toast.makeText(ctx, "بلا إذن الموقع اختر مدينتك من القائمة", Toast.LENGTH_LONG).show()
    }
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var restoreCopy by remember { mutableStateOf<File?>(null) }
    var showCopies by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** Replaces everything with a backup, after a copy of the current data is kept on the phone. */
    fun restore(read: () -> String?) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Backup.restore(ctx, db, read() ?: "")
                    true
                } catch (e: Exception) {
                    false
                }
            }
            city = PrayerTimes.city(ctx).name
            refresh()
            Toast.makeText(
                ctx, if (ok) "استُعيدت النسخة، وحُفظت نسخة مما كان قبلها" else "الملف ليس نسخة احتياطية صالحة، ولم يتغير شيء",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.json(ctx, db).toByteArray()) }
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
        if (uri != null) restoreFrom = uri
    }

    var folderSet by remember { mutableStateOf(Backup.folder(ctx) != null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
            }
            Backup.setFolder(ctx, tree)
            folderSet = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Backup.writeToFolder(ctx, db) }
                Toast.makeText(ctx, if (ok) "حُفظت نسخة في المجلد، وتُحدّث كل يوم" else "تعذرت الكتابة في هذا المجلد", Toast.LENGTH_LONG).show()
            }
        }
    }

    val loopImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val copy = File(ctx.cacheDir, "loop-import.db")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                Backup.safetyCopy(ctx, db, "before-loop-import")
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
                        Column(
                            Modifier.weight(1f).clickable { adjust = i }.padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                prayerNames[i], style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(timeText(times[i]).substringBefore(' '), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Divider()
                SettingRow("المكان", city, "تُحسب المواقيت داخل الجوال بلا إنترنت. اضغط وقت أي صلاة لتقديمه أو تأخيره دقائق") { pickCity = true }
                Divider()
                SettingRow("طريقة الحساب", PrayerTimes.methods[PrayerTimes.method(ctx)].name, "أم القرى للسعودية، وغيرها لبلدان أخرى") { pickMethod = true }
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("العصر على المذهب الحنفي", style = MaterialTheme.typography.bodyLarge)
                        Text("حين يصير ظل الشيء مثليه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var hanafi by remember { mutableStateOf(PrayerTimes.hanafi(ctx)) }
                    Switch(checked = hanafi, onCheckedChange = {
                        hanafi = it
                        PrayerTimes.setHanafi(ctx, it)
                        prayerChanged()
                    })
                }
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

            SectionLabel("التاريخ الهجري")
            Panel(padding = 14.dp) {
                Text(Hijri.text(today), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "بتقويم أم القرى. إن ثبتت رؤية الهلال في يوم آخر فعدّل التاريخ هنا، فيتبعه عرض التاريخ وأيام الصيام ووقت العشاء في رمضان.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in listOf(-2 to "−٢", -1 to "−١", 0 to "بلا تعديل", 1 to "+١", 2 to "+٢")) {
                        FilterChip(selected = Settings.hijriOffset == value, onClick = {
                            Settings.setHijriOffset(ctx, value)
                            refresh()
                        }, label = { Text(label) })
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

            SectionLabel("المصحف")
            Panel(padding = 14.dp) {
                var edition by remember { mutableStateOf(Mushaf.edition(ctx)) }
                Text("الطبعة", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "الطبعتان من مجمع الملك فهد، وتتفقان في بداية كل صفحة ونهايتها، وتختلفان في توزيع الأسطر في بعض الصفحات. اختر التي تطابق مصحفك الورقي.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in listOf(Mushaf.QCF2 to "الإصدار الثاني", Mushaf.QCF4 to "طبعة 1441هـ")) {
                        FilterChip(selected = edition == value, onClick = {
                            Mushaf.setEdition(ctx, value)
                            edition = value
                        }, label = { Text(label) })
                    }
                }
            }

            Panel(padding = 14.dp, modifier = Modifier.padding(top = 8.dp)) {
                var reciter by remember { mutableStateOf(QuranAudio.reciter(ctx).id) }
                var bytes by remember { mutableStateOf(0L) }
                LaunchedEffect(Unit) { bytes = withContext(Dispatchers.IO) { QuranAudio.downloadedBytes(ctx) } }
                Text("القارئ", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in QuranAudio.reciters) {
                        FilterChip(selected = reciter == r.id, onClick = {
                            QuranAudio.setReciter(ctx, r.id)
                            reciter = r.id
                        }, label = { Text(r.name) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "التلاوات المنزّلة: " + "%.1f".format(bytes / 1048576.0) + " ميجا",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bytes > 0) TextButton(onClick = {
                        QuranAudio.deleteDownloads(ctx)
                        bytes = 0
                    }) { Text("حذفها") }
                }
            }

            SectionLabel("العدّادات")
            Panel(padding = 0.dp) {
                SettingRow("إدارة العدّادات", detail = "إنشاء عدّاد وربط العادات به واختيار مجموعته") { go(Screen.Counters) }
            }

            SectionLabel("البيانات")
            Panel(padding = 0.dp) {
                SettingRow("حفظ نسخة احتياطية", detail = "ملف فيه كل عاداتك وسجلها وإعداداتك") { exporter.launch("daab-backup.json") }
                Divider()
                SettingRow("استعادة نسخة احتياطية", detail = "تستبدل كل ما في التطبيق بما في الملف") {
                    importer.launch(arrayOf("application/json", "*/*"))
                }
                Divider()
                SettingRow(
                    "نسخة يومية إلى مجلد",
                    value = if (folderSet) "مفعّلة" else "",
                    detail = if (folderSet) "يُحدّث ملف daab-backup-auto.json في المجلد كل يوم. اضغط لاختيار مجلد آخر."
                    else "اختر مجلدًا، في Google Drive مثلًا، فتُحفظ فيه نسخة كل يوم وتبقى لو ضاع الجوال"
                ) { folderPicker.launch(null) }
                if (folderSet) {
                    TextButton(onClick = {
                        Backup.setFolder(ctx, null)
                        folderSet = false
                    }, modifier = Modifier.padding(horizontal = 6.dp)) { Text("إيقاف النسخة اليومية إلى المجلد") }
                }
                Divider()
                SettingRow(
                    "النسخ المحفوظة على الجوال",
                    detail = "نسخة كل يوم، ونسخة قبل كل استعادة أو استيراد، فتتراجع عنها إن احتجت"
                ) { showCopies = true }
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

    restoreFrom?.let { uri ->
        ConfirmRestore(
            onConfirm = {
                restoreFrom = null
                restore { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
            },
            onDismiss = { restoreFrom = null }
        )
    }

    restoreCopy?.let { f ->
        ConfirmRestore(
            onConfirm = {
                restoreCopy = null
                showCopies = false
                restore { f.readText() }
            },
            onDismiss = { restoreCopy = null }
        )
    }

    if (showCopies) {
        val copies = remember { Backup.safetyCopies(ctx) }
        val format = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm") }
        AlertDialog(
            onDismissRequest = { showCopies = false },
            title = { Text("النسخ المحفوظة على الجوال") },
            text = {
                if (copies.isEmpty()) Text("لا نسخ بعد. تُحفظ هنا نسخة قبل كل استعادة أو استيراد.")
                else LazyColumn {
                    items(copies) { f ->
                        val label = when {
                            f.name.startsWith("before-restore") -> "قبل استعادة"
                            f.name.startsWith("before-loop") -> "قبل استيراد Loop"
                            f.name.startsWith("auto") -> "نسخة يومية"
                            else -> "نسخة"
                        }
                        val time = Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).format(format)
                        Row(
                            Modifier.fillMaxWidth().clickable { restoreCopy = f }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text("\u200E" + time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("استرجاع", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCopies = false }) { Text("إغلاق") } }
        )
    }

    if (adjust >= 0) {
        val i = adjust
        var minutes by remember(i) { mutableStateOf(PrayerTimes.adjustment(ctx, i)) }
        AlertDialog(
            onDismissRequest = { adjust = -1 },
            title = { Text(prayerNames[i]) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("قدّم الوقت أو أخّره ليوافق مسجدك.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        TextButton(onClick = { if (minutes > -30) minutes-- }) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                        Text(
                            if (minutes == 0) "بلا تعديل" else "\u200E" + (if (minutes > 0) "+" else "") + minutes + " د",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TextButton(onClick = { if (minutes < 30) minutes++ }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    PrayerTimes.setAdjustment(ctx, i, minutes)
                    adjust = -1
                    prayerChanged()
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { adjust = -1 }) { Text("إلغاء") } }
        )
    }

    if (pickMethod) {
        AlertDialog(
            onDismissRequest = { pickMethod = false },
            title = { Text("طريقة الحساب") },
            text = {
                LazyColumn {
                    items(PrayerTimes.methods.indices.toList()) { i ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                PrayerTimes.setMethod(ctx, i)
                                pickMethod = false
                                prayerChanged()
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = PrayerTimes.method(ctx) == i, onClick = null)
                            Text(PrayerTimes.methods[i].name, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickMethod = false }) { Text("إغلاق") } }
        )
    }

    if (pickCity) {
        AlertDialog(
            onDismissRequest = { pickCity = false },
            title = { Text("المكان") },
            text = {
                LazyColumn {
                    item {
                        TextButton(onClick = {
                            val has = androidx.core.content.ContextCompat.checkSelfPermission(
                                ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (has) useLocation() else askLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        }, enabled = !locating) { Text(if (locating) "جارٍ تحديد موقعك…" else "📍 موقعي الحالي") }
                    }
                    items(PrayerTimes.cities) { c ->
                        TextButton(onClick = {
                            PrayerTimes.setCity(ctx, c)
                            city = c.name
                            pickCity = false
                            prayerChanged()
                        }) { Text(c.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCity = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ConfirmRestore(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استبدال كل البيانات؟") },
        text = {
            Text("ستحل النسخة محل كل عاداتك وسجلها وإعداداتك الحالية. تُحفظ نسخة مما عندك الآن على الجوال قبل ذلك، فتستطيع الرجوع إليها من «النسخ المحفوظة على الجوال».")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("استبدال") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
