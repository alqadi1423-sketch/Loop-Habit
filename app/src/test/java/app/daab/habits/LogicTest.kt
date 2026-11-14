package app.daab.habits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.daab.habits.data.Backup
import app.daab.habits.data.Counter
import app.daab.habits.data.Db
import app.daab.habits.data.Group
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import app.daab.habits.data.Quran
import app.daab.habits.logic.Hijri
import app.daab.habits.logic.Schedule
import app.daab.habits.logic.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The rules the app's numbers rest on. A release is only published when these pass. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LogicTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: Db

    @Before
    fun open() {
        Db.resetForTests()
        ctx.deleteDatabase("daab.db")
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.read(ctx)
        db = Db.get(ctx)
    }

    @After
    fun close() = Db.resetForTests()

    private val day0 = LocalDate.of(2026, 1, 3).toEpochDay()

    // Strength and runs

    @Test
    fun strengthRisesWhenDoneAndFallsWhenMissed() {
        val h = Habit(id = 1, created = day0)
        val all = (day0 until day0 + 60).associateWith { Mark.DONE }
        val none = emptyMap<Long, Int>()
        val high = Schedule.score(h, emptySet(), all, day0 + 59)
        val low = Schedule.score(h, emptySet(), none, day0 + 59)
        assertTrue("done every day: $high", high > 0.9)
        assertEquals(0.0, low, 1e-9)
    }

    @Test
    fun excusedDaysAndAnOpenTodayDoNotHurt() {
        val h = Habit(id = 1, created = day0)
        val done = (day0 until day0 + 10).associateWith { Mark.DONE }
        val before = Schedule.score(h, emptySet(), done, day0 + 9)
        val excused = done + ((day0 + 10) to Mark.EXCUSED)
        assertEquals(before, Schedule.score(h, emptySet(), excused, day0 + 10), 1e-9)
        // Today not yet done leaves the score where it was.
        assertEquals(before, Schedule.score(h, emptySet(), done, day0 + 10), 1e-9)
    }

    @Test
    fun scoreSeriesEndsAtTheScore() {
        val h = Habit(id = 1, created = day0, kind = Kind.WEEKDAYS, weekdays = 0b0101010)
        val entries = (day0 until day0 + 40 step 2).associateWith { Mark.DONE }
        val today = day0 + 40
        val series = Schedule.scoreSeries(h, emptySet(), entries, today, emptyMap())
        assertEquals(Schedule.score(h, emptySet(), entries, today), series.last(), 1e-9)
    }

    @Test
    fun runsSkipExcusedDays() {
        val h = Habit(id = 1, created = day0)
        val e = mapOf(day0 to Mark.DONE, day0 + 1 to Mark.EXCUSED, day0 + 2 to Mark.DONE, day0 + 4 to Mark.DONE)
        val runs = Schedule.streaks(h, emptySet(), e, day0 + 5)
        assertEquals(2, runs.first().third)
        assertEquals(1, runs[1].third)
    }

    @Test
    fun timesPerWeekStopsBeingDueOnceReached() {
        val h = Habit(id = 1, created = day0, kind = Kind.PER_WEEK, perWeek = 2)
        val start = Schedule.weekStart(day0 + 7)
        val e = mapOf(start to Mark.DONE, start + 1 to Mark.DONE)
        assertFalse(Schedule.isDue(h, emptySet(), e, start + 2))
        assertTrue(Schedule.isDue(h, emptySet(), e, start + 1))
    }

    // Marks

    @Test
    fun markingADayBeforeTheStartMovesTheStartBack() {
        val id = db.save(Habit(name = "مشي", created = day0))
        db.setMark(id, day0 - 3, Mark.DONE)
        assertEquals(day0 - 3, db.habit(id)!!.created)
        // Clearing it again does not move the start forward.
        db.setMark(id, day0 - 3, Mark.NONE)
        assertEquals(day0 - 3, db.habit(id)!!.created)
    }

    @Test
    fun countedHabitIsDoneAtItsTarget() {
        val id = db.save(Habit(name = "ماء", created = day0, target = 3))
        val h = db.habit(id)!!
        repeat(2) { db.tap(h, day0) }
        assertEquals(null, db.entries(id)[day0])
        db.tap(h, day0)
        assertEquals(Mark.DONE, db.entries(id)[day0])
    }

    @Test
    fun deletingAHabitRemovesAllItsRecord() {
        val id = db.save(Habit(name = "قراءة", created = day0))
        db.setMark(id, day0, Mark.DONE)
        db.setPartial(id, day0 + 1, 400)
        db.delete(id)
        assertEquals(null, db.habit(id))
        assertTrue(db.entries(id).isEmpty())
        assertTrue(db.partials(id).isEmpty())
    }

    // Quran portions

    @Test
    fun quranPortionCarriesOnFromWhereTheLastOneEnded() {
        val q = Quran.text(ctx)
        val id = db.save(Habit(name = "ورد", created = day0, contentRef = Quran.CONTENT, contentUnit = Quran.UNIT_RUB, contentAmount = 1))
        val h = db.habit(id)!!
        val first = Quran.today(ctx, db, h, day0)
        assertEquals(0, first.start)
        assertEquals(q.rub[1], first.end)
        db.setMark(id, day0, Mark.DONE)
        val second = Quran.today(ctx, db, h, day0 + 1)
        assertEquals(q.rub[1], second.start)
        assertEquals(q.rub[2], second.end)
    }

    // Backups

    @Test
    fun backupBringsBackEverythingIncludingSettings() {
        val g = db.saveGroup(Group(name = "الصلوات"))
        val c = db.saveCounter(Counter(name = "الصيام", groupId = g))
        val id = db.save(Habit(name = "الفجر", created = day0, groupId = g, counterId = c, counterEffect = 3, reminder = 300))
        db.setMark(id, day0, Mark.DONE)
        db.setMark(id, day0 + 1, Mark.EXCUSED)
        Settings.setTheme(ctx, Settings.THEME_DARK)
        Settings.setExtendDay(ctx, true)
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("city", "جدة").putFloat("lat", 21.5f).putInt("reciter_id", 9).commit()

        val text = Backup.json(ctx, db)

        // Change everything, then restore.
        db.delete(id)
        db.deleteGroup(g)
        Settings.setTheme(ctx, Settings.THEME_LIGHT)
        Settings.setExtendDay(ctx, false)
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("reciter_id", 1).commit()

        Backup.restore(ctx, db, text)

        val h = db.habits().single()
        assertEquals("الفجر", h.name)
        assertEquals(3, h.counterEffect)
        assertEquals(300, h.reminder)
        assertEquals(mapOf(day0 to Mark.DONE, day0 + 1 to Mark.EXCUSED), db.entries(h.id))
        assertEquals("الصلوات", db.groups().single().name)
        assertEquals(h.groupId, db.groups().single().id)
        assertEquals("الصيام", db.counters().single().name)
        assertEquals(Settings.THEME_DARK, Settings.theme)
        assertTrue(Settings.extendDay)
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertEquals("جدة", p.getString("city", ""))
        assertEquals(21.5f, p.getFloat("lat", 0f), 1e-6f)
        assertEquals(9, p.getInt("reciter_id", 0))
        // A copy of what was there before the restore is kept.
        assertTrue(Backup.safetyCopies(ctx).any { it.name.startsWith("before-restore") })
    }

    @Test
    fun aBrokenBackupChangesNothing() {
        val id = db.save(Habit(name = "صدقة", created = day0))
        db.setMark(id, day0, Mark.DONE)
        val broken = """{"app":"daab","habits":[{"name":"x"}]}"""
        try {
            Backup.restore(ctx, db, broken)
            fail("a broken file should be refused")
        } catch (e: Exception) {
        }
        assertEquals("صدقة", db.habits().single().name)
        assertEquals(Mark.DONE, db.entries(id)[day0])
    }

    @Test
    fun oldBackupsWithOnlyTheCityStillLoad() {
        val old = """{"app":"daab","settings":{"city":"أبها","lat":18.2,"lng":42.5},"habits":[]}"""
        Backup.restore(ctx, db, old)
        assertEquals("أبها", ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("city", ""))
    }

    // Fasting days

    @Test
    fun arafahTasuaAndAshuraOnceEach() {
        val days = Hijri.arafahAndAshura(day0)
        assertEquals(3, days.size)
        assertNotNull(days.minOrNull())
        assertTrue(days.max() - days.min() < 360)
    }
}
