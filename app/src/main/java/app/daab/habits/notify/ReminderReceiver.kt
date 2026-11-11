package app.daab.habits.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.daab.habits.R
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import app.daab.habits.ui.MainActivity
import app.daab.habits.widget.HabitWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Daily reminder per habit. The alarm fires every day and stays silent when the habit is not due. */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_FIRE = "app.daab.habits.notify.FIRE"
        private const val ACTION_DONE = "app.daab.habits.notify.DONE"
        private const val EXTRA_HABIT = "habit"
        private const val CHANNEL = "reminders"

        private fun firePending(ctx: Context, habitId: Long, flags: Int): PendingIntent? {
            val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_HABIT, habitId)
            return PendingIntent.getBroadcast(ctx, habitId.toInt(), i, flags or PendingIntent.FLAG_IMMUTABLE)
        }

        fun schedule(ctx: Context, h: Habit) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = firePending(ctx, h.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            alarms.cancel(pending)
            if (h.reminder < 0 || h.archived) return
            var at = LocalDate.now().atTime(h.reminder / 60, h.reminder % 60)
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }

        fun cancel(ctx: Context, habitId: Long) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            firePending(ctx, habitId, PendingIntent.FLAG_UPDATE_CURRENT)?.let { alarms.cancel(it) }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(habitId.toInt())
        }

        fun scheduleAll(ctx: Context) {
            for (h in Db.get(ctx).habits()) schedule(ctx, h)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val db = Db.get(ctx)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> scheduleAll(ctx)
            ACTION_DONE -> {
                val id = intent.getLongExtra(EXTRA_HABIT, 0)
                if (id > 0) {
                    db.setEntry(id, Schedule.today(), Mark.DONE)
                    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
                    HabitWidget.updateAll(ctx)
                }
            }
            ACTION_FIRE -> {
                val h = db.habit(intent.getLongExtra(EXTRA_HABIT, 0)) ?: return
                schedule(ctx, h)
                val today = Schedule.today()
                val entries = db.entries(h.id)
                val untouched = (entries[today] ?: Mark.NONE) == Mark.NONE
                if (untouched && Schedule.isDue(h, db.dates(h.id), entries, today)) notify(ctx, h)
            }
        }
    }

    private fun notify(ctx: Context, h: Habit) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير العادات", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, h.id.toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val done = PendingIntent.getBroadcast(
            ctx, h.id.toInt(),
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, h.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(h.name)
            .setContentText("حان وقت عادتك")
            .setColor(h.color)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "تم", done).build())
            .build()
        try {
            nm.notify(h.id.toInt(), n)
        } catch (e: SecurityException) {
        }
    }
}
