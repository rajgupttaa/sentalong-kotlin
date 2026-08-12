package com.sentalong.sdk

/** In-memory [SentalongStorage] fake for JVM tests. */
class InMemoryStorage : SentalongStorage {

    val map = mutableMapOf<String, String>()

    override fun get(key: String): String? = map[key]

    override fun put(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}

/**
 * Scripted [HttpTransport] fake. Records every request; answers with the
 * queued responses in order (null simulates a network failure).
 */
internal class FakeTransport : HttpTransport {

    data class Request(val url: String, val body: String)

    val requests = mutableListOf<Request>()
    val responses = ArrayDeque<String?>()

    fun enqueue(response: String?) {
        responses.addLast(response)
    }

    override suspend fun postJson(url: String, body: String): String? {
        requests.add(Request(url, body))
        return if (responses.isEmpty()) null else responses.removeFirst()
    }
}
