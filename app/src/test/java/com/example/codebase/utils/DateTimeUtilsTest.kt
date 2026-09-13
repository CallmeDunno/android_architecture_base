package com.example.codebase.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class DateTimeUtilsTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val plus7: ZoneId = ZoneOffset.ofHours(7)
    private val us = Locale.US
    private val sample = millis("2024-05-01T08:30:00Z")

    @Test
    fun `format applies pattern and zone`() {
        assertEquals("01/05/2024 08:30", DateTimeUtils.format(sample, zone = utc, locale = us))
        assertEquals("01/05/2024 15:30", DateTimeUtils.format(sample, zone = plus7, locale = us))
        assertEquals("08:30", DateTimeUtils.format(sample, DateTimeUtils.PATTERN_TIME, utc, us))
    }

    @Test
    fun `parse is the inverse of format`() {
        val text = DateTimeUtils.format(sample, zone = plus7, locale = us)

        assertEquals(sample, DateTimeUtils.parse(text, zone = plus7, locale = us))
    }

    @Test
    fun `date-only pattern parses to the start of the day`() {
        assertEquals(
            millis("2024-05-01T00:00:00Z"),
            DateTimeUtils.parse("01/05/2024", DateTimeUtils.PATTERN_DATE, utc, us)
        )
    }

    @Test
    fun `parse returns null for invalid text or a pattern without a date`() {
        assertNull(DateTimeUtils.parse("32/13/2024 25:00", zone = utc, locale = us))
        assertNull(DateTimeUtils.parse("not a date", zone = utc, locale = us))
        assertNull(DateTimeUtils.parse("08:30", DateTimeUtils.PATTERN_TIME, utc, us))
    }

    @Test
    fun `iso round trip in UTC`() {
        assertEquals("2024-05-01T08:30:00Z", DateTimeUtils.formatIso(sample))
        assertEquals(sample, DateTimeUtils.parseIso("2024-05-01T08:30:00Z"))
    }

    @Test
    fun `iso with an offset resolves to the same instant and missing offset is rejected`() {
        assertEquals(sample, DateTimeUtils.parseIso("2024-05-01T15:30:00+07:00"))
        assertNull(DateTimeUtils.parseIso("2024-05-01T08:30:00"))
        assertNull(DateTimeUtils.parseIso("2024-05-01 08:30"))
    }

    @Test
    fun `startOfDay returns midnight in the zone`() {
        assertEquals(millis("2024-05-01T00:00:00Z"), DateTimeUtils.startOfDay(sample, utc))
        assertEquals(millis("2024-04-30T17:00:00Z"), DateTimeUtils.startOfDay(sample, plus7))
    }

    @Test
    fun `isToday compares calendar days in the zone`() {
        val now = millis("2024-05-01T23:59:59Z")

        assertTrue(DateTimeUtils.isToday(millis("2024-05-01T00:00:00Z"), now, utc))
        assertFalse(DateTimeUtils.isToday(millis("2024-05-02T00:00:00Z"), now, utc))
        // In UTC+7, `now` is already 2024-05-02 while 12:00Z is still 2024-05-01.
        assertFalse(DateTimeUtils.isToday(millis("2024-05-01T12:00:00Z"), now, plus7))
    }

    @Test
    fun `daysBetween counts calendar days, not 24 hour periods`() {
        val lateEvening = millis("2024-05-01T23:00:00Z")
        val nextMorning = millis("2024-05-02T01:00:00Z")

        assertEquals(1L, DateTimeUtils.daysBetween(lateEvening, nextMorning, utc))
        assertEquals(-1L, DateTimeUtils.daysBetween(nextMorning, lateEvening, utc))
        assertEquals(0L, DateTimeUtils.daysBetween(sample, sample, utc))
    }

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
