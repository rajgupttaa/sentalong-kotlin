package com.sentalong.sdk

/**
 * Minimal JSON encode/parse, kept dependency-free so the SDK adds nothing
 * beyond coroutines and works identically on Android and the plain JVM.
 * Encoding only needs flat string maps (the tracking API bodies are all
 * string fields); parsing handles arbitrary JSON but callers only read the
 * flat top-level fields of the API responses.
 */
internal object MiniJson {

    /** Encodes a flat map of string fields as a JSON object. */
    fun encode(fields: Map<String, String>): String =
        fields.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
            "${quote(k)}:${quote(v)}"
        }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Parses [text] as a JSON object into a Map. Values are String, Boolean,
     * Double, null, nested Map<String, Any?> or List<Any?>.
     * Throws [IllegalArgumentException] on malformed input.
     */
    fun parseObject(text: String): Map<String, Any?> {
        val p = Parser(text)
        p.skipWs()
        val value = p.parseValue()
        p.skipWs()
        if (!p.atEnd()) throw IllegalArgumentException("Trailing content after JSON value")
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?>
            ?: throw IllegalArgumentException("Top-level JSON value is not an object")
    }

    private class Parser(private val s: String) {
        var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun fail(msg: String): Nothing =
            throw IllegalArgumentException("$msg at index $i")

        private fun peek(): Char {
            if (atEnd()) fail("Unexpected end of input")
            return s[i]
        }

        private fun expect(literal: String) {
            if (!s.startsWith(literal, i)) fail("Expected '$literal'")
            i += literal.length
        }

        fun parseValue(): Any? {
            skipWs()
            return when (peek()) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> parseNumber()
            }
        }

        private fun parseObj(): Map<String, Any?> {
            expect("{")
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return out }
            while (true) {
                skipWs()
                if (peek() != '"') fail("Expected object key")
                val key = parseString()
                skipWs()
                expect(":")
                out[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun parseArr(): List<Any?> {
            expect("[")
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { i++; return out }
            while (true) {
                out.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            expect("\"")
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Bad unicode escape")
                                val hex = s.substring(i, i + 4)
                                val code = hex.toIntOrNull(16) ?: fail("Bad unicode escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            if (i == start) fail("Unexpected character '${s[start]}'")
            return s.substring(start, i).toDoubleOrNull() ?: fail("Bad number")
        }
    }
}
