package ua.savosh.ecoflowdiag.core

import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Запис журналу перевірок станції. [kind]: [StationLog.DIAG] — діагностика, [StationLog.BALANCE] — перевірка
 * балансу, [StationLog.CAPACITY] — тест ємності. [deltaMv] для діагностики — поточний розкид груп,
 * для перевірки балансу — розкид наприкінці заряду.
 */
class LogEntry(
    val timeMs: Long,
    val kind: String,
    val soh: Double? = null,
    val cycles: Long? = null,
    val deltaMv: Long? = null,
    val soc: Double? = null,
    val text: String? = null,
)

/**
 * Журнал перевірок станції між візитами: один знімок діагностики на добу + кожна перевірка балансу й тест ємності.
 * Один знімок нічого не каже про швидкість зносу — тренд за журналом каже.
 */
object StationLog {

    const val DIAG = "diag"
    const val BALANCE = "balance"
    const val CAPACITY = "capacity"
    const val MAX_ENTRIES = 60

    /** Новіші першими. Знімок діагностики — не частіше одного на добу: запис того самого дня замінюється свіжішим. */
    fun add(list: List<LogEntry>, e: LogEntry): List<LogEntry> {
        val rest = if (e.kind == DIAG) list.filterNot { it.kind == DIAG && sameDay(it.timeMs, e.timeMs) } else list
        return (rest + e).sortedByDescending { it.timeMs }.take(MAX_ENTRIES)
    }

    /**
     * Тренд здоров'я за знімками діагностики. Висновок про швидкість зносу — лише за ≥ 100 циклів між знімками,
     * інакше крок SOH у BMS (1%) більший за саму зміну. null — даних для тренду ще замало.
     */
    fun trend(list: List<LogEntry>, chem: Chemistry): String? {
        val pts = list.filter { it.kind == DIAG && it.soh != null }.sortedBy { it.timeMs }
        if (pts.size < 2) return null
        val a = pts.first()
        val b = pts.last()
        val dSoh = b.soh!! - a.soh!!
        val days = (b.timeMs - a.timeMs) / 86_400_000L
        val ca = a.cycles
        val cb = b.cycles
        val dCyc = if (ca != null && cb != null) cb - ca else null
        val since = "з ${dateShort(a.timeMs)}"
        val change = when {
            abs(dSoh) < 1 -> "здоров'я не змінилось"
            dSoh < 0 -> "здоров'я знизилось на ${fmt0(-dSoh)}%"
            else -> "здоров'я зросло на ${fmt0(dSoh)}% — BMS перекалібрувалась, батарея від цього не покращала"
        }
        if (dCyc != null && dCyc >= 100 && dSoh <= -1) {
            val per100 = -dSoh * 100.0 / dCyc
            // Типовий знос — ≈20% за паспортний ресурс: LFP ~3000 циклів, NCM ~800.
            val typical = when (chem) {
                Chemistry.LFP -> 0.7
                Chemistry.NCM -> 2.5
                Chemistry.UNKNOWN -> null
            }
            val judge = typical?.let {
                if (per100 > it * 2) " — швидше за типове для ${chem.short} (≈${fmt1(it)}%)" else " — у межах типового для ${chem.short}"
            } ?: ""
            return "За $dCyc ${cyclesWord(dCyc)} ($since) $change — ≈${fmt1(per100)}% на 100 циклів$judge."
        }
        if (days < 30 && (dCyc ?: 0L) < 30) return null
        val cyc = dCyc?.let { ", $it ${cyclesWord(it)}" } ?: ""
        return "За $days ${daysWord(days)}$cyc ($since) $change."
    }

    fun line(e: LogEntry): String {
        val d = dateShort(e.timeMs)
        return when (e.kind) {
            DIAG -> "$d · діагностика: " + listOfNotNull(
                e.soh?.let { "SOH ${fmt0(it)}%" },
                e.cycles?.let { "$it ${cyclesWord(it)}" },
                e.deltaMv?.let { "розкид $it мВ" + (e.soc?.let { s -> " при ${fmt0(s)}%" } ?: "") },
            ).joinToString(", ").ifEmpty { "—" }
            BALANCE -> "$d · перевірка балансу: ${e.text ?: "—"}"
            CAPACITY -> "$d · тест ємності: ${e.text ?: "—"}"
            else -> "$d · ${e.text ?: e.kind}"
        }
    }

    fun toJson(list: List<LogEntry>): List<Map<String, Any?>> = list.map {
        linkedMapOf<String, Any?>(
            "t" to it.timeMs, "kind" to it.kind, "soh" to it.soh, "cycles" to it.cycles,
            "delta" to it.deltaMv, "soc" to it.soc, "text" to it.text,
        )
    }

    fun fromJson(v: Any?): List<LogEntry> = (v as? List<*>)?.mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        val t = (m["t"] as? Number)?.toLong() ?: return@mapNotNull null
        LogEntry(
            timeMs = t,
            kind = m["kind"] as? String ?: DIAG,
            soh = (m["soh"] as? Number)?.toDouble(),
            cycles = (m["cycles"] as? Number)?.toLong(),
            deltaMv = (m["delta"] as? Number)?.toLong(),
            soc = (m["soc"] as? Number)?.toDouble(),
            text = m["text"] as? String,
        )
    }?.sortedByDescending { it.timeMs } ?: emptyList()

    fun dateShort(ms: Long): String = SimpleDateFormat("dd.MM.yy", Locale.US).format(Date(ms))

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun plural(n: Long, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11L..14L -> many
            m10 == 1L -> one
            m10 in 2L..4L -> few
            else -> many
        }
    }

    private fun cyclesWord(n: Long) = plural(n, "цикл", "цикли", "циклів")
    private fun daysWord(n: Long) = plural(n, "день", "дні", "днів")
}

/**
 * Останні отримані напруги груп однієї батареї. Частина станцій (Delta 2, River 2 тощо) шле повний список
 * окремими повідомленнями, тож у свіжому знімку його може ще не бути — тоді показуємо останній відомий із часом.
 */
data class CellSnapshot(val timeMs: Long, val cells: List<Long>) {
    fun timeText(): String = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(timeMs))

    companion object {
        /** { ключ батареї: {t, v:[мВ…]} } */
        fun toJson(m: Map<String, CellSnapshot>): Map<String, Any?> =
            m.mapValues { (_, s) -> linkedMapOf<String, Any?>("t" to s.timeMs, "v" to s.cells) }

        fun fromJson(src: Map<String, Any?>): Map<String, CellSnapshot> = src.mapNotNull { (k, v) ->
            val o = v as? Map<*, *> ?: return@mapNotNull null
            val t = (o["t"] as? Number)?.toLong() ?: return@mapNotNull null
            val cells = (o["v"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
            if (cells.size >= 2) k to CellSnapshot(t, cells) else null
        }.toMap()
    }
}
