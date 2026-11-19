package com.example.getyourmoneyupnotyourfunnyup.data.remote

/**
 * Pure-Kotlin JSON codec for `sync_queue.payload_json` snapshots and Firestore document maps.
 *
 * Avoids `android.jar` `org.json.JSONObject` stub exceptions in JVM unit tests while producing
 * standard RFC 8259 JSON strings compatible with Android's `org.json.JSONObject`.
 */
internal object SyncPayloadJson {

    fun encode(map: Map<String, Any?>): String = buildString {
        appendValue(map)
    }

    fun decodeObject(json: String): Map<String, Any?> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val parser = Parser(trimmed)
        val value = parser.parseValue()
        @Suppress("UNCHECKED_CAST")
        return (value as? Map<String, Any?>).orEmpty()
    }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean -> append(if (value) "true" else "false")
            is Number -> {
                val d = value.toDouble()
                if (d.isNaN() || d.isInfinite()) append("null") else append(value.toString())
            }
            is String -> appendString(value)
            is Map<*, *> -> {
                append('{')
                var first = true
                for ((k, v) in value) {
                    val key = k?.toString() ?: continue
                    if (!first) append(',')
                    first = false
                    appendString(key)
                    append(':')
                    appendValue(v)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                var first = true
                for (item in value) {
                    if (!first) append(',')
                    first = false
                    appendValue(item)
                }
                append(']')
            }
            is Array<*> -> {
                append('[')
                var first = true
                for (item in value) {
                    if (!first) append(',')
                    first = false
                    appendValue(item)
                }
                append(']')
            }
            else -> appendString(value.toString())
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) return null
            return when (input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = linkedMapOf<String, Any?>()
            pos++ // '{'
            skipWhitespace()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (pos < input.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (pos < input.length && input[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            pos++ // '['
            skipWhitespace()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (pos < input.length) {
                list.add(parseValue())
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun parseString(): String {
            if (pos < input.length && input[pos] == '"') pos++
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                if (ch == '"') break
                if (ch == '\\' && pos < input.length) {
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> if (pos + 4 <= input.length) {
                            val hex = input.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun parseBoolean(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else {
                pos += 5
                false
            }
        }

        private fun parseNull(): Any? {
            pos += 4
            return null
        }

        private fun parseNumber(): Number {
            val start = pos
            while (pos < input.length && input[pos] !in charArrayOf(',', '}', ']', ' ', '\n', '\r', '\t')) {
                pos++
            }
            val token = input.substring(start, pos)
            return if ('.' in token || 'e' in token || 'E' in token) {
                token.toDoubleOrNull() ?: 0.0
            } else {
                token.toLongOrNull() ?: 0L
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
