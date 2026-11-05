package ua.savosh.ecoflowdiag.core

/** Покоління протоколу EcoFlow. */
enum class Generation(private val titleKey: String) {
    GEN1(tk("1-ше покоління (JSON: Delta Max / Pro / Mini, River)")),
    GEN2(tk("2-ге покоління (JSON: Delta 2 / 2 Max, River 2)")),
    GEN3(tk("3-тє покоління (protobuf: Delta 3, River 3, Delta Pro 3)")),
    UNKNOWN(tk("ще не визначено"));

    val title: String get() = tr(titleKey)
}

/** Результат розбору одного MQTT-повідомлення. */
class DecodedMessage(
    val params: Map<String, Any?>,
    /** true/false, якщо повідомлення явно каже про стан онлайн; null — якщо ні. */
    val online: Boolean?,
    val kind: String,
    val note: String? = null,
)

object MessageDecoder {

    /** Відповідність typeCode -> префікс ключів (як у Developer/Public API). */
    private val TYPE_CODE_PREFIX = mapOf(
        "pdStatus" to "pd",
        "mpptStatus" to "mppt",
        "emsStatus" to "bms_emsStatus",
        "bmsStatus" to "bms_bmsStatus",
        "bmsInfo" to "bms_bmsInfo",
        "invStatus" to "inv",
        "bmsSlaveStatus" to "bms_slave",
        "bmsSlaveStatus_1" to "bms_slave_bmsSlaveStatus_1",
        "bmsSlaveStatus_2" to "bms_slave_bmsSlaveStatus_2",
    )

    fun decode(topic: String, payload: ByteArray): DecodedMessage? {
        if (payload.isEmpty()) return null
        if (!Gen3Decoder.looksLikeJson(payload)) return decodeProto(payload)
        // Двійкове повідомлення може випадково починатись і закінчуватись дужками: тоді це не JSON.
        return decodeJson(topic, payload).takeIf { it?.kind != "json?" } ?: decodeProto(payload)
    }

    private fun decodeProto(payload: ByteArray): DecodedMessage? {
        val r = Gen3Decoder.decodeBinary(payload) ?: return DecodedMessage(
            emptyMap(), null, "binary", tr("Нерозпізнане двійкове повідомлення ({0} байт)", payload.size),
        )
        return DecodedMessage(
            r.params,
            if (r.params.isNotEmpty()) true else null,
            "protobuf",
            tr("protobuf: {0} блок(и) [{1}], BMS-пакетів: {2}", r.headers, r.description, r.bmsPacks),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeJson(topic: String, payload: ByteArray): DecodedMessage? {
        val text = payload.toString(Charsets.UTF_8)
        val obj = Json.parseObject(text) ?: return DecodedMessage(emptyMap(), null, "json?", tr("Некоректний JSON"))

        // Відповідь на запит latestQuotas: {"operateType":"latestQuotas","data":{"online":1,"quotaMap":{...}}}
        val data = obj["data"] as? Map<String, Any?>
        if (data != null && (data.containsKey("quotaMap") || obj["operateType"] == "latestQuotas")) {
            val online = toBool(data["online"])
            val quota = data["quotaMap"] as? Map<String, Any?> ?: emptyMap()
            val note = when {
                online == false -> tr("Хмара EcoFlow: станція з цим SN для цього акаунта ОФЛАЙН (online=0)")
                quota.isEmpty() -> tr("Повний знімок даних порожній (online={0})", data["online"])
                else -> tr("Повний знімок даних: {0} параметрів", quota.size)
            }
            return DecodedMessage(flatten(quota), online, "latestQuotas", note)
        }

        val message = obj["message"] as? String
        if (message != null && message.contains("not online", ignoreCase = true)) {
            return DecodedMessage(emptyMap(), false, "status", tr("Хмара EcoFlow: станція офлайн («{0}»)", message))
        }

        // Повідомлення про статус онлайн: {"params":{"status":1}}
        val params = (obj["params"] as? Map<String, Any?>) ?: (obj["param"] as? Map<String, Any?>)
        if (params != null) {
            if (params.size == 1 && params.containsKey("status") && topic.contains("status")) {
                return DecodedMessage(emptyMap(), toBool(params["status"]), "status")
            }
            val typeCode = obj["typeCode"] as? String
            val flat = flatten(params)
            val prefixed = if (typeCode != null) {
                val prefix = TYPE_CODE_PREFIX[typeCode] ?: "unknown_$typeCode"
                flat.mapKeys { "$prefix.${it.key}" }
            } else {
                flat
            }
            return DecodedMessage(prefixed, null, "params", null)
        }

        val code = obj["code"]?.toString()
        return DecodedMessage(
            emptyMap(), null, "other",
            if (message != null) tr("Відповідь: {0}{1}", message, if (code != null) tr(" (код {0})", code) else "") else null,
        )
    }

    /** Розгортає вкладені об'єкти в ключі через крапку; масиви лишаються списками. */
    fun flatten(src: Map<String, Any?>, prefix: String = "", out: MutableMap<String, Any?> = LinkedHashMap()): Map<String, Any?> {
        for ((k, v) in src) {
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                flatten(v as Map<String, Any?>, key, out)
            } else {
                out[key] = v
            }
        }
        return out
    }

    fun toBool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", true)
        else -> null
    }

    /** Запит повного знімка для JSON-станцій (1-ше і 2-ге покоління). */
    fun buildJsonQuotaRequest(seq: Int, from: String = "Android"): ByteArray {
        val m = linkedMapOf<String, Any?>(
            "from" to from,
            "id" to seq.toString(),
            "version" to "1.1",
            "moduleType" to 0,
            "operateType" to "latestQuotas",
            "params" to emptyMap<String, Any?>(),
        )
        return Json.stringify(m).toByteArray(Charsets.UTF_8)
    }
}

/** Накопичувач усіх отриманих параметрів станції. Потокобезпечний. */
class QuotaStore {
    private val map = LinkedHashMap<String, Any?>()
    private val lock = Any()

    @Volatile var online: Boolean? = null
        private set
    @Volatile var messages: Int = 0
        private set
    @Volatile var lastUpdateMs: Long = 0
        private set

    fun apply(msg: DecodedMessage, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (msg.params.isNotEmpty()) {
                map.putAll(msg.params)
                lastUpdateMs = nowMs
                if (online == null) online = true
            }
            msg.online?.let { online = it }
            // Живе повідомлення станції доводить, що вона онлайн, навіть якщо хмара щойно казала інакше.
            if (msg.kind == "params" && msg.params.isNotEmpty()) online = true
            messages++
        }
    }

    fun snapshot(): Map<String, Any?> = synchronized(lock) { java.util.TreeMap(map) }

    fun clear() = synchronized(lock) {
        map.clear()
        online = null
        messages = 0
        lastUpdateMs = 0
    }

    fun size(): Int = synchronized(lock) { map.size }

    /** Чи є вже дані батареї. Інші параметри (наприклад, потужність виходів) можуть прийти першими. */
    fun hasBattery(): Boolean = synchronized(lock) { Analyzer.hasBatteryData(map.keys) }

    /** Забути відповідь хмари «офлайн» — наприклад, перед запитом через інший сервер. */
    fun forgetOnline() {
        synchronized(lock) { online = null }
    }

    companion object {
        fun detectGeneration(keys: Collection<String>): Generation {
            var g1 = false
            var g2 = false
            var g3 = false
            for (k in keys) {
                when {
                    k.startsWith("bmsMaster.") || k.startsWith("bmsSlave") || k.startsWith("ems.") -> g1 = true
                    k.startsWith("bms_") -> g2 = true
                    k.startsWith("bmsPack") || k.startsWith("display.") || k.startsWith("runtime.") -> g3 = true
                }
            }
            return when {
                g3 -> Generation.GEN3
                g2 -> Generation.GEN2
                g1 -> Generation.GEN1
                keys.any { it.startsWith("inv.") || it.startsWith("pd.") } -> Generation.GEN1
                else -> Generation.UNKNOWN
            }
        }
    }
}
