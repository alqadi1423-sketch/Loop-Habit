package app.daab.habits.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Reciter(val folder: String, val name: String)

class DownloadState(val title: String, val done: Int, val total: Int, val failed: Int)

/**
 * Recitation ayah by ayah, from the everyayah.com archive. A file already downloaded
 * is played from the phone; otherwise it is streamed.
 */
object QuranAudio {
    val reciters = listOf(
        Reciter("Alafasy_128kbps", "مشاري العفاسي"),
        Reciter("Husary_128kbps", "محمود خليل الحصري"),
        Reciter("Abdul_Basit_Murattal_192kbps", "عبدالباسط عبدالصمد"),
        Reciter("Minshawy_Murattal_128kbps", "محمد صديق المنشاوي"),
        Reciter("Abdurrahmaan_As-Sudais_192kbps", "عبدالرحمن السديس"),
        Reciter("Saood_ash-Shuraym_128kbps", "سعود الشريم"),
        Reciter("MaherAlMuaiqly128kbps", "ماهر المعيقلي"),
        Reciter("Ghamadi_40kbps", "سعد الغامدي"),
        Reciter("Yasser_Ad-Dussary_128kbps", "ياسر الدوسري"),
        Reciter("Abu_Bakr_Ash-Shaatree_128kbps", "أبو بكر الشاطري"),
        Reciter("Nasser_Alqatami_128kbps", "ناصر القطامي"),
        Reciter("ahmed_ibn_ali_al_ajamy_128kbps", "أحمد العجمي")
    )

    /** The ayah being recited, or -1. */
    val current = MutableStateFlow(-1)
    val playing = MutableStateFlow(false)
    val download = MutableStateFlow<DownloadState?>(null)

    @Volatile
    private var cancelDownload = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun reciter(ctx: Context): Reciter {
        val f = prefs(ctx).getString("reciter", reciters[0].folder)
        return reciters.firstOrNull { it.folder == f } ?: reciters[0]
    }

    fun setReciter(ctx: Context, folder: String) = prefs(ctx).edit().putString("reciter", folder).apply()

    /** "SSSAAA.mp3" for a surah and ayah numbered from 1. */
    fun fileName(surah: Int, ayah: Int) = "%03d%03d.mp3".format(surah, ayah)

    fun url(r: Reciter, surah: Int, ayah: Int) = "https://everyayah.com/data/${r.folder}/${fileName(surah, ayah)}"

    fun local(ctx: Context, r: Reciter, surah: Int, ayah: Int) = File(ctx.filesDir, "audio/${r.folder}/${fileName(surah, ayah)}")

    fun source(ctx: Context, surah: Int, ayah: Int): String {
        val r = reciter(ctx)
        val f = local(ctx, r, surah, ayah)
        return if (f.exists() && f.length() > 0) f.absolutePath else url(r, surah, ayah)
    }

    /** Downloaded size for every reciter, in bytes. */
    fun downloadedBytes(ctx: Context): Long =
        File(ctx.filesDir, "audio").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteDownloads(ctx: Context) {
        File(ctx.filesDir, "audio").deleteRecursively()
    }

    fun cancel() {
        cancelDownload = true
    }

    /**
     * Downloads the given ayat (surah and ayah numbered from 1) for the chosen reciter, in the
     * background. Files already there are skipped; a failed file is counted and left out.
     */
    fun downloadAyat(ctx: Context, title: String, ayat: List<Pair<Int, Int>>) {
        if (download.value != null) return
        val app = ctx.applicationContext
        val r = reciter(app)
        cancelDownload = false
        download.value = DownloadState(title, 0, ayat.size, 0)
        Thread {
            var failed = 0
            for ((i, a) in ayat.withIndex()) {
                if (cancelDownload) break
                val f = local(app, r, a.first, a.second)
                if (!(f.exists() && f.length() > 0)) {
                    try {
                        f.parentFile?.mkdirs()
                        val tmp = File(f.path + ".part")
                        val c = URL(url(r, a.first, a.second)).openConnection() as HttpURLConnection
                        c.connectTimeout = 15000
                        c.readTimeout = 30000
                        c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        c.disconnect()
                        tmp.renameTo(f)
                    } catch (e: Exception) {
                        failed++
                    }
                }
                download.value = DownloadState(title, i + 1, ayat.size, failed)
            }
            val last = download.value
            download.value = null
            lastResult.value = last
        }.start()
    }

    /** The outcome of the last download, to show once. */
    val lastResult = MutableStateFlow<DownloadState?>(null)
}
