package com.example.healthheatv2.ui.screens

import com.example.healthheatv2.data.parseServerTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ScanDateTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ist).toInstant().toEpochMilli()
    private fun fmt(ts: Long) = formatScanDate(ts, ist, today, Locale.ENGLISH)

    @Test
    fun recentScansReadNaturally() {
        assertEquals("Today, 3:40 pm", fmt(at(2026, 10, 12, 15, 40)))
        assertEquals("Yesterday", fmt(at(2026, 10, 11, 23, 59)))
        assertEquals("3 Oct", fmt(at(2026, 10, 3)))
        assertEquals("28 Dec 2025", fmt(at(2025, 12, 28)))
    }

    @Test
    fun unknownTimeIsBlank() {
        assertEquals("", fmt(0L))
    }

    @Test
    fun serverUtcTimeShowsAsLocalDay() {
        // 20:00 UTC on the 11th is 01:30 IST on the 12th.
        val ts = parseServerTime("2026-10-11T20:00:00.123456")!!
        assertEquals("Today, 1:30 am", fmt(ts))
    }
}
