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
        Reciter(3, "عبدالرحمن السديس"),
        Reciter(10, "سعود الشريم"),
        Reciter(4, "أبو بكر الشاطري"),
        Reciter(5, "هاني الرفاعي")
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

    /**
     * Downloads [url] into [to]. The file only takes its name once [valid] accepts it, so a page
     * from a network that asks to sign in first, or a cut connection, never passes for the real file.
     */
    private fun fetch(url: String, to: File, valid: (File) -> Boolean) {
        to.parentFile?.mkdirs()
        val tmp = File(to.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw IllegalStateException("http ${c.responseCode}")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!valid(tmp)) throw IllegalStateException("not the expected file")
            if (!tmp.renameTo(to)) throw IllegalStateException("rename")
        } finally {
            c.disconnect()
            tmp.delete()
        }
    }

    private fun timingUrl(r: Reciter, surah: Int) = "https://api.quran.com/api/v4/chapter_recitations/${r.id}/$surah?segments=true"

    private fun parseTiming(f: File): JSONObject = JSONObject(f.readText()).getJSONObject("audio_file").also {
        it.getString("audio_url")
        it.getJSONArray("timestamps")
    }

    private fun isTiming(f: File): Boolean = try {
        parseTiming(f)
        true
    } catch (e: Exception) {
        false
    }

    /** An MP3 starts with its tag or with a frame header; a web page does not. */
    private fun isAudio(f: File): Boolean {
        if (f.length() < 16 * 1024) return false
        val head = ByteArray(3)
        f.inputStream().use { if (it.read(head) < 3) return false }
        val id3 = head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
        val frame = head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0
        return id3 || frame
    }

    /** The timings file of a surah, fetched again if it is missing or damaged. */
    private fun timingJson(ctx: Context, r: Reciter, surah: Int): JSONObject {
        val f = timingFile(ctx, r, surah)
        if (f.exists() && f.length() > 0) {
            try {
                return parseTiming(f)
            } catch (e: Exception) {
                f.delete()
            }
        }
        fetch(timingUrl(r, surah), f, ::isTiming)
        return parseTiming(f)
    }

    /** The timings of a surah (numbered from 1), kept on the phone after the first time. Network: call off the main thread. */
    fun timing(ctx: Context, r: Reciter, surah: Int, firstAyah: Int): SurahTiming {
        val a = timingJson(ctx, r, surah)
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

    /** Removes pieces left by downloads that were cut off. */
    fun cleanPartial(ctx: Context) {
        File(ctx.filesDir, "audio").walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach { it.delete() }
    }

    fun downloadedBytes(ctx: Context): Long =
        File(ctx.filesDir, "audio").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteDownloads(ctx: Context) {
        File(ctx.filesDir, "audio").deleteRecursively()
    }

    fun cancel() {
        cancelDownload = true
    }

    /**
     * Downloads whole surahs (numbered from 1) with their timings for the chosen reciter. It runs in
     * a service with its own notification, so it carries on when the app is left.
     */
    fun downloadSurahs(ctx: Context, title: String, surahs: List<Int>) {
        if (download.value != null) return
        cancelDownload = false
        download.value = DownloadState(title, 0, surahs.size, 0)
        DownloadService.start(ctx, title, surahs)
    }

    /** The download itself, run by [DownloadService] off the main thread. */
    internal fun runDownload(ctx: Context, title: String, surahs: List<Int>, progress: (DownloadState) -> Unit) {
        val app = ctx.applicationContext
        val r = reciter(app)
        var failed = 0
        for ((i, s) in surahs.withIndex()) {
            if (cancelDownload) break
            try {
                val url = timingJson(app, r, s).getString("audio_url")
                val af = audioFile(app, r, s)
                if (af.exists() && !isAudio(af)) af.delete()
                if (!af.exists()) fetch(url, af, ::isAudio)
            } catch (e: Exception) {
                failed++
            }
            val state = DownloadState(title, i + 1, surahs.size, failed)
            download.value = state
            progress(state)
        }
        val last = download.value
        download.value = null
        lastResult.value = last
    }
}
