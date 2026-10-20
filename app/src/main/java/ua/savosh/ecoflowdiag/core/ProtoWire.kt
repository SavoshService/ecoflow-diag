package ua.savosh.ecoflowdiag.core

/**
 * Ручний розбір protobuf wire-формату (без бібліотеки protobuf і без .proto-компіляції).
 * Станції 3-го покоління (Delta 3, River 3, Delta Pro 3) шлють дані саме так.
 */
class ProtoField(
    val number: Int,
    val wireType: Int,
    /** Значення для varint (0), fixed64 (1), fixed32 (5). */
    val raw: Long,
    /** Байти для length-delimited (2). */
    val bytes: ByteArray?,
) {
    val asInt: Int get() = raw.toInt()
    val asLong: Long get() = raw
    val asFloat: Float get() = java.lang.Float.intBitsToFloat(raw.toInt())
    val asDouble: Double get() = java.lang.Double.longBitsToDouble(raw)
    val asString: String get() = bytes?.toString(Charsets.UTF_8) ?: ""

    /** Розпаковує packed repeated varint (наприклад, cell_vol). */
    fun packedVarints(): List<Long> {
        val b = bytes ?: return emptyList()
        val out = ArrayList<Long>()
        val r = ProtoWire.Reader(b)
        while (!r.atEnd()) out.add(r.readVarint() ?: break)
        return out
    }
}

object ProtoWire {

    class Reader(private val data: ByteArray) {
        var pos = 0

        fun atEnd() = pos >= data.size

        fun readVarint(): Long? {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= data.size) return null
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        fun readFixed32(): Long? {
            if (pos + 4 > data.size) return null
            var v = 0L
            for (i in 0 until 4) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
            pos += 4
            return v
        }

        fun readFixed64(): Long? {
            if (pos + 8 > data.size) return null
            var v = 0L
            for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
            pos += 8
            return v
        }

        fun readBytes(len: Int): ByteArray? {
            if (len < 0 || pos + len > data.size) return null
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }
    }

    /**
     * Розбирає повідомлення на поля. Повертає null, якщо байти не схожі на коректний protobuf.
     */
    fun parse(data: ByteArray): List<ProtoField>? {
        val r = Reader(data)
        val out = ArrayList<ProtoField>()
        while (!r.atEnd()) {
            val key = r.readVarint() ?: return null
            val number = (key ushr 3).toInt()
            val wire = (key and 7).toInt()
            if (number <= 0) return null
            when (wire) {
                0 -> out.add(ProtoField(number, wire, r.readVarint() ?: return null, null))
                1 -> out.add(ProtoField(number, wire, r.readFixed64() ?: return null, null))
                2 -> {
                    val len = r.readVarint() ?: return null
                    if (len > Int.MAX_VALUE) return null
                    out.add(ProtoField(number, wire, 0, r.readBytes(len.toInt()) ?: return null))
                }
                5 -> out.add(ProtoField(number, wire, r.readFixed32() ?: return null, null))
                else -> return null
            }
        }
        return out
    }

    // ---------- кодування (для запиту "дай усі дані" на станції 3-го покоління) ----------

    fun varint(value: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return out.toByteArray()
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    class Builder {
        private val out = java.io.ByteArrayOutputStream()

        fun varintField(number: Int, value: Long): Builder {
            out.write(varint(((number shl 3) or 0).toLong()))
            out.write(varint(value))
            return this
        }

        fun bytesField(number: Int, value: ByteArray): Builder {
            out.write(varint(((number shl 3) or 2).toLong()))
            out.write(varint(value.size.toLong()))
            out.write(value)
            return this
        }

        fun stringField(number: Int, value: String) = bytesField(number, value.toByteArray(Charsets.UTF_8))

        fun build(): ByteArray = out.toByteArray()
    }
}
