package app.daab.habits.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.daab.habits.R
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import app.daab.habits.ui.MainActivity

/**
 * One square on the home screen. It is bound either to a single habit or to "all habits",
 * in which case it shows the next habit still to do today and moves on after each tap.
 */
class HabitWidget : AppWidgetProvider() {

    companion object {
        const val ALL_HABITS = -1L
        private const val ACTION_TAP = "app.daab.habits.widget.TAP"
        private const val EXTRA_HABIT = "habit"
        private const val PREFS = "widgets"
        private const val NEUTRAL = 0xFF2C2C2A.toInt()
        private const val LIGHT = 0xFFF1EFE8.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DONE_ALL = 0xFF0F6E56.toInt()

        fun bind(ctx: Context, widgetId: Int, habitId: Long) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("w_$widgetId", habitId).apply()
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HabitWidget::class.java))
            for (id in ids) render(ctx, mgr, id)
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

        fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bound = prefs.getLong("w_$widgetId", ALL_HABITS)
            val db = Db.get(ctx)
            val today = Schedule.today()
            val views = RemoteViews(ctx.packageName, R.layout.habit_widget)

            fun show(bg: Int, fg: Int, mark: String, name: String, click: PendingIntent) {
                views.setInt(R.id.widget_bg, "setColorFilter", bg)
                views.setTextColor(R.id.widget_mark, fg)
                views.setTextColor(R.id.widget_name, fg)
                views.setTextViewText(R.id.widget_mark, mark)
                views.setTextViewText(R.id.widget_name, name)
                views.setOnClickPendingIntent(R.id.widget_root, click)
            }

            if (bound == ALL_HABITS) {
                val habits = db.habits()
                var next: Habit? = null
                for (h in habits) {
                    val entries = db.entries(h.id)
                    val v = entries[today] ?: Mark.NONE
                    if (v == Mark.NONE && Schedule.isDue(h, db.dates(h.id), entries, today)) {
                        next = h
                        break
                    }
                }
                when {
                    habits.isEmpty() -> show(NEUTRAL, LIGHT, "+", "أضف عادة", openApp(ctx, widgetId))
                    next == null -> show(DONE_ALL, WHITE, "✓", "أنجزت عادات اليوم", openApp(ctx, widgetId))
                    else -> show(NEUTRAL, next.color, "○", next.name, tap(ctx, widgetId, next.id))
                }
            } else {
                val h = db.habit(bound)
                if (h == null) {
                    show(NEUTRAL, LIGHT, "؟", "العادة محذوفة", openApp(ctx, widgetId))
                } else {
                    val entries = db.entries(h.id)
                    val v = entries[today] ?: Mark.NONE
                    val due = Schedule.isDue(h, db.dates(h.id), entries, today)
                    when {
                        v == Mark.DONE -> show(h.color, WHITE, "✓", h.name, tap(ctx, widgetId, h.id))
                        v == Mark.EXCUSED -> show(NEUTRAL, LIGHT, "ع", h.name, openApp(ctx, widgetId))
                        due -> show(NEUTRAL, h.color, "○", h.name, tap(ctx, widgetId, h.id))
                        else -> show(NEUTRAL, LIGHT, "·", h.name + "\nليست اليوم", openApp(ctx, widgetId))
                    }
                }
            }
            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, mgr, id)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (id in ids) e.remove("w_$id")
        e.apply()
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TAP) {
            val habitId = intent.getLongExtra(EXTRA_HABIT, 0)
            if (habitId > 0) {
                val db = Db.get(ctx)
                val today = Schedule.today()
                val current = db.entries(habitId)[today] ?: Mark.NONE
                db.setEntry(habitId, today, if (current == Mark.DONE) Mark.NONE else Mark.DONE)
            }
            updateAll(ctx)
            return
        }
        super.onReceive(ctx, intent)
    }
}
