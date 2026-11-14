package app.daab.habits.logic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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

    /** Days added to the Umm al-Qura date when the month was sighted earlier or later, from -2 to 2. */
    var hijriOffset by mutableIntStateOf(0)
        private set

    fun load(ctx: Context) {
        if (loaded) return
        read(ctx)
    }

    /** Reads every choice again, after a backup has replaced them. */
    fun read(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        theme = p.getInt("theme", THEME_SYSTEM)
        weekStart = p.getInt("week_start", 6)
        extendDay = p.getBoolean("extend_day", false)
        sort = p.getInt("sort", SORT_MANUAL)
        hideDone = p.getBoolean("hide_done", false)
        hijriOffset = p.getInt("hijri_offset", 0)
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

    fun setHijriOffset(ctx: Context, value: Int) {
        hijriOffset = value.coerceIn(-2, 2)
        edit(ctx).putInt("hijri_offset", hijriOffset).apply()
    }

    fun setHideDone(ctx: Context, value: Boolean) {
        hideDone = value
        edit(ctx).putBoolean("hide_done", value).apply()
    }

    /**
     * Every stored choice (theme, city, reciter, reader options and the rest) with its type,
     * so a restored backup brings all of them back exactly.
     */
    fun exportAll(ctx: Context): JSONObject {
        val out = JSONObject()
        for ((key, value) in ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
            // Where this phone keeps its own daily copies means nothing on another phone.
            if (key.startsWith("auto_backup")) continue
            val typed = when (value) {
                is Boolean -> JSONArray().put("b").put(value)
                is Int -> JSONArray().put("i").put(value)
                is Long -> JSONArray().put("l").put(value)
                is Float -> JSONArray().put("f").put(value.toDouble())
                is String -> JSONArray().put("s").put(value)
                else -> null
            }
            if (typed != null) out.put(key, typed)
        }
        return JSONObject().put("prefs", out)
    }

    /** Puts back the choices of a backup. Backups from before every choice was kept carry only the city. */
    fun importAll(ctx: Context, settings: JSONObject) {
        val e = edit(ctx)
        val prefs = settings.optJSONObject("prefs")
        if (prefs != null) {
            for (key in prefs.keys()) {
                val a = prefs.optJSONArray(key) ?: continue
                when (a.optString(0)) {
                    "b" -> e.putBoolean(key, a.getBoolean(1))
                    "i" -> e.putInt(key, a.getInt(1))
                    "l" -> e.putLong(key, a.getLong(1))
                    "f" -> e.putFloat(key, a.getDouble(1).toFloat())
                    "s" -> e.putString(key, a.getString(1))
                }
            }
        } else if (settings.has("city")) {
            e.putString("city", settings.getString("city"))
                .putFloat("lat", settings.getDouble("lat").toFloat())
                .putFloat("lng", settings.getDouble("lng").toFloat())
        }
        e.commit()
        read(ctx)
    }

    /** Minutes the logical day runs past midnight. */
    fun shiftMinutes(): Int = if (extendDay) EXTENSION_HOURS * 60 else 0
}
