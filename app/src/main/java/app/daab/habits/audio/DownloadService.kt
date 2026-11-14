package app.daab.habits.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.daab.habits.R
import app.daab.habits.ui.MainActivity

/** Keeps a recitation download going while the app is in the background, with its progress in a notification. */
class DownloadService : Service() {

    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            QuranAudio.cancel()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra("title") ?: ""
        val surahs = intent?.getIntArrayExtra("surahs")?.toList() ?: emptyList()
        show(title, 0, surahs.size)
        if (running || surahs.isEmpty()) {
            if (!running) finish()
            return START_NOT_STICKY
        }
        running = true
        Thread {
            QuranAudio.cleanPartial(this)
            QuranAudio.runDownload(this, title, surahs) { show(it.title, it.done, it.total) }
            finish()
        }.start()
        return START_NOT_STICKY
    }

    private fun finish() {
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun show(title: String, done: Int, total: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تنزيل التلاوات", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, DownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("تنزيل $title")
            .setContentText("$done من $total")
            .setProgress(total.coerceAtLeast(1), done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "إلغاء", cancel).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val ID = 7002
        private const val CANCEL = "app.daab.audio.CANCEL_DOWNLOAD"

        fun start(ctx: Context, title: String, surahs: List<Int>) {
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra("title", title).putExtra("surahs", surahs.toIntArray())
            ctx.startForegroundService(i)
        }
    }
}
