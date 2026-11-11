package app.daab.habits.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.ceil

class Surah(val name: String, val start: Int, val count: Int, val basmala: Boolean)

class QuranText(val surahs: List<Surah>, val rub: IntArray, val ayat: Array<String>) {

    /** Index of the surah that holds this ayah position. */
    fun surahAt(position: Int): Int {
        var lo = 0
        var hi = surahs.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (surahs[mid].start <= position) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** "Surah name, ayah number" for a position. */
    fun label(position: Int): String {
        val p = position.coerceIn(0, ayat.size - 1)
        val s = surahs[surahAt(p)]
        return "${s.name} ${p - s.start + 1}"
    }

    /** The basmala as written at the opening of al-Fatihah, without its ayah number. */
    fun basmala(): String = ayat[0].trimEnd { it.isDigit() || it.isWhitespace() || it == ' ' }
}

class QuranRange(val start: Int, val end: Int, val reached: Int)

/**
 * The Quran text bundled with the app: the King Fahd Complex Hafs edition, checked verse by
 * verse against an independent copy before it was added. The daily portion of a habit runs
 * from where the reader stopped, for the amount the habit sets.
 */
object Quran {

    const val CONTENT = -1
    const val TOTAL = 6236

    const val UNIT_RUB = 0
    const val UNIT_HIZB = 1
    const val UNIT_JUZ = 2
    const val UNIT_AYAT = 3
    const val UNIT_DAYS = 4

    val unitNames = listOf("أرباع", "أحزاب", "أجزاء", "آيات", "ختمة في أيام")

    @Volatile
    private var cache: QuranText? = null

    fun text(ctx: Context): QuranText {
        cache?.let { return it }
        val raw = ctx.applicationContext.assets.open("quran.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(raw)
        val ss = root.getJSONArray("surahs")
        val surahs = ArrayList<Surah>()
        for (i in 0 until ss.length()) {
            val o = ss.getJSONObject(i)
            surahs.add(Surah(o.getString("name"), o.getInt("start"), o.getInt("count"), o.getBoolean("basmala")))
        }
        val rs = root.getJSONArray("rub")
        val rub = IntArray(rs.length()) { rs.getInt(it) }
        val ay = root.getJSONArray("ayat")
        val ayat = Array(ay.length()) { ay.getString(it) }
        return QuranText(surahs, rub, ayat).also { cache = it }
    }

    fun describe(unit: Int, amount: Int): String =
        if (unit == UNIT_DAYS) "ختمة في $amount يومًا" else "$amount ${unitNames[unit.coerceIn(0, 3)]} كل يوم"

    /** Where a portion that begins at [start] ends, never past the end of the Quran. */
    private fun endOf(q: QuranText, start: Int, unit: Int, amount: Int, daysLeft: Int): Int {
        val n = amount.coerceAtLeast(1)
        val end = when (unit) {
            UNIT_AYAT -> start + n
            UNIT_DAYS -> start + ceil((TOTAL - start).toDouble() / daysLeft.coerceAtLeast(1)).toInt()
            else -> {
                val rubs = when (unit) {
                    UNIT_HIZB -> n * 4
                    UNIT_JUZ -> n * 8
                    else -> n
                }
                val current = q.rub.indexOfLast { it <= start }
                val target = current + rubs
                if (target >= q.rub.size) TOTAL else q.rub[target]
            }
        }
        return end.coerceIn(start + 1, TOTAL)
    }

    /** Today's portion for the habit. It is fixed the first time it is asked for on a day. */
    fun today(ctx: Context, db: Db, h: Habit, day: Long): QuranRange {
        db.quranDay(h.id, day)?.let { return it }
        val q = text(ctx)
        var (position, planEnd) = db.quranState(h.id)
        // Carry on from where the last portion was left, whether it was finished from the
        // reader, marked done from the record, or only partly read.
        db.lastQuranDay(h.id, day)?.let { last ->
            val finished = db.entries(h.id)[last[0]] == Mark.DONE
            val stopped = (if (finished) last[2] else last[3]).toInt()
            if (position >= last[1] && position < stopped) {
                position = stopped
                db.setQuranState(h.id, position, planEnd, addKhatma = stopped >= TOTAL)
            }
        }
        if (position >= TOTAL) position = 0
        if (h.contentUnit == UNIT_DAYS && planEnd < day) {
            planEnd = day + h.contentAmount.coerceAtLeast(1) - 1
            db.setQuranState(h.id, position, planEnd)
        }
        val end = endOf(q, position, h.contentUnit, h.contentAmount, (planEnd - day + 1).toInt())
        db.setQuranDay(h.id, day, position, end, position)
        return QuranRange(position, end, position)
    }
}
