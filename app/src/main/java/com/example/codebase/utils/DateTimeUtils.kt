package com.example.codebase.utils

import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalQuery
import java.util.Locale

/**
 * Date/time helpers on `java.time`, working with epoch milliseconds. `java.time` is available on every
 * supported API level through core library desugaring (see `app/build.gradle.kts`).
 *
 * Everything except [getRelativeTime] is pure JVM. Pass `zone` and `now` explicitly in tests.
 * An invalid `pattern` is a programming error and throws `IllegalArgumentException`.
 */
object DateTimeUtils {

    const val PATTERN_DATE = "dd/MM/yyyy"
    const val PATTERN_TIME = "HH:mm"
    const val PATTERN_DATE_TIME = "dd/MM/yyyy HH:mm"

    fun format(
        epochMillis: Long,
        pattern: String = PATTERN_DATE_TIME,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMillis))

    /**
     * Parses [text] with [pattern] into epoch millis, or returns `null` if it doesn't match.
     * A date-only pattern such as [PATTERN_DATE] resolves to the start of that day in [zone].
     */
    fun parse(
        text: String,
        pattern: String = PATTERN_DATE_TIME,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): Long? {
        val parsed = try {
            DateTimeFormatter.ofPattern(pattern, locale).parseBest(
                text,
                TemporalQuery { LocalDateTime.from(it) },
                TemporalQuery { LocalDate.from(it) },
            )
        } catch (e: DateTimeParseException) {
            return null
        }
        val dateTime = when (parsed) {
            is LocalDateTime -> parsed
            is LocalDate -> parsed.atStartOfDay()
            else -> return null
        }
        return dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    /** ISO-8601 in UTC, e.g. `2024-05-01T08:30:00Z` (milliseconds only when non-zero). */
    fun formatIso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    /** Parses ISO-8601 with `Z` or an offset (`2024-05-01T15:30:00+07:00`); `null` if invalid. */
    fun parseIso(text: String): Long? = try {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    /** Epoch millis of 00:00 on the day containing [epochMillis] in [zone]. */
    fun startOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        toLocalDate(epochMillis, zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun isSameDay(first: Long, second: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(first, zone) == toLocalDate(second, zone)

    fun isToday(
        epochMillis: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = isSameDay(epochMillis, now, zone)

    /** Calendar days from [from] to [to] in [zone]; negative if [to] is earlier. */
    fun daysBetween(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(toLocalDate(from, zone), toLocalDate(to, zone))

    /**
     * Relative span localized by the system, such as "5 minutes ago" or "in 2 hours"
     * (`DateUtils.getRelativeTimeSpanString`). Android-only, so not covered by unit tests.
     */
    fun getRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): CharSequence =
        DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS)

    private fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
