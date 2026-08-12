package com.sentalong.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoTest {

    @Test
    fun `round trips epoch millis at second precision`() {
        val t = 1_700_000_000_000L
        assertEquals(t, Iso.parse(Iso.format(t)))
    }

    @Test
    fun `formats as utc iso`() {
        assertEquals("2023-11-14T22:13:20Z", Iso.format(1_700_000_000_000L))
    }

    @Test
    fun `parses millisecond variant`() {
        assertEquals(1_700_000_000_500L, Iso.parse("2023-11-14T22:13:20.500Z"))
    }

    @Test
    fun `unparseable input yields null`() {
        assertNull(Iso.parse("not-a-date"))
        assertNull(Iso.parse(""))
    }
}
