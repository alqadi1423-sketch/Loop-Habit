package app.daab.habits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.daab.habits.audio.QuranAudio
import app.daab.habits.data.Mushaf
import app.daab.habits.data.MushafLine
import app.daab.habits.data.Quran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val report = File("build/shots/checks.txt")

    private fun note(s: String) {
        report.parentFile?.mkdirs()
        report.appendText(s + "\n")
        println(s)
    }

    /** Every page of both prints has its lines in order and every word appears once. */
    @Test
    fun mushafPages() {
        for (ed in listOf(Mushaf.QCF2, Mushaf.QCF4)) {
            val d = Mushaf.load(ctx, ed)
            assertEquals(604, d.pages.size)
            var next = 0
            for ((p, lines) in d.pages.withIndex()) {
                assertTrue("page $p lines ${lines.size}", lines.size == if (p < 2) 8 else 15)
                for (l in lines) if (l.kind == MushafLine.TEXT) {
                    assertEquals("page $p", next, l.a)
                    next = l.b + 1
                }
            }
            assertEquals(d.words.size, next)
            note("mushaf $ed ok: ${d.words.size} words")
        }
    }

    /** Every reciter answers with a recording and word timings. Needs the internet. */
    @Test
    fun reciters() {
        val q = Quran.text(ctx)
        val bad = ArrayList<String>()
        for (r in QuranAudio.reciters) {
            for (s in listOf(1, 2, 114)) {
                try {
                    val t = QuranAudio.timing(ctx, r, s, q.surahs[s - 1].start)
                    val words = t.verses.count { it.words.isNotEmpty() }
                    val c = URL(t.audioUrl).openConnection() as HttpURLConnection
                    c.requestMethod = "HEAD"
                    val code = c.responseCode
                    c.disconnect()
                    note("reciter ${r.id} ${r.name} surah $s: verses ${t.verses.size}/${q.surahs[s - 1].count}, with words $words, audio $code")
                    if (code != 200 || t.verses.size != q.surahs[s - 1].count || words < t.verses.size) bad.add("${r.id}:$s")
                } catch (e: Exception) {
                    note("reciter ${r.id} surah $s failed: $e")
                    bad.add("${r.id}:$s")
                }
            }
        }
        note("reciters with problems: $bad")
    }
}
