package app.daab.habits.data

import android.content.Context
import org.json.JSONObject

/** One line of a mushaf page: a surah title, the basmala, or a run of words. */
class MushafLine(val kind: Int, val a: Int, val b: Int) {
    companion object {
        const val HEADER = 0
        const val BASMALA = 1
        const val TEXT = 2
    }
}

/**
 * The 604 pages of the Madinah mushaf, line by line. Each line points into the words of the
 * bundled, verified Quran text, so the layout only decides where a line breaks, never what it says.
 * Two prints are kept because their line breaks differ on some pages; the page boundaries are the same.
 */
class MushafData(
    val pages: Array<Array<MushafLine>>,
    val words: Array<String>,
    /** The ayah position each word belongs to. */
    val ayahOf: IntArray,
    /** The first word of every ayah. */
    val firstWord: IntArray,
    /** For the few ayat Quran.com splits into words differently: its word position to ours. */
    private val qdc: Map<Int, IntArray> = emptyMap()
) {
    /** Our word for a Quran.com word position (from 1) in an ayah, as the recitation timings count them. */
    fun wordOf(ayah: Int, position: Int): Int {
        val count = (if (ayah + 1 < firstWord.size) firstWord[ayah + 1] else words.size) - firstWord[ayah]
        val i = qdc[ayah]?.getOrNull(position - 1) ?: (position - 1)
        return firstWord[ayah] + i.coerceIn(0, count - 1)
    }

    private val pageFirst = IntArray(pages.size) { p -> pages[p].first { it.kind == MushafLine.TEXT }.a }

    /** Page index (0 based) holding the start of an ayah. */
    fun pageOf(ayah: Int): Int {
        val w = firstWord[ayah.coerceIn(0, firstWord.size - 1)]
        var lo = 0
        var hi = pages.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pageFirst[mid] <= w) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun firstAyah(page: Int): Int = ayahOf[pageFirst[page]]
}

object Mushaf {
    const val QCF2 = "qcf2"
    const val QCF4 = "qcf4"
    const val PAGES = 604

    private var root: JSONObject? = null
    private val cache = HashMap<String, MushafData>()

    private val number = Regex("^[٠-٩۝]+$")

    /** Splits an ayah the same way the layout data was built: the number joins the word before it. */
    fun tokens(ayah: String): List<String> {
        val out = ArrayList<String>()
        for (w in ayah.split(' ', ' ')) {
            if (w.isEmpty()) continue
            if (out.isNotEmpty() && (number.matches(w) || out.last() == "۞")) {
                out[out.size - 1] = out.last() + " " + w
            } else out.add(w)
        }
        return out
    }

    fun edition(ctx: Context): String =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mushaf_edition", QCF2) ?: QCF2

    fun setEdition(ctx: Context, value: String) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("mushaf_edition", value).apply()
    }

    /** Heavy the first time; call it away from the main thread. */
    @Synchronized
    fun load(ctx: Context, edition: String = edition(ctx)): MushafData {
        cache[edition]?.let { return it }
        val q = Quran.text(ctx)
        val words = ArrayList<String>(78000)
        val ayahOf = ArrayList<Int>(78000)
        val firstWord = IntArray(q.ayat.size)
        for (i in q.ayat.indices) {
            firstWord[i] = words.size
            for (t in tokens(q.ayat[i])) {
                words.add(t)
                ayahOf.add(i)
            }
        }
        val r = root ?: JSONObject(
            ctx.applicationContext.assets.open("mushaf.json").use { it.readBytes().toString(Charsets.UTF_8) }
        ).also { root = it }
        check(r.getInt("total") == words.size) { "mushaf words do not match the text" }
        val ps = r.getJSONObject("editions").getJSONArray(edition)
        val pages = Array(ps.length()) { p ->
            val ls = ps.getJSONArray(p)
            Array(ls.length()) { i ->
                val l = ls.getJSONArray(i)
                when (l.getString(0)) {
                    "h" -> MushafLine(MushafLine.HEADER, l.getInt(1), 0)
                    "b" -> MushafLine(MushafLine.BASMALA, 0, 0)
                    else -> MushafLine(MushafLine.TEXT, l.getInt(1), l.getInt(2))
                }
            }
        }
        val qdc = HashMap<Int, IntArray>()
        r.optJSONObject("qdc")?.let { o ->
            for (key in o.keys()) {
                val s = key.substringBefore(':').toInt() - 1
                val a = key.substringAfter(':').toInt() - 1
                val arr = o.getJSONArray(key)
                qdc[q.surahs[s].start + a] = IntArray(arr.length()) { arr.getInt(it) }
            }
        }
        return MushafData(pages, words.toTypedArray(), ayahOf.toIntArray(), firstWord, qdc).also { cache[edition] = it }
    }
}
