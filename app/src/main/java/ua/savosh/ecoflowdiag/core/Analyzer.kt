package ua.savosh.ecoflowdiag.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Level(val rank: Int, private val titleKey: String) {
    UNKNOWN(-1, tk("Немає даних")),
    GOOD(0, tk("Добре")),
    OK(1, tk("Норма")),
    WARN(2, tk("Увага")),
    BAD(3, tk("Погано")),
    CRITICAL(4, tk("Критично"));

    fun worse(other: Level): Level = if (other.rank > rank) other else this

    /** Підпис загального стану станції: «Добре» тут означає лише відсутність зауважень, а не оцінку батареї. */
    val statusTitle: String
        get() = when (this) {
            UNKNOWN -> tr("Немає даних")
            GOOD, OK -> tr("Без зауважень")
            else -> title
        };

    val title: String get() = tr(titleKey)

}

enum class Chemistry(private val titleKey: String, val short: String, val nominalCellV: Double, val ratedCycles: Int) {
    LFP(tk("LiFePO4 (залізофосфат)"), "LFP", 3.2, 3000),
    NCM("Li-ion NCM", "NCM", 3.6, 800),
    UNKNOWN(tk("не визначено"), "?", 0.0, 0);

    val title: String get() = tr(titleKey)
}

/**
 * Єдина шкала оцінки батареї — ЛИШЕ за здоров'ям (SOH). Та сама в застосунку, на картці станції,
 * у звітах і в тесті ємності. Коди помилок, дисбаланс чи примітки на неї не впливають.
 * Межі рахуються від округленого SOH — щоб «95%» на екрані ніколи не отримало нижчу оцінку.
 */
object Health {
    /** [chip] — для значка, [verdict] — висновок у застосунку, [report] — оцінка у звіті клієнту. */
    class Grade(val level: Level, val chip: String, val verdict: String, val report: String)

    private val EXCELLENT get() = Grade(Level.GOOD, tr("Відмінно"), tr("Відмінний стан батареї"), tr("Відмінний"))
    private val GOOD get() = Grade(Level.GOOD, tr("Добре"), tr("Добрий стан батареї"), tr("Добрий"))
    private val WORN get() = Grade(Level.WARN, tr("Увага"), tr("Помітний знос батареї"), tr("Потребує уваги"))
    private val POOR get() = Grade(Level.BAD, tr("Погано"), tr("Сильний знос батареї"), tr("Незадовільний"))

    fun of(soh: Double?): Grade? {
        val s = soh?.roundToInt() ?: return null
        return when {
            s >= 95 -> EXCELLENT
            s >= 80 -> GOOD
            s >= 65 -> WORN
            else -> POOR
        }
    }
}

/**
 * Висновок. [short] — коротке формулювання для заголовка батареї, якщо саме воно визначає статус.
 * Рівень [Level.UNKNOWN] — довідкова примітка: показується сірим і на жодну оцінку не впливає.
 */
class Finding(val level: Level, val text: String, val short: String? = null, val codeInfo: Boolean = false)

/**
 * Рядок «показник — значення — пояснення».
 * [Level.UNKNOWN] означає інформаційний рядок без оцінки.
 * [codeKey] — ключ коду помилки, до якого можна додати власну примітку (наприклад, "bms:23").
 */
class Indicator(
    val title: String,
    val value: String,
    val level: Level,
    val note: String? = null,
    val codeKey: String? = null,
)

class PackReport(
    val key: String,
    val title: String,
    val isMain: Boolean,
    val sn: String?,
    val soc: Double?,
    val sohBms: Double?,
    val sohCapacity: Double?,
    val sohEffective: Double?,
    val cycSoh: Double?,
    val realSoh: Double?,
    val designCapMah: Long?,
    val fullCapMah: Long?,
    val remainCapMah: Long?,
    val cycles: Long?,
    val volMv: Long?,
    val ampMa: Long?,
    val tempC: Long?,
    val cellVols: List<Long>,
    val minCellMv: Long?,
    val maxCellMv: Long?,
    val cellDeltaMv: Long?,
    /** Номер найслабшої групи (з 1), якщо є повний список напруг. */
    val weakestCell: Int?,
    val cellTemps: List<Long>,
    val minCellTemp: Long?,
    val maxCellTemp: Long?,
    val minMosTemp: Long?,
    val maxMosTemp: Long?,
    val accuChgEnergyWh: Long?,
    val accuDsgEnergyWh: Long?,
    val accuChgCapMah: Long?,
    val accuDsgCapMah: Long?,
    val seriesCells: Int?,
    val chemistry: Chemistry,
    val chemistryConfident: Boolean,
    val packVnom: Double?,
    val designWh: Double?,
    val fullWh: Double?,
    val passport: Models.Passport?,
    val ratedCycles: Int?,
    val ratedCyclesSource: String?,
    val equivalentCycles: Double?,
    val resourceUsedPct: Double?,
    val mos: Indicator?,
    val codes: List<Indicator>,
    val notProvided: List<String>,
    val waterIn: Boolean,
    val firmware: String?,
    val hwVersion: String?,
    /** Хімія за напругами суперечить моделі, яку дає серійний номер. */
    val modelMismatch: Boolean,
    /** Загальний статус батареї: найгірше зі здоров'я та зауважень (для підсумку станції й техзвіту). */
    val level: Level,
    /** Оцінка батареї лише за SOH (шкала [Health]); null — SOH невідомий. */
    val health: Health.Grade?,
    /** Висновок лише за ємністю (SOH). */
    val capacityVerdict: String,
    /** Загальний висновок, узгоджений зі статусом [level]. */
    val verdict: String,
    val findings: List<Finding>,
    /** Зауваження, не пов'язані зі зносом (дисбаланс, температура, коди…), від «Увага» і вище. */
    val issues: List<Finding>,
    /** Оцінка розкиду груп (UNKNOWN — немає даних) і пороги відхилення групи для стовпчиків, мВ. */
    val balanceLevel: Level,
    val cellOkMv: Int,
    val cellWarnMv: Int,
) {
    val healthLevel: Level get() = health?.level ?: Level.UNKNOWN
}

class StationReport(
    val sn: String,
    val model: String?,
    val generation: Generation,
    val packs: List<PackReport>,
    val usage: List<Pair<String, String>>,
    val energy: List<Pair<String, String>>,
    val energyNote: String?,
    val protections: List<Indicator>,
    val mode: List<Indicator>,
    val firmware: List<Pair<String, String>>,
    val other: List<Pair<String, String>>,
    val totalDesignWh: Double?,
    val totalFullWh: Double?,
    val totalPassportWh: Double?,
    val level: Level,
    val summary: String,
    val paramCount: Int,
)

object Analyzer {

    private val PACK_MARKERS = setOf("cycles", "designCap", "fullCap", "soh")

    /** Біти регістра SYS_STAT чипа BQ769x0 (0–5 — аварійні, 6 — резерв, 7 — CC_READY). */
    private val BQ_ALARM_BITS get() = listOf(
        0 to tr("OCD — перевантаження струмом розряду"),
        1 to tr("SCD — коротке замикання"),
        2 to tr("OV — перенапруга групи"),
        3 to tr("UV — недонапруга групи"),
        4 to tr("OVRD_ALERT — зовнішня тривога"),
        5 to tr("DEVICE_XREADY — помилка чипа AFE"),
    )

    val ENERGY_NOTE: String
        get() = tr("Ці лічильники рахують енергію, що пройшла через станцію, а не через батарею: у режимі байпасу (UPS) транзит із мережі в навантаження теж додається. Ресурс батареї оцінюйте лише за лічильником циклів BMS.")

    /** Стан станції, потрібний для пояснень по батареях. */
    private class Ctx(
        val maxChargeSoc: Double?,
        val minDsgSoc: Double?,
        val iconBmsErr: Long?,
        val codeNotes: Map<String, String>,
    )

    fun analyze(sn: String, params: Map<String, Any?>, codeNotes: Map<String, String> = emptyMap()): StationReport {
        val generation = QuotaStore.detectGeneration(params.keys)
        val modelInfo = Models.bySn(sn)
        val ctx = Ctx(
            maxChargeSoc = chargeLimit(params),
            minDsgSoc = dischargeLimit(params),
            iconBmsErr = numOf(params["pd.iconBmsErrState"])?.toLong(),
            codeNotes = codeNotes,
        )

        val prefixes = packPrefixes(params.keys)

        val packs = ArrayList<PackReport>()
        val gen3Count = prefixes.count { it.startsWith("bmsPack") }
        for (p in prefixes) {
            packs.add(analyzePack(p, p, params, generation, gen3Count, modelInfo?.name, ctx))
        }
        // 3-тє покоління: якщо BMS heartbeat ще не прийшов, але є зведені дані станції
        if (packs.isEmpty() && (params.containsKey("display.bmsDesignCap") || params.containsKey("runtime.bmsFullCap"))) {
            packs.add(analyzePack(GEN3_SUMMARY, GEN3_SUMMARY, params, generation, 0, modelInfo?.name, ctx))
        }
        packs.sortWith(compareBy<PackReport>({ !it.isMain }, { it.key }))

        // Прибираємо «фантомні» додаткові батареї: прошивка часто завжди виставляє поля слейв-BMS,
        // навіть коли додаткової батареї фізично немає. Таку залишаємо лише якщо в неї є реальні дані.
        fun hasRealData(p: PackReport) = (p.designCapMah ?: 0) > 0 || (p.volMv ?: 0) > 0 || (p.fullCapMah ?: 0) > 0
        // Деякі River 2 шлють ті самі дані під двома назвами (bmsMaster і bms_bmsStatus), одна з них порожня.
        val mains = packs.filter { it.isMain }
        val mainKept = if (mains.size > 1) mains.firstOrNull { hasRealData(it) } ?: mains.first() else null
        val realPacks = packs.filter { p ->
            // Реальна батарея завжди має ємність і напругу; у «фантомного» слейва вони 0/порожні.
            if (p.isMain) mainKept == null || p === mainKept else hasRealData(p)
        }
        if (realPacks.size != packs.size) {
            packs.clear()
            packs.addAll(realPacks)
        }

        val protections = buildProtections(params, packs, ctx)
        val mode = buildMode(params, packs, ctx)

        var level = if (packs.isEmpty()) Level.UNKNOWN else Level.GOOD
        for (pk in packs) level = level.worse(pk.level)
        for (ind in protections) level = level.worse(ind.level)

        fun total(sel: (PackReport) -> Double?): Double? =
            packs.mapNotNull(sel).takeIf { it.size == packs.size && it.isNotEmpty() }?.sum()

        val summary = when {
            packs.isEmpty() -> tr("Даних про батареї ще немає")
            else -> {
                val worst = packs.maxByOrNull { it.level.rank }!!
                val soh = worst.sohEffective?.let { " (SOH ${fmt0(it)}%)" } ?: ""
                val prot = protections.filter { it.level.rank >= Level.WARN.rank }
                val protTxt = if (prot.isNotEmpty() && prot.maxOf { it.level.rank } > worst.level.rank) {
                    tr(" · станція: {0} — {1}", prot.first().title.lowercase(), prot.first().value)
                } else {
                    ""
                }
                if (packs.size == 1) {
                    "${worst.title}$soh — ${worst.verdict}$protTxt"
                } else {
                    tr("{0} · найгірша: {1}{2} — {3}{4}", packsWord(packs.size), worst.title, soh, worst.verdict, protTxt)
                }
            }
        }

        // Напруги сильніші за таблицю префіксів: не називаємо станцію моделлю, якою вона бути не може.
        val mismatch = packs.firstOrNull { it.isMain }?.modelMismatch == true
        return StationReport(
            sn = sn,
            model = (if (mismatch) modelInfo?.ncmName else modelInfo?.name)?.let { tr(it) },
            generation = if (generation != Generation.UNKNOWN) generation else modelInfo?.generation ?: Generation.UNKNOWN,
            packs = packs,
            usage = buildUsage(params),
            energy = buildEnergy(params, packs),
            energyNote = if (ENERGY_KEYS.any { (keys, _) -> keys.any { (numOf(params[it]) ?: 0.0) > 0 } }) ENERGY_NOTE else null,
            protections = protections,
            mode = mode,
            firmware = buildFirmware(params, packs),
            other = buildOther(params),
            totalDesignWh = total { it.designWh },
            totalFullWh = total { it.fullWh },
            totalPassportWh = total { it.passport?.wh?.toDouble() },
            level = level,
            summary = summary,
            paramCount = params.size,
        )
    }

    private const val GEN3_SUMMARY = "gen3summary"

    /** Назви батарей (bmsMaster, bms_slave…) серед ключів даних. */
    private fun packPrefixes(keys: Collection<String>): Set<String> = keys.mapNotNull { k ->
        val i = k.lastIndexOf('.')
        if (i > 0 && k.substring(i + 1) in PACK_MARKERS) k.substring(0, i) else null
    }.filter { it != "display" && it != "runtime" && !it.endsWith("Info") }.toSortedSet()

    /** Чи є серед ключів дані батареї. Перші повідомлення станції часто містять лише виходи й потужність. */
    fun hasBatteryData(keys: Collection<String>): Boolean =
        packPrefixes(keys).isNotEmpty() || "display.bmsDesignCap" in keys || "runtime.bmsFullCap" in keys

    /** Ліміт заряду: кожне покоління кладе його під свій ключ. */
    fun chargeLimit(p: Map<String, Any?>): Double? =
        listOf("ems.maxChargeSoc", "bms_emsStatus.maxChargeSoc", "bmsMaster.maxChargeSoc", "inv.maxChargeSoc").firstNotNullOfOrNull { numOf(p[it]) }

    fun dischargeLimit(p: Map<String, Any?>): Double? =
        listOf("ems.minDsgSoc", "bms_emsStatus.minDsgSoc", "bmsMaster.minDsgSoc", "inv.minDsgSoc").firstNotNullOfOrNull { numOf(p[it]) }

    /**
     * Докази NCM за напругою. LFP не тримає всі групи вище ≈3.65 В: BMS зупиняє заряд раніше.
     * Одна перезаряджена група доказом не є, тому дивимось на середню й найнижчу, а не на найвищу.
     */
    private const val NCM_PROVEN_AVG_MV = 3700.0
    private const val NCM_PROVEN_MIN_MV = 3600L

    /** Без заряду в середині діапазону LFP лежить на плато ≈3.3 В, а NCM уже від 10% тримає понад 3.5 В. */
    private const val NCM_REST_AVG_MV = 3500.0
    private const val NCM_REST_MIN_MV = 3450L

    /**
     * Плато LFP: між цими напругами групи напруга майже не залежить від заряду, тож розбалансовані групи
     * виглядають однаково. Баланс чесно видно лише біля країв — наприкінці заряду чи розряду.
     */
    private const val LFP_LOW_KNEE_MV = 3150L
    private const val LFP_TOP_KNEE_MV = 3400L

    private fun packsWord(n: Int): String = when {
        n == 1 -> tr("1 батарея")
        n in 2..4 -> tr("{0} батареї", n)
        else -> tr("{0} батарей", n)
    }

    // ---------------------------------------------------------------- батарея

    private class Src(val params: Map<String, Any?>, val prefix: String, val info: String?) {
        fun raw(name: String): Any? = params["$prefix.$name"] ?: info?.let { params["$it.$name"] }
        fun num(name: String): Double? = numOf(params["$prefix.$name"]) ?: info?.let { numOf(params["$it.$name"]) }
        fun lng(name: String): Long? = num(name)?.toLong()
        fun rawLong(name: String): Long? = (raw(name) as? Number)?.toLong()
        fun str(name: String): String? = (params["$prefix.$name"] as? String)?.takeIf { it.isNotBlank() && it.trim('0').isNotEmpty() }
        fun list(name: String): List<Long> = (params["$prefix.$name"] as? List<*>)?.mapNotNull { numOf(it)?.toLong() } ?: emptyList()
    }

    private fun gen3SummarySrc(params: Map<String, Any?>): Map<String, Any?> {
        val m = HashMap<String, Any?>()
        fun copy(from: String, to: String) {
            params[from]?.let { m["$GEN3_SUMMARY.$to"] = it }
        }
        copy("display.bmsBattSoh", "soh")
        copy("display.bmsBattSoc", "soc")
        copy("display.bmsDesignCap", "designCap")
        copy("runtime.bmsFullCap", "fullCap")
        copy("runtime.bmsRemainCap", "remainCap")
        copy("runtime.bmsMinCellVol", "minCellVol")
        copy("runtime.bmsMaxCellVol", "maxCellVol")
        copy("display.bmsMinCellTemp", "minCellTemp")
        copy("display.bmsMaxCellTemp", "maxCellTemp")
        copy("display.bmsMinMosTemp", "minMosTemp")
        copy("display.bmsMaxMosTemp", "maxMosTemp")
        copy("display.bmsErrCode", "errCode")
        return m
    }

    private fun titleFor(prefix: String, gen3Count: Int): Pair<String, Boolean> {
        if (prefix == "bmsMaster" || prefix == "bms_bmsStatus" || prefix == "inv") return tr("Основна батарея") to true
        if (prefix == GEN3_SUMMARY) return tr("Основна батарея") to true
        if (prefix == "bms_slave") return tr("Додаткова батарея") to false
        if (prefix.startsWith("bmsPack")) {
            val n = prefix.removePrefix("bmsPack")
            return if (gen3Count <= 1) tr("Батарея") to true else tr("Батарейний блок №{0}", n) to false
        }
        val digits = prefix.takeLastWhile { it.isDigit() }
        if (prefix.contains("Slave", ignoreCase = true) || prefix.contains("slave")) {
            return (if (digits.isNotEmpty()) tr("Додаткова батарея {0}", digits) else tr("Додаткова батарея")) to false
        }
        return prefix to false
    }

    private fun analyzePack(
        key: String,
        prefix: String,
        allParams: Map<String, Any?>,
        generation: Generation,
        gen3Count: Int,
        modelName: String?,
        ctx: Ctx,
    ): PackReport {
        val params = if (prefix == GEN3_SUMMARY) gen3SummarySrc(allParams) else allParams
        val info = when {
            prefix == "bms_bmsStatus" -> "bms_bmsInfo"
            prefix.startsWith("bms_slave_bmsSlaveStatus_") -> prefix.replace("bmsSlaveStatus", "bmsSlaveInfo")
            else -> null
        }
        val s = Src(params, prefix, info)
        val (title, isMain) = titleFor(prefix, gen3Count)

        val soc = s.num("f32ShowSoc") ?: s.num("soc")
        val sohBms = s.num("soh")?.takeIf { it > 0.0 && it <= 100.0 }
        val cycSoh = (s.num("cycSoh") ?: s.num("cycleSoh"))?.takeIf { it > 0.0 && it <= 100.0 }
        val realSoh = s.num("realSoh")?.takeIf { it > 0.0 && it <= 100.0 }
        val design = s.lng("designCap")?.takeIf { it > 0 }
        val full = s.lng("fullCap")?.takeIf { it > 0 }
        val remain = s.lng("remainCap")?.takeIf { it >= 0 }
        // Вивчена ємність понад номінал більш ніж на 15% — так не буває (збій або інші одиниці): не використовуємо.
        val capImplausible = design != null && full != null && full > design * 1.15
        val sohCap = if (design != null && full != null && !capImplausible) full * 100.0 / design else null
        // Нова батарея може мати ємність трохи понад номінал — здоров'я понад 100% не показуємо.
        val sohEffective = listOfNotNull(sohBms, sohCap?.coerceAtMost(100.0)).minOrNull()

        val cycles = s.lng("cycles")
        val vol = s.lng("vol")?.takeIf { it > 0 }
        val amp = s.lng("amp")
        val temp = s.lng("temp")

        val cellVols = s.list("cellVol").filter { it in 1000L..5000L }
        val minCell = s.lng("minCellVol")?.takeIf { it in 1000L..5000L } ?: cellVols.minOrNull()
        val maxCell = s.lng("maxCellVol")?.takeIf { it in 1000L..5000L } ?: cellVols.maxOrNull()
        val delta = if (minCell != null && maxCell != null) maxCell - minCell else s.lng("maxVolDiff")

        val cellTemps = s.list("cellTemp")
        val minCellTemp = s.lng("minCellTemp") ?: cellTemps.minOrNull()
        val maxCellTemp = s.lng("maxCellTemp") ?: cellTemps.maxOrNull()
        val minMos = s.lng("minMosTemp")
        val maxMos = s.lng("maxMosTemp")

        val accuChgE = s.lng("accuChgEnergy")?.takeIf { it > 0 }
        val accuDsgE = s.lng("accuDsgEnergy")?.takeIf { it > 0 }
        val accuChgC = s.lng("accuChgCap")?.takeIf { it > 0 }
        val accuDsgC = s.lng("accuDsgCap")?.takeIf { it > 0 }

        // --- кількість послідовних груп
        val avgCell: Double? = when {
            cellVols.size >= 4 -> cellVols.average()
            minCell != null && maxCell != null -> (minCell + maxCell) / 2.0
            else -> null
        }
        val declaredSeries = s.lng("cellSeriesNum")?.toInt()?.takeIf { it in 3..32 }
        val series: Int? = declaredSeries
            ?: if (cellVols.size >= 4 && vol != null && abs(cellVols.sum() - vol) < vol * 0.03) {
                cellVols.size
            } else if (vol != null && avgCell != null && avgCell > 2000) {
                (vol / avgCell).roundToInt().takeIf { it in 3..32 }
            } else {
                null
            }
        val fullCellList = series != null && cellVols.size == series
        val seriesSure = declaredSeries != null || fullCellList
        val weakest = if (fullCellList) cellVols.indexOf(cellVols.minOrNull()!!) + 1 else null

        // --- паспорт моделі (для основної батареї — за моделлю станції)
        val modelPassport = if (isMain) Models.passportForModel(modelName) else null

        // --- хімія. Спершу фізика: якщо напруги можливі лише для однієї хімії, таблиця серійних номерів не сперечається
        // (той самий префікс спільнота приписує різним моделям, а пакет могли замінити). Далі — паспорт моделі,
        // бо за однією високою групою LFP легко «стане» NCM. Для решти — за напругами й групами.
        // Додаткові батареї визначаємо лише за параметрами: до станції бувають сумісні батареї іншої хімії.
        val modelChem = modelPassport?.chemistry
        val ncmProven = avgCell != null && minCell != null && (
            (avgCell >= NCM_PROVEN_AVG_MV && minCell >= NCM_PROVEN_MIN_MV) ||
                (soc != null && soc in 10.0..85.0 && (amp ?: 0L) <= 300 && avgCell >= NCM_REST_AVG_MV && minCell >= NCM_REST_MIN_MV)
            )
        val lfpProven = maxCell != null && soc != null && soc >= 60 && maxCell in 2800L..3450L
        var chem = Chemistry.UNKNOWN
        var chemSure = false
        when {
            ncmProven -> { chem = Chemistry.NCM; chemSure = true }
            modelChem == Chemistry.NCM && lfpProven -> { chem = Chemistry.LFP; chemSure = true }
            modelChem != null -> { chem = modelChem; chemSure = true }
            maxCell != null && maxCell >= 3750 && (minCell == null || minCell >= NCM_PROVEN_MIN_MV) -> { chem = Chemistry.NCM; chemSure = true }
            maxCell != null && soc != null && soc >= 60 && maxCell in 2800L..3600L -> { chem = Chemistry.LFP; chemSure = true }
            maxCell != null && soc != null && soc >= 30 && maxCell in 2800L..3450L -> { chem = Chemistry.LFP; chemSure = true }
            series == 14 && (vol ?: 0) > 40000 -> { chem = Chemistry.NCM; chemSure = true }
            series == 16 && (vol ?: 0) > 45000 -> { chem = Chemistry.LFP; chemSure = true }
            // DELTA Pro / DELTA Pro EB: 15 груп LFP по ~80 А·год (інакше на низькому заряді вони «ставали» NCM)
            series == 15 && design != null && design in 76_000L..84_000L -> { chem = Chemistry.LFP; chemSure = true }
            // NCM навіть на 10% заряду тримає ~3.5 В на групу; нижче 3.35 В при такому заряді — це LFP
            maxCell != null && soc != null && soc >= 10 && maxCell in 2800L..3350L -> { chem = Chemistry.LFP; chemSure = true }
            generation == Generation.GEN1 -> chem = Chemistry.NCM
            generation == Generation.GEN2 || generation == Generation.GEN3 -> chem = Chemistry.LFP
        }
        val modelMismatch = modelChem != null && chem != modelChem

        val packVnom = if (chem != Chemistry.UNKNOWN && series != null) series * chem.nominalCellV else null
        val designWh = if (packVnom != null && design != null) design / 1000.0 * packVnom else null
        val fullWh = if (packVnom != null && full != null) full / 1000.0 * packVnom else null
        val eqCycles = if (accuDsgE != null && designWh != null && designWh > 0) accuDsgE / designWh else null

        val byPack = Models.passportForPack(chem, design, series)
        val passport = when {
            modelMismatch -> byPack
            modelPassport?.wh != null -> modelPassport
            else -> byPack ?: modelPassport
        }
        val ratedCycles = passport?.cycles80 ?: chem.ratedCycles.takeIf { it > 0 }
        val ratedSource = when {
            passport?.cycles80 != null ->
                tr("паспорт {0}", passport.name) + if (passport.inferred) tr(" — тип визначено за параметрами батареї") else ""
            ratedCycles != null -> tr("типове значення для {0}", chem.short)
            else -> null
        }
        val resourceUsed = if (cycles != null && ratedCycles != null) cycles * 100.0 / ratedCycles else null

        val findings = ArrayList<Finding>()

        if (modelMismatch && modelPassport != null) {
            findings.add(
                Finding(
                    Level.UNKNOWN,
                    tr("За серійним номером це схоже на {0} ({1}), але напруги груп можливі лише для {2}", modelPassport.name, modelChem?.short, chem.short) +
                        (avgCell?.let { tr(" (середня {0} В)", fmt3(it / 1000.0)) } ?: "") + tr(". Хімію визначено за напругами."),
                ),
            )
        }

        // --- здоров'я: єдина оцінка батареї, ЛИШЕ за SOH (шкала Health)
        val health = Health.of(sohEffective)
        val healthFinding = if (health != null && sohEffective != null) {
            val capTxt = if (design != null && full != null && !capImplausible) {
                tr(" (вивчена ємність {0} з {1} А·год", fmt1(full / 1000.0), fmt1(design / 1000.0)) +
                    (if (fullWh != null && designWh != null) tr(", ≈{0} з {1} Вт·год", fmt0(fullWh), fmt0(designWh)) else "") + ")"
            } else {
                ""
            }
            Finding(health.level, tr("Здоров'я {0}%{1} — {2}.", fmt0(sohEffective), capTxt, health.verdict.lowercase()), health.verdict.lowercase())
        } else {
            null
        }
        healthFinding?.let { findings.add(it) }
        val capVerdict = when {
            health != null -> health.verdict
            cycles != null -> tr("SOH не передається — знос не оцінити (є лише лічильник циклів)")
            else -> tr("Немає даних про ємність")
        }

        if (design != null && full != null) {
            if (capImplausible) {
                findings.add(
                    Finding(
                        Level.UNKNOWN,
                        tr("BMS передає вивчену ємність {0} А·год при номіналі {1} А·год — на {2}% більше, так не буває. Ці дані в оцінці не використано.", fmt1(full / 1000.0), fmt1(design / 1000.0), fmt0((full - design) * 100.0 / design)),
                    ),
                )
            } else if (full > design) {
                findings.add(
                    Finding(
                        Level.UNKNOWN,
                        tr("Вивчена ємність {0} А·год трохи більша за номінал BMS {1} А·год — у нових батарей так буває (елементи мають запас). Здоров'я понад 100% не показується.", fmt1(full / 1000.0), fmt1(design / 1000.0)),
                    ),
                )
            }
        }

        // --- номінал BMS і паспорт: різниця зі знаком і поясненням; на оцінку здоров'я не впливає
        if (passport?.wh != null && designWh != null && design != null && packVnom != null) {
            val pWh = passport.wh
            val diff = (designWh - pWh) * 100.0 / pWh
            val basis = tr("{0} А·год × {1} В", fmt1(design / 1000.0), fmt1(packVnom))
            if (diff > 3) {
                val holds = if (fullWh != null && !capImplausible && fullWh > pWh) {
                    tr(" Зараз батарея вміщує ≈{0} Вт·год — це ≈{1}% від паспорта.", fmt0(fullWh), fmt0(fullWh * 100.0 / pWh))
                } else {
                    ""
                }
                findings.add(
                    Finding(
                        Level.UNKNOWN,
                        tr("Номінал за BMS ≈{0} Вт·год ({1}) — на {2}% БІЛЬШЕ за паспортні {3} Вт·год {4}. Це не помилка й не знос: у паспорті вказано гарантовану енергію, а BMS рахує повну ємність елементів.{5} Здоров'я рахується від номіналу BMS, тому ця різниця на оцінку не впливає.", fmt0(designWh), basis, fmt0(diff), pWh, passport.name, holds),
                    ),
                )
            } else if (diff < -3) {
                // Помітно менший номінал підозрілий, лише коли ми певні хімії, кількості груп і моделі.
                val suspicious = diff < -10 && chemSure && seriesSure && !passport.inferred
                val why = when {
                    suspicious -> tr("Так буває, якщо батарею чи BMS міняли на неоригінальну або BMS перепрограмували — варто перевірити.")
                    diff < -10 -> tr("Можлива причина — хімію чи кількість груп визначено приблизно. На оцінку здоров'я не впливає.")
                    else -> tr("Різниця невелика й на оцінку здоров'я не впливає.")
                }
                findings.add(
                    Finding(
                        if (suspicious) Level.WARN else Level.UNKNOWN,
                        tr("Номінал за BMS ≈{0} Вт·год ({1}) — на {2}% МЕНШЕ за паспортні {3} Вт·год {4}. {5}", fmt0(designWh), basis, fmt0(-diff), pWh, passport.name, why),
                        if (suspicious) tr("номінал BMS менший за паспорт") else null,
                    ),
                )
            }
        }

        if (sohBms != null && sohCap != null && abs(sohBms - sohCap) >= 10) {
            findings.add(
                Finding(
                    Level.UNKNOWN,
                    tr("SOH за BMS ({0}%) і за вивченою ємністю ({1}%) сильно розходяться — BMS могла давно не калібруватись. Для оцінки взято менше значення. Уточнити: повний цикл 100% → 0% → 100% або «Тест ємності».", fmt0(sohBms), fmt0(sohCap)),
                ),
            )
        }

        // --- баланс груп: висновок лише такий, який дозволяє напруга
        var balanceLevel = Level.UNKNOWN
        val lfpPlateau = chem == Chemistry.LFP && minCell != null && maxCell != null &&
            minCell > LFP_LOW_KNEE_MV && maxCell < LFP_TOP_KNEE_MV
        // На плато LFP навіть кілька мВ — це вже велика різниця заряду груп, тому пороги нижчі.
        val okMv = if (lfpPlateau) 20 else 30
        val warnMv = if (lfpPlateau) 50 else 70
        if (delta != null) {
            val socNote = soc?.let { tr(" при заряді {0}%", fmt0(it)) } ?: ""
            val avgNote = avgCell?.let { tr(" (середня {0} В)", fmt3(it / 1000.0)) } ?: ""
            val (lv, txt, short) = when {
                delta <= okMv && lfpPlateau -> Triple(Level.GOOD, tr("явного розбалансу не видно"), null)
                delta <= okMv -> Triple(Level.GOOD, tr("групи збалансовані"), null)
                delta <= warnMv && lfpPlateau -> Triple(
                    Level.WARN,
                    tr("помітний розбаланс — на плато LFP навіть така різниця означає, що групи помітно розійшлися за зарядом"),
                    tr("розбаланс груп"),
                )
                delta <= warnMv -> Triple(Level.WARN, tr("помірний дисбаланс"), tr("помірний дисбаланс груп"))
                delta <= 150 -> Triple(Level.BAD, tr("сильний дисбаланс — ймовірно, є слабка група"), tr("сильний дисбаланс груп"))
                else -> Triple(Level.CRITICAL, tr("дуже сильний дисбаланс — слабка/пошкоджена група"), tr("дуже сильний дисбаланс груп"))
            }
            val weakTxt = if (weakest != null && lv.rank >= Level.WARN.rank) tr(" Найнижча напруга — група №{0}.", weakest) else ""
            val plateauTxt = when {
                !lfpPlateau -> ""
                lv.rank >= Level.WARN.rank -> tr(" Наприкінці заряду (групи вище 3.45 В) різниця буде ще більшою — там і перевірте.")
                soc != null && soc >= 90 ->
                    tr(" Після заряду напруга LFP швидко сідає на плато (≈3.3 В), тому навіть на {0}% різниця груп майже не видна. Точно баланс видно наприкінці заряду (групи вище 3.45 В) або біля 0%.", fmt0(soc))
                else ->
                    tr(" Це плато LFP: тут навіть розбалансовані групи мають майже однакову напругу. Точно баланс видно наприкінці заряду (групи вище 3.45 В) або біля 0%.")
            }
            val chemTxt = if (chem == Chemistry.UNKNOWN) tr(" Хімію не визначено — оцінка приблизна.") else ""
            findings.add(Finding(lv, tr("Розкид напруг груп {0} мВ{1}{2}: {3}.{4}{5}{6}", delta, socNote, avgNote, txt, weakTxt, plateauTxt, chemTxt), short))
            balanceLevel = lv
        }

        // --- температури
        val hotCell = listOfNotNull(maxCellTemp, cellTemps.maxOrNull()).maxOrNull()
        if (hotCell != null) {
            when {
                hotCell >= 55 -> findings.add(Finding(Level.BAD, tr("Температура елементів {0} °C — перегрів.", hotCell), tr("перегрів елементів")))
                hotCell >= 45 -> findings.add(Finding(Level.WARN, tr("Температура елементів {0} °C — підвищена.", hotCell), tr("підвищена температура елементів")))
            }
        }
        if (minCellTemp != null && maxCellTemp != null && maxCellTemp - minCellTemp >= 10) {
            findings.add(
                Finding(
                    Level.WARN,
                    tr("Різниця температур між елементами {0} °C — можливий локальний нагрів групи.", maxCellTemp - minCellTemp),
                    tr("нерівномірний нагрів елементів"),
                ),
            )
        }
        if (maxMos != null && maxMos >= 75) {
            findings.add(Finding(Level.WARN, tr("Температура MOSFET BMS {0} °C — висока.", maxMos), tr("висока температура MOSFET")))
        }
        // Холод: нижче −30 °C вважаємо збоєм датчика, а не реальною температурою.
        val coldCell = listOfNotNull(minCellTemp, cellTemps.minOrNull()).minOrNull()?.takeIf { it >= -30 }
        if (coldCell != null && coldCell < 0) {
            if ((amp ?: 0L) > 300) {
                findings.add(
                    Finding(
                        Level.WARN,
                        tr("Заряд при температурі елементів {0} °C — на морозі заряд шкодить літієвим елементам; BMS має обмежувати струм.", coldCell),
                        tr("заряд на морозі"),
                    ),
                )
            } else {
                findings.add(
                    Finding(
                        Level.UNKNOWN,
                        tr("Температура елементів {0} °C — на холоді ємність тимчасово менша, а заряд BMS може не дозволяти до прогріву.", coldCell),
                    ),
                )
            }
        }

        // --- коди, відмови, захисти
        val codes = buildPackCodes(s, ctx, findings)

        // --- силові ключі
        val mos = buildMos(s, soc, amp, ctx)
        if (mos != null && mos.level.rank >= Level.WARN.rank) {
            findings.add(Finding(mos.level, tr("Силові ключі BMS: {0}. {1}", mos.value, mos.note ?: "").trim(), tr("BMS вимкнула розряд")))
        }

        val water = (s.lng("waterInFlag") ?: 0L) != 0L
        if (water) {
            findings.add(Finding(Level.CRITICAL, tr("Спрацював датчик потрапляння вологи в батарею!"), tr("волога в батареї")))
        }

        // --- ресурс за циклами: лише довідка; знос оцінюється за SOH. Тривога — тільки коли SOH невідомий.
        if (cycles != null && ratedCycles != null) {
            val used = resourceUsed ?: 0.0
            val (lv, tail, short) = when {
                used < 80 -> Triple(Level.GOOD, "", null)
                used < 100 -> Triple(Level.UNKNOWN, tr(" Паспортний ресурс наближається."), null)
                sohEffective != null && sohEffective >= 80 -> Triple(
                    Level.UNKNOWN,
                    tr(" Паспортний ресурс пройдено, але ємність ще тримається — орієнтуйтесь на здоров'я (SOH)."),
                    null,
                )
                sohEffective != null -> Triple(Level.UNKNOWN, tr(" Паспортний ресурс пройдено — це видно і за здоров'ям (SOH)."), null)
                else -> Triple(
                    Level.WARN,
                    tr(" Паспортний ресурс пройдено, а SOH BMS не передає — зробіть «Тест ємності»."),
                    tr("ресурс за циклами пройдено"),
                )
            }
            findings.add(
                Finding(
                    lv,
                    tr("{0} циклів із ~{1} до 80% ємності ({2}) — використано ≈{3}% ресурсу. Це оцінка лише за лічильником циклів BMS", cycles, ratedCycles, ratedSource, fmt0(used)) + (if (!chemSure) tr("; хімію визначено приблизно") else "") + "." + tail,
                    short,
                ),
            )
        }
        if (eqCycles != null && cycles != null && eqCycles > cycles * 1.5 + 20) {
            findings.add(
                Finding(
                    Level.WARN,
                    tr("За накопиченою енергією розряду (лічильник BMS) це ≈{0} повних циклів, а лічильник циклів показує {1}. Можливі причини: скидання лічильника, заміна BMS або інший метод обліку.", fmt0(eqCycles), cycles),
                    tr("лічильник циклів не збігається з енергією"),
                ),
            )
        }

        // --- поля-заглушки
        val notProvided = buildNotProvided(s)
        val hw = hwString(s.raw("hwEdition")) ?: hwString(s.raw("hwVersion")) ?: hwString(s.raw("hwVer"))

        // --- загальний статус і узгоджений висновок. Довідкові примітки (UNKNOWN) статус не знижують.
        // Оцінка батареї (health) — окремо, лише за SOH; решта — «зауваження».
        val capLevel = health?.level ?: Level.UNKNOWN
        val issues = findings.filter { it !== healthFinding && it.level.rank >= Level.WARN.rank }
        var level = capLevel
        for (f in findings) level = level.worse(f.level)
        for (c in codes) level = level.worse(c.level)
        mos?.let { level = level.worse(it.level) }

        val driver = issues.firstOrNull { it.short != null && it.level == level && it.level.rank > capLevel.rank }
        val baseVerdict = when {
            driver == null -> capVerdict
            capLevel == Level.UNKNOWN -> driver.short!!.replaceFirstChar { it.uppercase() }
            else -> tr("{0}, але {1}", capVerdict, driver.short)
        }
        // Пояснення вивченого коду (напр. 23) показуємо завжди — як нейтральну примітку, без «Увага».
        val codeInfo = findings.firstOrNull { it.codeInfo && it.short != null }
        val verdict = if (codeInfo != null && codeInfo !== driver && !baseVerdict.contains(codeInfo.short!!)) {
            "$baseVerdict · ${codeInfo.short}"
        } else {
            baseVerdict
        }

        val sn = s.str("packSn") ?: s.str("bmsSn")
        val fw = s.lng("sysVer")?.takeIf { it > 0 }?.let { versionString(it) }

        return PackReport(
            key = key, title = title, isMain = isMain, sn = sn,
            soc = soc, sohBms = sohBms, sohCapacity = sohCap, sohEffective = sohEffective,
            cycSoh = cycSoh, realSoh = realSoh,
            designCapMah = design, fullCapMah = full, remainCapMah = remain,
            cycles = cycles, volMv = vol, ampMa = amp, tempC = temp,
            cellVols = cellVols, minCellMv = minCell, maxCellMv = maxCell, cellDeltaMv = delta,
            weakestCell = weakest,
            cellTemps = cellTemps, minCellTemp = minCellTemp, maxCellTemp = maxCellTemp,
            minMosTemp = minMos, maxMosTemp = maxMos,
            accuChgEnergyWh = accuChgE, accuDsgEnergyWh = accuDsgE, accuChgCapMah = accuChgC, accuDsgCapMah = accuDsgC,
            seriesCells = series, chemistry = chem, chemistryConfident = chemSure,
            packVnom = packVnom, designWh = designWh, fullWh = fullWh,
            passport = passport, ratedCycles = ratedCycles, ratedCyclesSource = ratedSource,
            equivalentCycles = eqCycles, resourceUsedPct = resourceUsed,
            mos = mos, codes = codes, notProvided = notProvided,
            waterIn = water, firmware = fw, hwVersion = hw, modelMismatch = modelMismatch,
            level = level, health = health, capacityVerdict = capVerdict, verdict = verdict, findings = findings,
            issues = issues, balanceLevel = balanceLevel, cellOkMv = okMv, cellWarnMv = warnMv,
        )
    }

    /** Коди помилок, відмови та захисти BMS. Додає пояснювальні висновки у [findings]. */
    private fun buildPackCodes(s: Src, ctx: Ctx, findings: MutableList<Finding>): List<Indicator> {
        val out = ArrayList<Indicator>()

        // Постійні відмови
        val fault = s.lng("bmsFault")
        val allFault = s.lng("allBmsFault")
        val faultState = s.lng("bmsFaultState")
        val faults = listOfNotNull(fault?.let { "bmsFault" to it }, allFault?.let { "allBmsFault" to it }, faultState?.let { "bmsFaultState" to it })
        val hasFault = faults.any { it.second != 0L }
        if (faults.isNotEmpty()) {
            out.add(
                Indicator(
                    tr("Відмови BMS"),
                    faults.joinToString(" · ") { "${it.first} ${it.second}" },
                    if (hasFault) Level.BAD else Level.GOOD,
                    if (hasFault) {
                        tr("BMS фіксує відмову: ") + faults.filter { it.second != 0L }.joinToString("; ") { tr("{0} — біти {1}", it.first, bitsText(it.second)) }
                    } else {
                        tr("постійних відмов немає")
                    },
                ),
            )
        }

        // Регістр стану AFE
        val bq = s.lng("bqSysStatReg")
        var bqAlarm = false
        if (bq != null) {
            val alarmBits = BQ_ALARM_BITS.filter { (bq shr it.first) and 1L == 1L }
            bqAlarm = alarmBits.isNotEmpty()
            val note = if (bqAlarm) {
                tr("аварійні біти: ") + alarmBits.joinToString("; ") { it.second }
            } else {
                tr("аварійних бітів (0–5) немає") + if ((bq shr 7) and 1L == 1L) tr("; біт 7 CC_READY — службовий") else ""
            }
            out.add(
                Indicator(
                    tr("Стан AFE (bqSysStatReg)"),
                    "$bq (0x${bq.toString(16).uppercase()})",
                    if (bqAlarm) Level.BAD else Level.GOOD,
                    tr("{0}. Трактування для чипа BQ769x0 — уточніть, який чип стоїть у цій BMS.", note),
                ),
            )
        }

        // Активні захисти й тривоги (3-тє покоління)
        var protect = false
        val alarms = ArrayList<String>()
        for ((name, title, lv) in listOf(
            Triple("bmsProtectState1", tr("Захист BMS 1"), Level.BAD),
            Triple("bmsProtectState2", tr("Захист BMS 2"), Level.BAD),
            Triple("bmsAlarmState1", tr("Тривога BMS 1"), Level.WARN),
            Triple("bmsAlarmState2", tr("Тривога BMS 2"), Level.WARN),
        )) {
            val v = s.lng(name) ?: continue
            if (v == 0L) continue
            if (lv == Level.BAD) protect = true else alarms.add(name)
            out.add(Indicator(title, tr("{0} (біти {1})", v, bitsText(v)), lv, tr("активний стан BMS ({0})", name)))
        }

        // Коди помилок
        val errCode = s.lng("errCode")
        val allErr = s.lng("allErrCode")
        val allBits = allErr?.let { setBits(it) } ?: emptyList()
        val userNote = errCode?.takeIf { it != 0L }?.let { ctx.codeNotes["bms:$it"] }
        // Вивчений (задокументований) код — напр. 23 (норма), 6 (дисбаланс). Для них беремо наш рівень і пояснення.
        val knownDoc = errCode?.takeIf { it != 0L }?.let { EcoflowCodes.documented("bms:$it") }
        if (errCode != null) {
            out.add(
                if (errCode == 0L) {
                    Indicator(tr("Код помилки BMS (errCode)"), "0", Level.GOOD, tr("немає"))
                } else {
                    Indicator(
                        tr("Код помилки BMS (errCode)"),
                        "$errCode",
                        knownDoc?.level ?: Level.WARN,
                        userNote?.let { tr("Ваша примітка: {0}", it) } ?: knownDoc?.short ?: tr("код не задокументований EcoFlow"),
                        "bms:$errCode",
                    )
                },
            )
        }
        if (allErr != null) {
            out.add(
                if (allErr == 0L) {
                    Indicator(tr("Маска помилок (allErrCode)"), "0", Level.GOOD, tr("немає"))
                } else {
                    val same = errCode != null && allBits == listOf(errCode.toInt())
                    Indicator(
                        tr("Маска помилок (allErrCode)"),
                        "$allErr (0x${allErr.toString(16).uppercase()})",
                        if (same) (knownDoc?.level ?: Level.WARN) else Level.WARN,
                        tr("встановлені біти: {0}", allBits.joinToString(", ")) + if (same) tr(" — той самий код, що й errCode") else "",
                    )
                },
            )
        }

        val unknownCode = (errCode ?: 0L) != 0L || (allErr ?: 0L) != 0L
        val noteTxt = userNote?.let { tr(" Ваша примітка до коду: {0}.", it) } ?: ""
        if (hasFault || bqAlarm || protect) {
            findings.add(
                Finding(
                    Level.BAD,
                    tr("BMS фіксує відмову або аварійний стан — див. блок «Коди BMS». Батарею потрібно перевірити."),
                    tr("BMS фіксує відмову"),
                ),
            )
        } else if (knownDoc != null) {
            // Вивчений код: показуємо пояснення одразу, рівень = рівень коду (23 → «Добре», не «Увага»).
            findings.add(Finding(knownDoc.level, knownDoc.detail + noteTxt, knownDoc.short, codeInfo = true))
        } else if (unknownCode) {
            val code = if ((errCode ?: 0L) != 0L) "$errCode" else allBits.joinToString(", ")
            val bitsTxt = if (allErr != null && allErr != 0L) tr(" (allErrCode: біт {0})", allBits.joinToString(", ")) else ""
            val context = ArrayList<String>()
            if (faults.isNotEmpty()) context.add(tr("постійних відмов BMS немає"))
            if (bq != null) context.add(tr("аварійних бітів AFE немає"))
            if (ctx.iconBmsErr == 0L) context.add(tr("станція сама іконку помилки BMS не показує"))
            val ctxTxt = if (context.isNotEmpty()) tr(" Інших ознак проблем немає: {0}.", context.joinToString(", ")) else ""
            findings.add(
                Finding(
                    Level.WARN,
                    tr("BMS передає код {0}{1}. EcoFlow не публікує значення кодів BMS.{2}{3} Якщо станція на гарантії — уточніть код у підтримці EcoFlow за серійним номером.", code, bitsTxt, ctxTxt, noteTxt),
                    tr("BMS передає незадокументований код {0}", code),
                ),
            )
        }
        // Тривога без відмови чи захисту — теж зауваження, щоб воно було видно не лише на вкладці «Коди».
        if (!hasFault && !bqAlarm && !protect && alarms.isNotEmpty()) {
            findings.add(
                Finding(
                    Level.WARN,
                    tr("BMS повідомляє активну тривогу ({0}) — див. вкладку «Коди». Значення бітів EcoFlow не публікує.", alarms.joinToString(", ")),
                    tr("активна тривога BMS"),
                ),
            )
        }
        return out
    }

    private fun buildMos(s: Src, soc: Double?, ampMa: Long?, ctx: Ctx): Indicator? {
        val v = s.lng("mosState") ?: return null
        val chgOn = v and 1L != 0L
        val dsgOn = v and 2L != 0L
        var lv = Level.GOOD
        val notes = ArrayList<String>()
        // Біти mosState підтверджені не для всіх моделей. Якщо струм батареї суперечить біту —
        // трактування для цієї BMS не підходить, і робити з нього висновок не можна.
        val amp = ampMa ?: 0L
        if ((!chgOn && amp > 300) || (!dsgOn && amp < -300)) {
            return Indicator(
                tr("Силові ключі BMS"),
                "mosState = $v",
                Level.UNKNOWN,
                tr("біти суперечать струму батареї ({0} А) — для цієї BMS вони означають інше, не зважайте", fmt1(amp / 1000.0)),
            )
        }
        if (!chgOn) {
            val max = ctx.maxChargeSoc
            notes.add(
                if (soc != null && max != null && soc >= max - 2) {
                    tr("заряд зупинено лімітом {0}% — це норма", fmt0(max))
                } else {
                    tr("ключ заряду вимкнено (буває нормою: немає джерела заряду, ліміт, температура)")
                },
            )
        }
        if (!dsgOn) {
            val min = ctx.minDsgSoc
            if (soc != null && min != null && soc <= min + 2) {
                notes.add(tr("розряд зупинено нижнім лімітом {0}%", fmt0(min)))
            } else {
                notes.add(tr("ключ розряду вимкнено BMS"))
                lv = Level.WARN
            }
        }
        notes.add(tr("трактування бітів імовірне: біт 0 — заряд, біт 1 — розряд"))
        return Indicator(
            tr("Силові ключі BMS"),
            tr("заряд {0}, розряд {1} (mosState = {2})", if (chgOn) tr("увімк.") else tr("вимк."), if (dsgOn) tr("увімк.") else tr("вимк."), v),
            lv,
            notes.joinToString("; "),
        )
    }

    /** Поля, які прошивка BMS не заповнює (заглушки) — показуються як «не передається». */
    private fun buildNotProvided(s: Src): List<String> {
        val out = ArrayList<String>()
        (s.raw("packSn") as? String)?.let { if (it.isNotEmpty() && it.all { c -> c == '0' }) out.add(tr("серійний номер пакета (packSn = {0})", "0".repeat(it.length))) }
        listOf("hwEdition", "hwVersion", "hwVer").forEach { n ->
            val h = hwRaw(s.raw(n))
            if (h != null && h.trim().equals("V0.0.0", ignoreCase = true)) out.add(tr("версія заліза ({0} = V0.0.0)", n))
        }
        val capStubs = listOf("totalChgCap", "totalDsgCap", "chgCap", "dsgCap").filter { s.rawLong(it) == 4294967295L }
        if (capStubs.isNotEmpty()) out.add(tr("накопичена ємність ({0} = 0xFFFFFFFF)", capStubs.joinToString(", ")))
        if (s.rawLong("ecloudOcv") == 65535L) out.add(tr("OCV для хмари (ecloudOcv = 0xFFFF)"))
        if (s.raw("soh") != null && s.rawLong("soh") == 0L) out.add(tr("SOH від BMS (soh = 0)"))
        val sohStubs = listOf("cycSoh", "cycleSoh", "realSoh", "caleSoh").filter { s.raw(it) != null && (s.raw(it) as? Number)?.toDouble() == 0.0 }
        if (sohStubs.isNotEmpty()) out.add(tr("додаткові оцінки SOH ({0} = 0)", sohStubs.joinToString(", ")))
        return out
    }

    private fun hwRaw(v: Any?): String? = when (v) {
        is String -> v
        is List<*> -> v.mapNotNull { (it as? Number)?.toInt()?.takeIf { c -> c in 32..126 }?.toChar() }.joinToString("").ifEmpty { null }
        else -> null
    }

    private fun hwString(v: Any?): String? = hwRaw(v)?.trim()?.takeIf { it.isNotEmpty() && !it.equals("V0.0.0", ignoreCase = true) }

    // ---------------------------------------------------------------- станція: захисти й режим

    private fun buildProtections(p: Map<String, Any?>, packs: List<PackReport>, ctx: Ctx): List<Indicator> {
        val out = ArrayList<Indicator>()
        fun num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { numOf(p[it]) }
        val soc = mainSoc(p, packs)

        num("ems.emsIsNormalFlag", "bms_emsStatus.emsIsNormalFlag")?.let { v ->
            out.add(
                Indicator(
                    tr("EMS у нормальному режимі"),
                    if (v == 1.0) tr("так") else tr("ні"),
                    if (v == 1.0) Level.GOOD else Level.WARN,
                    "emsIsNormalFlag = ${fmt0(v)}",
                ),
            )
        }

        // Зв'язок EMS з BMS кожної батареї
        val online = ArrayList<Double>()
        for (i in 0..2) num("ems.bms${i}Online")?.let { while (online.size < i) online.add(0.0); online.add(it) }
        if (online.isEmpty()) {
            (p["bms_emsStatus.bmsIsConnt"] as? List<*>)?.forEach { online.add(numOf(it) ?: 0.0) }
        }
        val extraCount = packs.count { !it.isMain }
        for ((i, v) in online.withIndex()) {
            val name = if (i == 0) tr("основної батареї") else tr("додаткової батареї {0}", i)
            val expected = i == 0 || i <= extraCount
            if (v == 0.0 && !expected) continue
            val (txt, lv) = when (v.toInt()) {
                3 -> tr("на зв'язку (HW + SW)") to Level.GOOD
                1, 2 -> tr("частковий зв'язок (= {0})", fmt0(v)) to Level.WARN
                0 -> tr("немає зв'язку") to Level.BAD
                else -> "= ${fmt0(v)}" to Level.UNKNOWN
            }
            out.add(Indicator(tr("Зв'язок з BMS {0}", name), txt, lv))
        }

        val chg = num("ems.chgCmd", "bms_emsStatus.chgCmd")
        val dsg = num("ems.dsgCmd", "bms_emsStatus.dsgCmd")
        if (chg != null || dsg != null) {
            var lv = Level.GOOD
            val notes = ArrayList<String>()
            if (chg == 0.0) {
                val max = ctx.maxChargeSoc
                if (soc != null && max != null && soc >= max - 2) notes.add(tr("заряд заборонено, бо досягнуто ліміту {0}%", fmt0(max))) else {
                    notes.add(tr("EMS заборонила заряд"))
                    lv = Level.WARN
                }
            }
            if (dsg == 0.0) {
                val min = ctx.minDsgSoc
                if (soc != null && min != null && soc <= min + 2) notes.add(tr("розряд заборонено нижнім лімітом {0}%", fmt0(min))) else {
                    notes.add(tr("EMS заборонила розряд"))
                    lv = Level.WARN
                }
            }
            out.add(
                Indicator(
                    tr("Заряд / розряд дозволені EMS"),
                    "${allowed(chg)} / ${allowed(dsg)}",
                    lv,
                    notes.joinToString("; ").ifEmpty { "chgCmd = ${chg?.let { fmt0(it) } ?: "—"}, dsgCmd = ${dsg?.let { fmt0(it) } ?: "—"}" },
                ),
            )
        }

        num("ems.bmsWarningState", "bms_emsStatus.bmsWarState")?.let { v ->
            out.add(Indicator(tr("Попередження EMS"), if (v == 0.0) tr("немає") else fmt0(v), if (v == 0.0) Level.GOOD else Level.WARN))
        }
        ctx.iconBmsErr?.let { v ->
            out.add(
                Indicator(
                    tr("Іконка помилки BMS на екрані станції"),
                    if (v == 0L) tr("не показується") else tr("показується"),
                    if (v == 0L) Level.GOOD else Level.WARN,
                    "pd.iconBmsErrState = $v",
                ),
            )
        }
        for ((key, title, prefix) in listOf(
            Triple("inv.errCode", tr("Помилка інвертора"), "inv"),
            Triple("pd.errCode", tr("Помилка плати PD"), "pd"),
            Triple("mppt.faultCode", tr("Помилка MPPT"), "mppt"),
        )) {
            val v = numOf(p[key])?.toLong() ?: continue
            if (v == 0L) {
                out.add(Indicator(title, tr("немає"), Level.GOOD, "$key = 0"))
            } else {
                val note = ctx.codeNotes["$prefix:$v"]
                out.add(
                    Indicator(
                        title,
                        tr("код {0} (0x{1})", v, v.toString(16).uppercase()),
                        Level.WARN,
                        note?.let { tr("Ваша примітка: {0}", it) } ?: tr("код не задокументований EcoFlow"),
                        "$prefix:$v",
                    ),
                )
            }
        }
        return out
    }

    private fun allowed(v: Double?): String = when (v) {
        null -> "—"
        1.0 -> tr("дозволено")
        0.0 -> tr("заборонено")
        else -> fmt0(v)
    }

    private fun mainSoc(p: Map<String, Any?>, packs: List<PackReport>): Double? =
        numOf(p["ems.f32LcdShowSoc"]) ?: numOf(p["ems.lcdShowSoc"])
            ?: numOf(p["bms_emsStatus.f32LcdShowSoc"]) ?: numOf(p["bms_emsStatus.lcdShowSoc"])
            ?: packs.firstOrNull { it.isMain }?.soc

    private fun buildMode(p: Map<String, Any?>, packs: List<PackReport>, ctx: Ctx): List<Indicator> {
        val out = ArrayList<Indicator>()
        fun num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { numOf(p[it]) }
        val inW = num("inv.inputWatts")
        val outW = num("inv.outputWatts")
        val ampMa = packs.firstOrNull { it.isMain }?.ampMa
        val soc = mainSoc(p, packs)
        val max = ctx.maxChargeSoc
        val min = ctx.minDsgSoc
        val charging = (ampMa ?: 0L) > 300
        val nearMax = soc != null && max != null && soc >= max - 2 && !charging
        val totalIn = num("pd.wattsInSum")

        if (inW != null || ampMa != null) {
            val ampA = (ampMa ?: 0L) / 1000.0
            val bypass = inW != null && outW != null && inW >= 20 && outW >= 10 &&
                abs(inW - outW) <= maxOf(30.0, inW * 0.2) && abs(ampMa ?: 0L) < 1000
            val (title, note) = when {
                bypass -> tr("Байпас (UPS)") to
                    tr("Навантаження {0} Вт живиться з мережі напряму (вхід {1} Вт ≈ вихід), струм батареї {2} А — батарея не заряджається", fmt0(outW!!), fmt0(inW!!), fmt1(ampA)) +
                    (if (nearMax) tr(", бо досягнуто ліміту заряду {0}%.", fmt0(max!!)) else ".")
                charging -> tr("Заряд") to tr("Струм батареї +{0} А", fmt1(ampA)) +
                    (totalIn?.let { tr(", вхід усього {0} Вт", fmt0(it)) } ?: "") + (inW?.let { tr(" (AC {0} Вт)", fmt0(it)) } ?: "")
                (ampMa ?: 0L) < -300 -> tr("Розряд") to tr("Струм батареї {0} А", fmt1(ampA)) + (outW?.let { tr(", вихід AC {0} Вт", fmt0(it)) } ?: "")
                else -> tr("Очікування") to tr("Струм батареї {0} А", fmt1(ampA)) +
                    (if (nearMax) tr(". Заряд не йде, бо досягнуто ліміту {0}% — це не несправність.", fmt0(max!!)) else "")
            }
            out.add(Indicator(tr("Режим роботи"), title, Level.UNKNOWN, note))
        }
        if (soc != null) out.add(Indicator(tr("Заряд за даними станції"), "${fmt0(soc)}%", Level.UNKNOWN))
        max?.let { out.add(Indicator(tr("Ліміт заряду"), "${fmt0(it)}%", Level.UNKNOWN, if (nearMax) tr("заряд досягнув ліміту") else null)) }
        min?.let { out.add(Indicator(tr("Нижній ліміт розряду"), "${fmt0(it)}%", Level.UNKNOWN)) }
        num("pd.bpPowerSoc", "pd.bppowerSoc")?.let { out.add(Indicator(tr("Резерв живлення"), "${fmt0(it)}%", Level.UNKNOWN, "pd.bpPowerSoc")) }
        num("ems.openUpsFlag", "bms_emsStatus.openUpsFlag")?.let { out.add(Indicator(tr("Прапорець UPS"), fmt0(it), Level.UNKNOWN, "openUpsFlag")) }

        val slow = num("inv.cfgSlowChgWatts", "inv.SlowChgWatts", "mppt.cfgChgWatts")
        val fast = num("inv.cfgFastChgWatts", "inv.FastChgWatts")
        if (slow != null || fast != null) {
            val v = buildString {
                slow?.let { append(tr("{0} Вт", fmt0(it))) }
                fast?.takeIf { it > 0 }?.let { append(if (isEmpty()) "" else " · ").append(tr("швидкий {0} Вт", fmt0(it))) }
            }.ifEmpty { "—" }
            out.add(Indicator(tr("Потужність AC-заряду"), v, Level.UNKNOWN, tr("налаштування станції")))
        }
        num("inv.cfgAcXboost", "mppt.cfgAcXboost")?.let { out.add(Indicator("X-Boost", if (it == 1.0) tr("увімк.") else tr("вимк."), Level.UNKNOWN)) }
        num("inv.chgPauseFlag")?.let { if (it != 0.0) out.add(Indicator(tr("Пауза AC-заряду"), tr("так"), Level.UNKNOWN, "chgPauseFlag = ${fmt0(it)}")) }
        num("inv.chargerType", "pd.chargerType")?.let { out.add(Indicator(tr("Тип зарядного (chargerType)"), fmt0(it), Level.UNKNOWN)) }
        return out
    }

    // ---------------------------------------------------------------- станція: лічильники

    private val USAGE_KEYS: List<Pair<List<String>, String>> get() = listOf(
        listOf("pd.invUsedTime") to tr("Інвертор (AC-вихід)"),
        listOf("pd.mpptUsedTime") to tr("Заряд від сонця (MPPT)"),
        listOf("pd.dcInUsedTime") to tr("DC-вхід"),
        listOf("pd.carUsedTime") to tr("Автомобільний вихід 12 В"),
        listOf("pd.usbUsedTime") to "USB-A",
        listOf("pd.usbqcUsedTime") to tr("USB-A швидкий"),
        listOf("pd.typecUsedTime", "pd.typccUsedTime") to "USB-C",
    )

    private fun buildUsage(p: Map<String, Any?>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((keys, title) in USAGE_KEYS) {
            val sec = keys.firstNotNullOfOrNull { numOf(p[it]) } ?: continue
            if (sec <= 0) continue
            out.add(title to durationString(sec.toLong()))
        }
        return out
    }

    private val ENERGY_KEYS: List<Pair<List<String>, String>> get() = listOf(
        listOf("pd.chgPowerAC", "pd.chgPowerAc") to tr("Через AC-вхід (включно з транзитом у байпасі)"),
        listOf("pd.chgSunPower") to tr("Від сонця"),
        listOf("pd.chgPowerDC", "pd.chgPowerDc") to tr("Через DC-вхід"),
        listOf("pd.dsgPowerAC", "pd.dsgPowerAc") to tr("Через AC-вихід (включно з транзитом)"),
        listOf("pd.dsgPowerDC", "pd.dsgPowerDc") to tr("Через DC/USB-виходи"),
    )

    /** Рядки енергії, яку станція видала споживачам: AC-вихід і DC/USB-виходи. */
    fun energyOutTitles(): List<String> = ENERGY_KEYS.drop(3).map { it.second }

    private fun buildEnergy(p: Map<String, Any?>, packs: List<PackReport>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((keys, title) in ENERGY_KEYS) {
            val wh = keys.firstNotNullOfOrNull { numOf(p[it]) } ?: continue
            if (wh <= 0) continue
            out.add(title to energyString(wh))
        }
        for (pk in packs) {
            if (pk.accuChgEnergyWh != null || pk.accuDsgEnergyWh != null) {
                val parts = ArrayList<String>()
                pk.accuChgEnergyWh?.let { parts.add(tr("заряд {0}", energyString(it.toDouble()))) }
                pk.accuDsgEnergyWh?.let { parts.add(tr("розряд {0}", energyString(it.toDouble()))) }
                out.add(tr("{0} (лічильник BMS)", pk.title) to parts.joinToString(", "))
            }
        }
        return out
    }

    private fun buildFirmware(p: Map<String, Any?>, packs: List<PackReport>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        numOf(p["pd.sysVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add(tr("Плата PD") to versionString(it)) }
        numOf(p["inv.sysVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add(tr("Інвертор") to versionString(it)) }
        numOf(p["mppt.swVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add("MPPT" to versionString(it)) }
        numOf(p["pd.wifiVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add(tr("Wi-Fi модуль") to versionString(it)) }
        for (pk in packs) {
            pk.firmware?.let { out.add("BMS: ${pk.title}" to it) }
            pk.hwVersion?.let { out.add(tr("Залізо BMS: {0}", pk.title) to it) }
        }
        return out
    }

    private fun buildOther(p: Map<String, Any?>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        numOf(p["pd.relaySwitchCnt"])?.let { if (it > 0) out.add(tr("Перемикань реле") to fmt0(it)) }
        (p["bms_kitInfo.watts"] as? List<*>)?.let { kits ->
            for (kit in kits) {
                val m = kit as? Map<*, *> ?: continue
                val ksn = m["sn"]?.toString() ?: continue
                val type = m["type"]?.toString() ?: "?"
                out.add(tr("Підключено до порту батареї") to tr("{0} (тип {1})", ksn, type))
            }
        }
        return out
    }

    // ---------------------------------------------------------------- утиліти

    fun setBits(v: Long): List<Int> = (0..63).filter { (v shr it) and 1L == 1L }

    private fun bitsText(v: Long): String = setBits(v).joinToString(", ").ifEmpty { "—" }

    fun numOf(v: Any?): Double? {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            is Boolean -> if (v) 1.0 else 0.0
            else -> null
        } ?: return null
        if (d.isNaN() || d.isInfinite() || d >= 4294967295.0) return null
        return d
    }

    fun versionString(v: Long): String = "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    fun durationString(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h >= 24 -> tr("{0} год ({1} доби)", h, fmt1(h / 24.0))
            h > 0 -> tr("{0} год {1} хв", h, m)
            else -> tr("{0} хв", m)
        }
    }

    fun energyString(wh: Double): String = if (wh >= 1000) tr("{0} кВт·год", fmt1(wh / 1000.0)) else tr("{0} Вт·год", fmt0(wh))

    fun fmt0(d: Double): String = String.format(Locale.US, "%.0f", d)
    fun fmt1(d: Double): String = String.format(Locale.US, "%.1f", d)
    fun fmt2(d: Double): String = String.format(Locale.US, "%.2f", d)
    fun fmt3(d: Double): String = String.format(Locale.US, "%.3f", d)
}
