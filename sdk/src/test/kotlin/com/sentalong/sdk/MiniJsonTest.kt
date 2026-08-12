package com.sentalong.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `encodes flat string map`() {
        val json = MiniJson.encode(linkedMapOf("program" to "prg_1", "via" to "maya"))
        assertEquals("""{"program":"prg_1","via":"maya"}""", json)
    }

    @Test
    fun `encodes escapes in values`() {
        val json = MiniJson.encode(linkedMapOf("url" to "https://x.com/?a=\"b\"\\c"))
        assertEquals("""{"url":"https://x.com/?a=\"b\"\\c"}""", json)
    }

    @Test
    fun `parses click response`() {
        val obj = MiniJson.parseObject(
            """{"ok":true,"cid":"clk_123","cookieDays":30,"attribution":"last_click"}""",
        )
        assertEquals(true, obj["ok"])
        assertEquals("clk_123", obj["cid"])
        assertEquals(30, (obj["cookieDays"] as Number).toInt())
        assertEquals("last_click", obj["attribution"])
    }

    @Test
    fun `parses failure response`() {
        val obj = MiniJson.parseObject("""{"ok":false}""")
        assertEquals(false, obj["ok"])
    }

    @Test
    fun `parses string escapes and unicode`() {
        // Triple-quoted: the parser sees literal backslash escapes.
        val obj = MiniJson.parseObject("""{"a":"x\n\t\"\\A","u":"\u0041"}""")
        assertEquals("x\n\t\"\\A", obj["a"])
        assertEquals("A", obj["u"])
    }

    @Test
    fun `parses null and nested values`() {
        val obj = MiniJson.parseObject("""{"a":null,"b":{"c":[1,2]},"d":-1.5e2}""")
        assertNull(obj["a"])
        @Suppress("UNCHECKED_CAST")
        val nested = obj["b"] as Map<String, Any?>
        assertEquals(listOf(1.0, 2.0), nested["c"])
        assertEquals(-150.0, obj["d"] as Double, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed json`() {
        MiniJson.parseObject("""{"ok":tru""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-object top level`() {
        MiniJson.parseObject("""[1,2,3]""")
    }
}
