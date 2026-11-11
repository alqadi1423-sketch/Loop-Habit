package app.daab.habits.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "daab.db", null, 1) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE habits(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INTEGER NOT NULL, " +
                "kind INTEGER NOT NULL, weekdays INTEGER NOT NULL, per_week INTEGER NOT NULL, " +
                "reminder INTEGER NOT NULL, archived INTEGER NOT NULL, position INTEGER NOT NULL, " +
                "created INTEGER NOT NULL)"
        )
        db.execSQL("CREATE TABLE habit_dates(habit_id INTEGER NOT NULL, day INTEGER NOT NULL, PRIMARY KEY(habit_id, day))")
        db.execSQL("CREATE TABLE entries(habit_id INTEGER NOT NULL, day INTEGER NOT NULL, value INTEGER NOT NULL, note TEXT, PRIMARY KEY(habit_id, day))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    fun habits(includeArchived: Boolean = false): List<Habit> {
        val out = ArrayList<Habit>()
        val where = if (includeArchived) null else "archived=0"
        readableDatabase.query("habits", null, where, null, null, null, "position, id").use { c ->
            while (c.moveToNext()) {
                out.add(
                    Habit(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        color = c.getInt(c.getColumnIndexOrThrow("color")),
                        kind = c.getInt(c.getColumnIndexOrThrow("kind")),
                        weekdays = c.getInt(c.getColumnIndexOrThrow("weekdays")),
                        perWeek = c.getInt(c.getColumnIndexOrThrow("per_week")),
                        reminder = c.getInt(c.getColumnIndexOrThrow("reminder")),
                        archived = c.getInt(c.getColumnIndexOrThrow("archived")) != 0,
                        position = c.getInt(c.getColumnIndexOrThrow("position")),
                        created = c.getLong(c.getColumnIndexOrThrow("created"))
                    )
                )
            }
        }
        return out
    }

    fun habit(id: Long): Habit? = habits(true).firstOrNull { it.id == id }

    private fun values(h: Habit) = ContentValues().apply {
        put("name", h.name)
        put("color", h.color)
        put("kind", h.kind)
        put("weekdays", h.weekdays)
        put("per_week", h.perWeek)
        put("reminder", h.reminder)
        put("archived", if (h.archived) 1 else 0)
        put("position", h.position)
        put("created", h.created)
    }

    fun save(h: Habit): Long {
        val db = writableDatabase
        return if (h.id == 0L) {
            db.insert("habits", null, values(h))
        } else {
            db.update("habits", values(h), "id=?", arrayOf(h.id.toString()))
            h.id
        }
    }

    fun delete(id: Long) {
        val db = writableDatabase
        val a = arrayOf(id.toString())
        db.delete("habits", "id=?", a)
        db.delete("habit_dates", "habit_id=?", a)
        db.delete("entries", "habit_id=?", a)
    }

    fun dates(habitId: Long): Set<Long> {
        val out = HashSet<Long>()
        readableDatabase.rawQuery("SELECT day FROM habit_dates WHERE habit_id=?", arrayOf(habitId.toString())).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    fun setDates(habitId: Long, days: Set<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("habit_dates", "habit_id=?", arrayOf(habitId.toString()))
            for (d in days) {
                db.insert("habit_dates", null, ContentValues().apply {
                    put("habit_id", habitId)
                    put("day", d)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun entries(habitId: Long): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        readableDatabase.rawQuery("SELECT day, value FROM entries WHERE habit_id=?", arrayOf(habitId.toString())).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
        }
        return out
    }

    fun setEntry(habitId: Long, day: Long, value: Int) {
        val db = writableDatabase
        if (value == Mark.NONE) {
            db.delete("entries", "habit_id=? AND day=?", arrayOf(habitId.toString(), day.toString()))
        } else {
            db.insertWithOnConflict("entries", null, ContentValues().apply {
                put("habit_id", habitId)
                put("day", day)
                put("value", value)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "daab")
        root.put("version", 1)
        val hs = JSONArray()
        for (h in habits(true)) {
            val o = JSONObject()
            o.put("id", h.id)
            o.put("name", h.name)
            o.put("color", h.color)
            o.put("kind", h.kind)
            o.put("weekdays", h.weekdays)
            o.put("perWeek", h.perWeek)
            o.put("reminder", h.reminder)
            o.put("archived", h.archived)
            o.put("position", h.position)
            o.put("created", h.created)
            o.put("dates", JSONArray(dates(h.id).sorted()))
            val es = JSONArray()
            for ((day, value) in entries(h.id).toSortedMap()) {
                es.put(JSONArray().put(day).put(value))
            }
            o.put("entries", es)
            hs.put(o)
        }
        root.put("habits", hs)
        return root.toString(2)
    }

    fun importJson(text: String) {
        val root = JSONObject(text)
        if (root.optString("app") != "daab") throw IllegalArgumentException("not a daab backup")
        val hs = root.getJSONArray("habits")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("habits", null, null)
            db.delete("habit_dates", null, null)
            db.delete("entries", null, null)
            for (i in 0 until hs.length()) {
                val o = hs.getJSONObject(i)
                val id = db.insert(
                    "habits", null, values(
                        Habit(
                            name = o.getString("name"),
                            color = o.getInt("color"),
                            kind = o.getInt("kind"),
                            weekdays = o.getInt("weekdays"),
                            perWeek = o.getInt("perWeek"),
                            reminder = o.getInt("reminder"),
                            archived = o.getBoolean("archived"),
                            position = o.getInt("position"),
                            created = o.getLong("created")
                        )
                    )
                )
                val ds = o.getJSONArray("dates")
                for (j in 0 until ds.length()) {
                    db.insert("habit_dates", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", ds.getLong(j))
                    })
                }
                val es = o.getJSONArray("entries")
                for (j in 0 until es.length()) {
                    val e = es.getJSONArray(j)
                    db.insert("entries", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", e.getLong(0))
                        put("value", e.getInt(1))
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
