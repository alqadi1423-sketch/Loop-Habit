package app.daab.habits.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.daab.habits.R
import app.daab.habits.data.Mushaf
import app.daab.habits.data.MushafData
import app.daab.habits.data.Quran
import app.daab.habits.ui.MainActivity

/**
 * Plays the recitation from one ayah up to another, surah after surah, and keeps the ayah and
 * the word being recited up to date from the recording's timings. Keeps playing with the screen off.
 */
class QuranAudioService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var timing: SurahTiming? = null
    private var mushaf: MushafData? = null
    private var to = Quran.TOTAL
    private var surah = 0
    private var failed = false
    private var session = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                to = intent.getIntExtra("to", Quran.TOTAL)
                failed = false
                foreground()
                load(intent.getIntExtra("from", 0))
            }
            TOGGLE -> {
                val p = player
                if (p == null) stopAll()
                else {
                    if (p.isPlaying) p.pause() else p.start()
                    QuranAudio.playing.value = p.isPlaying
                    if (p.isPlaying) tick()
                    foreground()
                }
            }
            STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    /** Fetches the surah holding [ayah] and starts it at that ayah. */
    private fun load(ayah: Int) {
        val my = ++session
        val q = Quran.text(this)
        surah = q.surahAt(ayah)
        QuranAudio.current.value = ayah
        QuranAudio.word.value = -1
        val r = QuranAudio.reciter(this)
        Thread {
            try {
                val m = mushaf ?: Mushaf.load(this).also { mushaf = it }
                val t = QuranAudio.timing(this, r, surah + 1, q.surahs[surah].start)
                main.post { if (my == session) start(t, ayah, m) }
            } catch (e: Exception) {
                main.post { if (my == session) fail() }
            }
        }.start()
    }

    private fun start(t: SurahTiming, ayah: Int, m: MushafData) {
        timing = t
        player?.release()
        val first = t.verses.firstOrNull { it.ayah == ayah }
        // From the opening of a surah the recording starts at zero, so the basmala is heard.
        val at = if (first == null || ayah == Quran.text(this).surahs[surah].start) 0 else first.from
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            setWakeMode(this@QuranAudioService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener {
                if (at > 0) it.seekTo(at.toLong(), MediaPlayer.SEEK_CLOSEST)
                it.start()
                QuranAudio.playing.value = true
                foreground()
                tick()
            }
            setOnCompletionListener { next() }
            setOnErrorListener { _, _, _ ->
                fail()
                true
            }
            try {
                setDataSource(t.audioUrl)
                prepareAsync()
            } catch (e: Exception) {
                fail()
            }
        }
    }

    /** The surah is over: carry on with the next one if the range goes on. */
    private fun next() {
        val q = Quran.text(this)
        val nextStart = if (surah + 1 < q.surahs.size) q.surahs[surah + 1].start else Quran.TOTAL
        if (nextStart >= to) stopAll() else load(nextStart)
    }

    private val ticker = Runnable { tick() }

    /** Reads the playing position and finds the ayah and the word that hold it. */
    private fun tick() {
        val p = player ?: return
        val t = timing ?: return
        val m = mushaf ?: return
        if (!p.isPlaying) return
        val pos = p.currentPosition
        val v = t.verses.lastOrNull { it.from <= pos }
        if (v != null) {
            if (v.ayah >= to) {
                stopAll()
                return
            }
            if (QuranAudio.current.value != v.ayah) {
                QuranAudio.current.value = v.ayah
                foreground()
            }
            var position = -1
            var i = 0
            while (i + 2 < v.words.size) {
                if (v.words[i + 1] <= pos) position = v.words[i] else break
                i += 3
            }
            QuranAudio.word.value = if (position > 0) m.wordOf(v.ayah, position) else -1
        }
        main.postDelayed(ticker, 60)
    }

    private fun fail() {
        failed = true
        QuranAudio.playing.value = false
        foreground()
    }

    private fun foreground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تلاوة القرآن", NotificationManager.IMPORTANCE_LOW))
        val q = Quran.text(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, QuranAudioService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val playing = QuranAudio.playing.value
        val at = QuranAudio.current.value
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(QuranAudio.reciter(this).name)
            .setContentText(if (failed) "تعذر التشغيل، تأكد من الاتصال" else if (at >= 0) q.label(at) else "")
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .addAction(0, if (playing) "إيقاف مؤقت" else "تشغيل", action(TOGGLE, 1))
            .addAction(0, "إنهاء", action(STOP, 2))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
    }

    private fun clear() {
        session++
        main.removeCallbacks(ticker)
        player?.release()
        player = null
        QuranAudio.playing.value = false
        QuranAudio.current.value = -1
        QuranAudio.word.value = -1
    }

    private fun stopAll() {
        clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "quran_audio"
        private const val ID = 7001
        const val PLAY = "app.daab.audio.PLAY"
        const val TOGGLE = "app.daab.audio.TOGGLE"
        const val STOP = "app.daab.audio.STOP"

        /** Recites from ayah [from] up to, not including, [to]. */
        fun play(ctx: Context, from: Int, to: Int = Quran.TOTAL) {
            val i = Intent(ctx, QuranAudioService::class.java).setAction(PLAY).putExtra("from", from).putExtra("to", to)
            ctx.startForegroundService(i)
        }

        fun toggle(ctx: Context) = ctx.startService(Intent(ctx, QuranAudioService::class.java).setAction(TOGGLE))

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, QuranAudioService::class.java).setAction(STOP))
    }
}
