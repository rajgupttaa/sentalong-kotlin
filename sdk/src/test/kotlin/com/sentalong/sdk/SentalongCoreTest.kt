package com.sentalong.sdk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentalongCoreTest {

    private val storage = InMemoryStorage()
    private val transport = FakeTransport()
    private val logs = mutableListOf<String>()
    private var now = 1_700_000_000_000L

    private fun core(configured: Boolean = true): SentalongCore {
        val c = SentalongCore(
            storage = storage,
            transport = transport,
            clock = { now },
            log = { logs.add(it) },
        )
        if (configured) c.configure("https://sentalong.com/", "prg_1")
        return c
    }

    // --- handleUrl / click ---

    @Test
    fun `handleUrl posts click and persists cid with expiry`() = runTest {
        transport.enqueue("""{"ok":true,"cid":"clk_abc","cookieDays":30,"attribution":"last_click"}""")

        val cid = core().handleUrl("https://example.com/pricing?via=maya&sub_id=summer&gclid=g1")

        assertEquals("clk_abc", cid)
        assertEquals(1, transport.requests.size)
        val request = transport.requests[0]
        assertEquals("https://sentalong.com/t/click", request.url)
        assertEquals(
            """{"program":"prg_1","via":"maya","url":"https://example.com/pricing?via=maya&sub_id=summer&gclid=g1","sub_id":"summer","gclid":"g1"}""",
            request.body,
        )
        assertEquals("clk_abc", storage.map[StorageKeys.CID])
        // 30 days after `now`
        assertEquals(Iso.format(now + 30L * 86_400_000L), storage.map[StorageKeys.CID_EXPIRES_AT])
    }

    @Test
    fun `handleUrl works with custom schemes`() = runTest {
        transport.enqueue("""{"ok":true,"cid":"clk_x","cookieDays":7,"attribution":"first_click"}""")

        val cid = core().handleUrl("myapp://open?via=maya")

        assertEquals("clk_x", cid)
        assertEquals(Iso.format(now + 7L * 86_400_000L), storage.map[StorageKeys.CID_EXPIRES_AT])
    }

    @Test
    fun `handleUrl without attribution param sends nothing`() = runTest {
        val cid = core().handleUrl("https://example.com/pricing?utm_source=x")

        assertNull(cid)
        assertTrue(transport.requests.isEmpty())
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `test click short-circuits without network or persistence`() = runTest {
        val cid = core().handleUrl("https://example.com/?via=test")

        assertNull(cid)
        assertTrue(transport.requests.isEmpty())
        assertTrue(storage.map.isEmpty())
        assertEquals(listOf("Sentalong: test click received"), logs)
    }

    @Test
    fun `network failure returns null without persisting`() = runTest {
        transport.enqueue(null)

        val cid = core().handleUrl("https://example.com/?via=maya")

        assertNull(cid)
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `server ok false returns null`() = runTest {
        transport.enqueue("""{"ok":false}""")

        assertNull(core().handleUrl("https://example.com/?via=maya"))
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `malformed response body returns null`() = runTest {
        transport.enqueue("<html>gateway error</html>")

        assertNull(core().handleUrl("https://example.com/?via=maya"))
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `unconfigured core returns null`() = runTest {
        assertNull(core(configured = false).handleUrl("https://example.com/?via=maya"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `long url and ids are truncated to server limits`() = runTest {
        transport.enqueue("""{"ok":true,"cid":"clk_t","cookieDays":30}""")

        val longSub = "s".repeat(500)
        val click = ClickData(
            via = "maya",
            subId = longSub,
            adIds = mapOf("gclid" to "g".repeat(1000)),
        )
        val longUrl = "https://example.com/?" + "x".repeat(5000)
        core().sendClick(click, longUrl)

        val body = MiniJson.parseObject(transport.requests[0].body)
        assertEquals(2048, (body["url"] as String).length)
        assertEquals(120, (body["sub_id"] as String).length)
        assertEquals(512, (body["gclid"] as String).length)
    }

    // --- stored cid / expiry ---

    @Test
    fun `expired cid is discarded`() {
        storage.map[StorageKeys.CID] = "clk_old"
        storage.map[StorageKeys.CID_EXPIRES_AT] = Iso.format(now - 1000L)

        assertNull(core().storedCid())
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `unexpired cid is returned and kept`() {
        storage.map[StorageKeys.CID] = "clk_live"
        storage.map[StorageKeys.CID_EXPIRES_AT] = Iso.format(now + 1000L)

        assertEquals("clk_live", core().storedCid())
        assertEquals("clk_live", storage.map[StorageKeys.CID])
    }

    // --- identify ---

    @Test
    fun `identify posts stored cid and returns referralId`() = runTest {
        storage.map[StorageKeys.CID] = "clk_abc"
        storage.map[StorageKeys.CID_EXPIRES_AT] = Iso.format(now + 86_400_000L)
        transport.enqueue("""{"ok":true,"referralId":"ref_9"}""")

        val referralId = core().identify("user@example.com", externalId = "user_123")

        assertEquals("ref_9", referralId)
        val request = transport.requests[0]
        assertEquals("https://sentalong.com/t/identify", request.url)
        assertEquals(
            """{"cid":"clk_abc","email":"user@example.com","externalId":"user_123"}""",
            request.body,
        )
        // cid stays stored after success (idempotent on the server side)
        assertEquals("clk_abc", storage.map[StorageKeys.CID])
    }

    @Test
    fun `identify without stored cid returns null and sends nothing`() = runTest {
        assertNull(core().identify("user@example.com"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `identify with expired cid returns null and clears storage`() = runTest {
        storage.map[StorageKeys.CID] = "clk_old"
        storage.map[StorageKeys.CID_EXPIRES_AT] = Iso.format(now - 1L)

        assertNull(core().identify("user@example.com"))
        assertTrue(transport.requests.isEmpty())
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `identify omits externalId when not given`() = runTest {
        storage.map[StorageKeys.CID] = "clk_abc"
        transport.enqueue("""{"ok":true,"referralId":"ref_1"}""")

        core().identify("user@example.com")

        assertEquals("""{"cid":"clk_abc","email":"user@example.com"}""", transport.requests[0].body)
    }

    // --- qualify ---

    @Test
    fun `qualify posts stage and returns server ok`() = runTest {
        storage.map[StorageKeys.CID] = "clk_abc"
        transport.enqueue("""{"ok":true}""")

        assertTrue(core().qualify("signup"))
        val request = transport.requests[0]
        assertEquals("https://sentalong.com/t/qualify", request.url)
        assertEquals("""{"cid":"clk_abc","stage":"signup"}""", request.body)
    }

    @Test
    fun `qualify is false on failure or missing cid`() = runTest {
        assertFalse(core().qualify("signup"))

        storage.map[StorageKeys.CID] = "clk_abc"
        transport.enqueue("""{"ok":false}""")
        assertFalse(core().qualify("signup"))

        transport.enqueue(null)
        assertFalse(core().qualify("signup"))
    }

    // --- install referrer path ---

    @Test
    fun `install referrer is decoded, parsed and sent with android-app url`() = runTest {
        transport.enqueue("""{"ok":true,"cid":"clk_ref","cookieDays":30}""")

        val cid = core().handleInstallReferrer("via%3Dmaya%26sub_id%3Dx", "com.example.app")

        assertEquals("clk_ref", cid)
        assertEquals(
            """{"program":"prg_1","via":"maya","url":"android-app://com.example.app","sub_id":"x"}""",
            transport.requests[0].body,
        )
    }

    @Test
    fun `organic install referrer sends nothing`() = runTest {
        val cid = core().handleInstallReferrer(
            "utm_source%3Dgoogle-play%26utm_medium%3Dorganic",
            "com.example.app",
        )

        assertNull(cid)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `test value in install referrer short-circuits`() = runTest {
        val cid = core().handleInstallReferrer("via%3Dtest", "com.example.app")

        assertNull(cid)
        assertTrue(transport.requests.isEmpty())
        assertEquals(listOf("Sentalong: test click received"), logs)
    }

    @Test
    fun `referrer checked guard round trips`() {
        val c = core()
        assertFalse(c.referrerChecked())
        c.markReferrerChecked()
        assertTrue(c.referrerChecked())
        assertEquals("1", storage.map[StorageKeys.REFERRER_CHECKED])
    }
}
