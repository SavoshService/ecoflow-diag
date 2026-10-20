package ua.savosh.ecoflowdiag.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Останній відомий стан одного коду/захисту. */
class CodeState(val value: String, val level: Level)

/**
 * Подія в історії кодів станції.
 * [from] == null — код уперше побачено вже в ненормальному стані.
 * [betweenChecks] — зміну помічено на початку нової перевірки, тобто вона сталася, поки застосунок не був підключений
 * (з моменту [prevCheckMs]); інакше — побачено наживо.
 */
class CodeEvent(
    val timeMs: Long,
    val scope: String,
    val title: String,
    val from: String?,
    val to: String,
    val level: Level,
    val betweenChecks: Boolean,
    val prevCheckMs: Long?,
    val context: String?,
)

/**
 * Відстежує зміни кодів BMS і захистів станції між оновленнями й між перевірками.
 *
 * Записується лише перехід у ненормальний стан або вихід із нього (рівень «Увага» і гірше з одного боку).
 * Зміни між двома нормальними станами (наприклад, EMS забороняє заряд на ліміті 80%) не засмічують історію.
 */
class CodeTracker(lastKnown: Map<String, CodeState>, private val prevCheckMs: Long?) {

    private val known = LinkedHashMap(lastKnown)
    private val seen = HashSet<String>()

    /** true — стан змінився і його варто зберегти. */
    var dirty = false
        private set

    fun knownStates(): Map<String, CodeState> = known

    fun markSaved() {
        dirty = false
    }

    fun update(r: StationReport, nowMs: Long): List<CodeEvent> {
        val out = ArrayList<CodeEvent>()
        val ctx by lazy { CodeHistory.context(r) }
        for (t in CodeHistory.tracked(r)) {
            val firstThisSession = seen.add(t.key)
            val prev = known[t.key]
            if (prev == null) {
                known[t.key] = t.state
                dirty = true
                if (CodeHistory.abnormal(t.state.level)) {
                    out.add(CodeEvent(nowMs, t.scope, t.title, null, t.state.value, t.state.level, false, null, ctx))
                }
                continue
            }
            if (prev.value == t.state.value) {
                if (prev.level != t.state.level) {
                    known[t.key] = t.state
                    dirty = true
                }
                continue
            }
            known[t.key] = t.state
            dirty = true
            if (!CodeHistory.abnormal(prev.level) && !CodeHistory.abnormal(t.state.level)) continue
            out.add(
                CodeEvent(
                    nowMs, t.scope, t.title, prev.value, t.state.value, t.state.level,
                    betweenChecks = firstThisSession && prevCheckMs != null,
                    prevCheckMs = if (firstThisSession) prevCheckMs else null,
                    context = ctx,
                ),
            )
        }
        return out
    }
}

object CodeHistory {

    const val MAX_EVENTS = 300

    class Tracked(val key: String, val scope: String, val title: String, val state: CodeState)

    fun abnormal(l: Level): Boolean = l.rank >= Level.WARN.rank

    /** Що відстежується: коди кожної батареї та захисти/зв'язок станції. */
    fun tracked(r: StationReport): List<Tracked> {
        val out = ArrayList<Tracked>()
        for (p in r.packs) {
            for (c in p.codes) out.add(Tracked("${p.key}|${c.title}", p.title, c.title, CodeState(c.value, c.level)))
        }
        for (c in r.protections) out.add(Tracked("station|${c.title}", "Станція", c.title, CodeState(c.value, c.level)))
        return out
    }

    /** Короткий стан станції в момент події — щоб пов'язати код з режимом, зарядом, температурою. */
    fun context(r: StationReport): String? {
        val parts = ArrayList<String>()
        val main = r.packs.firstOrNull { it.isMain } ?: r.packs.firstOrNull()
        main?.soc?.let { parts.add("SOC ${Analyzer.fmt0(it)}%") }
        r.mode.firstOrNull { it.title == "Режим роботи" }?.let { parts.add(it.value) }
        main?.ampMa?.let { parts.add("струм ${Analyzer.fmt1(it / 1000.0)} А") }
        main?.maxCellTemp?.let { parts.add("$it °C") }
        return parts.joinToString(", ").ifEmpty { null }
    }

    // ------------------------------------------------------------ текст

    fun timeText(ms: Long): String = SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(ms))

    fun changeText(e: CodeEvent): String = if (e.from == null) "виявлено: ${e.to}" else "${e.from} → ${e.to}"

    fun whenText(e: CodeEvent): String =
        if (e.betweenChecks) {
            "між перевірками" + (e.prevCheckMs?.let { " (${timeText(it)} → ${timeText(e.timeMs)})" } ?: "")
        } else {
            timeText(e.timeMs)
        }

    fun line(e: CodeEvent): String =
        "${whenText(e)} · ${e.scope} · ${e.title}: ${changeText(e)}" + (e.context?.let { " [$it]" } ?: "")

    // ------------------------------------------------------------ збереження (JSON)

    fun eventsToJson(list: List<CodeEvent>): String = Json.stringify(
        list.map {
            linkedMapOf<String, Any?>(
                "t" to it.timeMs, "s" to it.scope, "n" to it.title, "f" to it.from, "v" to it.to,
                "l" to it.level.name, "b" to it.betweenChecks, "p" to it.prevCheckMs, "c" to it.context,
            )
        },
    )

    fun eventsFromJson(text: String?): List<CodeEvent> {
        val arr = text?.let { Json.parseOrNull(it) } as? List<*> ?: return emptyList()
        return arr.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            CodeEvent(
                timeMs = (m["t"] as? Number)?.toLong() ?: return@mapNotNull null,
                scope = m["s"]?.toString() ?: "",
                title = m["n"]?.toString() ?: return@mapNotNull null,
                from = m["f"]?.toString(),
                to = m["v"]?.toString() ?: "",
                level = levelOf(m["l"]),
                betweenChecks = m["b"] == true,
                prevCheckMs = (m["p"] as? Number)?.toLong(),
                context = m["c"]?.toString(),
            )
        }
    }

    fun statesToJson(states: Map<String, CodeState>, checkMs: Long): String = Json.stringify(
        linkedMapOf<String, Any?>(
            "time" to checkMs,
            "states" to LinkedHashMap<String, Any?>().apply {
                for ((k, s) in states) put(k, linkedMapOf<String, Any?>("v" to s.value, "l" to s.level.name))
            },
        ),
    )

    /** Повертає збережені стани і час останньої перевірки. */
    fun statesFromJson(text: String?): Pair<Map<String, CodeState>, Long?> {
        val root = text?.let { Json.parseOrNull(it) } as? Map<*, *> ?: return emptyMap<String, CodeState>() to null
        val time = (root["time"] as? Number)?.toLong()
        val states = LinkedHashMap<String, CodeState>()
        (root["states"] as? Map<*, *>)?.forEach { (k, v) ->
            val m = v as? Map<*, *> ?: return@forEach
            val key = k?.toString() ?: return@forEach
            states[key] = CodeState(m["v"]?.toString() ?: "", levelOf(m["l"]))
        }
        return states to time
    }

    private fun levelOf(v: Any?): Level = Level.entries.firstOrNull { it.name == v?.toString() } ?: Level.UNKNOWN
}
