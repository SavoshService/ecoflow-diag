package ua.savosh.ecoflowdiag.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Level(val rank: Int, val title: String) {
    UNKNOWN(-1, "Немає даних"),
    GOOD(0, "Добре"),
    OK(1, "Норма"),
    WARN(2, "Увага"),
    BAD(3, "Погано"),
    CRITICAL(4, "Критично");

    fun worse(other: Level): Level = if (other.rank > rank) other else this
}

enum class Chemistry(val title: String, val short: String, val nominalCellV: Double, val ratedCycles: Int) {
    LFP("LiFePO4 (залізофосфат)", "LFP", 3.2, 3000),
    NCM("Li-ion NCM", "NCM", 3.6, 800),
    UNKNOWN("не визначено", "?", 0.0, 0),
}

/** Висновок. [short] — коротке формулювання для заголовка батареї, якщо саме воно визначає статус. */
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
    val level: Level,
    /** Висновок лише за ємністю (SOH). */
    val capacityVerdict: String,
    /** Загальний висновок, узгоджений зі статусом [level]. */
    val verdict: String,
    val findings: List<Finding>,
)

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
    private val BQ_ALARM_BITS = listOf(
        0 to "OCD — перевантаження струмом розряду",
        1 to "SCD — коротке замикання",
        2 to "OV — перенапруга групи",
        3 to "UV — недонапруга групи",
        4 to "OVRD_ALERT — зовнішня тривога",
        5 to "DEVICE_XREADY — помилка чипа AFE",
    )

    const val ENERGY_NOTE =
        "Ці лічильники рахують енергію, що пройшла через станцію, а не через батарею: у режимі байпасу (UPS) " +
            "транзит із мережі в навантаження теж додається. Ресурс батареї оцінюйте лише за лічильником циклів BMS."

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
            maxChargeSoc = numOf(params["ems.maxChargeSoc"]) ?: numOf(params["bms_emsStatus.maxChargeSoc"]),
            minDsgSoc = numOf(params["ems.minDsgSoc"]) ?: numOf(params["bms_emsStatus.minDsgSoc"]),
            iconBmsErr = numOf(params["pd.iconBmsErrState"])?.toLong(),
            codeNotes = codeNotes,
        )

        val prefixes = params.keys.mapNotNull { k ->
            val i = k.lastIndexOf('.')
            if (i > 0 && k.substring(i + 1) in PACK_MARKERS) k.substring(0, i) else null
        }.filter { it != "display" && it != "runtime" && !it.endsWith("Info") }.toSortedSet()

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
        val realPacks = packs.filter { p ->
            // Реальна батарея завжди має ємність і напругу; у «фантомного» слейва вони 0/порожні.
            p.isMain || (p.designCapMah ?: 0) > 0 || (p.volMv ?: 0) > 0 || (p.fullCapMah ?: 0) > 0
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
            packs.isEmpty() -> "Даних про батареї ще немає"
            else -> {
                val worst = packs.maxByOrNull { it.level.rank }!!
                val soh = worst.sohEffective?.let { " (SOH ${fmt0(it)}%)" } ?: ""
                val prot = protections.filter { it.level.rank >= Level.WARN.rank }
                val protTxt = if (prot.isNotEmpty() && prot.maxOf { it.level.rank } > worst.level.rank) {
                    " · станція: ${prot.first().title.lowercase()} — ${prot.first().value}"
                } else {
                    ""
                }
                if (packs.size == 1) {
                    "${worst.title}$soh — ${worst.verdict}$protTxt"
                } else {
                    "${packsWord(packs.size)} · найгірша: ${worst.title}$soh — ${worst.verdict}$protTxt"
                }
            }
        }

        return StationReport(
            sn = sn,
            model = modelInfo?.name,
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

    private fun packsWord(n: Int): String = when {
        n == 1 -> "1 батарея"
        n in 2..4 -> "$n батареї"
        else -> "$n батарей"
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
        if (prefix == "bmsMaster" || prefix == "bms_bmsStatus" || prefix == "inv") return "Основна батарея" to true
        if (prefix == GEN3_SUMMARY) return "Основна батарея" to true
        if (prefix == "bms_slave") return "Додаткова батарея" to false
        if (prefix.startsWith("bmsPack")) {
            val n = prefix.removePrefix("bmsPack")
            return if (gen3Count <= 1) "Батарея" to true else "Батарейний блок №$n" to false
        }
        val digits = prefix.takeLastWhile { it.isDigit() }
        if (prefix.contains("Slave", ignoreCase = true) || prefix.contains("slave")) {
            return (if (digits.isNotEmpty()) "Додаткова батарея $digits" else "Додаткова батарея") to false
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
        val sohCap = if (design != null && full != null && full <= design * 1.15) full * 100.0 / design else null
        val sohEffective = listOfNotNull(sohBms, sohCap).minOrNull()

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
        val weakest = if (fullCellList) cellVols.indexOf(cellVols.minOrNull()!!) + 1 else null

        // --- паспорт моделі (для основної батареї — за моделлю станції)
        val modelPassport = if (isMain) Models.passportForModel(modelName) else null

        // --- хімія
        var chem = Chemistry.UNKNOWN
        var chemSure = false
        when {
            maxCell != null && maxCell >= 3750 -> { chem = Chemistry.NCM; chemSure = true }
            maxCell != null && soc != null && soc >= 60 && maxCell in 2800L..3600L -> { chem = Chemistry.LFP; chemSure = true }
            maxCell != null && soc != null && soc >= 30 && maxCell in 2800L..3450L -> { chem = Chemistry.LFP; chemSure = true }
            series == 14 && (vol ?: 0) > 40000 -> { chem = Chemistry.NCM; chemSure = true }
            series == 16 && (vol ?: 0) > 45000 -> { chem = Chemistry.LFP; chemSure = true }
            modelPassport?.chemistry != null -> { chem = modelPassport.chemistry; chemSure = true }
            generation == Generation.GEN1 -> chem = Chemistry.NCM
            generation == Generation.GEN2 || generation == Generation.GEN3 -> chem = Chemistry.LFP
        }

        val packVnom = if (chem != Chemistry.UNKNOWN && series != null) series * chem.nominalCellV else null
        val designWh = if (packVnom != null && design != null) design / 1000.0 * packVnom else null
        val fullWh = if (packVnom != null && full != null) full / 1000.0 * packVnom else null
        val eqCycles = if (accuDsgE != null && designWh != null && designWh > 0) accuDsgE / designWh else null

        val passport = modelPassport ?: Models.passportForPack(chem, design, series)
        val ratedCycles = passport?.cycles80 ?: chem.ratedCycles.takeIf { it > 0 }
        val ratedSource = when {
            passport?.cycles80 != null ->
                "паспорт ${passport.name}" + if (passport.inferred) " — тип визначено за параметрами батареї" else ""
            ratedCycles != null -> "типове значення для ${chem.short}"
            else -> null
        }
        val resourceUsed = if (cycles != null && ratedCycles != null) cycles * 100.0 / ratedCycles else null

        val findings = ArrayList<Finding>()

        // --- ємність
        var capLevel = Level.UNKNOWN
        val capVerdict: String
        if (sohEffective != null) {
            val (lv, txt) = when {
                sohEffective >= 90 -> Level.GOOD to "Батарея в доброму стані"
                sohEffective >= 80 -> Level.OK to "Нормальний знос"
                sohEffective >= 70 -> Level.WARN to "Помітна деградація"
                sohEffective >= 60 -> Level.BAD to "Сильна деградація"
                else -> Level.CRITICAL to "Ресурс елементів вичерпано"
            }
            capLevel = lv
            capVerdict = txt
            val capTxt = if (design != null && full != null) {
                " (вивчена ємність ${fmt1(full / 1000.0)} з ${fmt1(design / 1000.0)} А·год" +
                    (if (fullWh != null && designWh != null) ", ≈${fmt0(fullWh)} з ${fmt0(designWh)} Вт·год" else "") + ")"
            } else {
                ""
            }
            findings.add(Finding(lv, "Здоров'я ${fmt0(sohEffective)}%$capTxt — ${txt.lowercase()}.", txt.lowercase()))
        } else if (cycles != null) {
            capLevel = Level.OK
            capVerdict = "SOH не передається — оцінка за циклами"
        } else {
            capVerdict = "Немає даних про ємність"
        }

        if (passport?.wh != null && designWh != null && abs(designWh - passport.wh) / passport.wh > 0.03) {
            val diff = (designWh - passport.wh) * 100.0 / passport.wh
            findings.add(
                Finding(
                    Level.OK,
                    "Номінал за даними BMS ≈${fmt0(designWh)} Вт·год (${fmt1(design!! / 1000.0)} А·год × ${fmt1(packVnom!!)} В), " +
                        "у паспорті ${passport.name} — ${passport.wh} Вт·год. Різниця ${fmt0(abs(diff))}% нормальна: паспорт вказує " +
                        "консервативну енергію, а BMS — номінал елементів. Знос оцінюйте за відсотком SOH.",
                ),
            )
        }

        if (sohBms != null && sohCap != null && abs(sohBms - sohCap) >= 10) {
            findings.add(
                Finding(
                    Level.WARN,
                    "SOH за BMS (${fmt0(sohBms)}%) і вивчена ємність (${fmt0(sohCap)}%) сильно розходяться. " +
                        "BMS могла не калібруватись — зробіть повний цикл 100% → 0% → 100% і перевірте ще раз.",
                    "SOH за BMS і за ємністю розходяться",
                ),
            )
        }

        // --- баланс груп
        if (delta != null) {
            val socNote = soc?.let { " при заряді ${fmt0(it)}%" } ?: ""
            val (lv, txt, short) = when {
                delta <= 30 -> Triple(Level.GOOD, "групи збалансовані", null)
                delta <= 70 -> Triple(Level.WARN, "помірний дисбаланс", "помірний дисбаланс груп")
                delta <= 150 -> Triple(Level.BAD, "сильний дисбаланс — ймовірно, є слабка група", "сильний дисбаланс груп")
                else -> Triple(Level.CRITICAL, "дуже сильний дисбаланс — слабка/пошкоджена група", "дуже сильний дисбаланс груп")
            }
            val weakTxt = if (weakest != null && delta > 30) " Найнижча напруга — група №$weakest." else ""
            findings.add(Finding(lv, "Розкид напруг груп $delta мВ$socNote: $txt.$weakTxt", short))
            if (chem == Chemistry.LFP && soc != null && soc in 15.0..90.0 && delta <= 30) {
                findings.add(
                    Finding(
                        Level.OK,
                        "У LFP в середині заряду напруги майже рівні навіть при дисбалансі. " +
                            "Надійніше перевіряти розкид біля 100% (кінець заряду) або біля 0%.",
                    ),
                )
            }
        }

        // --- температури
        val hotCell = listOfNotNull(maxCellTemp, cellTemps.maxOrNull()).maxOrNull()
        if (hotCell != null) {
            when {
                hotCell >= 55 -> findings.add(Finding(Level.BAD, "Температура елементів $hotCell °C — перегрів.", "перегрів елементів"))
                hotCell >= 45 -> findings.add(Finding(Level.WARN, "Температура елементів $hotCell °C — підвищена.", "підвищена температура елементів"))
            }
        }
        if (minCellTemp != null && maxCellTemp != null && maxCellTemp - minCellTemp >= 10) {
            findings.add(
                Finding(
                    Level.WARN,
                    "Різниця температур між елементами ${maxCellTemp - minCellTemp} °C — можливий локальний нагрів групи.",
                    "нерівномірний нагрів елементів",
                ),
            )
        }
        if (maxMos != null && maxMos >= 75) {
            findings.add(Finding(Level.WARN, "Температура MOSFET BMS $maxMos °C — висока.", "висока температура MOSFET"))
        }

        // --- коди, відмови, захисти
        val codes = buildPackCodes(s, ctx, findings)

        // --- силові ключі
        val mos = buildMos(s, soc, ctx)
        if (mos != null && mos.level.rank >= Level.WARN.rank) {
            findings.add(Finding(mos.level, "Силові ключі BMS: ${mos.value}. ${mos.note ?: ""}".trim(), "BMS вимкнула розряд"))
        }

        val water = (s.lng("waterInFlag") ?: 0L) != 0L
        if (water) {
            findings.add(Finding(Level.CRITICAL, "Спрацював датчик потрапляння вологи в батарею!", "волога в батареї"))
        }

        // --- ресурс за циклами
        if (cycles != null && ratedCycles != null) {
            val used = resourceUsed ?: 0.0
            val lv = if (used >= 100) Level.WARN else Level.GOOD
            findings.add(
                Finding(
                    lv,
                    "$cycles циклів із ~$ratedCycles до 80% ємності ($ratedSource) — використано ≈${fmt0(used)}% ресурсу. " +
                        "Це оцінка лише за лічильником циклів BMS" + (if (!chemSure) "; хімію визначено приблизно" else "") + ".",
                    if (lv == Level.WARN) "ресурс за циклами вичерпано" else null,
                ),
            )
        }
        if (eqCycles != null && cycles != null && eqCycles > cycles * 1.5 + 20) {
            findings.add(
                Finding(
                    Level.WARN,
                    "За накопиченою енергією розряду (лічильник BMS) це ≈${fmt0(eqCycles)} повних циклів, а лічильник циклів показує $cycles. " +
                        "Можливі причини: скидання лічильника, заміна BMS або інший метод обліку.",
                    "лічильник циклів не збігається з енергією",
                ),
            )
        }

        // --- поля-заглушки
        val notProvided = buildNotProvided(s)
        val hw = hwString(s.raw("hwEdition")) ?: hwString(s.raw("hwVersion")) ?: hwString(s.raw("hwVer"))

        // --- загальний статус і узгоджений висновок
        var level = capLevel
        for (f in findings) level = level.worse(f.level)
        for (c in codes) level = level.worse(c.level)
        mos?.let { level = level.worse(it.level) }

        val driver = findings.filter { it.short != null && it.level == level && it.level.rank > capLevel.rank }
            .firstOrNull()
        val baseVerdict = when {
            driver == null -> capVerdict
            capLevel == Level.UNKNOWN -> driver.short!!.replaceFirstChar { it.uppercase() }
            else -> "$capVerdict, але ${driver.short}"
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
            waterIn = water, firmware = fw, hwVersion = hw,
            level = level, capacityVerdict = capVerdict, verdict = verdict, findings = findings,
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
                    "Відмови BMS",
                    faults.joinToString(" · ") { "${it.first} ${it.second}" },
                    if (hasFault) Level.BAD else Level.GOOD,
                    if (hasFault) {
                        "BMS фіксує відмову: " + faults.filter { it.second != 0L }.joinToString("; ") { "${it.first} — біти ${bitsText(it.second)}" }
                    } else {
                        "постійних відмов немає"
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
                "аварійні біти: " + alarmBits.joinToString("; ") { it.second }
            } else {
                "аварійних бітів (0–5) немає" + if ((bq shr 7) and 1L == 1L) "; біт 7 CC_READY — службовий" else ""
            }
            out.add(
                Indicator(
                    "Стан AFE (bqSysStatReg)",
                    "$bq (0x${bq.toString(16).uppercase()})",
                    if (bqAlarm) Level.BAD else Level.GOOD,
                    "$note. Трактування для чипа BQ769x0 — уточніть, який чип стоїть у цій BMS.",
                ),
            )
        }

        // Активні захисти й тривоги (3-тє покоління)
        var protect = false
        for ((name, title, lv) in listOf(
            Triple("bmsProtectState1", "Захист BMS 1", Level.BAD),
            Triple("bmsProtectState2", "Захист BMS 2", Level.BAD),
            Triple("bmsAlarmState1", "Тривога BMS 1", Level.WARN),
            Triple("bmsAlarmState2", "Тривога BMS 2", Level.WARN),
        )) {
            val v = s.lng(name) ?: continue
            if (v == 0L) continue
            if (lv == Level.BAD) protect = true
            out.add(Indicator(title, "$v (біти ${bitsText(v)})", lv, "активний стан BMS ($name)"))
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
                    Indicator("Код помилки BMS (errCode)", "0", Level.GOOD, "немає")
                } else {
                    Indicator(
                        "Код помилки BMS (errCode)",
                        "$errCode",
                        knownDoc?.level ?: Level.WARN,
                        userNote?.let { "Ваша примітка: $it" } ?: knownDoc?.short ?: "код не задокументований EcoFlow",
                        "bms:$errCode",
                    )
                },
            )
        }
        if (allErr != null) {
            out.add(
                if (allErr == 0L) {
                    Indicator("Маска помилок (allErrCode)", "0", Level.GOOD, "немає")
                } else {
                    val same = errCode != null && allBits == listOf(errCode.toInt())
                    Indicator(
                        "Маска помилок (allErrCode)",
                        "$allErr (0x${allErr.toString(16).uppercase()})",
                        if (same) (knownDoc?.level ?: Level.WARN) else Level.WARN,
                        "встановлені біти: ${allBits.joinToString(", ")}" + if (same) " — той самий код, що й errCode" else "",
                    )
                },
            )
        }

        val unknownCode = (errCode ?: 0L) != 0L || (allErr ?: 0L) != 0L
        val noteTxt = userNote?.let { " Ваша примітка до коду: $it." } ?: ""
        if (hasFault || bqAlarm || protect) {
            findings.add(
                Finding(
                    Level.BAD,
                    "BMS фіксує відмову або аварійний стан — див. блок «Коди BMS». Батарею потрібно перевірити.",
                    "BMS фіксує відмову",
                ),
            )
        } else if (knownDoc != null) {
            // Вивчений код: показуємо пояснення одразу, рівень = рівень коду (23 → «Добре», не «Увага»).
            findings.add(Finding(knownDoc.level, knownDoc.detail + noteTxt, knownDoc.short, codeInfo = true))
        } else if (unknownCode) {
            val code = if ((errCode ?: 0L) != 0L) "$errCode" else allBits.joinToString(", ")
            val bitsTxt = if (allErr != null && allErr != 0L) " (allErrCode: біт ${allBits.joinToString(", ")})" else ""
            val context = ArrayList<String>()
            if (faults.isNotEmpty()) context.add("постійних відмов BMS немає")
            if (bq != null) context.add("аварійних бітів AFE немає")
            if (ctx.iconBmsErr == 0L) context.add("станція сама іконку помилки BMS не показує")
            val ctxTxt = if (context.isNotEmpty()) " Інших ознак проблем немає: ${context.joinToString(", ")}." else ""
            findings.add(
                Finding(
                    Level.WARN,
                    "BMS передає код $code$bitsTxt. EcoFlow не публікує значення кодів BMS.$ctxTxt$noteTxt " +
                        "Якщо станція на гарантії — уточніть код у підтримці EcoFlow за серійним номером.",
                    "BMS передає незадокументований код $code",
                ),
            )
        }
        return out
    }

    private fun buildMos(s: Src, soc: Double?, ctx: Ctx): Indicator? {
        val v = s.lng("mosState") ?: return null
        val chgOn = v and 1L != 0L
        val dsgOn = v and 2L != 0L
        var lv = Level.GOOD
        val notes = ArrayList<String>()
        if (!chgOn) {
            val max = ctx.maxChargeSoc
            notes.add(
                if (soc != null && max != null && soc >= max - 2) {
                    "заряд зупинено лімітом ${fmt0(max)}% — це норма"
                } else {
                    "ключ заряду вимкнено (буває нормою: немає джерела заряду, ліміт, температура)"
                },
            )
        }
        if (!dsgOn) {
            val min = ctx.minDsgSoc
            if (soc != null && min != null && soc <= min + 2) {
                notes.add("розряд зупинено нижнім лімітом ${fmt0(min)}%")
            } else {
                notes.add("ключ розряду вимкнено BMS")
                lv = Level.WARN
            }
        }
        notes.add("трактування бітів імовірне: біт 0 — заряд, біт 1 — розряд")
        return Indicator(
            "Силові ключі BMS",
            "заряд ${if (chgOn) "увімк." else "вимк."}, розряд ${if (dsgOn) "увімк." else "вимк."} (mosState = $v)",
            lv,
            notes.joinToString("; "),
        )
    }

    /** Поля, які прошивка BMS не заповнює (заглушки) — показуються як «не передається». */
    private fun buildNotProvided(s: Src): List<String> {
        val out = ArrayList<String>()
        (s.raw("packSn") as? String)?.let { if (it.isNotEmpty() && it.all { c -> c == '0' }) out.add("серійний номер пакета (packSn = ${"0".repeat(it.length)})") }
        listOf("hwEdition", "hwVersion", "hwVer").forEach { n ->
            val h = hwRaw(s.raw(n))
            if (h != null && h.trim().equals("V0.0.0", ignoreCase = true)) out.add("версія заліза ($n = V0.0.0)")
        }
        val capStubs = listOf("totalChgCap", "totalDsgCap", "chgCap", "dsgCap").filter { s.rawLong(it) == 4294967295L }
        if (capStubs.isNotEmpty()) out.add("накопичена ємність (${capStubs.joinToString(", ")} = 0xFFFFFFFF)")
        if (s.rawLong("ecloudOcv") == 65535L) out.add("OCV для хмари (ecloudOcv = 0xFFFF)")
        if (s.raw("soh") != null && s.rawLong("soh") == 0L) out.add("SOH від BMS (soh = 0)")
        val sohStubs = listOf("cycSoh", "cycleSoh", "realSoh", "caleSoh").filter { s.raw(it) != null && (s.raw(it) as? Number)?.toDouble() == 0.0 }
        if (sohStubs.isNotEmpty()) out.add("додаткові оцінки SOH (${sohStubs.joinToString(", ")} = 0)")
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
                    "EMS у нормальному режимі",
                    if (v == 1.0) "так" else "ні",
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
            val name = if (i == 0) "основної батареї" else "додаткової батареї $i"
            val expected = i == 0 || i <= extraCount
            if (v == 0.0 && !expected) continue
            val (txt, lv) = when (v.toInt()) {
                3 -> "на зв'язку (HW + SW)" to Level.GOOD
                1, 2 -> "частковий зв'язок (= ${fmt0(v)})" to Level.WARN
                0 -> "немає зв'язку" to Level.BAD
                else -> "= ${fmt0(v)}" to Level.UNKNOWN
            }
            out.add(Indicator("Зв'язок з BMS $name", txt, lv))
        }

        val chg = num("ems.chgCmd", "bms_emsStatus.chgCmd")
        val dsg = num("ems.dsgCmd", "bms_emsStatus.dsgCmd")
        if (chg != null || dsg != null) {
            var lv = Level.GOOD
            val notes = ArrayList<String>()
            if (chg == 0.0) {
                val max = ctx.maxChargeSoc
                if (soc != null && max != null && soc >= max - 2) notes.add("заряд заборонено, бо досягнуто ліміту ${fmt0(max)}%") else {
                    notes.add("EMS заборонила заряд")
                    lv = Level.WARN
                }
            }
            if (dsg == 0.0) {
                val min = ctx.minDsgSoc
                if (soc != null && min != null && soc <= min + 2) notes.add("розряд заборонено нижнім лімітом ${fmt0(min)}%") else {
                    notes.add("EMS заборонила розряд")
                    lv = Level.WARN
                }
            }
            out.add(
                Indicator(
                    "Заряд / розряд дозволені EMS",
                    "${allowed(chg)} / ${allowed(dsg)}",
                    lv,
                    notes.joinToString("; ").ifEmpty { "chgCmd = ${chg?.let { fmt0(it) } ?: "—"}, dsgCmd = ${dsg?.let { fmt0(it) } ?: "—"}" },
                ),
            )
        }

        num("ems.bmsWarningState", "bms_emsStatus.bmsWarState")?.let { v ->
            out.add(Indicator("Попередження EMS", if (v == 0.0) "немає" else fmt0(v), if (v == 0.0) Level.GOOD else Level.WARN))
        }
        ctx.iconBmsErr?.let { v ->
            out.add(
                Indicator(
                    "Іконка помилки BMS на екрані станції",
                    if (v == 0L) "не показується" else "показується",
                    if (v == 0L) Level.GOOD else Level.WARN,
                    "pd.iconBmsErrState = $v",
                ),
            )
        }
        for ((key, title, prefix) in listOf(
            Triple("inv.errCode", "Помилка інвертора", "inv"),
            Triple("pd.errCode", "Помилка плати PD", "pd"),
            Triple("mppt.faultCode", "Помилка MPPT", "mppt"),
        )) {
            val v = numOf(p[key])?.toLong() ?: continue
            if (v == 0L) {
                out.add(Indicator(title, "немає", Level.GOOD, "$key = 0"))
            } else {
                val note = ctx.codeNotes["$prefix:$v"]
                out.add(
                    Indicator(
                        title,
                        "код $v (0x${v.toString(16).uppercase()})",
                        Level.WARN,
                        note?.let { "Ваша примітка: $it" } ?: "код не задокументований EcoFlow",
                        "$prefix:$v",
                    ),
                )
            }
        }
        return out
    }

    private fun allowed(v: Double?): String = when (v) {
        null -> "—"
        1.0 -> "дозволено"
        0.0 -> "заборонено"
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
                bypass -> "Байпас (UPS)" to
                    "Навантаження ${fmt0(outW!!)} Вт живиться з мережі напряму (вхід ${fmt0(inW!!)} Вт ≈ вихід), " +
                    "струм батареї ${fmt1(ampA)} А — батарея не заряджається" +
                    (if (nearMax) ", бо досягнуто ліміту заряду ${fmt0(max!!)}%." else ".")
                charging -> "Заряд" to "Струм батареї +${fmt1(ampA)} А" +
                    (totalIn?.let { ", вхід усього ${fmt0(it)} Вт" } ?: "") + (inW?.let { " (AC ${fmt0(it)} Вт)" } ?: "")
                (ampMa ?: 0L) < -300 -> "Розряд" to "Струм батареї ${fmt1(ampA)} А" + (outW?.let { ", вихід AC ${fmt0(it)} Вт" } ?: "")
                else -> "Очікування" to "Струм батареї ${fmt1(ampA)} А" +
                    (if (nearMax) ". Заряд не йде, бо досягнуто ліміту ${fmt0(max!!)}% — це не несправність." else "")
            }
            out.add(Indicator("Режим роботи", title, Level.UNKNOWN, note))
        }
        if (soc != null) out.add(Indicator("Заряд за даними станції", "${fmt0(soc)}%", Level.UNKNOWN))
        max?.let { out.add(Indicator("Ліміт заряду", "${fmt0(it)}%", Level.UNKNOWN, if (nearMax) "заряд досягнув ліміту" else null)) }
        min?.let { out.add(Indicator("Нижній ліміт розряду", "${fmt0(it)}%", Level.UNKNOWN)) }
        num("pd.bpPowerSoc", "pd.bppowerSoc")?.let { out.add(Indicator("Резерв живлення", "${fmt0(it)}%", Level.UNKNOWN, "pd.bpPowerSoc")) }
        num("ems.openUpsFlag", "bms_emsStatus.openUpsFlag")?.let { out.add(Indicator("Прапорець UPS", fmt0(it), Level.UNKNOWN, "openUpsFlag")) }

        val slow = num("inv.cfgSlowChgWatts", "inv.SlowChgWatts", "mppt.cfgChgWatts")
        val fast = num("inv.cfgFastChgWatts", "inv.FastChgWatts")
        if (slow != null || fast != null) {
            val v = buildString {
                slow?.let { append("${fmt0(it)} Вт") }
                fast?.takeIf { it > 0 }?.let { append(if (isEmpty()) "" else " · ").append("швидкий ${fmt0(it)} Вт") }
            }.ifEmpty { "—" }
            out.add(Indicator("Потужність AC-заряду", v, Level.UNKNOWN, "налаштування станції"))
        }
        num("inv.cfgAcXboost", "mppt.cfgAcXboost")?.let { out.add(Indicator("X-Boost", if (it == 1.0) "увімк." else "вимк.", Level.UNKNOWN)) }
        num("inv.chgPauseFlag")?.let { if (it != 0.0) out.add(Indicator("Пауза AC-заряду", "так", Level.UNKNOWN, "chgPauseFlag = ${fmt0(it)}")) }
        num("inv.chargerType", "pd.chargerType")?.let { out.add(Indicator("Тип зарядного (chargerType)", fmt0(it), Level.UNKNOWN)) }
        return out
    }

    // ---------------------------------------------------------------- станція: лічильники

    private val USAGE_KEYS: List<Pair<List<String>, String>> = listOf(
        listOf("pd.invUsedTime") to "Інвертор (AC-вихід)",
        listOf("pd.mpptUsedTime") to "Заряд від сонця (MPPT)",
        listOf("pd.dcInUsedTime") to "DC-вхід",
        listOf("pd.carUsedTime") to "Автомобільний вихід 12 В",
        listOf("pd.usbUsedTime") to "USB-A",
        listOf("pd.usbqcUsedTime") to "USB-A швидкий",
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

    private val ENERGY_KEYS: List<Pair<List<String>, String>> = listOf(
        listOf("pd.chgPowerAC", "pd.chgPowerAc") to "Через AC-вхід (включно з транзитом у байпасі)",
        listOf("pd.chgSunPower") to "Від сонця",
        listOf("pd.chgPowerDC", "pd.chgPowerDc") to "Через DC-вхід",
        listOf("pd.dsgPowerAC", "pd.dsgPowerAc") to "Через AC-вихід (включно з транзитом)",
        listOf("pd.dsgPowerDC", "pd.dsgPowerDc") to "Через DC/USB-виходи",
    )

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
                pk.accuChgEnergyWh?.let { parts.add("заряд ${energyString(it.toDouble())}") }
                pk.accuDsgEnergyWh?.let { parts.add("розряд ${energyString(it.toDouble())}") }
                out.add("${pk.title} (лічильник BMS)" to parts.joinToString(", "))
            }
        }
        return out
    }

    private fun buildFirmware(p: Map<String, Any?>, packs: List<PackReport>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        numOf(p["pd.sysVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add("Плата PD" to versionString(it)) }
        numOf(p["inv.sysVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add("Інвертор" to versionString(it)) }
        numOf(p["mppt.swVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add("MPPT" to versionString(it)) }
        numOf(p["pd.wifiVer"])?.toLong()?.takeIf { it > 0 }?.let { out.add("Wi-Fi модуль" to versionString(it)) }
        for (pk in packs) {
            pk.firmware?.let { out.add("BMS: ${pk.title}" to it) }
            pk.hwVersion?.let { out.add("Залізо BMS: ${pk.title}" to it) }
        }
        return out
    }

    private fun buildOther(p: Map<String, Any?>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        numOf(p["pd.relaySwitchCnt"])?.let { if (it > 0) out.add("Перемикань реле" to fmt0(it)) }
        (p["bms_kitInfo.watts"] as? List<*>)?.let { kits ->
            for (kit in kits) {
                val m = kit as? Map<*, *> ?: continue
                val ksn = m["sn"]?.toString() ?: continue
                val type = m["type"]?.toString() ?: "?"
                out.add("Підключено до порту батареї" to "$ksn (тип $type)")
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
            h >= 24 -> "$h год (${fmt1(h / 24.0)} доби)"
            h > 0 -> "$h год $m хв"
            else -> "$m хв"
        }
    }

    fun energyString(wh: Double): String = if (wh >= 1000) "${fmt1(wh / 1000.0)} кВт·год" else "${fmt0(wh)} Вт·год"

    fun fmt0(d: Double): String = String.format(Locale.US, "%.0f", d)
    fun fmt1(d: Double): String = String.format(Locale.US, "%.1f", d)
    fun fmt2(d: Double): String = String.format(Locale.US, "%.2f", d)
    fun fmt3(d: Double): String = String.format(Locale.US, "%.3f", d)
}
