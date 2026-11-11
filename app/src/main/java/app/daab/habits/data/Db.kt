package app.daab.habits.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "daab.db", null, 2) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also { instance = it }
            }

        private val windowColumns = listOf("group_id", "start_kind", "start_value", "end_kind", "end_value")
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
        addGroups(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addGroups(db)
    }

    private fun addGroups(db: SQLiteDatabase) {
        for (c in windowColumns) db.execSQL("ALTER TABLE habits ADD COLUMN $c INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INTEGER NOT NULL, position INTEGER NOT NULL)")
    }

    fun habits(includeArchived: Boolean = false): List<Habit> {
        val out = ArrayList<Habit>()
        val where = if (includeArchived) null else "archived=0"
        readableDatabase.query("habits", null, where, null, null, null, "position, id").use { c ->
            fun int(name: String) = c.getInt(c.getColumnIndexOrThrow(name))
            while (c.moveToNext()) {
                out.add(
                    Habit(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        color = int("color"),
                        kind = int("kind"),
                        weekdays = int("weekdays"),
                        perWeek = int("per_week"),
                        reminder = int("reminder"),
                        archived = int("archived") != 0,
                        position = int("position"),
                        created = c.getLong(c.getColumnIndexOrThrow("created")),
                        groupId = c.getLong(c.getColumnIndexOrThrow("group_id")),
                        startKind = int("start_kind"),
                        startValue = int("start_value"),
                        endKind = int("end_kind"),
                        endValue = int("end_value")
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
        put("group_id", h.groupId)
        put("start_kind", h.startKind)
        put("start_value", h.startValue)
        put("end_kind", h.endKind)
        put("end_value", h.endValue)
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

    fun groups(): List<Group> {
        val out = ArrayList<Group>()
        readableDatabase.query("groups", null, null, null, null, null, "position, id").use { c ->
            while (c.moveToNext()) {
                out.add(Group(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
            }
        }
        return out
    }

    fun saveGroup(g: Group): Long {
        val v = ContentValues().apply {
            put("name", g.name)
            put("color", g.color)
            put("position", g.position)
        }
        return if (g.id == 0L) writableDatabase.insert("groups", null, v)
        else {
            writableDatabase.update("groups", v, "id=?", arrayOf(g.id.toString()))
            g.id
        }
    }

    /** Removes the group and leaves its habits as standalone habits with their records intact. */
    fun deleteGroup(id: Long) {
        val db = writableDatabase
        db.execSQL("UPDATE habits SET group_id=0 WHERE group_id=?", arrayOf(id.toString()))
        db.delete("groups", "id=?", arrayOf(id.toString()))
    }

    private fun addToGroup(groupId: Long, name: String, color: Int, today: Long, startPrayer: Int, endPrayer: Int) {
        save(
            Habit(
                name = name, color = color, created = today, groupId = groupId,
                position = habits(true).size,
                startKind = Anchor.PRAYER, startValue = startPrayer,
                endKind = if (endPrayer < 0) Anchor.NONE else Anchor.PRAYER,
                endValue = if (endPrayer < 0) 0 else endPrayer
            )
        )
    }

    fun addPrayerGroup(today: Long) {
        val color = Palette.colors[0]
        val g = saveGroup(Group(name = "الصلوات", color = color, position = groups().size))
        addToGroup(g, "الفجر", color, today, 0, 1)
        addToGroup(g, "الظهر", color, today, 2, 3)
        addToGroup(g, "العصر", color, today, 3, 4)
        addToGroup(g, "المغرب", color, today, 4, 5)
        addToGroup(g, "العشاء", color, today, 5, -1)
    }

    fun addAdhkarGroup(today: Long) {
        val color = Palette.colors[2]
        val g = saveGroup(Group(name = "الأذكار", color = color, position = groups().size))
        addToGroup(g, "أذكار الصباح", color, today, 0, 2)
        addToGroup(g, "أذكار المساء", color, today, 3, -1)
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
        root.put("version", 2)
        val gs = JSONArray()
        for (g in groups()) {
            gs.put(JSONObject().put("id", g.id).put("name", g.name).put("color", g.color).put("position", g.position))
        }
        root.put("groups", gs)
        val hs = JSONArray()
        for (h in habits(true)) {
            val o = JSONObject()
            o.put("name", h.name)
            o.put("color", h.color)
            o.put("kind", h.kind)
            o.put("weekdays", h.weekdays)
            o.put("perWeek", h.perWeek)
            o.put("reminder", h.reminder)
            o.put("archived", h.archived)
            o.put("position", h.position)
            o.put("created", h.created)
            o.put("groupId", h.groupId)
            o.put("startKind", h.startKind)
            o.put("startValue", h.startValue)
            o.put("endKind", h.endKind)
            o.put("endValue", h.endValue)
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

    /** Replaces everything with the backup. Files from the first version, without groups, still load. */
    fun importJson(text: String) {
        val root = JSONObject(text)
        if (root.optString("app") != "daab") throw IllegalArgumentException("not a daab backup")
        val hs = root.getJSONArray("habits")
        val gs = root.optJSONArray("groups") ?: JSONArray()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("habits", null, null)
            db.delete("habit_dates", null, null)
            db.delete("entries", null, null)
            db.delete("groups", null, null)
            val groupIds = HashMap<Long, Long>()
            for (i in 0 until gs.length()) {
                val o = gs.getJSONObject(i)
                val newId = db.insert("groups", null, ContentValues().apply {
                    put("name", o.getString("name"))
                    put("color", o.getInt("color"))
                    put("position", o.optInt("position"))
                })
                groupIds[o.getLong("id")] = newId
            }
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
                            created = o.getLong("created"),
                            groupId = groupIds[o.optLong("groupId")] ?: 0,
                            startKind = o.optInt("startKind"),
                            startValue = o.optInt("startValue"),
                            endKind = o.optInt("endKind"),
                            endValue = o.optInt("endValue")
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
