package com.salonbooking.app.customer.booking

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun `money and durations read naturally`() {
        assertEquals("$85", Formats.money(8500))
        assertEquals("$85.50", Formats.money(8550))
        assertEquals("45 min", Formats.duration(45))
        assertEquals("1 h", Formats.duration(60))
        assertEquals("1 h 30 min", Formats.duration(90))
    }

    @Test
    fun `times show in the branch time zone, not the phone's`() {
        // 23:00 UTC on 2 Nov is 10:00 on 3 Nov in Sydney (daylight saving) and 9:00 in Brisbane.
        assertEquals("10:00 am", Formats.clock("2026-11-02T23:00:00Z", "Australia/Sydney"))
        assertEquals("9:00 am", Formats.clock("2026-11-02T23:00:00Z", "Australia/Brisbane"))
        assertEquals("Tuesday 3 November", Formats.longDate("2026-11-02T23:00:00Z", "Australia/Sydney"))
        assertEquals("Tue 3 Nov", Formats.shortDate("2026-11-02T23:00:00Z", "Australia/Sydney"))
        assertEquals("2026-11-03", Formats.today("Australia/Sydney", Instant.parse("2026-11-02T23:00:00Z")))
    }

    @Test
    fun `cut-off and branch labels`() {
        assertEquals("24 hours", Formats.cutoff(1440))
        assertEquals("1 hour", Formats.cutoff(60))
        assertEquals("2 days", Formats.cutoff(2880))
        assertEquals("Fortitude Valley, QLD", Formats.branch("Fortitude Valley", "Fortitude Valley", "QLD"))
        assertEquals("Sydney CBD, Sydney", Formats.branch("Sydney CBD", "Sydney", "NSW"))
    }
}
