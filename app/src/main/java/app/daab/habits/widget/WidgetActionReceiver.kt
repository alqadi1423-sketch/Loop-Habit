package app.daab.habits.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.daab.habits.data.Db
import app.daab.habits.data.Mark
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.ReminderReceiver

/** A tap on a square and the timed redraws. Not exported, so only the squares themselves can mark a habit. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            HabitWidget.ACTION_TAP -> {
                val habitId = intent.getLongExtra(HabitWidget.EXTRA_HABIT, 0)
                val db = Db.get(ctx)
                val prefs = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val h = db.habit(habitId)
                // Ignore a second tap that lands before the square has redrawn.
                if (h != null && now - prefs.getLong("tap_$habitId", 0) > 1500) {
                    prefs.edit().putLong("tap_$habitId", now).apply()
                    val today = Schedule.today()
                    val finished = db.entries(habitId)[today] == Mark.DONE
                    if (!(h.target > 0 && finished)) db.tap(h, today)
                }
                HabitWidget.updateAll(ctx)
                ReminderReceiver.clearHandled(ctx)
            }
            HabitWidget.ACTION_REFRESH -> HabitWidget.updateAll(ctx)
        }
    }
}
