package app.daab.habits.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

object Alarms {
    /** Fires at the exact minute when the system allows it, otherwise as close as it permits. */
    fun set(ctx: Context, atMillis: Long, pending: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
