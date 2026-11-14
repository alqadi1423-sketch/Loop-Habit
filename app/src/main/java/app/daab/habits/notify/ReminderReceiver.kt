package app.daab.habits.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.daab.habits.R
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Mark
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.ui.MainActivity
import app.daab.habits.widget.HabitWidget
import java.time.LocalDate
import java.time.ZoneId

/** System events that move or wipe alarms: a restart, an update, a change of clock or time zone. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val pending = goAsync()
                Thread {
                    try {
                        ReminderReceiver.scheduleAll(ctx)
                        HabitWidget.updateAll(ctx)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}

/**
 * Reminders of a habit: at a fixed clock time, when its time window opens, and shortly before
 * the window closes. Each alarm fires every day and stays silent when the habit is not due
 * or is already marked.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_FIRE = "app.daab.habits.notify.FIRE"
        private const val ACTION_DONE = "app.daab.habits.notify.DONE"
        private const val ACTION_SNOOZE = "app.daab.habits.notify.SNOOZE"
        private const val EXTRA_HABIT = "habit"
        private const val EXTRA_KIND = "kind"
        private const val CHANNEL = "reminders"
        private const val CLOCK = 0
        private const val WINDOW_START = 1
        private const val WINDOW_END = 2
        private const val SNOOZED = 3
        private const val SNOOZE_MINUTES = 15L

        private fun firePending(ctx: Context, habitId: Long, kind: Int): PendingIntent {
            val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
                .putExtra(EXTRA_HABIT, habitId).putExtra(EXTRA_KIND, kind)
            return PendingIntent.getBroadcast(
                ctx, (habitId * 4 + kind).toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Minute of the day this kind of reminder fires on [day], or -1 when the habit has none. */
        private fun minuteFor(ctx: Context, h: Habit, kind: Int, day: Long): Int {
            val times = PrayerTimes.forDay(ctx, day)
            val (start, end) = Schedule.window(h, times)
            return when (kind) {
                CLOCK -> h.reminder
                WINDOW_START -> if (h.remindStart && h.startKind != Anchor.NONE) start else -1
                WINDOW_END -> if (h.remindEnd > 0 && Schedule.hasWindow(h)) (end - h.remindEnd).coerceAtLeast(start) else -1
                else -> -1
            }
        }

        fun schedule(ctx: Context, h: Habit) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            val today = Schedule.today()
            for (kind in CLOCK..WINDOW_END) {
                val pending = firePending(ctx, h.id, kind)
                alarms.cancel(pending)
                if (h.archived) continue
                for (day in today..today + 1) {
                    val minute = minuteFor(ctx, h, kind, day)
                    if (minute < 0) break
                    val at = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault())
                        .plusMinutes(minute.toLong()).toInstant().toEpochMilli()
                    if (at > now + 1000) {
                        Alarms.set(ctx, at, pending)
                        break
                    }
                }
            }
        }

        fun cancel(ctx: Context, habitId: Long) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (kind in CLOCK..SNOOZED) alarms.cancel(firePending(ctx, habitId, kind))
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(habitId.toInt())
        }

        fun scheduleAll(ctx: Context) {
            for (h in Db.get(ctx).habits()) schedule(ctx, h)
        }

        /** Takes down reminders of habits already marked today, wherever they were marked. */
        fun clearHandled(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shown = try {
                nm.activeNotifications
            } catch (e: Exception) {
                return
            }
            if (shown.isEmpty()) return
            val db = Db.get(ctx)
            val today = Schedule.today()
            for (n in shown) {
                if (n.notification.channelId != CHANNEL) continue
                val marked = (db.entries(n.id.toLong())[today] ?: Mark.NONE) != Mark.NONE
                if (marked) nm.cancel(n.id)
            }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val db = Db.get(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = intent.getLongExtra(EXTRA_HABIT, 0)
        when (intent.action) {
            ACTION_DONE -> if (id > 0) {
                db.setMark(id, Schedule.today(), Mark.DONE)
                nm.cancel(id.toInt())
                HabitWidget.updateAll(ctx)
            }
            ACTION_SNOOZE -> if (id > 0) {
                nm.cancel(id.toInt())
                Alarms.set(ctx, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000, firePending(ctx, id, SNOOZED))
            }
            ACTION_FIRE -> {
                val h = db.habit(id) ?: return
                val kind = intent.getIntExtra(EXTRA_KIND, CLOCK)
                if (kind != SNOOZED) schedule(ctx, h)
                val today = Schedule.today()
                val entries = db.entries(h.id)
                val untouched = (entries[today] ?: Mark.NONE) == Mark.NONE
                if (untouched && Schedule.isDue(h, db.dates(h.id), entries, today)) {
                    val text = when (kind) {
                        WINDOW_START -> "دخل وقتها الآن"
                        WINDOW_END -> "بقي ${h.remindEnd} دقيقة على خروج وقتها"
                        else -> "حان وقت عادتك"
                    }
                    notify(ctx, nm, h, text)
                }
            }
        }
    }

    private fun notify(ctx: Context, nm: NotificationManager, h: Habit, text: String) {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير العادات", NotificationManager.IMPORTANCE_DEFAULT))
        val openIntent = Intent(ctx, MainActivity::class.java).setData(Uri.parse("daab://open/habit/${h.id}"))
        if (h.contentRef != 0) openIntent.putExtra(MainActivity.EXTRA_READER, h.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(ctx, h.id.toInt(), openIntent, flags)
        fun action(name: String) = PendingIntent.getBroadcast(
            ctx, h.id.toInt(), Intent(ctx, ReminderReceiver::class.java).setAction(name).putExtra(EXTRA_HABIT, h.id), flags
        )
        val builder = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(h.name)
            .setContentText(text)
            .setColor(h.color)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (h.contentRef == 0 && h.target == 0) {
            builder.addAction(Notification.Action.Builder(null, "تم", action(ACTION_DONE)).build())
        }
        builder.addAction(Notification.Action.Builder(null, "ذكّرني بعد ربع ساعة", action(ACTION_SNOOZE)).build())
        try {
            nm.notify(h.id.toInt(), builder.build())
        } catch (e: SecurityException) {
        }
    }
}
