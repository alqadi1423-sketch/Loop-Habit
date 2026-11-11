package app.daab.habits.data

object Kind {
    const val DAILY = 0
    const val WEEKDAYS = 1
    const val PER_WEEK = 2
    const val DATES = 3
}

object Mark {
    const val NONE = 0
    const val DONE = 1
    const val EXCUSED = 2
}

/** What a time window edge is tied to. */
object Anchor {
    const val NONE = 0
    const val CLOCK = 1
    const val PRAYER = 2
}

val prayerNames = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")

data class Habit(
    val id: Long = 0,
    val name: String = "",
    val color: Int = Palette.colors[0],
    val kind: Int = Kind.DAILY,
    val weekdays: Int = 127,
    val perWeek: Int = 3,
    val reminder: Int = -1,
    val archived: Boolean = false,
    val position: Int = 0,
    val created: Long = 0,
    val groupId: Long = 0,
    val startKind: Int = Anchor.NONE,
    val startValue: Int = 0,
    val endKind: Int = Anchor.NONE,
    val endValue: Int = 0,
    /** Chapter of adhkar opened when the habit is tapped, -1 for the Quran, 0 for no reading. */
    val contentRef: Int = 0,
    val contentPart: Int = 0,
    /** For a Quran habit: how the daily portion is measured and how much of it. */
    val contentUnit: Int = 0,
    val contentAmount: Int = 1,
    /** Notify when the habit's time window opens. */
    val remindStart: Boolean = false,
    /** Notify this many minutes before the window closes, 0 for no notice. */
    val remindEnd: Int = 0,
    /** Daily count to reach for a counted habit such as cups of water, 0 for a plain check. */
    val target: Int = 0,
    /** Running counter this habit feeds, and how much each completion adds to or takes from it. */
    val counterId: Long = 0,
    val counterEffect: Int = 0
)

/** [groupId] is the group the counter is shown under on the habits page, or 0 for none. */
data class Counter(val id: Long = 0, val name: String = "", val position: Int = 0, val groupId: Long = 0)

data class Group(
    val id: Long = 0,
    val name: String = "",
    val color: Int = Palette.colors[0],
    val position: Int = 0
)

object Palette {
    val colors: List<Int> = listOf(
        0xFF1D9E75.toInt(),
        0xFF378ADD.toInt(),
        0xFF7F77DD.toInt(),
        0xFFD4537E.toInt(),
        0xFFD85A30.toInt(),
        0xFFBA7517.toInt(),
        0xFF639922.toInt(),
        0xFF5F5E5A.toInt(),
        0xFFB5382F.toInt(),
        0xFF2A8C9C.toInt(),
        0xFF8E8B1F.toInt(),
        0xFF9A4FB3.toInt()
    )
}
