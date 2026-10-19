package ua.savosh.ecoflowdiag.core

/**
 * Декодер protobuf-повідомлень станцій 3-го покоління (Delta 3 / River 3 / Delta Pro 3 тощо).
 *
 * Номери полів узяті з відкритих .proto-описів спільноти (hassio-ecoflow-cloud) і
 * однакові для Delta 3, River 3 та Delta Pro 3.
 *
 * Результат — плоский словник ключ->значення в тому ж стилі, що й JSON 1-го/2-го поколінь:
 *   bmsPack<N>.cycles, bmsPack<N>.soh, bmsPack<N>.cellVol ...   (BMS heartbeat кожного блоку)
 *   display.bmsBattSoh, runtime.bmsFullCap ...                  (зведені дані станції)
 *   p<func>_<id>.f<номер>                                         (усі інші поля — для "сирих" даних)
 */
object Gen3Decoder {

    private enum class T { U, I, F, S, RU, RI }

    private class F(val name: String, val type: T)

    /** BMS heartbeat (однаковий для всіх моделей 3-го покоління). */
    private val BMS: Map<Int, F> = mapOf(
        1 to F("num", T.U), 2 to F("type", T.U), 3 to F("cellId", T.U), 4 to F("errCode", T.U),
        5 to F("sysVer", T.U), 6 to F("soc", T.U), 7 to F("vol", T.U), 8 to F("amp", T.I),
        9 to F("temp", T.I), 10 to F("openBmsFlag", T.U), 11 to F("designCap", T.U),
        12 to F("remainCap", T.U), 13 to F("fullCap", T.U), 14 to F("cycles", T.U),
        15 to F("soh", T.U), 16 to F("maxCellVol", T.U), 17 to F("minCellVol", T.U),
        18 to F("maxCellTemp", T.I), 19 to F("minCellTemp", T.I), 20 to F("maxMosTemp", T.I),
        21 to F("minMosTemp", T.I), 22 to F("bmsFault", T.U), 23 to F("bqSysStatReg", T.U),
        24 to F("tagChgAmp", T.U), 25 to F("f32ShowSoc", T.F), 26 to F("inputWatts", T.U),
        27 to F("outputWatts", T.U), 28 to F("remainTime", T.U), 29 to F("mosState", T.U),
        30 to F("balanceState", T.U), 31 to F("maxVolDiff", T.U), 32 to F("cellSeriesNum", T.U),
        33 to F("cellVol", T.RU), 34 to F("cellNtcNum", T.U), 35 to F("cellTemp", T.RI),
        36 to F("hwVer", T.S), 37 to F("bmsHeartbeatVer", T.U), 38 to F("ecloudOcv", T.U),
        39 to F("bmsSn", T.S), 40 to F("productType", T.U), 41 to F("productDetail", T.U),
        42 to F("actSoc", T.F), 43 to F("diffSoc", T.F), 44 to F("targetSoc", T.F),
        45 to F("sysLoaderVer", T.U), 46 to F("sysState", T.U), 47 to F("chgDsgState", T.U),
        48 to F("allErrCode", T.U), 49 to F("allBmsFault", T.U), 50 to F("accuChgCap", T.U),
        51 to F("accuDsgCap", T.U), 52 to F("realSoh", T.F), 53 to F("calendarSoh", T.F),
        54 to F("cycSoh", T.F), 55 to F("mosNtcNum", T.U), 56 to F("mosTemp", T.RI),
        57 to F("envNtcNum", T.U), 58 to F("envTemp", T.RI), 63 to F("maxEnvTemp", T.I),
        64 to F("minEnvTemp", T.I), 74 to F("bmsAlarmState1", T.U), 75 to F("bmsAlarmState2", T.U),
        76 to F("bmsProtectState1", T.U), 77 to F("bmsProtectState2", T.U),
        78 to F("bmsFaultState", T.U), 79 to F("accuChgEnergy", T.U), 80 to F("accuDsgEnergy", T.U),
        81 to F("packSn", T.S), 82 to F("waterInFlag", T.U),
    )

    /** DisplayPropertyUpload (cmdFunc 254 / cmdId 21) — лише поля, корисні для діагностики. */
    private val DISPLAY: Map<Int, F> = mapOf(
        140 to F("bmsErrCode", T.U), 242 to F("bmsBattSoc", T.F), 243 to F("bmsBattSoh", T.F),
        248 to F("bmsDesignCap", T.U), 258 to F("bmsMinCellTemp", T.I), 259 to F("bmsMaxCellTemp", T.I),
        260 to F("bmsMinMosTemp", T.I), 261 to F("bmsMaxMosTemp", T.I), 262 to F("cmsBattSoc", T.F),
        263 to F("cmsBattSoh", T.F),
    )

    /** RuntimePropertyUpload (cmdFunc 254 / cmdId 22). */
    private val RUNTIME: Map<Int, F> = mapOf(
        247 to F("bmsFullCap", T.U), 249 to F("bmsRemainCap", T.U), 253 to F("bmsErrCode", T.U),
        256 to F("bmsMinCellVol", T.U), 257 to F("bmsMaxCellVol", T.U),
    )

    private val BMS_HEARTBEAT_CMDS: Set<Pair<Int, Int>> = setOf(
        3 to 1, 3 to 2, 3 to 30, 3 to 50,
        32 to 1, 32 to 3, 32 to 50, 32 to 51, 32 to 52,
        254 to 24, 254 to 25, 254 to 26, 254 to 27, 254 to 28, 254 to 29, 254 to 30,
    )

    class Header(
        val pdata: ByteArray?,
        val src: Int,
        val dest: Int,
        val encType: Int,
        val cmdFunc: Int,
        val cmdId: Int,
        val seq: Int,
        val deviceSn: String?,
        val moduleSn: String?,
    )

    class Result(
        val params: Map<String, Any?>,
        val headers: Int,
        val bmsPacks: Int,
        val description: String,
    )

    /** Чи схожий payload на JSON (перший не-пробільний символ — '{'). */
    fun looksLikeJson(payload: ByteArray): Boolean {
        for (b in payload) {
            val c = b.toInt().toChar()
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue
            return c == '{' || c == '['
        }
        return false
    }

    private fun maybeBase64(payload: ByteArray): ByteArray {
        if (payload.isEmpty() || payload.size % 4 != 0) return payload
        for (b in payload) {
            val c = b.toInt().toChar()
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
            if (!ok) return payload
        }
        return try {
            java.util.Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            payload
        }
    }

    fun parseHeaders(payload: ByteArray): List<Header>? {
        val data = maybeBase64(payload)
        val top = ProtoWire.parse(data) ?: return null
        val headers = ArrayList<Header>()
        for (f in top) {
            if (f.number != 1 || f.wireType != 2) continue
            val hf = ProtoWire.parse(f.bytes ?: continue) ?: continue
            var pdata: ByteArray? = null
            var src = 0
            var dest = 0
            var enc = 0
            var func = 0
            var id = 0
            var seq = 0
            var deviceSn: String? = null
            var moduleSn: String? = null
            for (x in hf) {
                when (x.number) {
                    1 -> if (x.wireType == 2) pdata = x.bytes
                    2 -> src = x.asInt
                    3 -> dest = x.asInt
                    6 -> enc = x.asInt
                    8 -> func = x.asInt
                    9 -> id = x.asInt
                    14 -> seq = x.asInt
                    24 -> if (x.wireType == 2) moduleSn = x.asString
                    25 -> if (x.wireType == 2) deviceSn = x.asString
                }
            }
            headers.add(Header(pdata, src, dest, enc, func, id, seq, deviceSn, moduleSn))
        }
        return if (headers.isEmpty()) null else headers
    }

    /** XOR-розшифровка payload молодшим байтом seq (як у мобільного застосунку EcoFlow). */
    fun xorDecode(pdata: ByteArray, seq: Int): ByteArray {
        val key = seq and 0xFF
        return ByteArray(pdata.size) { i -> ((pdata[i].toInt() and 0xFF) xor key).toByte() }
    }

    fun decode(payload: ByteArray): Result? {
        if (looksLikeJson(payload)) return null
        val headers = parseHeaders(payload) ?: return null
        val out = LinkedHashMap<String, Any?>()
        var packs = 0
        val desc = StringBuilder()
        for (h in headers) {
            val raw = h.pdata ?: continue
            if (raw.isEmpty()) continue
            val pdata = if (h.encType == 1 && h.src != 32) xorDecode(raw, h.seq) else raw
            val fields = ProtoWire.parse(pdata) ?: continue
            val cmd = h.cmdFunc to h.cmdId
            if (desc.isNotEmpty()) desc.append(", ")
            desc.append("${h.cmdFunc}/${h.cmdId}")
            when {
                cmd in BMS_HEARTBEAT_CMDS || looksLikeBmsHeartbeat(fields) -> {
                    val num = fields.firstOrNull { it.number == 1 && it.wireType == 0 }?.asInt ?: 0
                    putTyped(out, "bmsPack$num", fields, BMS, keepUnknown = true)
                    packs++
                }
                cmd == (254 to 21) -> putTyped(out, "display", fields, DISPLAY, keepUnknown = false).also {
                    putUnknown(out, "p254_21", fields, DISPLAY)
                }
                cmd == (254 to 22) -> putTyped(out, "runtime", fields, RUNTIME, keepUnknown = false).also {
                    putUnknown(out, "p254_22", fields, RUNTIME)
                }
                else -> putUnknown(out, "p${h.cmdFunc}_${h.cmdId}", fields, emptyMap())
            }
        }
        return Result(out, headers.size, packs, desc.toString())
    }

    /** Запит "надішли всі дані" (як робить застосунок EcoFlow при відкритті сторінки станції). */
    fun buildGetAllRequest(seq: Int, from: String = "Android"): ByteArray {
        val header = ProtoWire.Builder()
            .varintField(2, 32)
            .varintField(3, 32)
            .varintField(14, seq.toLong() and 0xFFFFFFFFL)
            .stringField(23, from)
            .build()
        return ProtoWire.Builder().bytesField(1, header).build()
    }

    private fun looksLikeBmsHeartbeat(fields: List<ProtoField>): Boolean {
        if (fields.any { it.number > 100 }) return false
        val nums = fields.filter { it.wireType == 0 }.map { it.number }.toSet()
        return 11 in nums && (14 in nums || 79 in nums || 80 in nums)
    }

    private fun putTyped(
        out: MutableMap<String, Any?>,
        prefix: String,
        fields: List<ProtoField>,
        schema: Map<Int, F>,
        keepUnknown: Boolean,
    ) {
        val repeated = HashMap<String, MutableList<Long>>()
        for (f in fields) {
            val def = schema[f.number]
            if (def == null) {
                if (keepUnknown) putRaw(out, "$prefix.f${f.number}", f)
                continue
            }
            val key = "$prefix.${def.name}"
            when (def.type) {
                T.U -> if (f.wireType == 0) out[key] = f.raw and 0xFFFFFFFFL else putRaw(out, key, f)
                T.I -> if (f.wireType == 0) out[key] = f.raw.toInt().toLong() else putRaw(out, key, f)
                T.F -> out[key] = when (f.wireType) {
                    5 -> f.asFloat.toDouble()
                    1 -> f.asDouble
                    else -> f.raw.toDouble()
                }
                T.S -> out[key] = f.asString.trim { it <= ' ' }
                T.RU, T.RI -> {
                    val list = repeated.getOrPut(key) { ArrayList() }
                    val signed = def.type == T.RI
                    if (f.wireType == 2) {
                        for (v in f.packedVarints()) list.add(if (signed) v.toInt().toLong() else v and 0xFFFFFFFFL)
                    } else if (f.wireType == 0) {
                        list.add(if (signed) f.raw.toInt().toLong() else f.raw and 0xFFFFFFFFL)
                    }
                }
            }
        }
        for ((k, v) in repeated) out[k] = v.toList()
    }

    private fun putUnknown(out: MutableMap<String, Any?>, prefix: String, fields: List<ProtoField>, known: Map<Int, F>) {
        for (f in fields) {
            if (f.number in known) continue
            putRaw(out, "$prefix.f${f.number}", f)
        }
    }

    private fun putRaw(out: MutableMap<String, Any?>, key: String, f: ProtoField) {
        out[key] = when (f.wireType) {
            0 -> f.raw
            5 -> {
                val fl = f.asFloat
                if (fl.isNaN() || fl.isInfinite()) f.raw else fl.toDouble()
            }
            1 -> f.asDouble
            2 -> {
                val b = f.bytes ?: ByteArray(0)
                val printable = b.isNotEmpty() && b.all { it in 0x20..0x7E }
                if (printable) String(b, Charsets.US_ASCII) else "bytes[${b.size}]"
            }
            else -> null
        }
    }
}
