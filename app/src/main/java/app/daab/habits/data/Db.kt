package app.daab.habits.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import app.daab.habits.logic.Settings

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "daab.db", null, 6) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also {
                    Settings.load(ctx)
                    instance = it
                }
            }

        private val windowColumns = listOf("group_id", "start_kind", "start_value", "end_kind", "end_value")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        setWriteAheadLoggingEnabled(true)
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
        addReading(db)
        addQuran(db)
        addExtras(db)
        addCounters(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addGroups(db)
        if (oldVersion < 3) addReading(db)
        if (oldVersion < 4) addQuran(db)
        if (oldVersion < 5) addExtras(db)
        if (oldVersion < 6) addCounters(db)
    }

    private fun addCounters(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN counter_id INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN counter_effect INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE counters(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, position INTEGER NOT NULL)")
    }

    fun counters(): List<Counter> {
        val out = ArrayList<Counter>()
        readableDatabase.rawQuery("SELECT id, name, position FROM counters ORDER BY position, id", null).use { c ->
            while (c.moveToNext()) out.add(Counter(c.getLong(0), c.getString(1), c.getInt(2)))
        }
        return out
    }

    fun saveCounter(counter: Counter): Long {
        val v = ContentValues().apply {
            put("name", counter.name)
            put("position", counter.position)
        }
        return if (counter.id == 0L) writableDatabase.insert("counters", null, v)
        else {
            writableDatabase.update("counters", v, "id=?", arrayOf(counter.id.toString()))
            counter.id
        }
    }

    /** Removes the counter. Its habits and their records stay, they just stop feeding it. */
    fun deleteCounter(id: Long) {
        writableDatabase.execSQL("UPDATE habits SET counter_id=0, counter_effect=0 WHERE counter_id=?", arrayOf(id.toString()))
        writableDatabase.delete("counters", "id=?", arrayOf(id.toString()))
    }

    /** How many days the habit has been marked done, over its whole record. */
    fun doneCount(habitId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM entries WHERE habit_id=? AND value=" + Mark.DONE, arrayOf(habitId.toString())
        ).use { c -> if (c.moveToNext()) return c.getInt(0) }
        return 0
    }

    private fun addExtras(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN remind_start INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN remind_end INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN target INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE custom_items(habit_id INTEGER NOT NULL, item_id INTEGER NOT NULL, text TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(habit_id, item_id))")
    }

    /** Adhkar the user wrote for a habit, in their order. */
    fun customItems(habitId: Long): List<Dhikr> {
        val out = ArrayList<Dhikr>()
        readableDatabase.rawQuery(
            "SELECT item_id, text, count FROM custom_items WHERE habit_id=? ORDER BY item_id", arrayOf(habitId.toString())
        ).use { c -> while (c.moveToNext()) out.add(Dhikr(c.getInt(0), c.getString(1), c.getInt(2))) }
        return out
    }

    fun setCustomItems(habitId: Long, items: List<Dhikr>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("custom_items", "habit_id=?", arrayOf(habitId.toString()))
            items.forEachIndexed { i, it ->
                db.insert("custom_items", null, ContentValues().apply {
                    put("habit_id", habitId)
                    put("item_id", i + 1)
                    put("text", it.text)
                    put("count", it.count)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Sets a day's mark from the record calendar and keeps the reading progress in step with
     * it, so a cleared day no longer counts as read and a day marked done counts in full.
     */
    fun setMark(habitId: Long, day: Long, value: Int) {
        setEntry(habitId, day, value)
        val a = arrayOf(habitId.toString(), day.toString())
        if (value == Mark.DONE) {
            setPartial(habitId, day, 1000)
        } else {
            writableDatabase.delete("partials", "habit_id=? AND day=?", a)
            writableDatabase.delete("progress", "habit_id=? AND day=?", a)
            writableDatabase.execSQL("UPDATE quran_day SET reached=start WHERE habit_id=? AND day=?", a)
        }
    }

    /** The most recent Quran portion before [day], as day, start, end and how far it was read. */
    fun lastQuranDay(habitId: Long, day: Long): LongArray? {
        readableDatabase.rawQuery(
            "SELECT day, start, end, reached FROM quran_day WHERE habit_id=? AND day<? ORDER BY day DESC LIMIT 1",
            arrayOf(habitId.toString(), day.toString())
        ).use { c -> if (c.moveToNext()) return longArrayOf(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3)) }
        return null
    }

    /** One tap on a habit: a counted habit moves one step toward its target, a plain one flips. */
    fun tap(h: Habit, day: Long) {
        val current = entries(h.id)[day] ?: Mark.NONE
        if (h.target <= 0) {
            setEntry(h.id, day, if (current == Mark.DONE) Mark.NONE else Mark.DONE)
            return
        }
        if (current == Mark.DONE) {
            setEntry(h.id, day, Mark.NONE)
            setProgress(h.id, day, 0, 0)
            setPartial(h.id, day, 0)
            return
        }
        val count = (progress(h.id, day)[0] ?: 0) + 1
        setProgress(h.id, day, 0, count)
        setPartial(h.id, day, (count * 1000 / h.target).coerceAtMost(1000))
        if (count >= h.target) setEntry(h.id, day, Mark.DONE)
    }

    private fun addQuran(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN content_unit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN content_amount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE TABLE quran_state(habit_id INTEGER PRIMARY KEY, position INTEGER NOT NULL, plan_end INTEGER NOT NULL, khatmas INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE quran_day(habit_id INTEGER NOT NULL, day INTEGER NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, reached INTEGER NOT NULL, PRIMARY KEY(habit_id, day))")
    }

    /** Where the reader stands in the Quran and the last day of a timed khatma plan. */
    fun quranState(habitId: Long): Pair<Int, Long> {
        readableDatabase.rawQuery("SELECT position, plan_end FROM quran_state WHERE habit_id=?", arrayOf(habitId.toString())).use { c ->
            if (c.moveToNext()) return c.getInt(0) to c.getLong(1)
        }
        return 0 to 0L
    }

    fun khatmas(habitId: Long): Int {
        readableDatabase.rawQuery("SELECT khatmas FROM quran_state WHERE habit_id=?", arrayOf(habitId.toString())).use { c ->
            if (c.moveToNext()) return c.getInt(0)
        }
        return 0
    }

    fun setQuranState(habitId: Long, position: Int, planEnd: Long, addKhatma: Boolean = false) {
        val done = khatmas(habitId) + if (addKhatma) 1 else 0
        writableDatabase.insertWithOnConflict("quran_state", null, ContentValues().apply {
            put("habit_id", habitId)
            put("position", position)
            put("plan_end", planEnd)
            put("khatmas", done)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun quranDay(habitId: Long, day: Long): QuranRange? {
        readableDatabase.rawQuery(
            "SELECT start, end, reached FROM quran_day WHERE habit_id=? AND day=?", arrayOf(habitId.toString(), day.toString())
        ).use { c -> if (c.moveToNext()) return QuranRange(c.getInt(0), c.getInt(1), c.getInt(2)) }
        return null
    }

    fun setQuranDay(habitId: Long, day: Long, start: Int, end: Int, reached: Int) {
        writableDatabase.insertWithOnConflict("quran_day", null, ContentValues().apply {
            put("habit_id", habitId)
            put("day", day)
            put("start", start)
            put("end", end)
            put("reached", reached)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Forgets today's portion so it is worked out again, used after the habit's amount changes. */
    fun clearQuranDay(habitId: Long, day: Long) {
        writableDatabase.delete("quran_day", "habit_id=? AND day=? AND reached=start", arrayOf(habitId.toString(), day.toString()))
    }

    private fun addReading(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN content_ref INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN content_part INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE progress(habit_id INTEGER NOT NULL, day INTEGER NOT NULL, item_id INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(habit_id, day, item_id))")
        db.execSQL("CREATE TABLE partials(habit_id INTEGER NOT NULL, day INTEGER NOT NULL, permille INTEGER NOT NULL, PRIMARY KEY(habit_id, day))")
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
                        endValue = int("end_value"),
                        contentRef = int("content_ref"),
                        contentPart = int("content_part"),
                        contentUnit = int("content_unit"),
                        contentAmount = int("content_amount"),
                        remindStart = int("remind_start") != 0,
                        remindEnd = int("remind_end"),
                        target = int("target"),
                        counterId = c.getLong(c.getColumnIndexOrThrow("counter_id")),
                        counterEffect = int("counter_effect")
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
        put("content_ref", h.contentRef)
        put("content_part", h.contentPart)
        put("content_unit", h.contentUnit)
        put("content_amount", h.contentAmount)
        put("remind_start", if (h.remindStart) 1 else 0)
        put("remind_end", h.remindEnd)
        put("target", h.target)
        put("counter_id", h.counterId)
        put("counter_effect", h.counterEffect)
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
        db.delete("progress", "habit_id=?", a)
        db.delete("partials", "habit_id=?", a)
        db.delete("quran_state", "habit_id=?", a)
        db.delete("quran_day", "habit_id=?", a)
        db.delete("custom_items", "habit_id=?", a)
    }

    /** How many times each dhikr was counted on that day. */
    fun progress(habitId: Long, day: Long): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        readableDatabase.rawQuery(
            "SELECT item_id, count FROM progress WHERE habit_id=? AND day=?", arrayOf(habitId.toString(), day.toString())
        ).use { c -> while (c.moveToNext()) out[c.getInt(0)] = c.getInt(1) }
        return out
    }

    fun setProgress(habitId: Long, day: Long, itemId: Int, count: Int) {
        writableDatabase.insertWithOnConflict("progress", null, ContentValues().apply {
            put("habit_id", habitId)
            put("day", day)
            put("item_id", itemId)
            put("count", count)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Share of the reading finished on each day, in thousandths. */
    fun partials(habitId: Long): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        readableDatabase.rawQuery("SELECT day, permille FROM partials WHERE habit_id=?", arrayOf(habitId.toString())).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
        }
        return out
    }

    fun setPartial(habitId: Long, day: Long, permille: Int) {
        writableDatabase.insertWithOnConflict("partials", null, ContentValues().apply {
            put("habit_id", habitId)
            put("day", day)
            put("permille", permille)
        }, SQLiteDatabase.CONFLICT_REPLACE)
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

    private fun addToGroup(
        groupId: Long, name: String, color: Int, today: Long, startPrayer: Int, endPrayer: Int,
        contentRef: Int = 0, contentPart: Int = 0
    ) {
        save(
            Habit(
                name = name, color = color, created = today, groupId = groupId,
                position = habits(true).size,
                startKind = Anchor.PRAYER, startValue = startPrayer,
                endKind = if (endPrayer < 0) Anchor.NONE else Anchor.PRAYER,
                endValue = if (endPrayer < 0) 0 else endPrayer,
                contentRef = contentRef, contentPart = contentPart
            )
        )
    }

    fun addPrayerGroup(today: Long) {
        val p = Palette.colors
        val g = saveGroup(Group(name = "الصلوات", color = p[0], position = groups().size))
        addToGroup(g, "الفجر", p[1], today, 0, 1)
        addToGroup(g, "الظهر", p[5], today, 2, 3)
        addToGroup(g, "العصر", p[4], today, 3, 4)
        addToGroup(g, "المغرب", p[3], today, 4, 5)
        addToGroup(g, "العشاء", p[2], today, 5, -1)
    }

    fun addAdhkarGroup(today: Long) {
        val color = Palette.colors[2]
        val g = saveGroup(Group(name = "الأذكار", color = color, position = groups().size))
        addToGroup(g, "أذكار الصباح", Palette.colors[8], today, 0, 2, Adhkar.MORNING_EVENING, Adhkar.PART_MORNING)
        addToGroup(g, "أذكار المساء", Palette.colors[6], today, 3, -1, Adhkar.MORNING_EVENING, Adhkar.PART_EVENING)
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

    /** Writes many days at once, leaving days that already carry a mark as they are. */
    fun addEntries(habitId: Long, marks: Map<Long, Int>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((day, value) in marks) {
                db.insertWithOnConflict("entries", null, ContentValues().apply {
                    put("habit_id", habitId)
                    put("day", day)
                    put("value", value)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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

    /** Every logged day of every habit as a spreadsheet, for reading outside the app. */
    fun exportCsv(): String {
        val sb = StringBuilder("\uFEFFالعادة,التاريخ,الحالة,النسبة\n")
        for (h in habits(true)) {
            val es = entries(h.id)
            val ps = partials(h.id)
            for (day in (es.keys + ps.keys).toSortedSet()) {
                val v = es[day] ?: Mark.NONE
                val state = when (v) {
                    Mark.DONE -> "منجزة"
                    Mark.EXCUSED -> "معذور"
                    else -> "غير مكتملة"
                }
                val percent = if (v == Mark.DONE) 100 else (ps[day] ?: 0) / 10
                val name = "\"" + h.name.replace("\"", "\"\"") + "\""
                sb.append(name).append(',').append(java.time.LocalDate.ofEpochDay(day)).append(',')
                    .append(state).append(',').append(percent).append('\n')
            }
        }
        return sb.toString()
    }

    fun exportJson(settings: JSONObject? = null): String {
        val root = JSONObject()
        root.put("app", "daab")
        if (settings != null) root.put("settings", settings)
        root.put("version", 6)
        val gs = JSONArray()
        for (g in groups()) {
            gs.put(JSONObject().put("id", g.id).put("name", g.name).put("color", g.color).put("position", g.position))
        }
        root.put("groups", gs)
        val cts = JSONArray()
        for (c in counters()) cts.put(JSONObject().put("id", c.id).put("name", c.name).put("position", c.position))
        root.put("counters", cts)
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
            o.put("groupId", h.groupId)
            o.put("startKind", h.startKind)
            o.put("startValue", h.startValue)
            o.put("endKind", h.endKind)
            o.put("endValue", h.endValue)
            o.put("contentRef", h.contentRef)
            o.put("contentPart", h.contentPart)
            o.put("contentUnit", h.contentUnit)
            o.put("contentAmount", h.contentAmount)
            o.put("remindStart", h.remindStart)
            o.put("remindEnd", h.remindEnd)
            o.put("target", h.target)
            o.put("counterId", h.counterId)
            o.put("counterEffect", h.counterEffect)
            val cs = JSONArray()
            for (it in customItems(h.id)) cs.put(JSONObject().put("text", it.text).put("count", it.count))
            o.put("custom", cs)
            if (h.contentRef == Quran.CONTENT) {
                val state = quranState(h.id)
                o.put("quranPosition", state.first)
                o.put("quranPlanEnd", state.second)
                o.put("quranKhatmas", khatmas(h.id))
            }
            val pr = JSONArray()
            readableDatabase.rawQuery("SELECT day, item_id, count FROM progress WHERE habit_id=?", arrayOf(h.id.toString())).use { c ->
                while (c.moveToNext()) pr.put(JSONArray().put(c.getLong(0)).put(c.getInt(1)).put(c.getInt(2)))
            }
            o.put("progress", pr)
            val qd = JSONArray()
            readableDatabase.rawQuery("SELECT day, start, end, reached FROM quran_day WHERE habit_id=?", arrayOf(h.id.toString())).use { c ->
                while (c.moveToNext()) qd.put(JSONArray().put(c.getLong(0)).put(c.getInt(1)).put(c.getInt(2)).put(c.getInt(3)))
            }
            o.put("quranDays", qd)
            val ps = JSONArray()
            for ((day, permille) in partials(h.id).toSortedMap()) {
                ps.put(JSONArray().put(day).put(permille))
            }
            o.put("partials", ps)
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
    fun importJson(text: String): JSONObject? {
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
            db.delete("progress", null, null)
            db.delete("partials", null, null)
            db.delete("quran_state", null, null)
            db.delete("quran_day", null, null)
            db.delete("custom_items", null, null)
            db.delete("counters", null, null)
            val cts = root.optJSONArray("counters") ?: JSONArray()
            for (i in 0 until cts.length()) {
                val o = cts.getJSONObject(i)
                db.insert("counters", null, ContentValues().apply {
                    put("id", o.getLong("id"))
                    put("name", o.getString("name"))
                    put("position", o.optInt("position"))
                })
            }
            val groupIds = HashMap<Long, Long>()
            for (i in 0 until gs.length()) {
                val o = gs.getJSONObject(i)
                val newId = db.insert("groups", null, ContentValues().apply {
                    put("id", o.getLong("id"))
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
                            endValue = o.optInt("endValue"),
                            contentRef = o.optInt("contentRef"),
                            contentPart = o.optInt("contentPart"),
                            contentUnit = o.optInt("contentUnit"),
                            contentAmount = o.optInt("contentAmount", 1),
                            remindStart = o.optBoolean("remindStart"),
                            remindEnd = o.optInt("remindEnd"),
                            target = o.optInt("target"),
                            counterId = o.optLong("counterId"),
                            counterEffect = o.optInt("counterEffect")
                        )
                    ).apply { if (o.optLong("id") > 0) put("id", o.getLong("id")) }
                )
                val pr = o.optJSONArray("progress") ?: JSONArray()
                for (j in 0 until pr.length()) {
                    val e = pr.getJSONArray(j)
                    db.insert("progress", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", e.getLong(0))
                        put("item_id", e.getInt(1))
                        put("count", e.getInt(2))
                    })
                }
                val qd = o.optJSONArray("quranDays") ?: JSONArray()
                for (j in 0 until qd.length()) {
                    val e = qd.getJSONArray(j)
                    db.insert("quran_day", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", e.getLong(0))
                        put("start", e.getInt(1))
                        put("end", e.getInt(2))
                        put("reached", e.getInt(3))
                    })
                }
                val cs = o.optJSONArray("custom") ?: JSONArray()
                for (j in 0 until cs.length()) {
                    val e = cs.getJSONObject(j)
                    db.insert("custom_items", null, ContentValues().apply {
                        put("habit_id", id)
                        put("item_id", j + 1)
                        put("text", e.getString("text"))
                        put("count", e.getInt("count"))
                    })
                }
                if (o.has("quranPosition")) {
                    db.insert("quran_state", null, ContentValues().apply {
                        put("habit_id", id)
                        put("position", o.getInt("quranPosition"))
                        put("plan_end", o.optLong("quranPlanEnd"))
                        put("khatmas", o.optInt("quranKhatmas"))
                    })
                }
                val ds = o.getJSONArray("dates")
                for (j in 0 until ds.length()) {
                    db.insert("habit_dates", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", ds.getLong(j))
                    })
                }
                val ps = o.optJSONArray("partials") ?: JSONArray()
                for (j in 0 until ps.length()) {
                    val e = ps.getJSONArray(j)
                    db.insert("partials", null, ContentValues().apply {
                        put("habit_id", id)
                        put("day", e.getLong(0))
                        put("permille", e.getInt(1))
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
        return root.optJSONObject("settings")
    }
}
