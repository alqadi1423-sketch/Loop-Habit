package app.daab.habits.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import app.daab.habits.R
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Mark
import app.daab.habits.logic.Phase
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.Alarms
import app.daab.habits.ui.MainActivity
import app.daab.habits.ui.timeText
import java.time.LocalDate
import java.time.ZoneId

/**
 * One square on the home screen. It is bound to a single habit, to a linked group, or to
 * all habits. For a group or all habits it shows the habit whose time has come and moves
 * on once that habit is marked or its time passes.
 */
class HabitWidget : AppWidgetProvider() {

    companion object {
        const val ALL_HABITS = -1L
        private const val ACTION_TAP = "app.daab.habits.widget.TAP"
        private const val ACTION_REFRESH = "app.daab.habits.widget.REFRESH"
        private const val EXTRA_HABIT = "habit"
        private const val PREFS = "widgets"
        private const val NEUTRAL = 0xFF2C2C2A.toInt()
        private const val LIGHT = 0xFFF1EFE8.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DONE_ALL = 0xFF0F6E56.toInt()

        fun bind(ctx: Context, widgetId: Int, habitId: Long, groupId: Long) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("w_$widgetId", habitId).putLong("g_$widgetId", groupId).apply()
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HabitWidget::class.java))
            for (id in ids) render(ctx, mgr, id)
            if (ids.isNotEmpty()) scheduleRefresh(ctx)
        }

        /** Wakes the squares at the next moment a time window can open or close. */
        private fun scheduleRefresh(ctx: Context) {
            val today = Schedule.today()
            val now = Schedule.nowMinutes()
            val marks = ArrayList<Int>()
            marks.addAll(PrayerTimes.forDay(ctx, today).toList())
            for (h in Db.get(ctx).habits()) {
                if (h.startKind == Anchor.CLOCK) marks.add(h.startValue)
                if (h.endKind == Anchor.CLOCK) marks.add(h.endValue)
            }
            val next = marks.filter { it > now }.minOrNull() ?: 1440
            val at = LocalDate.ofEpochDay(today).atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(next.toLong()).plusSeconds(20).toInstant().toEpochMilli()
            val pending = PendingIntent.getBroadcast(
                ctx, -7, Intent(ctx, HabitWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Alarms.set(ctx, at, pending)
        }

        private fun openApp(ctx: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                ctx, widgetId, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun tap(ctx: Context, widgetId: Int, habitId: Long): PendingIntent {
            val i = Intent(ctx, HabitWidget::class.java).setAction(ACTION_TAP).putExtra(EXTRA_HABIT, habitId)
            return PendingIntent.getBroadcast(
                ctx, widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Opens the reading of a habit instead of marking it. */
        private fun read(ctx: Context, widgetId: Int, habitId: Long): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .setData(Uri.parse("daab://reader/$habitId"))
                .putExtra(MainActivity.EXTRA_READER, habitId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                ctx, widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private class Slot(val habit: Habit, val value: Int, val phase: Int, val start: Int)

        fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val boundHabit = prefs.getLong("w_$widgetId", ALL_HABITS)
            val boundGroup = prefs.getLong("g_$widgetId", 0)
            val db = Db.get(ctx)
            val today = Schedule.today()
            val now = Schedule.nowMinutes()
            val times = PrayerTimes.forDay(ctx, today)
            val views = RemoteViews(ctx.packageName, R.layout.habit_widget)

            fun show(bg: Int, fg: Int, mark: String, name: String, click: PendingIntent) {
                views.setInt(R.id.widget_bg, "setColorFilter", bg)
                views.setTextColor(R.id.widget_mark, fg)
                views.setTextColor(R.id.widget_name, fg)
                views.setTextViewText(R.id.widget_mark, mark)
                views.setTextViewText(R.id.widget_name, name)
                views.setOnClickPendingIntent(R.id.widget_root, click)
            }

            fun pending(h: Habit) {
                if (h.target > 0) {
                    val count = db.progress(h.id, today)[0] ?: 0
                    show(NEUTRAL, h.color, "$count/${h.target}", h.name, tap(ctx, widgetId, h.id))
                } else if (h.contentRef != 0) {
                    val percent = (db.partials(h.id)[today] ?: 0) / 10
                    show(NEUTRAL, h.color, if (percent > 0) "$percent%" else "○", h.name, read(ctx, widgetId, h.id))
                } else {
                    show(NEUTRAL, h.color, "○", h.name, tap(ctx, widgetId, h.id))
                }
            }

            fun done(h: Habit) = show(
                h.color, WHITE, "✓", h.name,
                if (h.contentRef != 0) read(ctx, widgetId, h.id) else tap(ctx, widgetId, h.id)
            )

            if (boundGroup == 0L && boundHabit != ALL_HABITS) {
                val h = db.habit(boundHabit)
                if (h == null) {
                    show(NEUTRAL, LIGHT, "؟", "العادة محذوفة", openApp(ctx, widgetId))
                } else {
                    val entries = db.entries(h.id)
                    val v = entries[today] ?: Mark.NONE
                    val due = Schedule.isDue(h, db.dates(h.id), entries, today)
                    val phase = Schedule.phase(h, times, now)
                    when {
                        v == Mark.DONE -> done(h)
                        v == Mark.EXCUSED -> show(NEUTRAL, LIGHT, "ع", h.name, openApp(ctx, widgetId))
                        !due -> show(NEUTRAL, LIGHT, "·", h.name + "\nليست اليوم", openApp(ctx, widgetId))
                        phase == Phase.BEFORE ->
                            show(NEUTRAL, LIGHT, "·", h.name + "\n" + timeText(Schedule.window(h, times).first), openApp(ctx, widgetId))
                        phase == Phase.CLOSED -> show(NEUTRAL, LIGHT, "×", h.name + "\nفائتة", openApp(ctx, widgetId))
                        else -> pending(h)
                    }
                }
                mgr.updateAppWidget(widgetId, views)
                return
            }

            val all = db.habits()
            val pool = if (boundGroup != 0L) all.filter { it.groupId == boundGroup } else all
            val slots = ArrayList<Slot>()
            for (h in pool) {
                val entries = db.entries(h.id)
                if (!Schedule.isDue(h, db.dates(h.id), entries, today)) continue
                slots.add(Slot(h, entries[today] ?: Mark.NONE, Schedule.phase(h, times, now), Schedule.window(h, times).first))
            }
            val open = slots.firstOrNull { it.phase == Phase.OPEN && it.value == Mark.NONE }
            val later = slots.filter { it.phase == Phase.BEFORE && it.value == Mark.NONE }.minByOrNull { it.start }
            val missed = slots.any { it.phase == Phase.CLOSED && it.value == Mark.NONE }
            val current = if (boundGroup != 0L) {
                slots.filter { it.phase == Phase.OPEN && it.value == Mark.DONE && Schedule.hasWindow(it.habit) }.maxByOrNull { it.start }
            } else null
            when {
                pool.isEmpty() -> show(NEUTRAL, LIGHT, "+", "أضف عادة", openApp(ctx, widgetId))
                open != null -> pending(open.habit)
                current != null -> done(current.habit)
                later != null -> show(NEUTRAL, LIGHT, "·", later.habit.name + "\n" + timeText(later.start), openApp(ctx, widgetId))
                missed -> show(NEUTRAL, LIGHT, "·", "انتهى وقت عادات اليوم", openApp(ctx, widgetId))
                else -> show(DONE_ALL, WHITE, "✓", "أنجزت عادات اليوم", openApp(ctx, widgetId))
            }
            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, mgr, id)
        scheduleRefresh(ctx)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (id in ids) e.remove("w_$id").remove("g_$id")
        e.apply()
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAP -> {
                val habitId = intent.getLongExtra(EXTRA_HABIT, 0)
                val db = Db.get(ctx)
                db.habit(habitId)?.let { db.tap(it, Schedule.today()) }
                updateAll(ctx)
            }
            ACTION_REFRESH -> updateAll(ctx)
            else -> super.onReceive(ctx, intent)
        }
    }
}
