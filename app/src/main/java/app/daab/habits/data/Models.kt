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
    val created: Long = 0
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
        0xFF5F5E5A.toInt()
    )
}
