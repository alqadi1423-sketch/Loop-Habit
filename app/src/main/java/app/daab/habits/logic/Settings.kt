package app.daab.habits.logic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide choices. Loaded once per process, before anything asks what day it is. */
object Settings {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val SORT_MANUAL = 0
    const val SORT_NAME = 1
    const val SORT_COLOR = 2
    const val SORT_SCORE = 3
    const val SORT_STATUS = 4

    /** Hours after midnight that still belong to the day before, when the day is extended. */
    const val EXTENSION_HOURS = 3

    private const val PREFS = "settings"
    private var loaded = false

    var theme by mutableIntStateOf(THEME_SYSTEM)
        private set

    /** First day of the week, with Sunday as 0 and Saturday as 6. */
    var weekStart by mutableIntStateOf(6)
        private set

    var extendDay by mutableStateOf(false)
        private set

    var sort by mutableIntStateOf(SORT_MANUAL)
        private set

    var hideDone by mutableStateOf(false)
        private set

    fun load(ctx: Context) {
        if (loaded) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        theme = p.getInt("theme", THEME_SYSTEM)
        weekStart = p.getInt("week_start", 6)
        extendDay = p.getBoolean("extend_day", false)
        sort = p.getInt("sort", SORT_MANUAL)
        hideDone = p.getBoolean("hide_done", false)
        loaded = true
    }

    private fun edit(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()

    fun setTheme(ctx: Context, value: Int) {
        theme = value
        edit(ctx).putInt("theme", value).apply()
    }

    fun setWeekStart(ctx: Context, value: Int) {
        weekStart = value
        edit(ctx).putInt("week_start", value).apply()
    }

    fun setExtendDay(ctx: Context, value: Boolean) {
        extendDay = value
        edit(ctx).putBoolean("extend_day", value).apply()
    }

    fun setSort(ctx: Context, value: Int) {
        sort = value
        edit(ctx).putInt("sort", value).apply()
    }

    fun setHideDone(ctx: Context, value: Boolean) {
        hideDone = value
        edit(ctx).putBoolean("hide_done", value).apply()
    }

    /** Minutes the logical day runs past midnight. */
    fun shiftMinutes(): Int = if (extendDay) EXTENSION_HOURS * 60 else 0
}
