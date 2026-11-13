package app.daab.habits

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.data.Mushaf
import app.daab.habits.data.Quran
import app.daab.habits.logic.Schedule
import app.daab.habits.ui.AdhkarScreen
import app.daab.habits.ui.CountersScreen
import app.daab.habits.ui.DaabTheme
import app.daab.habits.ui.DetailScreen
import app.daab.habits.ui.EditScreen
import app.daab.habits.ui.HomeScreen
import app.daab.habits.ui.MushafScreen
import app.daab.habits.ui.QuranScreen
import app.daab.habits.ui.QuranTab
import app.daab.habits.ui.ReaderScreen
import app.daab.habits.ui.SettingsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws every screen with sample data and saves pictures of them, to look at after each build. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-w411dp-h914dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: Db
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        db = Db.get(ctx)
        if (db.habits().isNotEmpty()) return
        val today = Schedule.today()
        db.addPrayerGroup(today)
        db.addAdhkarGroup(today)
        db.save(Habit(name = "ورد القرآن", contentRef = Quran.CONTENT, contentUnit = Quran.UNIT_HIZB, contentAmount = 1, created = today - 60, color = 0))
        db.save(Habit(name = "شرب الماء", target = 8, created = today - 60))
        db.save(Habit(name = "صيام م", kind = Kind.WEEKDAYS, weekdays = (1 shl 1) or (1 shl 4), created = today - 60))
        for (h in db.habits()) for (d in 1..45) {
            if ((d * 7 + h.id.toInt()) % 4 != 0) db.setEntry(h.id, today - d, Mark.DONE)
        }
        Mushaf.load(ctx)
    }

    private fun shot(name: String, content: @Composable () -> Unit, waitFor: String? = null) {
        rule.setContent { DaabTheme { content() } }
        if (waitFor != null) rule.waitUntil(30_000) {
            rule.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun quranId() = db.habits().first { it.contentRef == Quran.CONTENT }.id
    private fun adhkarId() = db.habits().first { it.contentRef > 0 }.id

    @Test fun home() = shot("01-home", { HomeScreen(db, 0, {}, {}) })
    @Test @Config(qualifiers = "+night") fun homeDark() = shot("02-home-dark", { HomeScreen(db, 0, {}, {}) })
    @Test fun detail() = shot("03-detail", { DetailScreen(db, quranId(), 0, {}, {}) })
    @Test fun edit() = shot("04-edit", { EditScreen(db, 0, false, {}, {}) })
    @Test fun settings() = shot("05-settings", { SettingsScreen(db, {}, {}) })
    @Test fun counters() = shot("06-counters", { CountersScreen(db, 0, {}, {}) })
    @Test fun adhkar() = shot("07-adhkar", { AdhkarScreen(db, 0, {}) })
    @Test fun reader() = shot("08-reader", { ReaderScreen(db, adhkarId(), {}, {}) })
    @Test fun quranTab() = shot("09-quran-tab", { QuranTab(db, 0, {}) })
    @Test fun wird() = shot("10-wird", { QuranScreen(db, quranId(), {}, {}) }, "أتممت")
    @Test fun page1() = shot("11-page-001", { MushafScreen(0, 0) {} }, "الجزء")
    @Test fun page2() = shot("12-page-002", { MushafScreen(0, 1) {} }, "الجزء")
    @Test fun page7() = shot("13-page-007", { MushafScreen(0, 6) {} }, "الجزء")
    @Test fun page76() = shot("14-page-076", { MushafScreen(0, 75) {} }, "الجزء")
    @Test fun page77() = shot("15-page-077", { MushafScreen(0, 76) {} }, "الجزء")
    @Test fun page262() = shot("16-page-262", { MushafScreen(0, 261) {} }, "الجزء")
    @Test fun page604() = shot("17-page-604", { MushafScreen(0, 603) {} }, "الجزء")
    @Test @Config(qualifiers = "+night") fun page7Dark() = shot("18-page-007-dark", { MushafScreen(0, 6) {} }, "الجزء")
}
