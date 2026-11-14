package app.daab.habits.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import app.daab.habits.R
import app.daab.habits.data.Mushaf
import app.daab.habits.data.MushafData
import app.daab.habits.data.Quran
import app.daab.habits.data.QuranText
import app.daab.habits.ui.MainActivity
import java.io.File

/**
 * Plays the recitation from one ayah up to another, surah after surah, and keeps the ayah and
 * the word being recited up to date from the recording's timings. Keeps playing with the screen off,
 * gives way to calls and other sounds, stops when headphones are pulled out, and answers the
 * lock screen and headset buttons.
 */
class QuranAudioService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var timing: SurahTiming? = null
    private var mushaf: MushafData? = null
    private var quran: QuranText? = null
    private var to = Quran.TOTAL
    private var surah = 0
    private var failed = false
    private var session = 0

    /** A local recording that would not play is deleted and the surah streamed once instead. */
    private var retriedLocal = false

    /** Paused because another sound took over for a moment, so it resumes when that ends. */
    private var pausedForFocus = false

    private lateinit var audio: AudioManager
    private lateinit var media: MediaSession
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Recitation is speech: lowering it under another sound would only blur it, so pause.
                if (player?.isPlaying == true) {
                    pause()
                    pausedForFocus = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForFocus) resume()
        }
    }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
    }

    /** Headphones pulled out: stop before the recitation comes out of the speaker. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private var noisyRegistered = false

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        media = MediaSession(this, "daab-quran").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopAll()
            })
            setSessionActivity(openApp())
            isActive = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                to = intent.getIntExtra("to", Quran.TOTAL)
                failed = false
                retriedLocal = false
                foreground()
                load(intent.getIntExtra("from", 0))
            }
            TOGGLE -> {
                val p = player
                if (p == null) stopAll() else if (p.isPlaying) pause() else resume()
            }
            STOP -> stopAll()
            else -> if (player == null) stopAll()
        }
        return START_NOT_STICKY
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun requestFocus(): Boolean =
        audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun pause() {
        val p = player ?: return
        pausedForFocus = false
        if (p.isPlaying) p.pause()
        main.removeCallbacks(ticker)
        QuranAudio.playing.value = false
        unregisterNoisy()
        foreground()
    }

    private fun resume() {
        val p = player ?: return
        pausedForFocus = false
        if (!requestFocus()) return
        p.start()
        QuranAudio.playing.value = true
        registerNoisy()
        tick()
        foreground()
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisy, filter)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        try {
            unregisterReceiver(noisy)
        } catch (e: IllegalArgumentException) {
        }
        noisyRegistered = false
    }

    /** Fetches the surah holding [ayah] and starts it at that ayah. The text and timings load off the main thread. */
    private fun load(ayah: Int) {
        val my = ++session
        QuranAudio.current.value = ayah
        QuranAudio.word.value = -1
        val r = QuranAudio.reciter(this)
        Thread {
            try {
                val q = quran ?: Quran.text(this).also { quran = it }
                val s = q.surahAt(ayah)
                val m = mushaf ?: Mushaf.load(this).also { mushaf = it }
                val t = QuranAudio.timing(this, r, s + 1, q.surahs[s].start)
                main.post {
                    if (my == session) {
                        surah = s
                        start(t, ayah, m, q)
                    }
                }
            } catch (e: Exception) {
                main.post { if (my == session) fail() }
            }
        }.start()
    }

    private fun start(t: SurahTiming, ayah: Int, m: MushafData, q: QuranText) {
        timing = t
        main.removeCallbacks(ticker)
        player?.release()
        val first = t.verses.firstOrNull { it.ayah == ayah }
        // From the opening of a surah the recording starts at zero, so the basmala is heard.
        val at = if (first == null || ayah == q.surahs[surah].start) 0 else first.from
        val local = !t.audioUrl.startsWith("http")
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setWakeMode(this@QuranAudioService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener {
                if (at > 0) it.seekTo(at.toLong(), MediaPlayer.SEEK_CLOSEST)
                if (requestFocus()) {
                    it.start()
                    QuranAudio.playing.value = true
                    registerNoisy()
                    tick()
                }
                foreground()
            }
            setOnCompletionListener { next() }
            setOnErrorListener { _, _, _ ->
                if (local && !retriedLocal) {
                    // A damaged download: remove it and stream this surah instead.
                    retriedLocal = true
                    File(t.audioUrl).delete()
                    load(ayah)
                } else fail()
                true
            }
            try {
                setDataSource(t.audioUrl)
                prepareAsync()
            } catch (e: Exception) {
                fail()
            }
        }
        updateMetadata()
    }

    /** The surah is over: carry on with the next one if the range goes on. */
    private fun next() {
        val q = quran ?: return stopAll()
        val nextStart = if (surah + 1 < q.surahs.size) q.surahs[surah + 1].start else Quran.TOTAL
        retriedLocal = false
        if (nextStart >= to) stopAll() else load(nextStart)
    }

    private val ticker = Runnable { tick() }

    /** Reads the playing position and finds the ayah and the word that hold it. */
    private fun tick() {
        // Only one loop ever runs, however quickly playback is paused and resumed.
        main.removeCallbacks(ticker)
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
                updateMetadata()
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
        unregisterNoisy()
        foreground()
    }

    private fun label(): String {
        val at = QuranAudio.current.value
        val q = quran
        return when {
            failed -> "تعذر التشغيل، تأكد من الاتصال"
            at >= 0 && q != null -> q.label(at)
            else -> ""
        }
    }

    private fun updateMetadata() {
        media.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, label())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, QuranAudio.reciter(this).name)
                .build()
        )
    }

    private fun foreground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تلاوة القرآن", NotificationManager.IMPORTANCE_LOW))
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, QuranAudioService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val playing = QuranAudio.playing.value
        media.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else if (failed) PlaybackState.STATE_ERROR else PlaybackState.STATE_PAUSED,
                    player?.let { runCatching { it.currentPosition.toLong() }.getOrNull() } ?: 0L,
                    if (playing) 1f else 0f
                )
                .build()
        )
        val toggle = Notification.Action.Builder(
            Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
            if (playing) "إيقاف مؤقت" else "تشغيل", action(TOGGLE, 1)
        ).build()
        val stop = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "إنهاء", action(STOP, 2)
        ).build()
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(QuranAudio.reciter(this).name)
            .setContentText(label())
            .setContentIntent(openApp())
            .setDeleteIntent(action(STOP, 3))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
    }

    private fun clear() {
        session++
        main.removeCallbacks(ticker)
        player?.release()
        player = null
        pausedForFocus = false
        audio.abandonAudioFocusRequest(focusRequest)
        unregisterNoisy()
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
        media.release()
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
