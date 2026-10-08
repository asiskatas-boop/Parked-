package com.parked.app.ui

import android.content.Context
import android.text.format.DateFormat
import com.parked.app.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private fun timeOf(context: Context, ts: Long): String = DateFormat.getTimeFormat(context).format(Date(ts))

/** "Parked today at 14:32", "Parked yesterday at…", "Parked 3 Oct at…". */
fun parkedAtLabel(context: Context, ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val time = timeOf(context, ts)
    val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = then.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> context.getString(R.string.parked_today, time)
        isYesterday -> context.getString(R.string.parked_yesterday, time)
        else -> context.getString(R.string.parked_on, shortDate(ts), time)
    }
}

fun shortDate(ts: Long): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM"), ts).toString()

fun shortDateYear(ts: Long): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy"), ts).toString()

fun dateTime(context: Context, ts: Long): String = "${shortDate(ts)} · ${timeOf(context, ts)}"

fun clockTime(context: Context, ts: Long): String = timeOf(context, ts)

/** Whole kilometres with grouping, e.g. "83,456 km" (or "83.456 km" in Greek). */
fun formatKm(km: Double): String = NumberFormat.getIntegerInstance().format(km.roundToInt()) + " km"

fun formatDecimal(value: Double, digits: Int): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)

fun formatMoney(currency: String, value: Double): String = currency + formatDecimal(value, 2)

/** "120 m" under a kilometre, "1.4 km" above. */
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else "${formatDecimal(meters / 1000, 1)} km"

/** Walking minutes at an unhurried 1.3 m/s, never less than one. */
fun walkingMinutes(meters: Double): Int = ceil(meters / 1.3 / 60).toInt().coerceAtLeast(1)

/** Parses user input that may use a comma as the decimal separator. */
fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun monthName(year: Int, month: Int): String {
    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    return DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM"), cal).toString()
}

fun monthNameLong(year: Int, month: Int): String {
    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    return DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLL yyyy"), cal).toString()
}
