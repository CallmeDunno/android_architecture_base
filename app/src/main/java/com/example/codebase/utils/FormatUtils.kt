package com.example.codebase.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Locale-aware formatting for file sizes, counters, durations and percentages. Pure Kotlin/JVM. */
object FormatUtils {

    private const val FILE_SIZE_BASE = 1024.0
    private const val COMPACT_BASE = 1000.0
    private val FILE_SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    private val COMPACT_SUFFIXES = arrayOf("K", "M", "B", "T")

    /**
     * Formats a byte count with base-1024 units and at most one decimal: `0 B`, `1023 B`, `1 KB`,
     * `1.5 MB`. The decimal separator follows [locale].
     */
    fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        require(bytes >= 0) { "bytes must not be negative: $bytes" }
        val (value, unit) = scale(bytes.toDouble(), FILE_SIZE_BASE, FILE_SIZE_UNITS.lastIndex)
        return "${formatOneDecimal(value, locale, grouping = false)} ${FILE_SIZE_UNITS[unit]}"
    }

    /** `m:ss` below one hour, `h:mm:ss` from one hour on. Negative values are treated as zero. */
    fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Grouped integer: `1,234,567` for `Locale.US`, `1.234.567` for Vietnamese. */
    fun formatNumber(value: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    /**
     * Short form for counters: `999`, `1.2K`, `3.4M`, `-5B`. Values below 1000 fall back to
     * [formatNumber]. The suffixes are not localized; the decimal separator follows [locale].
     */
    fun formatCompactNumber(value: Long, locale: Locale = Locale.getDefault()): String {
        val (scaled, step) = scale(abs(value.toDouble()), COMPACT_BASE, COMPACT_SUFFIXES.size)
        if (step == 0) return formatNumber(value, locale)
        val sign = if (value < 0) "-" else ""
        return sign + formatOneDecimal(scaled, locale, grouping = true) + COMPACT_SUFFIXES[step - 1]
    }

    /** [fraction] as a percentage (`0.256` → `26%` with [decimals] = 0). */
    fun formatPercent(fraction: Double, locale: Locale = Locale.getDefault(), decimals: Int = 0): String {
        require(decimals >= 0) { "decimals must not be negative: $decimals" }
        return NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(fraction)
    }

    /**
     * Divides [value] by [base] while its one-decimal rounding is still at least [base], so that
     * `1023.96 KB` becomes `1 MB` instead of `1024 KB`. Returns the scaled value and the step count.
     */
    private fun scale(value: Double, base: Double, maxSteps: Int): Pair<Double, Int> {
        var scaled = value
        var steps = 0
        while (steps < maxSteps && roundToTenth(scaled) >= BigDecimal.valueOf(base)) {
            scaled /= base
            steps++
        }
        return scaled to steps
    }

    // Rounding is done on BigDecimal once and the rounded value is what gets formatted, so the unit
    // decision and the printed number can never disagree.
    private fun roundToTenth(value: Double): BigDecimal =
        BigDecimal(value).setScale(1, RoundingMode.HALF_UP)

    private fun formatOneDecimal(value: Double, locale: Locale, grouping: Boolean): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
            isGroupingUsed = grouping
        }.format(roundToTenth(value))
}
