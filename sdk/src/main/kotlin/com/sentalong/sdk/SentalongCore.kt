package com.sentalong.sdk

/** Immutable SDK configuration. */
internal data class SentalongConfig(
    val baseUrl: String,
    val programId: String,
    val urlParam: String,
)

/**
 * All SDK logic, free of Android framework types so it runs under plain
 * JUnit on the JVM. [Sentalong] is a thin Android-facing wrapper around this
 * class. Storage, HTTP transport, clock and logging are injected.
 */
internal class SentalongCore(
    private val storage: SentalongStorage,
    private val transport: HttpTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val log: (String) -> Unit = { },
) {

    @Volatile
    var config: SentalongConfig? = null
        private set

    fun configure(baseUrl: String, programId: String, urlParam: String = "via") {
        config = SentalongConfig(
            baseUrl = baseUrl.trimEnd('/'),
            programId = programId,
            urlParam = urlParam.ifBlank { "via" },
        )
    }

    /**
     * Parses a deep link / app link, and when the attribution param is
     * present, records the click. Returns the new cid or null.
     * Never throws; network errors yield null.
     */
    suspend fun handleUrl(url: String): String? {
        val cfg = config ?: return null
        val click = try {
            ClickParser.fromParams(UrlQuery.paramsOf(url), cfg.urlParam)
        } catch (_: Throwable) {
            null
        } ?: return null
        return sendClick(click, url)
    }

    /**
     * Shared click path used by [handleUrl] and the install-referrer capture.
     * The literal `test` value short-circuits: nothing is sent or persisted
     * (mirrors t.js behavior).
     */
    suspend fun sendClick(click: ClickData, url: String?): String? {
        val cfg = config ?: return null

        if (click.via == "test") {
            log("Sentalong: test click received")
            return null
        }

        val body = LinkedHashMap<String, String>()
        body["program"] = cfg.programId
        body["via"] = click.via
        if (url != null) body["url"] = url.take(2048)
        click.subId?.let { body["sub_id"] = it.take(120) }
        for ((key, value) in click.adIds) body[key] = value.take(512)

        val response = transport.postJson("${cfg.baseUrl}/t/click", MiniJson.encode(body))
            ?: return null
        val obj = try {
            MiniJson.parseObject(response)
        } catch (_: Throwable) {
            return null
        }

        if (obj["ok"] != true) return null
        val cid = obj["cid"] as? String ?: return null
        val cookieDays = (obj["cookieDays"] as? Number)?.toInt() ?: 30

        storage.put(StorageKeys.CID, cid)
        storage.put(
            StorageKeys.CID_EXPIRES_AT,
            Iso.format(clock() + cookieDays * 86_400_000L),
        )
        return cid
    }

    /**
     * Returns the stored cid, or null. An expired cid is discarded (both
     * keys removed) and null is returned.
     */
    fun storedCid(): String? {
        val cid = storage.get(StorageKeys.CID) ?: return null
        val expiresAt = storage.get(StorageKeys.CID_EXPIRES_AT)?.let { Iso.parse(it) }
        if (expiresAt != null && expiresAt <= clock()) {
            storage.remove(StorageKeys.CID)
            storage.remove(StorageKeys.CID_EXPIRES_AT)
            return null
        }
        return cid
    }

    /**
     * Links the stored click to a user. Returns the referralId or null.
     * The cid stays stored after success — repeat identifies are
     * server-side idempotent.
     */
    suspend fun identify(email: String, externalId: String? = null): String? {
        val cfg = config ?: return null
        val cid = storedCid() ?: return null

        val body = LinkedHashMap<String, String>()
        body["cid"] = cid
        body["email"] = email
        externalId?.let { body["externalId"] = it }

        val response = transport.postJson("${cfg.baseUrl}/t/identify", MiniJson.encode(body))
            ?: return null
        val obj = try {
            MiniJson.parseObject(response)
        } catch (_: Throwable) {
            return null
        }
        if (obj["ok"] != true) return null
        return obj["referralId"] as? String
    }

    /** Marks a funnel stage ("signup" | "onboarded" | "demo") for the stored click. */
    suspend fun qualify(stage: String): Boolean {
        val cfg = config ?: return false
        val cid = storedCid() ?: return false

        val body = linkedMapOf("cid" to cid, "stage" to stage)
        val response = transport.postJson("${cfg.baseUrl}/t/qualify", MiniJson.encode(body))
            ?: return false
        val obj = try {
            MiniJson.parseObject(response)
        } catch (_: Throwable) {
            return false
        }
        return obj["ok"] == true
    }

    // --- Install referrer support (guard lives here so it is testable) ---

    fun referrerChecked(): Boolean = storage.get(StorageKeys.REFERRER_CHECKED) != null

    fun markReferrerChecked() {
        storage.put(StorageKeys.REFERRER_CHECKED, "1")
    }

    /**
     * Feeds a Play Store install referrer through the click path. [rawReferrer]
     * is the string exactly as returned by the Install Referrer API — it
     * arrives URL-encoded (e.g. `via%3Dmaya%26sub_id%3Dx`), so it is decoded
     * once and then parsed as a query string.
     */
    suspend fun handleInstallReferrer(rawReferrer: String, packageName: String): String? {
        val cfg = config ?: return null
        val click = try {
            val decoded = UrlQuery.decode(rawReferrer) ?: return null
            ClickParser.fromParams(UrlQuery.parseQuery(decoded), cfg.urlParam)
        } catch (_: Throwable) {
            null
        } ?: return null
        return sendClick(click, "android-app://$packageName")
    }
}
