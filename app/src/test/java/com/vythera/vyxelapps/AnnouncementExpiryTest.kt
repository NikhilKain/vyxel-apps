package com.vythera.vyxelapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AnnouncementExpiryTest {

    private val IST = ZoneOffset.ofHoursMinutes(5, 30)

    private fun stamp(instant: Instant, offset: ZoneOffset): String =
        OffsetDateTime.ofInstant(instant, offset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun ann(expiresAt: String) = Announcement(
        id = "x", active = true, title = "t", message = "m", expiresAt = expiresAt
    )

    @Test
    fun `no expiry stamp means it never expires on its own`() {
        assertFalse(ann("").isExpired)
    }

    @Test
    fun `a past deadline is expired`() {
        val past = Instant.now().minusSeconds(60)
        assertTrue(ann(stamp(past, ZoneOffset.UTC)).isExpired)
        assertTrue(ann(stamp(past, IST)).isExpired)
    }

    @Test
    fun `a future deadline is not expired`() {
        val future = Instant.now().plusSeconds(3600)
        assertFalse(ann(stamp(future, ZoneOffset.UTC)).isExpired)
        assertFalse(ann(stamp(future, IST)).isExpired)
    }

    /**
     * The author writes IST because the event is announced in IST. 4:00 PM IST
     * and 10:30 UTC are the same instant, so both spellings must agree — this is
     * exactly the off-by-5:30 mistake the offset form exists to prevent.
     */
    @Test
    fun `IST offset and equivalent UTC stamp describe the same instant`() {
        val ist = ann("2026-08-20T16:00:00+05:30")
        val utc = ann("2026-08-20T10:30:00Z")
        assertEquals(utc.isExpired, ist.isExpired)

        // And an IST stamp is NOT read as if it were UTC (which would be 5.5h off).
        val naive = ann("2026-08-20T16:00:00Z")
        assertEquals(
            OffsetDateTime.parse("2026-08-20T16:00:00+05:30").toInstant(),
            OffsetDateTime.parse("2026-08-20T10:30:00Z").toInstant()
        )
        assertNotEquals(
            OffsetDateTime.parse(naive.expiresAt).toInstant(),
            OffsetDateTime.parse(ist.expiresAt).toInstant()
        )
    }

    @Test
    fun `a malformed stamp fails open rather than blanking a live campaign`() {
        assertFalse(ann("20 August 2026 4pm").isExpired)
        assertFalse(ann("2026-08-20").isExpired)
        assertFalse(ann("garbage").isExpired)
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val past = stamp(Instant.now().minusSeconds(60), IST)
        assertTrue(ann("  $past  ").isExpired)
    }

    @Test
    fun `expiry does not change the dedupe signature`() {
        val a = Announcement(id = "x", title = "t", message = "m", expiresAt = "2026-08-20T16:00:00+05:30")
        val b = a.copy(expiresAt = "2026-09-01T16:00:00+05:30")
        // Extending a deadline must not re-show the banner to someone who dismissed it.
        assertEquals(a.signature, b.signature)
    }
}
