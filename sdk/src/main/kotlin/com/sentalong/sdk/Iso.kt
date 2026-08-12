package com.sentalong.sdk

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 UTC timestamps via SimpleDateFormat so the SDK stays minSdk 21
 * compatible (java.time needs API 26 or desugaring). SimpleDateFormat is not
 * thread-safe, so instances are created per call.
 */
internal object Iso {

    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val PATTERN_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    private fun formatter(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

    fun format(epochMs: Long): String = formatter(PATTERN).format(Date(epochMs))

    /** Parses an ISO string (with or without millis). Null when unparseable. */
    fun parse(iso: String): Long? {
        for (pattern in arrayOf(PATTERN, PATTERN_MILLIS)) {
            try {
                return formatter(pattern).parse(iso)?.time ?: continue
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }
}
