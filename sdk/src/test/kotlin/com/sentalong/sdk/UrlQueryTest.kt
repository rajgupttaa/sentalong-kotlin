package com.sentalong.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlQueryTest {

    @Test
    fun `parses https link query`() {
        val params = UrlQuery.paramsOf("https://example.com/pricing?via=maya&utm_source=x")
        assertEquals("maya", params["via"])
        assertEquals("x", params["utm_source"])
    }

    @Test
    fun `parses custom scheme link`() {
        val params = UrlQuery.paramsOf("myapp://open?via=maya&sub_id=summer")
        assertEquals("maya", params["via"])
        assertEquals("summer", params["sub_id"])
    }

    @Test
    fun `url without query yields empty map`() {
        assertEquals(emptyMap<String, String>(), UrlQuery.paramsOf("https://example.com/pricing"))
        assertEquals(emptyMap<String, String>(), UrlQuery.paramsOf("myapp://open"))
    }

    @Test
    fun `fragment after query is ignored`() {
        val params = UrlQuery.paramsOf("https://example.com/?via=maya#section")
        assertEquals("maya", params["via"])
        assertEquals(1, params.size)
    }

    @Test
    fun `values are url decoded`() {
        val params = UrlQuery.paramsOf("https://example.com/?via=maya%20k&sub_id=a%26b")
        assertEquals("maya k", params["via"])
        assertEquals("a&b", params["sub_id"])
    }

    @Test
    fun `first occurrence of a key wins`() {
        val params = UrlQuery.paramsOf("https://example.com/?via=first&via=second")
        assertEquals("first", params["via"])
    }

    @Test
    fun `key without value parses to empty string`() {
        val params = UrlQuery.paramsOf("https://example.com/?via")
        assertEquals("", params["via"])
    }

    @Test
    fun `bare query string parsing for install referrer payloads`() {
        // Referrer arrives URL-encoded; the SDK decodes once, then parses.
        val decoded = UrlQuery.decode("via%3Dmaya%26sub_id%3Dx")
        assertEquals("via=maya&sub_id=x", decoded)
        val params = UrlQuery.parseQuery(decoded!!)
        assertEquals("maya", params["via"])
        assertEquals("x", params["sub_id"])
    }

    @Test
    fun `rawQueryOf handles trailing question mark`() {
        assertNull(UrlQuery.rawQueryOf("https://example.com/?"))
    }
}
