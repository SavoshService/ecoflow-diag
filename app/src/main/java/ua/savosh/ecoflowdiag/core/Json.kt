package ua.savosh.ecoflowdiag.core

/**
 * Мінімальний JSON-парсер без залежностей (щоб ядро можна було тестувати на звичайній JVM).
 * Об'єкти -> LinkedHashMap<String, Any?>, масиви -> List<Any?>,
 * цілі числа -> Long, дробові -> Double, рядки -> String, true/false -> Boolean, null -> null.
 */
object Json {

    class ParseException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Зайві символи після JSON на позиції ${p.pos}")
        return v
    }

    fun parseOrNull(text: String): Any? = try {
        parse(text)
    } catch (e: Exception) {
        null
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?>? = parseOrNull(text) as? Map<String, Any?>

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWs()
            if (atEnd()) throw ParseException("Несподіваний кінець JSON")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw ParseException("Неочікуваний символ '$c' на позиції $pos")
            }
        }

        fun readLiteral(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Очікувалось $word на позиції $pos")
            pos += word.length
            return value
        }

        fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') throw ParseException("Очікувався ключ на позиції $pos")
                val key = readString()
                skipWs()
                if (atEnd() || s[pos] != ':') throw ParseException("Очікувалось ':' на позиції $pos")
                pos++
                map[key] = readValue()
                skipWs()
                if (atEnd()) throw ParseException("Несподіваний кінець об'єкта")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw ParseException("Очікувалось ',' або '}' на позиції $pos")
                }
            }
        }

        fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWs()
                if (atEnd()) throw ParseException("Несподіваний кінець масиву")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw ParseException("Очікувалось ',' або ']' на позиції $pos")
                }
            }
        }

        fun readString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Незакритий рядок")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Незакрите екранування")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Обрізаний \\u")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun readNumber(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            var isFloat = false
            while (pos < s.length) {
                val c = s[pos]
                if (c.isDigit()) {
                    pos++
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true
                    pos++
                } else {
                    break
                }
            }
            val str = s.substring(start, pos)
            if (!isFloat) {
                str.toLongOrNull()?.let { return it }
            }
            return str.toDoubleOrNull() ?: throw ParseException("Некоректне число '$str'")
        }
    }

    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> write(sb, v.toDouble())
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
