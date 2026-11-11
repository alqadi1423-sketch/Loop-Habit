package app.daab.habits.logic

import app.daab.habits.data.Anchor
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import kotlin.math.pow

object Phase {
    const val BEFORE = 0
    const val OPEN = 1
    const val CLOSED = 2
}

object Schedule {

    fun today(): Long = LocalDate.now().toEpochDay()

    fun nowMinutes(): Int = LocalTime.now().toSecondOfDay() / 60

    fun hasWindow(h: Habit): Boolean = h.startKind != Anchor.NONE || h.endKind != Anchor.NONE

    private fun resolve(kind: Int, value: Int, times: IntArray, fallback: Int): Int = when (kind) {
        Anchor.CLOCK -> value
        Anchor.PRAYER -> times[value.coerceIn(0, 5)]
        else -> fallback
    }

    /** Start and end of the habit's time window today, in minutes after midnight. */
    fun window(h: Habit, times: IntArray): Pair<Int, Int> {
        val start = resolve(h.startKind, h.startValue, times, 0)
        var end = resolve(h.endKind, h.endValue, times, 1440)
        if (end <= start) end = 1440
        return start to end
    }

    fun phase(h: Habit, times: IntArray, now: Int): Int {
        val (start, end) = window(h, times)
        return when {
            now < start -> Phase.BEFORE
            now >= end -> Phase.CLOSED
            else -> Phase.OPEN
        }
    }

    /** Sunday = 0 ... Saturday = 6 */
    fun weekdayIndex(day: Long): Int = LocalDate.ofEpochDay(day).dayOfWeek.value % 7

    fun weekStart(day: Long): Long =
        LocalDate.ofEpochDay(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toEpochDay()

    /** Whether the habit is planned for this calendar day, regardless of what was logged. */
    fun isScheduled(h: Habit, dates: Set<Long>, day: Long): Boolean = when (h.kind) {
        Kind.DAILY -> true
        Kind.WEEKDAYS -> (h.weekdays shr weekdayIndex(day)) and 1 == 1
        Kind.DATES -> day in dates
        else -> true
    }

    fun doneInWeek(entries: Map<Long, Int>, day: Long): Int {
        val start = weekStart(day)
        var n = 0
        for (d in start..start + 6) if (entries[d] == Mark.DONE) n++
        return n
    }

    /** Whether the habit should be shown as something to do on this day. */
    fun isDue(h: Habit, dates: Set<Long>, entries: Map<Long, Int>, day: Long): Boolean {
        if (day < h.created) return false
        if (h.kind == Kind.PER_WEEK) {
            return entries[day] == Mark.DONE || doneInWeek(entries, day) < h.perWeek
        }
        return isScheduled(h, dates, day)
    }

    /**
     * Strength from 0 to 1. Each planned day pulls the score toward 1 when done and toward 0
     * when missed, with older days weighing less. A reading left unfinished counts by the
     * share that was read. Excused days are skipped. The current day
     * only counts once it is done, so an unfinished today never lowers the score.
     */
    fun score(
        h: Habit, dates: Set<Long>, entries: Map<Long, Int>, today: Long,
        partials: Map<Long, Int> = emptyMap()
    ): Double {
        var s = 0.0
        if (h.kind == Kind.PER_WEEK) {
            val keep = 0.5.pow(1.0 / 4.0)
            var w = weekStart(h.created)
            val thisWeek = weekStart(today)
            while (w <= thisWeek) {
                val ratio = min(1.0, doneInWeek(entries, w).toDouble() / h.perWeek.coerceAtLeast(1))
                if (w < thisWeek || ratio > s) s = s * keep + ratio * (1 - keep)
                w += 7
            }
            return s
        }
        val keep = 0.5.pow(1.0 / 13.0)
        for (d in h.created..today) {
            if (!isScheduled(h, dates, d)) continue
            val v = entries[d] ?: Mark.NONE
            if (v == Mark.EXCUSED) continue
            if (d == today && v != Mark.DONE) continue
            val share = if (v == Mark.DONE) 1.0 else (partials[d] ?: 0) / 1000.0
            s = s * keep + share * (1 - keep)
        }
        return s
    }

    /** Consecutive planned days done, counting back from today. */
    fun streak(h: Habit, dates: Set<Long>, entries: Map<Long, Int>, today: Long): Int {
        var n = 0
        var d = today
        while (d >= h.created) {
            if (isScheduled(h, dates, d)) {
                val v = entries[d] ?: Mark.NONE
                if (v == Mark.DONE) n++
                else if (v == Mark.EXCUSED || d == today) {
                } else break
            }
            d--
        }
        return n
    }
}
