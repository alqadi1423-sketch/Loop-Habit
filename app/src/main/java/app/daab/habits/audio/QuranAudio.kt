package app.daab.habits.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Reciter(val id: Int, val name: String)

class DownloadState(val title: String, val done: Int, val total: Int, val failed: Int)

/** When each ayah of a surah starts and ends in its recording, and each word within it, in milliseconds. */
class VerseTiming(val ayah: Int, val from: Int, val to: Int, val words: IntArray)

class SurahTiming(val audioUrl: String, val verses: List<VerseTiming>)

/**
 * Recitation from Quran.com: one recording per surah, with the time of every ayah and every word,
 * so the page follows the reciter word by word. A surah already downloaded plays from the phone.
 */
object QuranAudio {
    /** Recitations that come with word timings. */
    val reciters = listOf(
        Reciter(7, "مشاري العفاسي"),
        Reciter(6, "محمود خليل الحصري"),
        Reciter(12, "محمود خليل الحصري، المعلم"),
        Reciter(2, "عبدالباسط عبدالصمد، مرتل"),
        Reciter(1, "عبدالباسط عبدالصمد، مجود"),
        Reciter(9, "محمد صديق المنشاوي، مرتل"),
        Reciter(8, "محمد صديق المنشاوي، مجود"),
        Reciter(3, "عبدالرحمن السديس"),
        Reciter(10, "سعود الشريم"),
        Reciter(4, "أبو بكر الشاطري"),
        Reciter(5, "هاني الرفاعي"),
        Reciter(11, "محمد الطبلاوي")
    )

    /** The ayah being recited, or -1. */
    val current = MutableStateFlow(-1)

    /** The word being recited, as an index into the mushaf words, or -1. */
    val word = MutableStateFlow(-1)
    val playing = MutableStateFlow(false)
    val download = MutableStateFlow<DownloadState?>(null)

    /** The outcome of the last download, to show once. */
    val lastResult = MutableStateFlow<DownloadState?>(null)

    @Volatile
    private var cancelDownload = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun reciter(ctx: Context): Reciter {
        val id = prefs(ctx).getInt("reciter_id", reciters[0].id)
        return reciters.firstOrNull { it.id == id } ?: reciters[0]
    }

    fun setReciter(ctx: Context, id: Int) = prefs(ctx).edit().putInt("reciter_id", id).apply()

    private fun dir(ctx: Context, r: Reciter) = File(ctx.filesDir, "audio/qdc${r.id}")
    private fun timingFile(ctx: Context, r: Reciter, surah: Int) = File(dir(ctx, r), "$surah.json")
    private fun audioFile(ctx: Context, r: Reciter, surah: Int) = File(dir(ctx, r), "$surah.mp3")

    private fun fetch(url: String, to: File) {
        to.parentFile?.mkdirs()
        val tmp = File(to.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IllegalStateException("http ${c.responseCode}")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        if (!tmp.renameTo(to)) throw IllegalStateException("rename")
    }

    /** The timings of a surah (numbered from 1), kept on the phone after the first time. Network: call off the main thread. */
    fun timing(ctx: Context, r: Reciter, surah: Int, firstAyah: Int): SurahTiming {
        val f = timingFile(ctx, r, surah)
        if (!(f.exists() && f.length() > 0)) {
            fetch("https://api.quran.com/api/v4/chapter_recitations/${r.id}/$surah?segments=true", f)
        }
        val a = JSONObject(f.readText()).getJSONObject("audio_file")
        val ts = a.getJSONArray("timestamps")
        val verses = ArrayList<VerseTiming>()
        for (i in 0 until ts.length()) {
            val t = ts.getJSONObject(i)
            val n = t.getString("verse_key").substringAfter(':').toInt()
            val seg = t.optJSONArray("segments")
            val words = ArrayList<Int>()
            if (seg != null) for (j in 0 until seg.length()) {
                val s = seg.optJSONArray(j) ?: continue
                if (s.length() < 3) continue
                words.add(s.getInt(0)); words.add(s.getInt(1)); words.add(s.getInt(2))
            }
            verses.add(VerseTiming(firstAyah + n - 1, t.getInt("timestamp_from"), t.getInt("timestamp_to"), words.toIntArray()))
        }
        val audio = audioFile(ctx, r, surah)
        val src = if (audio.exists() && audio.length() > 0) audio.absolutePath else a.getString("audio_url")
        return SurahTiming(src, verses)
    }

    fun downloadedBytes(ctx: Context): Long =
        File(ctx.filesDir, "audio").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteDownloads(ctx: Context) {
        File(ctx.filesDir, "audio").deleteRecursively()
    }

    fun cancel() {
        cancelDownload = true
    }

    /** Downloads whole surahs (numbered from 1) with their timings for the chosen reciter, in the background. */
    fun downloadSurahs(ctx: Context, title: String, surahs: List<Int>) {
        if (download.value != null) return
        val app = ctx.applicationContext
        val r = reciter(app)
        cancelDownload = false
        download.value = DownloadState(title, 0, surahs.size, 0)
        Thread {
            var failed = 0
            for ((i, s) in surahs.withIndex()) {
                if (cancelDownload) break
                try {
                    val tf = timingFile(app, r, s)
                    if (!(tf.exists() && tf.length() > 0)) {
                        fetch("https://api.quran.com/api/v4/chapter_recitations/${r.id}/$s?segments=true", tf)
                    }
                    val af = audioFile(app, r, s)
                    if (!(af.exists() && af.length() > 0)) {
                        val url = JSONObject(tf.readText()).getJSONObject("audio_file").getString("audio_url")
                        fetch(url, af)
                    }
                } catch (e: Exception) {
                    failed++
                }
                download.value = DownloadState(title, i + 1, surahs.size, failed)
            }
            val last = download.value
            download.value = null
            lastResult.value = last
        }.start()
    }
}
