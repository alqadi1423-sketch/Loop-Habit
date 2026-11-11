package app.daab.habits.logic

import app.daab.habits.data.Anchor
import app.daab.habits.data.Habit
import app.daab.habits.data.Kind
import app.daab.habits.data.Mark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import kotlin.math.pow

object Phase {
    const val BEFORE = 0
    const val OPEN = 1
    const val CLOSED = 2
}

object Schedule {

    /**
     * The day habits are logged against. With the extended day on, the hours right after
     * midnight still count as the day before.
     */
    fun today(): Long = LocalDateTime.now().minusMinutes(Settings.shiftMinutes().toLong()).toLocalDate().toEpochDay()

    /** Minutes since the logical day began. Past midnight on an extended day this goes above 1440. */
    fun nowMinutes(): Int {
        val now = LocalDateTime.now()
        val dayStart = LocalDate.ofEpochDay(today()).atStartOfDay()
        return java.time.Duration.between(dayStart, now).toMinutes().toInt()
    }

    /** The minute the logical day ends. */
    fun dayEnd(): Int = 1440 + Settings.shiftMinutes()

    fun hasWindow(h: Habit): Boolean = h.startKind != Anchor.NONE || h.endKind != Anchor.NONE

    private fun resolve(kind: Int, value: Int, times: IntArray, fallback: Int): Int = when (kind) {
        Anchor.CLOCK -> value
        Anchor.PRAYER -> times[value.coerceIn(0, 5)]
        else -> fallback
    }

    /** Start and end of the habit's time window today, in minutes after midnight. */
    fun window(h: Habit, times: IntArray): Pair<Int, Int> {
        val start = resolve(h.startKind, h.startValue, times, 0)
        var end = resolve(h.endKind, h.endValue, times, dayEnd())
        if (end <= start) end = dayEnd()
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
        LocalDate.ofEpochDay(day).with(TemporalAdjusters.previousOrSame(firstWeekday())).toEpochDay()

    private fun firstWeekday(): DayOfWeek = when (Settings.weekStart) {
        0 -> DayOfWeek.SUNDAY
        1 -> DayOfWeek.MONDAY
        else -> DayOfWeek.SATURDAY
    }

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

    /**
     * The strength as it stood on each day from the habit's first day to today, in one pass.
     * Index 0 is the first day. Used for the strength chart and the monthly and yearly change.
     */
    fun scoreSeries(
        h: Habit, dates: Set<Long>, entries: Map<Long, Int>, today: Long, partials: Map<Long, Int>
    ): DoubleArray {
        val n = (today - h.created + 1).toInt().coerceAtLeast(1)
        val out = DoubleArray(n)
        var s = 0.0
        if (h.kind == Kind.PER_WEEK) {
            val keep = 0.5.pow(1.0 / 4.0)
            for (i in 0 until n) {
                val d = h.created + i
                if (d == weekStart(d) + 6 || d == today) {
                    val ratio = min(1.0, doneInWeek(entries, d).toDouble() / h.perWeek.coerceAtLeast(1))
                    if (d != today || ratio > s || d == weekStart(d) + 6) s = s * keep + ratio * (1 - keep)
                }
                out[i] = s
            }
            return out
        }
        val keep = 0.5.pow(1.0 / 13.0)
        for (i in 0 until n) {
            val d = h.created + i
            if (isScheduled(h, dates, d)) {
                val v = entries[d] ?: Mark.NONE
                if (v != Mark.EXCUSED && !(d == today && v != Mark.DONE)) {
                    val share = if (v == Mark.DONE) 1.0 else (partials[d] ?: 0) / 1000.0
                    s = s * keep + share * (1 - keep)
                }
            }
            out[i] = s
        }
        return out
    }

    /** Every unbroken run of planned days done, as first day, last day and length, longest first. */
    fun streaks(h: Habit, dates: Set<Long>, entries: Map<Long, Int>, today: Long): List<Triple<Long, Long, Int>> {
        val out = ArrayList<Triple<Long, Long, Int>>()
        var start = -1L
        var last = -1L
        var length = 0
        for (d in h.created..today) {
            if (!isScheduled(h, dates, d)) continue
            val v = entries[d] ?: Mark.NONE
            if (v == Mark.DONE) {
                if (length == 0) start = d
                last = d
                length++
            } else if (v == Mark.EXCUSED || d == today) {
                continue
            } else if (length > 0) {
                out.add(Triple(start, last, length))
                length = 0
            }
        }
        if (length > 0) out.add(Triple(start, last, length))
        return out.sortedByDescending { it.third }
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
