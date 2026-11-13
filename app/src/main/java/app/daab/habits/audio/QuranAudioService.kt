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
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.daab.habits.R
import app.daab.habits.data.Quran
import app.daab.habits.ui.MainActivity

/** Plays a run of ayat one after another, and keeps playing with the screen off. */
class QuranAudioService : Service() {

    private var player: MediaPlayer? = null
    private var from = 0
    private var to = Quran.TOTAL
    private var at = 0

    /** True while the basmala is played before the first ayah of a surah. */
    private var inBasmala = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                from = intent.getIntExtra("from", 0)
                to = intent.getIntExtra("to", Quran.TOTAL)
                at = from
                foreground()
                start(at, basmala = true)
            }
            TOGGLE -> {
                val p = player
                if (p == null) stopAll()
                else if (p.isPlaying) {
                    p.pause()
                    QuranAudio.playing.value = false
                    foreground()
                } else {
                    p.start()
                    QuranAudio.playing.value = true
                    foreground()
                }
            }
            STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start(ayah: Int, basmala: Boolean) {
        if (ayah >= to || ayah >= Quran.TOTAL) {
            stopAll()
            return
        }
        at = ayah
        val q = Quran.text(this)
        val s = q.surahAt(ayah)
        val number = ayah - q.surahs[s].start + 1
        // The basmala opens every surah except at-Tawbah; al-Fatihah carries it as its first ayah.
        inBasmala = basmala && number == 1 && q.surahs[s].basmala && s != 0
        val src = if (inBasmala) QuranAudio.source(this, 1, 1) else QuranAudio.source(this, s + 1, number)
        QuranAudio.current.value = ayah
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            setWakeMode(this@QuranAudioService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener {
                it.start()
                QuranAudio.playing.value = true
                foreground()
            }
            setOnCompletionListener {
                if (inBasmala) start(at, basmala = false) else start(at + 1, basmala = true)
            }
            setOnErrorListener { _, _, _ ->
                QuranAudio.playing.value = false
                failed = true
                foreground()
                true
            }
            try {
                setDataSource(src)
                prepareAsync()
            } catch (e: Exception) {
                failed = true
                QuranAudio.playing.value = false
            }
        }
        failed = false
    }

    private var failed = false

    private fun foreground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تلاوة القرآن", NotificationManager.IMPORTANCE_LOW))
        val q = Quran.text(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, QuranAudioService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val playing = QuranAudio.playing.value
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(QuranAudio.reciter(this).name)
            .setContentText(if (failed) "تعذر التشغيل، تأكد من الاتصال" else q.label(at))
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .addAction(0, if (playing) "إيقاف مؤقت" else "تشغيل", action(TOGGLE, 1))
            .addAction(0, "إنهاء", action(STOP, 2))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
    }

    private fun stopAll() {
        player?.release()
        player = null
        QuranAudio.playing.value = false
        QuranAudio.current.value = -1
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        QuranAudio.playing.value = false
        QuranAudio.current.value = -1
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
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun toggle(ctx: Context) = ctx.startService(Intent(ctx, QuranAudioService::class.java).setAction(TOGGLE))

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, QuranAudioService::class.java).setAction(STOP))
    }
}
