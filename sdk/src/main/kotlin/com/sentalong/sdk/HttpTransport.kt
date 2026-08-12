package com.sentalong.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP abstraction so the core logic can be unit-tested without a
 * network. The production implementation uses HttpURLConnection — no OkHttp,
 * no heavy dependencies.
 */
internal interface HttpTransport {
    /**
     * POSTs [body] as application/json to [url] and returns the response
     * body as a string, or null on any network/HTTP failure. Never throws.
     */
    suspend fun postJson(url: String, body: String): String?
}

internal class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpTransport {

    override suspend fun postJson(url: String, body: String): String? =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } catch (_: Throwable) {
                null
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
}
