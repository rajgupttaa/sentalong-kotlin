package com.sentalong.sdk

import java.net.URLDecoder

/**
 * Pure query-string parsing. No android.net.Uri so the logic runs (and is
 * tested) on the plain JVM. Handles https links, custom schemes
 * (myapp://open?via=x) and bare query strings (install referrer payloads).
 */
internal object UrlQuery {

    /**
     * Extracts the raw query portion of a URL: everything after the first
     * `?` and before the first `#` that follows it. Returns null when the
     * URL has no query.
     */
    fun rawQueryOf(url: String): String? {
        val q = url.indexOf('?')
        if (q < 0 || q == url.length - 1) return null
        val rest = url.substring(q + 1)
        val hash = rest.indexOf('#')
        val query = if (hash >= 0) rest.substring(0, hash) else rest
        return if (query.isEmpty()) null else query
    }

    /** Parses a URL's query string into a map. First occurrence of a key wins. */
    fun paramsOf(url: String): Map<String, String> {
        val raw = rawQueryOf(url) ?: return emptyMap()
        return parseQuery(raw)
    }

    /**
     * Parses a bare query string (`a=1&b=2`) into a map, URL-decoding both
     * keys and values. Malformed pairs are skipped, never thrown on.
     * First occurrence of a key wins.
     */
    fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val rawKey = if (eq >= 0) pair.substring(0, eq) else pair
            val rawValue = if (eq >= 0) pair.substring(eq + 1) else ""
            val key = decode(rawKey) ?: continue
            if (key.isEmpty()) continue
            val value = decode(rawValue) ?: continue
            if (!out.containsKey(key)) out[key] = value
        }
        return out
    }

    /** URL-decodes a component. Returns null when the input is malformed. */
    fun decode(s: String): String? = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        null
    }
}
