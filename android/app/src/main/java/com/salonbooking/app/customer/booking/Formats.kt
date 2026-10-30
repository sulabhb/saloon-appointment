package com.salonbooking.app.customer.booking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting for the booking screens. Times always show in the branch's time zone, not the phone's. */
object Formats {

    private val AU = Locale.forLanguageTag("en-AU")
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", AU)
    private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", AU)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", AU)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", AU)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", AU)

    /** "$85", or "$85.50" when there are cents. */
    fun money(cents: Long): String =
        if (cents % 100 == 0L) "$%d".format(cents / 100) else "$%d.%02d".format(cents / 100, cents % 100)

    /** "45 min", "1 h", "1 h 30 min". */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** "9:30 am" in the branch's time zone. */
    fun clock(instant: String, timeZone: String): String = CLOCK.format(Instant.parse(instant).atZone(ZoneId.of(timeZone))).lowercase(AU)

    /** "Monday 3 November". */
    fun longDate(instant: String, timeZone: String): String = LONG_DATE.format(Instant.parse(instant).atZone(ZoneId.of(timeZone)))

    /** "Mon 3 Nov". */
    fun shortDate(instant: String, timeZone: String): String = SHORT_DATE.format(Instant.parse(instant).atZone(ZoneId.of(timeZone)))

    /** "Mon" for a date such as "2026-11-03". */
    fun weekday(date: String): String = WEEKDAY.format(LocalDate.parse(date))

    /** "November 2026". */
    fun month(date: String): String = MONTH.format(LocalDate.parse(date))

    /** Today in the branch's time zone, "YYYY-MM-DD". (LocalDate.ofInstant needs API 34.) */
    fun today(timeZone: String, now: Instant = Instant.now()): String = now.atZone(ZoneId.of(timeZone)).toLocalDate().toString()

    /** "24 hours", "1 hour", "2 days", "90 minutes". */
    fun cutoff(minutes: Int): String = when {
        minutes % 1440 == 0 && minutes >= 2880 -> "${minutes / 1440} days"
        minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
        else -> "$minutes minutes"
    }

    /** "Fortitude Valley, QLD" when the branch is named after its suburb. */
    fun branch(name: String, suburb: String, state: String): String =
        "$name, ${if (name.trim().equals(suburb.trim(), ignoreCase = true)) state else suburb}"
}
