package com.example.codebase.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatUtilsTest {

    private val us = Locale.US

    @Test
    fun `file size below one KB is shown in bytes`() {
        assertEquals("0 B", FormatUtils.formatFileSize(0, us))
        assertEquals("1023 B", FormatUtils.formatFileSize(1023, us))
    }

    @Test
    fun `file size uses base 1024 with at most one decimal`() {
        assertEquals("1 KB", FormatUtils.formatFileSize(1024, us))
        assertEquals("1.5 KB", FormatUtils.formatFileSize(1536, us))
        assertEquals("1.5 MB", FormatUtils.formatFileSize(1_572_864, us))
        assertEquals("2 GB", FormatUtils.formatFileSize(2L * 1024 * 1024 * 1024, us))
    }

    @Test
    fun `file size that rounds up to the base moves to the next unit`() {
        assertEquals("1 MB", FormatUtils.formatFileSize(1024L * 1024 - 1, us))
    }

    @Test
    fun `file size decimal separator follows the locale`() {
        assertEquals("1,5 KB", FormatUtils.formatFileSize(1536, Locale.forLanguageTag("vi-VN")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative file size is rejected`() {
        FormatUtils.formatFileSize(-1, us)
    }

    @Test
    fun `duration below one hour has no hour part`() {
        assertEquals("0:00", FormatUtils.formatDuration(0))
        assertEquals("1:05", FormatUtils.formatDuration(65_000))
        assertEquals("59:59", FormatUtils.formatDuration(3_599_999))
        assertEquals("0:00", FormatUtils.formatDuration(-5_000))
    }

    @Test
    fun `duration from one hour on includes hours`() {
        assertEquals("1:00:00", FormatUtils.formatDuration(3_600_000))
        assertEquals("10:02:03", FormatUtils.formatDuration(36_123_000))
    }

    @Test
    fun `number is grouped by locale`() {
        assertEquals("1,234,567", FormatUtils.formatNumber(1_234_567, us))
    }

    @Test
    fun `compact number uses K, M and B suffixes`() {
        assertEquals("999", FormatUtils.formatCompactNumber(999, us))
        assertEquals("1K", FormatUtils.formatCompactNumber(1_000, us))
        assertEquals("1.2K", FormatUtils.formatCompactNumber(1_234, us))
        assertEquals("3.4M", FormatUtils.formatCompactNumber(3_400_000, us))
        assertEquals("1M", FormatUtils.formatCompactNumber(999_960, us))
        assertEquals("-5B", FormatUtils.formatCompactNumber(-5_000_000_000, us))
        assertEquals("-999", FormatUtils.formatCompactNumber(-999, us))
    }

    @Test
    fun `percent respects the requested decimals`() {
        assertEquals("26%", FormatUtils.formatPercent(0.256, us))
        assertEquals("25.6%", FormatUtils.formatPercent(0.256, us, decimals = 1))
    }
}
