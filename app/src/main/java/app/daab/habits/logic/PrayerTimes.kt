package app.daab.habits.logic

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
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
 * Prayer times computed on the device from the sun's position, with the Umm al-Qura
 * conventions used in Saudi Arabia: Fajr at 18.5 degrees below the horizon, Isha 90 minutes
 * after Maghrib (120 in Ramadan), and Asr when a shadow equals its object's length.
 */
object PrayerTimes {

    val cities = listOf(
        City("الرياض", 24.7136, 46.6753),
        City("مكة المكرمة", 21.4225, 39.8262),
        City("المدينة المنورة", 24.4672, 39.6111),
        City("جدة", 21.5433, 39.1728),
        City("الدمام", 26.4207, 50.0888),
        City("الطائف", 21.2703, 40.4158),
        City("بيشة", 20.0005, 42.6052),
        City("أبها", 18.2164, 42.5053),
        City("خميس مشيط", 18.3000, 42.7333),
        City("تبوك", 28.3838, 36.5550),
        City("بريدة", 26.3260, 43.9750),
        City("حائل", 27.5114, 41.7208),
        City("الأحساء", 25.3833, 49.5833),
        City("جازان", 16.8892, 42.5511),
        City("نجران", 17.4924, 44.1277)
    )

    private const val PREFS = "settings"

    fun city(ctx: Context): City {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = cities[0]
        return City(
            p.getString("city", d.name) ?: d.name,
            p.getFloat("lat", d.lat.toFloat()).toDouble(),
            p.getFloat("lng", d.lng.toFloat()).toDouble()
        )
    }

    fun setCity(ctx: Context, c: City) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("city", c.name).putFloat("lat", c.lat.toFloat()).putFloat("lng", c.lng.toFloat()).apply()
    }

    /** Minutes after local midnight for Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha. */
    fun forDay(ctx: Context, day: Long): IntArray {
        val c = city(ctx)
        val date = LocalDate.ofEpochDay(day)
        val offset = ZoneId.systemDefault().rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val ramadan = try {
            HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) == 9
        } catch (e: Exception) {
            false
        }
        return compute(date, c.lat, c.lng, offset, ramadan)
    }

    fun compute(date: LocalDate, lat: Double, lng: Double, tzHours: Double, ramadan: Boolean): IntArray {
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

        val asrAltitude = deg(atan(1.0 / (1.0 + tan(rad(abs(lat - decl))))))
        val maghrib = noon + hourAngle(-0.833)
        val hours = doubleArrayOf(
            noon - hourAngle(-18.5),
            noon - hourAngle(-0.833),
            noon,
            noon + hourAngle(asrAltitude),
            maghrib,
            maghrib + if (ramadan) 2.0 else 1.5
        )
        return IntArray(6) { (hours[it] * 60).roundToInt().coerceIn(0, 1439) }
    }
}
