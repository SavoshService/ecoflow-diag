package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.Json
import ua.savosh.ecoflowdiag.core.ProtoWire
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/** Імітація однієї станції: що й коли вона відповідає в кожному форматі. */
class SimStation(
    val sn: String,
    /** Повний знімок у JSON. null — прошивка не відповідає на JSON-запит. */
    val quota: Map<String, Any?>? = null,
    /** Відповідь у protobuf. null — прошивка не відповідає на protobuf-запит. */
    val proto: (() -> ByteArray)? = null,
    var online: Boolean = true,
    /** Офлайн-станція: хмара віддає останній збережений знімок. */
    val cachedWhenOffline: Boolean = false,
    /** JSON-запит до protobuf-станції: хмара відповідає online=0, хоча станція онлайн. */
    val jsonSaysOffline: Boolean = false,
    val replyDelayMs: Long = 0,
    /** Перше, що станція шле одразу після підписки, ще до відповіді на запит. */
    val partialOnSubscribe: ByteArray? = null,
    /** Після стількох відповідей одному з'єднанню замовкає, доки не буде нового з'єднання. */
    val stallAfterReplies: Int? = null,
    /** Брокер не дозволяє підписку (станції немає в цьому акаунті). */
    val denied: Boolean = false,
    val pushEveryMs: Long = 0,
    val push: () -> ByteArray? = { null },
) {
    val dataTopic = "/app/device/property/$sn"
    val commands = CopyOnWriteArrayList<String>()
    @Volatile var lastPushMs = 0L

    fun answer(isJson: Boolean): ByteArray? = when {
        isJson && !online -> quotaReply(false, if (cachedWhenOffline) quota ?: emptyMap() else emptyMap())
        isJson && quota != null -> quotaReply(true, quota)
        isJson && jsonSaysOffline -> quotaReply(false, emptyMap())
        isJson -> null
        online -> proto?.invoke()
        else -> null
    }

    companion object {
        fun quotaReply(online: Boolean, quota: Map<String, Any?>): ByteArray = Json.stringify(
            linkedMapOf(
                "id" to "1", "version" to "1.1", "operateType" to "latestQuotas", "code" to "0", "message" to "Success",
                "data" to linkedMapOf("online" to if (online) 1 else 0, "quotaMap" to quota),
            ),
        ).toByteArray()

        /** Телеметрія 2-го покоління приходить короткими порціями з typeCode. */
        fun typeCodePush(type: String, params: Map<String, Any?>): ByteArray =
            Json.stringify(linkedMapOf("id" to 7, "version" to "1.0", "typeCode" to type, "params" to params)).toByteArray()

        /** Телеметрія 1-го покоління: ключі вже з крапкою. */
        fun flatPush(params: Map<String, Any?>): ByteArray =
            Json.stringify(linkedMapOf("id" to 7, "version" to "1.0", "params" to params)).toByteArray()
    }
}

/** Типові дані станцій за поколіннями: ключі й одиниці такі, як у спільноти. */
object SimData {

    private fun cells(series: Int, mv: Long, spread: Long): List<Long> = List(series) { i -> if (i == series / 2) mv + spread else mv }

    fun pack(p: String, series: Int, mv: Long, soc: Long, design: Long, soh: Long = 95, cycles: Long = 120, amp: Long = 0, spread: Long = 6): Map<String, Any?> {
        val c = cells(series, mv, spread)
        return linkedMapOf(
            "$p.soc" to soc, "$p.f32ShowSoc" to soc.toDouble(), "$p.soh" to soh, "$p.designCap" to design,
            "$p.fullCap" to design * soh / 100, "$p.remainCap" to design * soh / 100 * soc / 100,
            "$p.cycles" to cycles, "$p.vol" to c.sum(), "$p.amp" to amp, "$p.temp" to 26L,
            "$p.minCellVol" to c.min(), "$p.maxCellVol" to c.max(), "$p.cellVol" to c,
            "$p.minCellTemp" to 24L, "$p.maxCellTemp" to 27L, "$p.minMosTemp" to 28L, "$p.maxMosTemp" to 30L,
            "$p.errCode" to 0L, "$p.bmsFault" to 0L, "$p.mosState" to 3L,
        )
    }

    /** 1-ше покоління: DELTA, DELTA Max, DELTA mini, DELTA Pro, River першого покоління. */
    fun gen1(series: Int, mv: Long, soc: Long, design: Long): Map<String, Any?> = pack("bmsMaster", series, mv, soc, design) + linkedMapOf<String, Any?>(
        "ems.maxChargeSoc" to 100L, "ems.minDsgSoc" to 0L, "ems.lcdShowSoc" to soc, "ems.emsIsNormalFlag" to 1L,
        "ems.bms0Online" to 3L, "ems.chgCmd" to 1L, "ems.dsgCmd" to 1L,
        "pd.soc" to soc, "pd.wattsInSum" to 0L, "pd.wattsOutSum" to 40L, "pd.iconBmsErrState" to 0L, "pd.errCode" to 0L,
        "inv.inputWatts" to 0L, "inv.outputWatts" to 40L, "inv.errCode" to 0L,
    )

    /** River mini: батарея не окремим блоком, а в полях інвертора, без напруг і ємності. */
    fun riverMini(soc: Long): Map<String, Any?> = linkedMapOf(
        "inv.soc" to soc, "inv.cycles" to 80L, "inv.maxChargeSoc" to 100L, "inv.inputWatts" to 0L, "inv.outputWatts" to 10L,
    )

    /** 2-ге покоління: DELTA 2, DELTA 2 Max, River 2, DELTA 3 1500. */
    fun gen2(series: Int, mv: Long, soc: Long, design: Long): Map<String, Any?> = pack("bms_bmsStatus", series, mv, soc, design) + linkedMapOf<String, Any?>(
        "bms_bmsInfo.soh" to 95L, "bms_emsStatus.maxChargeSoc" to 100L, "bms_emsStatus.minDsgSoc" to 0L, "bms_emsStatus.lcdShowSoc" to soc,
        "bms_emsStatus.emsIsNormalFlag" to 1L, "bms_emsStatus.bmsIsConnt" to listOf(3L, 0L, 0L), "bms_emsStatus.chgCmd" to 1L, "bms_emsStatus.dsgCmd" to 1L,
        "pd.soc" to soc, "pd.wattsInSum" to 0L, "pd.wattsOutSum" to 40L, "pd.iconBmsErrState" to 0L, "pd.errCode" to 0L,
        "inv.inputWatts" to 0L, "inv.outputWatts" to 40L, "inv.errCode" to 0L, "mppt.faultCode" to 0L,
    )

    // ---------------------------------------------------------------- protobuf 3-го покоління

    private class W {
        val o = ByteArrayOutputStream()
        fun v(n: Int, x: Long) = apply { o.write(ProtoWire.varint((n shl 3).toLong())); o.write(ProtoWire.varint(x)) }
        fun f32(n: Int, x: Float) = apply {
            o.write(ProtoWire.varint(((n shl 3) or 5).toLong()))
            val b = java.lang.Float.floatToIntBits(x)
            for (i in 0 until 4) o.write((b shr (8 * i)) and 255)
        }
        fun b(n: Int, x: ByteArray) = apply { o.write(ProtoWire.varint(((n shl 3) or 2).toLong())); o.write(ProtoWire.varint(x.size.toLong())); o.write(x) }
        fun bytes(): ByteArray = o.toByteArray()
    }

    private fun bmsHeartbeat(num: Long, series: Int, mv: Long, soc: Long, design: Long): ByteArray {
        val c = cells(series, mv, 6)
        val packed = ByteArrayOutputStream().also { o -> c.forEach { o.write(ProtoWire.varint(it)) } }.toByteArray()
        return W().v(1, num).v(4, 0).v(6, soc).v(7, c.sum()).v(8, -1200).v(9, 26).v(11, design).v(12, design * soc / 100).v(13, design * 97 / 100)
            .v(14, 60).v(15, 97).v(16, c.max()).v(17, c.min()).v(18, 27).v(19, 24).v(20, 30).v(21, 28).v(22, 0).v(23, 0)
            .f32(25, soc.toFloat()).v(29, 3).v(32, series.toLong()).b(33, packed).v(48, 0).v(49, 0).v(79, 200_000).v(80, 180_000).bytes()
    }

    private fun display(soc: Long, design: Long): ByteArray = W().v(140, 0).f32(242, soc.toFloat()).f32(243, 97f).v(248, design).v(258, 24).v(259, 27).v(260, 28).v(261, 30).bytes()

    private fun runtime(mv: Long, design: Long, soc: Long): ByteArray = W().v(247, design * 97 / 100).v(249, design * soc / 100).v(253, 0).v(256, mv).v(257, mv + 6).bytes()

    private fun header(pdata: ByteArray, func: Int, id: Int, seq: Int): ByteArray {
        // Шифрування як у справжніх станцій: XOR молодшим байтом seq, якщо enc=1 і src≠32.
        val enc = ByteArray(pdata.size) { i -> ((pdata[i].toInt() and 255) xor (seq and 255)).toByte() }
        return W().b(1, enc).v(2, 2).v(3, 32).v(6, 1).v(8, func.toLong()).v(9, id.toLong()).v(14, seq.toLong()).bytes()
    }

    /** Повний знімок 3-го покоління: кілька заголовків в одному повідомленні, як у get_reply. */
    fun gen3(series: Int, mv: Long, soc: Long, design: Long): ByteArray = W()
        .b(1, header(bmsHeartbeat(1, series, mv, soc, design), 3, 30, 11))
        .b(1, header(display(soc, design), 254, 21, 12))
        .b(1, header(runtime(mv, design, soc), 254, 22, 13))
        .bytes()
}

/** Станції всіх серій. [Case] описує, що застосунок має показати. */
object SimFleet {

    class Case(val title: String, val station: SimStation, val model: String?, val chem: String, val wh: Int?, val packs: Int = 1)

    private const val LFP = "LFP"
    private const val NCM = "NCM"

    private fun json(title: String, sn: String, data: Map<String, Any?>, model: String?, chem: String, wh: Int?, packs: Int = 1) =
        Case(title, SimStation(sn, quota = data), model, chem, wh, packs)

    /** Прошивка завжди шле поля додаткової батареї, навіть коли її немає: нулі. */
    private fun phantom(p: String) = mapOf("$p.designCap" to 0L, "$p.fullCap" to 0L, "$p.cycles" to 0L, "$p.soh" to 0L, "$p.vol" to 0L)

    private fun pb(title: String, sn: String, series: Int, design: Long, model: String?, wh: Int?) =
        Case(title, SimStation(sn, proto = { SimData.gen3(series, 3290, 55, design) }), model, LFP, wh)

    val series: List<Case> = listOf(
        // 1-ше покоління
        json("DELTA Max 2000", "DAEBZ5KE9110449", SimData.gen1(14, 3900, 70, 40_000), "Delta / Delta Max", NCM, 2016),
        json("DELTA Max 2000, низький заряд", "DAABZ5ZE1000001", SimData.gen1(14, 3550, 20, 40_000), "Delta Max", NCM, 2016),
        json("DELTA Max 1600", "DAABZ5ZE1000002", SimData.gen1(14, 3800, 60, 32_000), "Delta Max", NCM, 1613),
        json("DELTA 1300", "DAEBZ5ZE1000003", SimData.gen1(14, 3620, 30, 25_000), "Delta / Delta Max", NCM, 1260),
        json("DELTA mini", "DBEBZ5ZE1000004", SimData.gen1(14, 3900, 70, 17_500), "Delta Mini", NCM, 882),
        json("DELTA Pro", "DCGBZ5SGB240204", SimData.gen1(15, 3327, 99, 80_000), "Delta Pro", LFP, 3840),
        json("DELTA Pro + додаткова батарея", "DCABZ5ZE1000005", SimData.gen1(15, 3300, 60, 80_000) + SimData.pack("bmsSlave1", 15, 3295, 58, 80_000) + phantom("bmsSlave2"), "Delta Pro", LFP, 3840, packs = 2),
        json("DELTA Max + дві додаткові", "DAABZ5ZE1000006", SimData.gen1(14, 3900, 70, 40_000) + SimData.pack("bmsSlave1", 14, 3890, 68, 40_000) + SimData.pack("bmsSlave2", 14, 3880, 66, 40_000), "Delta Max", NCM, 2016, packs = 3),
        json("River Max (R601)", "R601ZEB4VF000001", SimData.gen1(8, 3850, 60, 20_000), "River (1-ше покоління)", NCM, 576),
        json("River Max, 30% (R601)", "R601ZEB4VF000002", SimData.gen1(8, 3620, 30, 20_000), "River (1-ше покоління)", NCM, 576),
        json("River Pro (R602)", "R602ZEB4VF000003", SimData.gen1(8, 3850, 60, 25_000), "River Pro", NCM, 720),
        json("River mini (R501)", "R501ZEB4VF000004", SimData.riverMini(60), "River Mini", NCM, null),
        // 2-ге покоління
        json("DELTA 2", "R331ZEB4ZE100001", SimData.gen2(16, 3290, 50, 20_000), "Delta 2", LFP, 1024),
        json("DELTA 2 Max", "R351ZEB4ZE100002", SimData.gen2(16, 3290, 50, 40_000), "Delta 2 Max", LFP, 2048),
        json(
            "DELTA 2 Max + додаткова, друга порожня", "R354ZEB4ZE100003",
            SimData.gen2(16, 3290, 50, 40_000) + SimData.pack("bms_slave_bmsSlaveStatus_1", 16, 3285, 48, 40_000) + phantom("bms_slave_bmsSlaveStatus_2"),
            "Delta 2 Max", LFP, 2048, packs = 2,
        ),
        json("River 2", "R601ZCB5HX000001", SimData.gen2(8, 3290, 50, 10_000), "River 2", LFP, 256),
        json("River 2 Max", "R611ZEB1AB123456", SimData.gen2(8, 3290, 50, 20_000), "River 2 Max", LFP, 512),
        json("River 2 Pro", "R621ZEB1AB123457", SimData.gen2(8, 3290, 50, 30_000), "River 2 Pro", LFP, 768),
        json("River 2, ключі bmsMaster", "R603ZCB5HX000002", SimData.gen1(8, 3290, 50, 10_000), "River 2", LFP, 256),
        json("DELTA 3 1500 (JSON)", "D361ZEB5HX000001", SimData.gen2(16, 3290, 50, 30_000), "Delta 3 1500", LFP, 1536),
        json("Невідома модель, JSON, LFP", "QX01ZEB5HX000001", SimData.gen2(16, 3290, 50, 20_000), null, LFP, 1024),
        json("Невідома модель, JSON, NCM", "QX02ZEB5HX000002", SimData.gen1(14, 3900, 70, 40_000), null, NCM, 2016),
        // 3-тє покоління
        pb("DELTA 3", "P231ZEB5HX000001", 16, 20_000, "Delta 3", 1024),
        pb("DELTA 3 Classic", "P321ZEB5HX000002", 16, 20_000, "Delta 3 Classic", 1024),
        pb("DELTA 3 Plus", "P351ZEB5HX000003", 16, 20_000, "Delta 3 Plus", 1024),
        pb("DELTA 3 Max", "D3N1ZEB5HX000004", 16, 40_000, "Delta 3 Max", 2048),
        pb("DELTA 3 Max Plus", "D3M1ZEB5HX000005", 16, 40_000, "Delta 3 Max Plus", 2048),
        pb("DELTA 3 Ultra", "D751ZEB5HX000006", 16, 60_000, "Delta 3 Ultra", 3072),
        pb("DELTA 3 Air", "PR11ZEB5HX000007", 8, 20_000, "Delta 3 Air", 512),
        pb("DELTA Pro 3", "MR51ZEB5HX000008", 16, 80_000, "Delta Pro 3", 4096),
        pb("DELTA Pro Ultra", "Y711ZEB5HX000009", 16, 120_000, "Delta Pro Ultra", 6144),
        pb("River 3", "R651ZEB5HX000010", 8, 10_000, "River 3", 256),
        pb("River 3 Plus", "R631ZEB5HX000011", 8, 11_000, "River 3 Plus", 282),
        pb("Невідома модель, protobuf", "QX03ZEB5HX000012", 16, 20_000, null, 1024),
        Case("DELTA 3: прошивка лише на JSON", SimStation("P231ZEB5HX000013", quota = SimData.gen2(16, 3290, 50, 20_000)), "Delta 3", LFP, 1024),
        Case(
            "Protobuf-станція, JSON каже «офлайн»",
            SimStation("QX04ZEB5HX000014", proto = { SimData.gen3(16, 3290, 55, 20_000) }, jsonSaysOffline = true),
            null, LFP, 1024,
        ),
    )

    /** Станції для ручної перевірки на емуляторі: усі серії плюс особливі випадки. */
    fun all(): List<SimStation> = series.map { it.station } + listOf(
        SimStation("R331ZEB4ZE100009", quota = SimData.gen2(16, 3290, 50, 20_000), online = false),
        SimStation("R331ZEB4ZE100010", quota = SimData.gen2(16, 3290, 50, 20_000), online = false, cachedWhenOffline = true),
        SimStation("R351ZEB4ZE100011", quota = SimData.gen2(16, 3290, 50, 40_000), replyDelayMs = 15_000, partialOnSubscribe = SimStation.typeCodePush("pdStatus", mapOf("soc" to 50L, "wattsOutSum" to 120L))),
        SimStation("R331ZEB4ZE100012", quota = SimData.gen2(16, 3290, 50, 20_000), denied = true),
    )
}
