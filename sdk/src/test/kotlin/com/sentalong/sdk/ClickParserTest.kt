package com.sentalong.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickParserTest {

    @Test
    fun `missing via param yields null`() {
        assertNull(ClickParser.fromParams(mapOf("utm_source" to "x"), "via"))
    }

    @Test
    fun `blank via param yields null`() {
        assertNull(ClickParser.fromParams(mapOf("via" to ""), "via"))
        assertNull(ClickParser.fromParams(mapOf("via" to "  "), "via"))
    }

    @Test
    fun `extracts via and sub_id`() {
        val click = ClickParser.fromParams(mapOf("via" to "maya", "sub_id" to "summer"), "via")!!
        assertEquals("maya", click.via)
        assertEquals("summer", click.subId)
    }

    @Test
    fun `sub1 is accepted as sub_id alias`() {
        val click = ClickParser.fromParams(mapOf("via" to "maya", "sub1" to "fall"), "via")!!
        assertEquals("fall", click.subId)
    }

    @Test
    fun `sub_id wins over sub1`() {
        val click = ClickParser.fromParams(
            mapOf("via" to "maya", "sub_id" to "primary", "sub1" to "secondary"),
            "via",
        )!!
        assertEquals("primary", click.subId)
    }

    @Test
    fun `custom url param is honored`() {
        val click = ClickParser.fromParams(mapOf("ref" to "maya", "via" to "ignored"), "ref")!!
        assertEquals("maya", click.via)
    }

    @Test
    fun `collects all six ad click ids`() {
        val params = mapOf(
            "via" to "maya",
            "gclid" to "g1",
            "fbclid" to "f1",
            "ttclid" to "t1",
            "twclid" to "tw1",
            "li_fat_id" to "l1",
            "msclkid" to "m1",
            "otherid" to "nope",
        )
        val click = ClickParser.fromParams(params, "via")!!
        assertEquals(
            mapOf(
                "gclid" to "g1",
                "fbclid" to "f1",
                "ttclid" to "t1",
                "twclid" to "tw1",
                "li_fat_id" to "l1",
                "msclkid" to "m1",
            ),
            click.adIds,
        )
    }

    @Test
    fun `blank ad click ids are skipped`() {
        val click = ClickParser.fromParams(mapOf("via" to "maya", "gclid" to ""), "via")!!
        assertEquals(emptyMap<String, String>(), click.adIds)
    }
}
