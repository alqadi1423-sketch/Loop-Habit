package app.daab.habits

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.daab.habits.audio.QuranAudio
import app.daab.habits.audio.QuranAudioService
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.logic.Schedule
import app.daab.habits.ui.DaabTheme
import app.daab.habits.ui.DetailScreen
import app.daab.habits.ui.HomeScreen
import app.daab.habits.ui.MushafScreen
import app.daab.habits.ui.QuranScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pictures of screens drawn by real Android, saved where the build can collect them. */
@RunWith(AndroidJUnit4::class)
class DeviceScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: Db

    @Before
    fun seed() {
        db = Db.get(ctx)
        if (db.habits().isNotEmpty()) return
        val today = Schedule.today()
        db.addPrayerGroup(today)
        db.addAdhkarGroup(today)
        db.save(Habit(name = "ورد القرآن", contentRef = Quran.CONTENT, contentUnit = Quran.UNIT_HIZB, contentAmount = 1, created = today - 60))
        db.save(Habit(name = "شرب الماء", target = 8, created = today - 60))
        db.save(Habit(name = "صيام م", kind = Kind.WEEKDAYS, weekdays = (1 shl 1) or (1 shl 4), created = today - 60))
        for (h in db.habits()) for (d in 1..45) if ((d * 7 + h.id.toInt()) % 4 != 0) db.setEntry(h.id, today - d, Mark.DONE)
    }

    private fun shot(name: String, waitFor: String?, content: @Composable () -> Unit, before: () -> Unit = {}) {
        rule.setContent { DaabTheme { content() } }
        if (waitFor != null) rule.waitUntil(30_000) {
            rule.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        before()
        Thread.sleep(1500)
        rule.waitForIdle()
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Test fun screens() {
        shot("d1-home", null, { HomeScreen(db, 0, {}, {}) })
    }

    @Test fun detail() {
        val id = db.habits().first { it.contentRef == Quran.CONTENT }.id
        shot("d2-detail", null, { DetailScreen(db, id, 0, {}, {}) })
    }

    @Test fun page1() = shot("d3-page-001", "الجزء", { MushafScreen(0, 0) {} })
    @Test fun page7() = shot("d4-page-007", "الجزء", { MushafScreen(0, 6) {} })
    @Test fun page50() = shot("d5-page-050", "الجزء", { MushafScreen(0, 49) {} })

    /** The page while al-Fatihah is recited: the word being read should be lit. */
    @Test fun reciting() = shot("d6-reciting", "الجزء", { MushafScreen(0, 0) {} }) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { QuranAudioService.play(ctx, 1, 7) }
        val start = System.currentTimeMillis()
        while (QuranAudio.word.value < 6 && System.currentTimeMillis() - start < 30_000) Thread.sleep(100)
    }

    @Test fun wird() {
        val id = db.habits().first { it.contentRef == Quran.CONTENT }.id
        shot("d7-wird", "أتممت", { QuranScreen(db, id, {}, {}) })
        InstrumentationRegistry.getInstrumentation().runOnMainSync { QuranAudioService.stop(ctx) }
    }
}
