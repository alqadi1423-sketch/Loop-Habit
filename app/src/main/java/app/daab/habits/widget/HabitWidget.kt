package app.daab.habits.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.RemoteViews
import app.daab.habits.R
import app.daab.habits.data.Anchor
import app.daab.habits.data.Db
import app.daab.habits.data.Habit
import app.daab.habits.data.Mark
import app.daab.habits.logic.Phase
import app.daab.habits.logic.PrayerTimes
import app.daab.habits.logic.Schedule
import app.daab.habits.notify.Alarms
import app.daab.habits.notify.ReminderReceiver
import app.daab.habits.ui.MainActivity
import app.daab.habits.ui.timeText
import java.time.LocalDate
import java.time.ZoneId

/**
 * One square on the home screen. It is bound to a single habit, to a linked group, or to
 * all habits. For a group or all habits it shows the habit whose time has come and moves
 * on once that habit is marked or its time passes.
 */
class HabitWidget : AppWidgetProvider() {

    companion object {
        const val ALL_HABITS = -1L
        internal const val ACTION_TAP = "app.daab.habits.widget.TAP"
        internal const val ACTION_REFRESH = "app.daab.habits.widget.REFRESH"
        internal const val EXTRA_HABIT = "habit"
        private const val PREFS = "widgets"
        private const val DARK = 0xFF1B2A2E.toInt()
        private const val TRACK = 0xFF33454A.toInt()
        private const val SOFT = 0xFFB9C6C4.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val WHITE_FAINT = 0x4DFFFFFF
        private const val GRAY = 0xFF3A4649.toInt()
        private const val GRAY_RING = 0xFF8FA09F.toInt()
        private const val GRAY_TRACK = 0xFF4B5A5D.toInt()
        private const val ALL_DONE = 0xFF0F5C55.toInt()

        fun bind(ctx: Context, widgetId: Int, habitId: Long, groupId: Long) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("w_$widgetId", habitId).putLong("g_$widgetId", groupId).apply()
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HabitWidget::class.java))
            for (id in ids) render(ctx, mgr, id)
            if (ids.isNotEmpty()) scheduleRefresh(ctx)
        }

        /** Wakes the squares at the next moment a time window can open or close. */
        private fun scheduleRefresh(ctx: Context) {
            val today = Schedule.today()
            val now = Schedule.nowMinutes()
            val marks = ArrayList<Int>()
            marks.addAll(PrayerTimes.forDay(ctx, today).toList())
            for (h in Db.get(ctx).habits()) {
                if (h.startKind == Anchor.CLOCK) marks.add(h.startValue)
                if (h.endKind == Anchor.CLOCK) marks.add(h.endValue)
            }
            marks.add(Schedule.dayEnd())
            val next = marks.filter { it > now }.minOrNull() ?: (now + 30)
            val at = LocalDate.ofEpochDay(today).atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(next.toLong()).plusSeconds(20).toInstant().toEpochMilli()
            val pending = PendingIntent.getBroadcast(
                ctx, -7, Intent(ctx, WidgetActionReceiver::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Alarms.set(ctx, at, pending)
        }

        private fun openApp(ctx: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                ctx, widgetId,
                Intent(ctx, MainActivity::class.java).setData(Uri.parse("daab://open/widget/$widgetId")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun tap(ctx: Context, widgetId: Int, habitId: Long): PendingIntent {
            val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(ACTION_TAP).putExtra(EXTRA_HABIT, habitId)
            return PendingIntent.getBroadcast(
                ctx, widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Opens the reading of a habit instead of marking it. */
        private fun read(ctx: Context, widgetId: Int, habitId: Long): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .setData(Uri.parse("daab://reader/$habitId"))
                .putExtra(MainActivity.EXTRA_READER, habitId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                ctx, widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private class Slot(val habit: Habit, val value: Int, val phase: Int, val start: Int)

        private const val GLYPH_NONE = 0
        private const val GLYPH_CHECK = 1
        private const val GLYPH_CROSS = 2

        /** Draws the ring of a square: a track, an arc for the habit's strength, and what is inside it. */
        private fun ringBitmap(arc: Float, ringColor: Int, trackColor: Int, glyph: Int, text: String, inkColor: Int): Bitmap {
            val size = 168
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val stroke = 11f
            val box = RectF(stroke, stroke, size - stroke, size - stroke)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = trackColor
            canvas.drawArc(box, 0f, 360f, false, paint)
            if (arc > 0.01f) {
                paint.color = ringColor
                canvas.drawArc(box, -90f, 360f * arc.coerceIn(0f, 1f), false, paint)
            }
            paint.color = inkColor
            paint.strokeWidth = 13f
            val c = size / 2f
            when (glyph) {
                GLYPH_CHECK -> {
                    val path = Path()
                    path.moveTo(c - 30f, c + 2f)
                    path.lineTo(c - 9f, c + 23f)
                    path.lineTo(c + 32f, c - 21f)
                    paint.strokeJoin = Paint.Join.ROUND
                    canvas.drawPath(path, paint)
                }
                GLYPH_CROSS -> {
                    canvas.drawLine(c - 22f, c - 22f, c + 22f, c + 22f, paint)
                    canvas.drawLine(c + 22f, c - 22f, c - 22f, c + 22f, paint)
                }
                else -> if (text.isNotEmpty()) {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = inkColor
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = if (text.length > 4) 36f else 44f
                    }
                    canvas.drawText(text, c, c - (tp.descent() + tp.ascent()) / 2, tp)
                }
            }
            return bmp
        }

        /** A habit color lightened so it stands out on the dark square. */
        private fun bright(color: Int): Int {
            fun mix(v: Int) = v + ((255 - v) * 0.35f).toInt()
            return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
        }

        fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val boundHabit = prefs.getLong("w_$widgetId", ALL_HABITS)
            val boundGroup = prefs.getLong("g_$widgetId", 0)
            val db = Db.get(ctx)
            val today = Schedule.today()
            val now = Schedule.nowMinutes()
            val times = PrayerTimes.forDay(ctx, today)
            val views = RemoteViews(ctx.packageName, R.layout.habit_widget)

            fun show(bg: Int, ring: Bitmap, nameColor: Int, name: String, click: PendingIntent) {
                views.setInt(R.id.widget_bg, "setColorFilter", bg)
                views.setImageViewBitmap(R.id.widget_ring, ring)
                views.setTextColor(R.id.widget_name, nameColor)
                views.setTextViewText(R.id.widget_name, name)
                views.setOnClickPendingIntent(R.id.widget_root, click)
            }

            fun strength(h: Habit): Float =
                Schedule.score(h, db.dates(h.id), db.entries(h.id), today, db.partials(h.id)).toFloat()

            fun plain(name: String, text: String) =
                show(DARK, ringBitmap(0f, TRACK, TRACK, GLYPH_NONE, text, SOFT), SOFT, name, openApp(ctx, widgetId))

            // Before a habit is done its square stays dark and only the ring carries its color.
            fun pending(h: Habit) {
                val c = bright(h.color)
                when {
                    h.target > 0 -> {
                        val count = db.progress(h.id, today)[0] ?: 0
                        show(DARK, ringBitmap(count.toFloat() / h.target, c, TRACK, GLYPH_NONE, "$count/${h.target}", WHITE), WHITE, h.name, tap(ctx, widgetId, h.id))
                    }
                    h.contentRef != 0 -> {
                        val permille = db.partials(h.id)[today] ?: 0
                        val text = if (permille > 0) "${permille / 10}%" else ""
                        show(DARK, ringBitmap(if (permille > 0) permille / 1000f else strength(h), c, TRACK, GLYPH_NONE, text, WHITE), WHITE, h.name, read(ctx, widgetId, h.id))
                    }
                    else -> show(DARK, ringBitmap(strength(h), c, TRACK, GLYPH_NONE, "", WHITE), WHITE, h.name, tap(ctx, widgetId, h.id))
                }
            }

            // A finished habit fills the square with its color. A second tap unmarks it only on
            // a square bound to that one habit, so a quick extra tap never wipes a record.
            fun done(h: Habit, single: Boolean) = show(
                h.color, ringBitmap(strength(h), WHITE, WHITE_FAINT, GLYPH_CHECK, "", WHITE), WHITE, h.name,
                when {
                    h.contentRef != 0 -> read(ctx, widgetId, h.id)
                    single && h.target == 0 -> tap(ctx, widgetId, h.id)
                    else -> openApp(ctx, widgetId)
                }
            )

            fun later(h: Habit, start: Int) = show(
                DARK, ringBitmap(0f, TRACK, TRACK, GLYPH_NONE, timeText(start).substringBefore(' '), SOFT), SOFT, h.name,
                openApp(ctx, widgetId)
            )

            fun missed(h: Habit) = show(
                GRAY, ringBitmap(strength(h), GRAY_RING, GRAY_TRACK, GLYPH_CROSS, "", SOFT), SOFT, h.name, openApp(ctx, widgetId)
            )

            if (boundGroup == 0L && boundHabit != ALL_HABITS) {
                val h = db.habit(boundHabit)
                if (h == null || h.archived) {
                    plain("العادة محذوفة", "؟")
                } else {
                    val entries = db.entries(h.id)
                    val v = entries[today] ?: Mark.NONE
                    val due = Schedule.isDue(h, db.dates(h.id), entries, today)
                    val phase = Schedule.phase(h, times, now)
                    when {
                        v == Mark.DONE -> done(h, true)
                        v == Mark.EXCUSED -> plain(h.name, "عذر")
                        !due -> plain(h.name, "")
                        phase == Phase.BEFORE -> later(h, Schedule.window(h, times).first)
                        phase == Phase.CLOSED -> missed(h)
                        else -> pending(h)
                    }
                }
                mgr.updateAppWidget(widgetId, views)
                return
            }

            val all = db.habits()
            val pool = if (boundGroup != 0L) all.filter { it.groupId == boundGroup } else all
            val slots = ArrayList<Slot>()
            for (h in pool) {
                val entries = db.entries(h.id)
                if (!Schedule.isDue(h, db.dates(h.id), entries, today)) continue
                slots.add(Slot(h, entries[today] ?: Mark.NONE, Schedule.phase(h, times, now), Schedule.window(h, times).first))
            }
            // Habits whose time is running come first, the one that ends soonest at the front,
            // then habits that can be done at any time of day.
            val open = slots.filter { it.phase == Phase.OPEN && it.value == Mark.NONE }
                .minWithOrNull(compareBy({ !Schedule.hasWindow(it.habit) }, { Schedule.window(it.habit, times).second }))
            val groupGone = boundGroup != 0L && db.groups().none { it.id == boundGroup }
            val later = slots.filter { it.phase == Phase.BEFORE && it.value == Mark.NONE }.minByOrNull { it.start }
            val lost = slots.filter { it.phase == Phase.CLOSED && it.value == Mark.NONE }.maxByOrNull { it.start }
            val current = if (boundGroup != 0L) {
                slots.filter { it.phase == Phase.OPEN && it.value == Mark.DONE && Schedule.hasWindow(it.habit) }.maxByOrNull { it.start }
            } else null
            when {
                groupGone -> plain("المجموعة محذوفة", "؟")
                pool.isEmpty() -> plain("أضف عادة", "+")
                open != null -> pending(open.habit)
                current != null -> done(current.habit, false)
                later != null -> later(later.habit, later.start)
                lost != null -> missed(lost.habit)
                else -> show(
                    ALL_DONE, ringBitmap(1f, WHITE, WHITE_FAINT, GLYPH_CHECK, "", WHITE), WHITE, "أنجزت اليوم", openApp(ctx, widgetId)
                )
            }
            mgr.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, mgr, id)
        scheduleRefresh(ctx)
        ReminderReceiver.scheduleAll(ctx)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (id in ids) e.remove("w_$id").remove("g_$id")
        e.apply()
    }
}
