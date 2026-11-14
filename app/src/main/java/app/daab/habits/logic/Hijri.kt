package app.daab.habits.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Hijri dates by the Umm al-Qura calendar, moved by the user's adjustment when the moon was sighted on another day. */
object Hijri {

    private val months = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    private val weekdays = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    private fun parts(day: Long): Triple<Int, Int, Int>? = try {
        val h = HijrahDate.from(LocalDate.ofEpochDay(day + Settings.hijriOffset))
        Triple(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    } catch (e: Exception) {
        null
    }

    fun isRamadan(day: Long): Boolean = parts(day)?.second == 9

    fun text(day: Long): String {
        val name = weekdays[Schedule.weekdayIndex(day)]
        val p = parts(day) ?: return name
        return "$name، ${p.third} ${months[p.second - 1]} ${p.first}"
    }

    /** Days on which fasting is not allowed: the two Eids and the three days after Eid al-Adha. */
    private fun noFasting(day: Long): Boolean {
        val p = parts(day) ?: return false
        return (p.second == 10 && p.third == 1) || (p.second == 12 && p.third in 10..13)
    }

    fun mondaysAndThursdays(from: Long, days: Int): Set<Long> =
        (from until from + days).filter {
            val w = LocalDate.ofEpochDay(it).dayOfWeek
            (w == DayOfWeek.MONDAY || w == DayOfWeek.THURSDAY) && !noFasting(it)
        }.toSet()

    /** The 13th, 14th and 15th of each Hijri month. */
    fun whiteDays(from: Long, days: Int): Set<Long> =
        (from until from + days).filter {
            val p = parts(it)
            p != null && p.third in 13..15 && !noFasting(it)
        }.toSet()

    /** The next day of Arafah, and the next Tasu'a and Ashura (the 9th and 10th of Muharram), once each. */
    fun arafahAndAshura(from: Long): Set<Long> {
        val out = HashSet<Long>()
        val found = HashSet<Pair<Int, Int>>()
        for (d in from until from + 370) {
            val p = parts(d) ?: continue
            val key = p.second to p.third
            val wanted = key == (12 to 9) || key == (1 to 9) || key == (1 to 10)
            if (wanted && found.add(key)) out.add(d)
            if (found.size == 3) break
        }
        return out
    }
}
