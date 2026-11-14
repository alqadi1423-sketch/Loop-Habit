package app.daab.habits.logic

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class City(val name: String, val lat: Double, val lng: Double)

/**
 * How Fajr and Isha are found. [ishaMinutes] above 0 places Isha that long after Maghrib
 * (with [ramadanMinutes] in Ramadan); otherwise Isha is when the sun is [isha] degrees below the horizon.
 */
class Method(val name: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0, val ramadanMinutes: Int = 0)

/**
 * Prayer times computed on the device from the sun's position. Umm al-Qura, the method used in
 * Saudi Arabia, is the default; other common methods serve users elsewhere. Each prayer can be
 * moved by a few minutes to match the local mosque.
 */
object PrayerTimes {

    val cities = listOf(
        City("الرياض", 24.7136, 46.6753),
        City("مكة المكرمة", 21.4225, 39.8262),
        City("المدينة المنورة", 24.4672, 39.6111),
        City("جدة", 21.5433, 39.1728),
        City("الدمام", 26.4207, 50.0888),
        City("الخبر", 26.2172, 50.1971),
        City("الطائف", 21.2703, 40.4158),
        City("بيشة", 20.0005, 42.6052),
        City("أبها", 18.2164, 42.5053),
        City("خميس مشيط", 18.3000, 42.7333),
        City("الباحة", 20.0129, 41.4677),
        City("تبوك", 28.3838, 36.5550),
        City("بريدة", 26.3260, 43.9750),
        City("عنيزة", 26.0840, 43.9930),
        City("حائل", 27.5114, 41.7208),
        City("الجوف (سكاكا)", 29.9697, 40.2064),
        City("عرعر", 30.9753, 41.0381),
        City("الأحساء", 25.3833, 49.5833),
        City("الجبيل", 27.0046, 49.6460),
        City("حفر الباطن", 28.4328, 45.9708),
        City("ينبع", 24.0895, 38.0618),
        City("جازان", 16.8892, 42.5511),
        City("نجران", 17.4924, 44.1277),
        City("الكويت", 29.3759, 47.9774),
        City("الدوحة", 25.2854, 51.5310),
        City("المنامة", 26.2285, 50.5860),
        City("أبوظبي", 24.4539, 54.3773),
        City("دبي", 25.2048, 55.2708),
        City("مسقط", 23.5880, 58.3829),
        City("عمّان", 31.9454, 35.9284),
        City("القاهرة", 30.0444, 31.2357),
        City("إسطنبول", 41.0082, 28.9784),
        City("لندن", 51.5072, -0.1276),
        City("نيويورك", 40.7128, -74.0060)
    )

    val methods = listOf(
        Method("أم القرى", 18.5, 0.0, ishaMinutes = 90, ramadanMinutes = 120),
        Method("رابطة العالم الإسلامي", 18.0, 17.0),
        Method("الهيئة المصرية", 19.5, 17.5),
        Method("أمريكا الشمالية", 15.0, 15.0),
        Method("جامعة كراتشي", 18.0, 18.0),
        Method("الكويت", 18.0, 17.5),
        Method("قطر", 18.0, 0.0, ishaMinutes = 90, ramadanMinutes = 90),
        Method("دبي", 18.2, 18.2)
    )

    private const val PREFS = "settings"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun city(ctx: Context): City {
        val p = prefs(ctx)
        val d = cities[0]
        return City(
            p.getString("city", d.name) ?: d.name,
            p.getFloat("lat", d.lat.toFloat()).toDouble(),
            p.getFloat("lng", d.lng.toFloat()).toDouble()
        )
    }

    fun setCity(ctx: Context, c: City) {
        prefs(ctx).edit()
            .putString("city", c.name).putFloat("lat", c.lat.toFloat()).putFloat("lng", c.lng.toFloat()).apply()
    }

    fun method(ctx: Context): Int = prefs(ctx).getInt("prayer_method", 0).coerceIn(0, methods.size - 1)

    fun setMethod(ctx: Context, index: Int) = prefs(ctx).edit().putInt("prayer_method", index).apply()

    /** Asr when a shadow is twice its object's length, as the Hanafi school holds. */
    fun hanafi(ctx: Context): Boolean = prefs(ctx).getBoolean("asr_hanafi", false)

    fun setHanafi(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean("asr_hanafi", value).apply()

    /** Minutes added to each of Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha. */
    fun adjustment(ctx: Context, prayer: Int): Int = prefs(ctx).getInt("adj_$prayer", 0)

    fun setAdjustment(ctx: Context, prayer: Int, minutes: Int) =
        prefs(ctx).edit().putInt("adj_$prayer", minutes.coerceIn(-30, 30)).apply()

    /** Minutes after local midnight for Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha. */
    fun forDay(ctx: Context, day: Long): IntArray {
        val c = city(ctx)
        val date = LocalDate.ofEpochDay(day)
        val offset = ZoneId.systemDefault().rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val times = compute(date, c.lat, c.lng, offset, Hijri.isRamadan(day), methods[method(ctx)], hanafi(ctx))
        return IntArray(6) { (times[it] + adjustment(ctx, it)).coerceIn(0, 1439) }
    }

    fun compute(
        date: LocalDate, lat: Double, lng: Double, tzHours: Double, ramadan: Boolean,
        method: Method = methods[0], hanafi: Boolean = false
    ): IntArray {
        fun rad(x: Double) = Math.toRadians(x)
        fun deg(x: Double) = Math.toDegrees(x)
        val julian = date.toEpochDay() + 2440587.5
        val d = julian + 0.5 - 2451545.0 - lng / 360.0
        val g = (357.529 + 0.98560028 * d).mod(360.0)
        val q = (280.459 + 0.98564736 * d).mod(360.0)
        val l = (q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g))).mod(360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = (deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0).mod(24.0)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        val eqt = (q / 15.0 - ra + 12.0).mod(24.0) - 12.0
        val noon = 12 + tzHours - lng / 15.0 - eqt

        fun hourAngle(altitude: Double): Double {
            val x = (sin(rad(altitude)) - sin(rad(lat)) * sin(rad(decl))) / (cos(rad(lat)) * cos(rad(decl)))
            return deg(acos(x.coerceIn(-1.0, 1.0))) / 15.0
        }

        val shadow = if (hanafi) 2.0 else 1.0
        val asrAltitude = deg(atan(1.0 / (shadow + tan(rad(abs(lat - decl))))))
        val maghrib = noon + hourAngle(-0.833)
        val isha = if (method.ishaMinutes > 0) {
            maghrib + (if (ramadan) method.ramadanMinutes else method.ishaMinutes) / 60.0
        } else noon + hourAngle(-method.isha)
        val hours = doubleArrayOf(
            noon - hourAngle(-method.fajr),
            noon - hourAngle(-0.833),
            noon,
            noon + hourAngle(asrAltitude),
            maghrib,
            isha
        )
        return IntArray(6) { (hours[it] * 60).roundToInt().coerceIn(0, 1439) }
    }
}
