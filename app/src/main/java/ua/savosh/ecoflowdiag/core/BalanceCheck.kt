package ua.savosh.ecoflowdiag.core

import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt3
import kotlin.math.abs

/**
 * Перевірка балансу груп наприкінці заряду.
 *
 * На плато LFP розбалансовані групи мають майже однакову напругу, тож чесно побачити баланс можна лише
 * біля верху заряду. Під час заряду фіксуємо найбільший розкид, коли батарея в «зоні висновку»
 * (LFP — найвища група вище 3.45 В; NCM — від 90% заряду), і лише за ним робимо висновок.
 */
class BalanceCheck(val sn: String) {

    enum class Phase { IDLE, RUNNING, DONE }

    /** Найбільший розкид батареї, зафіксований у зоні висновку. */
    class Top(
        val key: String,
        val title: String,
        val deltaMv: Long,
        val soc: Double?,
        val maxCellMv: Long?,
        /** Група з найвищою напругою (з 1) — першою доходить до верху; null — немає повного списку груп. */
        val highCell: Int?,
    )

    class Verdict(val level: Level, val title: String, val detail: String)

    var phase = Phase.IDLE
        private set
    var startMs = 0L
        private set
    var endMs = 0L
        private set

    // поточні значення основної батареї — для екрана
    var soc: Double? = null
        private set
    var ampA: Double? = null
        private set
    var maxCellMv: Long? = null
        private set
    var deltaMv: Long? = null
        private set
    var chemistry = Chemistry.UNKNOWN
        private set
    var chargeLimit: Double? = null
        private set
    var lastDataMs = 0L
        private set

    /** Найвища напруга групи за всю перевірку — щоб пояснити, чому зона висновку не настала. */
    var peakCellMv: Long? = null
        private set

    /** Заряд дійшов до ліміту, а струм ~0 уже 2 хв — перевірку можна завершувати. */
    var autoDone = false
        private set

    private val tops = LinkedHashMap<String, Top>()
    private var quietSinceMs = 0L

    val results: List<Top> get() = tops.values.toList()

    /** Батарея з найбільшим розкидом — для короткого підсумку. */
    fun worst(): Top? = tops.values.maxByOrNull { it.deltaMv }

    fun start(nowMs: Long) {
        tops.clear()
        peakCellMv = null
        autoDone = false
        quietSinceMs = 0L
        startMs = nowMs
        endMs = 0L
        phase = Phase.RUNNING
    }

    fun finish(nowMs: Long) {
        if (phase != Phase.RUNNING) return
        endMs = nowMs
        phase = Phase.DONE
        autoDone = false
    }

    /** Новий знімок даних станції. Викликати при кожному оновленні. */
    fun feed(nowMs: Long, r: StationReport, params: Map<String, Any?>) {
        val main = r.packs.firstOrNull { it.isMain } ?: r.packs.firstOrNull() ?: return
        soc = main.soc ?: soc
        ampA = r.packs.mapNotNull { it.ampMa }.takeIf { it.isNotEmpty() }?.sum()?.let { it / 1000.0 }
        maxCellMv = main.maxCellMv
        deltaMv = main.cellDeltaMv
        chemistry = main.chemistry
        chargeLimit = Analyzer.numOf(params["ems.maxChargeSoc"]) ?: Analyzer.numOf(params["bms_emsStatus.maxChargeSoc"]) ?: chargeLimit
        lastDataMs = nowMs
        if (phase != Phase.RUNNING) return

        for (p in r.packs) {
            val m = p.maxCellMv
            if (m != null && m > (peakCellMv ?: 0L)) peakCellMv = m
            val delta = p.cellDeltaMv ?: continue
            if (!inZone(p.chemistry, p.maxCellMv, p.soc)) continue
            val cur = tops[p.key]
            if (cur == null || delta > cur.deltaMv) {
                val full = p.cellVols.size >= 2 && p.cellVols.size == p.seriesCells
                val high = if (full) p.cellVols.indexOf(p.cellVols.max()) + 1 else null
                tops[p.key] = Top(p.key, p.title, delta, p.soc, p.maxCellMv, high)
            }
        }

        val s = soc
        val atEnd = s != null && s >= (chargeLimit ?: 100.0) - 0.5
        if (atEnd && abs(ampA ?: 0.0) < QUIET_A) {
            if (quietSinceMs == 0L) quietSinceMs = nowMs
            if (nowMs - quietSinceMs >= QUIET_MS) autoDone = true
        } else {
            quietSinceMs = 0L
        }
    }

    /** Чи батарея зараз у зоні, де розкид чесно показує баланс. */
    fun inZoneNow(): Boolean = inZone(chemistry, maxCellMv, soc)

    /** Застереження до перевірки. */
    fun warnings(): List<String> {
        val out = ArrayList<String>()
        chargeLimit?.let {
            if (it < 99.5) out.add("Ліміт заряду ${fmt0(it)}% — станція не зарядиться до верху. Виставте 100% у застосунку EcoFlow.")
        }
        val s = soc
        if (phase == Phase.RUNNING && !autoDone && (ampA ?: 0.0) < 0.3 && s != null && s < (chargeLimit ?: 100.0) - 1) {
            out.add("Станція зараз не заряджається — поставте її на заряд від мережі.")
        }
        return out
    }

    /** Чому висновку немає (зона не настала). */
    fun noZoneText(): String {
        val peak = peakCellMv?.let { " Найвища група дійшла лише до ${fmt3(it / 1000.0)} В." } ?: ""
        return "Заряд не дійшов до зони, де видно баланс (LFP — групи вище 3.45 В, NCM — від 90%), тому висновку немає.$peak " +
            "Поставте ліміт заряду 100% і заряджайте до кінця."
    }

    /** Один рядок для журналу перевірок. */
    fun shortResult(): String {
        val w = worst() ?: return "зону висновку не досягнуто"
        return "${w.deltaMv} мВ наприкінці заряду — ${verdict(w).title.lowercase()}"
    }

    fun summaryText(model: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.appendLine("Перевірка балансу груп наприкінці заряду")
        sb.appendLine("Дата: $dateText")
        sb.appendLine("Станція: ${model ?: "?"} · SN $sn")
        sb.appendLine()
        val res = results
        if (res.isEmpty()) {
            sb.appendLine(noZoneText())
        } else {
            for (t in res) {
                val v = verdict(t)
                if (res.size > 1) sb.appendLine("■ ${t.title}")
                sb.appendLine("${v.title}. ${v.detail}")
                sb.appendLine(
                    "Зафіксовано при заряді ${t.soc?.let { fmt0(it) + "%" } ?: "?"}" +
                        (t.maxCellMv?.let { ", найвища група ${fmt3(it / 1000.0)} В" } ?: "") + ".",
                )
            }
        }
        sb.appendLine()
        sb.appendLine("Розкид враховано лише біля верху заряду, де розбаланс справді видно (на плато LFP його не видно).")
        return sb.toString()
    }

    companion object {
        /** LFP виходить з плато: вище цієї напруги групи розбалансовані групи розходяться помітно. */
        const val LFP_ZONE_MV = 3450L
        const val QUIET_A = 0.5
        const val QUIET_MS = 120_000L

        fun inZone(chem: Chemistry, maxCellMv: Long?, soc: Double?): Boolean = when (chem) {
            Chemistry.LFP -> (maxCellMv ?: 0L) >= LFP_ZONE_MV
            Chemistry.NCM -> (soc ?: 0.0) >= 90.0
            Chemistry.UNKNOWN -> (soc ?: 0.0) >= 95.0
        }

        /** Висновок за розкидом біля верху заряду — ті самі пороги, що й в аналізаторі поза плато. */
        fun verdict(t: Top): Verdict {
            val d = t.deltaMv
            val who = t.highCell?.let {
                " Першою до верху доходить група №$it — у неї або більший заряд за інші (розбаланс), або менша ємність."
            } ?: ""
            return when {
                d <= 30 -> Verdict(Level.GOOD, "Групи збалансовані", "Розкид наприкінці заряду $d мВ — норма.")
                d <= 70 -> Verdict(
                    Level.WARN,
                    "Помірний дисбаланс",
                    "Розкид наприкінці заряду $d мВ.$who BMS вирівнює групи саме наприкінці заряду: кілька повних зарядів " +
                        "до 100% з паузою на 100% зазвичай зменшують розкид. Повторіть перевірку після них.",
                )
                d <= 150 -> Verdict(
                    Level.BAD,
                    "Сильний дисбаланс",
                    "Розкид наприкінці заряду $d мВ.$who Якщо після кількох повних зарядів не зменшиться — потрібне " +
                        "сервісне балансування; можлива слабка група.",
                )
                else -> Verdict(
                    Level.CRITICAL,
                    "Дуже сильний дисбаланс",
                    "Розкид наприкінці заряду $d мВ.$who Ймовірна слабка або пошкоджена група — батарею треба перевірити.",
                )
            }
        }
    }
}
