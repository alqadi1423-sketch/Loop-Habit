package app.daab.habits.data

import android.content.Context
import org.json.JSONArray

class Dhikr(val id: Int, val text: String, val count: Int)

class DhikrCategory(val id: Int, val name: String, val items: List<Dhikr>)

/**
 * Adhkar texts bundled with the app, taken from Hisn al-Muslim as published in the
 * rn0x/Adhkar-json data set. Texts are shown exactly as they are in the source.
 */
object Adhkar {

    const val PART_ALL = 0
    const val PART_MORNING = 1
    const val PART_EVENING = 2

    /** The source keeps morning and evening adhkar in one chapter, this is its id. */
    const val MORNING_EVENING = 1

    /** Entries the source itself marks as said only in the morning or only in the evening. */
    private val morningOnly = setOf(19, 20, 21)
    private val eveningOnly = setOf(23)

    @Volatile
    private var cache: List<DhikrCategory>? = null

    fun categories(ctx: Context): List<DhikrCategory> {
        cache?.let { return it }
        val text = ctx.applicationContext.assets.open("adhkar.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val arr = JSONArray(text)
        val out = ArrayList<DhikrCategory>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val items = c.getJSONArray("items")
            val list = ArrayList<Dhikr>()
            for (j in 0 until items.length()) {
                val o = items.getJSONObject(j)
                list.add(Dhikr(o.getInt("id"), o.getString("text"), o.getInt("count")))
            }
            out.add(DhikrCategory(c.getInt("id"), c.getString("name"), list))
        }
        cache = out
        return out
    }

    fun items(ctx: Context, categoryId: Int, part: Int): List<Dhikr> {
        val all = categories(ctx).firstOrNull { it.id == categoryId }?.items ?: return emptyList()
        if (categoryId != MORNING_EVENING) return all
        return when (part) {
            PART_MORNING -> all.filter { it.id !in eveningOnly }
            PART_EVENING -> all.filter { it.id !in morningOnly }
            else -> all
        }
    }

    fun title(ctx: Context, categoryId: Int, part: Int): String {
        if (categoryId == MORNING_EVENING && part == PART_MORNING) return "أذكار الصباح"
        if (categoryId == MORNING_EVENING && part == PART_EVENING) return "أذكار المساء"
        return categories(ctx).firstOrNull { it.id == categoryId }?.name ?: ""
    }
}
