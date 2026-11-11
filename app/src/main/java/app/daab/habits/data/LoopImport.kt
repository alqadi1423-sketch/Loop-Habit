package app.daab.habits.data

import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlin.math.roundToInt

/** What an import brought in. */
class ImportResult(val habits: Int, val days: Int)

/**
 * Reads a full backup made by the Loop Habit Tracker app and adds its habits and their
 * whole record to this app. Only the file's data is read. A habit that already exists here
 * under the same name receives the record instead of being created twice.
 */
object LoopImport {

    private const val DAY_MILLIS = 86_400_000L
    private const val LOOP_SKIP = 3
    private const val LOOP_YES = 2

    fun run(db: Db, file: File, today: Long): ImportResult {
        val source = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val columns = HashSet<String>()
            source.rawQuery("PRAGMA table_info(Habits)", null).use { c ->
                while (c.moveToNext()) columns.add(c.getString(1).lowercase())
            }
            if ("name" !in columns) throw IllegalArgumentException("not a Loop backup")
            val hasValue = source.rawQuery("PRAGMA table_info(Repetitions)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1).equals("value", true)) found = true
                found
            }
            fun pick(name: String, fallback: String) = if (name in columns) name else fallback

            val existing = db.habits(true).associateBy { it.name.trim() }
            var habits = 0
            var days = 0
            val query = "SELECT id, name, color, freq_num, freq_den, archived, position, reminder_hour, reminder_min, " +
                pick("type", "0") + ", " + pick("target_value", "0") + " FROM Habits ORDER BY position"
            source.rawQuery(query, null).use { c ->
                while (c.moveToNext()) {
                    val loopId = c.getLong(0)
                    val name = (c.getString(1) ?: "").trim()
                    if (name.isEmpty()) continue
                    val num = c.getInt(3).coerceAtLeast(1)
                    val den = c.getInt(4).coerceAtLeast(1)
                    val numeric = c.getInt(9) == 1
                    val target = c.getDouble(10)

                    val marks = HashMap<Long, Int>()
                    val sql = "SELECT timestamp, " + (if (hasValue) "value" else LOOP_YES.toString()) +
                        " FROM Repetitions WHERE habit=?"
                    source.rawQuery(sql, arrayOf(loopId.toString())).use { r ->
                        while (r.moveToNext()) {
                            val day = Math.floorDiv(r.getLong(0), DAY_MILLIS)
                            val value = r.getInt(1)
                            val mark = when {
                                value == LOOP_SKIP && !numeric -> Mark.EXCUSED
                                numeric -> if (value / 1000.0 >= target && value > 0) Mark.DONE else Mark.NONE
                                value == LOOP_YES -> Mark.DONE
                                else -> Mark.NONE
                            }
                            if (mark != Mark.NONE) marks[day] = mark
                        }
                    }

                    val known = existing[name]
                    val id = if (known != null) known.id else {
                        val reminder = if (c.isNull(7) || c.isNull(8)) -1 else c.getInt(7) * 60 + c.getInt(8)
                        db.save(
                            Habit(
                                name = name,
                                color = Palette.colors[Math.floorMod(c.getInt(2), Palette.colors.size)],
                                kind = if (num >= den) Kind.DAILY else Kind.PER_WEEK,
                                perWeek = (7.0 * num / den).roundToInt().coerceIn(1, 7),
                                reminder = reminder,
                                archived = c.getInt(5) != 0,
                                position = db.habits(true).size,
                                created = marks.keys.minOrNull() ?: today,
                                target = if (numeric) target.roundToInt().coerceAtLeast(0) else 0
                            )
                        )
                    }
                    if (known != null && marks.isNotEmpty() && marks.keys.min() < known.created) {
                        db.save(known.copy(created = marks.keys.min()))
                    }
                    db.addEntries(id, marks)
                    habits++
                    days += marks.size
                }
            }
            return ImportResult(habits, days)
        } finally {
            source.close()
        }
    }
}
