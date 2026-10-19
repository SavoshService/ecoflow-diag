package ua.savosh.ecoflowdiag.core

import kotlin.math.abs

enum class CapPhase { IDLE, CHARGE, CHARGE_DONE, DISCHARGE, DONE }

/** Підсумки одного етапу тесту (заряд або розряд). */
class CapStage {
    var startMs = 0L
    var endMs = 0L
    var socStart: Double? = null
    var socEnd: Double? = null
    /** Енергія на клемах батареї (за напругою й струмом BMS), Вт·год. */
    var wh = 0.0
    /** Заряд, А·год (сума струмів усіх батарей). */
    var ah = 0.0
    /** Енергія з боку станції (вхід або вихід, разом із втратами перетворення), Вт·год. */
    var stationWh = 0.0
    /** Час без даних, який не враховано (зв'язок пропадав), мс. */
    var gapMs = 0L
    var samples = 0
    var peakW = 0.0

    val durationMs: Long get() = if (endMs > startMs) endMs - startMs else 0L
    val socSpan: Double? get() = if (socStart != null && socEnd != null) abs(socEnd!! - socStart!!) else null
}

/**
 * Тест реальної ємності: етап 1 — заряд з 0 до 100%, етап 2 — розряд зі 100 до 0%.
 * Енергія рахується інтегруванням потужності на клемах батареї (напруга × струм з BMS кожної батареї)
 * по часу. Додатний струм — заряд, від'ємний — розряд.
 */
class CapacityTest(val sn: String) {

    var phase = CapPhase.IDLE
        private set
    val charge = CapStage()
    val discharge = CapStage()

    /** Номінал за даними BMS і за паспортом — для порівняння. */
    var designWh: Double? = null
        private set
    var passportWh: Double? = null
        private set
    var chargeLimit: Double? = null
        private set
    var dischargeLimit: Double? = null
        private set

    // поточні значення для екрана
    var soc: Double? = null
        private set
    var batteryW: Double? = null
        private set
    var batteryV: Double? = null
        private set
    var batteryA: Double? = null
        private set
    var lastDataMs = 0L
        private set

    /** Етап, схоже, завершився сам (заряд дійшов до ліміту / розряд до нуля і струм ~0 вже 2 хв). */
    var autoDone = false
        private set

    private var lastMs = 0L
    private var lastP: Double? = null
    private var lastA: Double? = null
    private var lastStationW: Double? = null
    private var quietSinceMs = 0L

    fun startCharge(nowMs: Long) {
        reset(charge, nowMs)
        phase = CapPhase.CHARGE
    }

    fun finishCharge(nowMs: Long) {
        if (phase != CapPhase.CHARGE) return
        charge.endMs = nowMs
        charge.socEnd = soc
        phase = CapPhase.CHARGE_DONE
        autoDone = false
    }

    fun startDischarge(nowMs: Long) {
        reset(discharge, nowMs)
        phase = CapPhase.DISCHARGE
    }

    fun finishDischarge(nowMs: Long) {
        if (phase != CapPhase.DISCHARGE) return
        discharge.endMs = nowMs
        discharge.socEnd = soc
        phase = CapPhase.DONE
        autoDone = false
    }

    private fun reset(st: CapStage, nowMs: Long) {
        st.startMs = nowMs
        st.endMs = 0
        st.socStart = soc
        st.socEnd = null
        st.wh = 0.0; st.ah = 0.0; st.stationWh = 0.0
        st.gapMs = 0; st.samples = 0; st.peakW = 0.0
        lastMs = 0L; lastP = null; lastA = null; lastStationW = null
        quietSinceMs = 0L
        autoDone = false
    }

    /** Новий знімок даних станції. Викликати при кожному оновленні. */
    fun feed(nowMs: Long, r: StationReport, params: Map<String, Any?>) {
        val packs = r.packs.filter { it.volMv != null && it.ampMa != null }
        if (packs.isEmpty()) return
        val p = packs.sumOf { it.volMv!! * it.ampMa!! / 1_000_000.0 }
        val a = packs.sumOf { it.ampMa!! / 1000.0 }
        val main = r.packs.firstOrNull { it.isMain } ?: r.packs.first()
        soc = main.soc ?: soc
        batteryW = p
        batteryA = a
        batteryV = main.volMv?.let { it / 1000.0 }
        lastDataMs = nowMs

        val dw = r.packs.mapNotNull { it.designWh }
        if (dw.size == r.packs.size && dw.isNotEmpty()) designWh = dw.sum()
        val pw = r.packs.mapNotNull { it.passport?.wh?.toDouble() }
        if (pw.size == r.packs.size && pw.isNotEmpty()) passportWh = pw.sum()
        chargeLimit = num(params, "ems.maxChargeSoc", "bms_emsStatus.maxChargeSoc") ?: chargeLimit
        dischargeLimit = num(params, "ems.minDsgSoc", "bms_emsStatus.minDsgSoc") ?: dischargeLimit

        val stage = when (phase) {
            CapPhase.CHARGE -> charge
            CapPhase.DISCHARGE -> discharge
            else -> return
        }
        val sign = if (phase == CapPhase.CHARGE) 1.0 else -1.0
        val stationW = if (phase == CapPhase.CHARGE) {
            num(params, "pd.wattsInSum", "inv.inputWatts", "display.powInSumW")
        } else {
            num(params, "pd.wattsOutSum", "inv.outputWatts", "display.powOutSumW")
        }
        if (stage.socStart == null) stage.socStart = soc

        if (lastMs > 0) {
            val dt = nowMs - lastMs
            if (dt in 1..MAX_GAP_MS) {
                val h = dt / 3_600_000.0
                val pAvg = (p + (lastP ?: p)) / 2
                val aAvg = (a + (lastA ?: a)) / 2
                stage.wh += sign * pAvg * h
                stage.ah += sign * aAvg * h
                if (stationW != null) stage.stationWh += (stationW + (lastStationW ?: stationW)) / 2 * h
            } else if (dt > MAX_GAP_MS) {
                stage.gapMs += dt
            }
        }
        lastMs = nowMs
        lastP = p
        lastA = a
        lastStationW = stationW
        stage.samples++
        if (abs(p) > stage.peakW) stage.peakW = abs(p)

        // автовизначення кінця етапу
        val s = soc
        val atEnd = when (phase) {
            CapPhase.CHARGE -> s != null && s >= (chargeLimit ?: 100.0) - 0.5
            CapPhase.DISCHARGE -> s != null && s <= (dischargeLimit ?: 0.0) + 0.5
            else -> false
        }
        if (atEnd && abs(a) < QUIET_A) {
            if (quietSinceMs == 0L) quietSinceMs = nowMs
            if (nowMs - quietSinceMs >= QUIET_MS) autoDone = true
        } else {
            quietSinceMs = 0L
        }
    }

    // ------------------------------------------------------------ результати

    /** Виміряна корисна ємність — енергія, віддана батареєю від 100 до 0%. */
    val measuredWh: Double? get() = if (phase == CapPhase.DONE && discharge.wh > 0) discharge.wh else null

    /** Якщо етап розряду покрив не весь діапазон SOC — оцінка ємності на повні 100%. */
    val extrapolatedWh: Double?
        get() {
            val span = discharge.socSpan ?: return null
            if (span < 50 || span >= 97) return null
            return discharge.wh * 100.0 / span
        }

    val sohMeasured: Double? get() {
        val m = measuredWh ?: return null
        val d = designWh ?: return null
        return m * 100.0 / d
    }

    /** Ефективність циклу «заряд → розряд» на клемах батареї. */
    val roundTrip: Double? get() = if (charge.wh > 0 && discharge.wh > 0) discharge.wh * 100.0 / charge.wh else null

    /** Застереження до точності тесту. */
    fun warnings(): List<String> {
        val out = ArrayList<String>()
        chargeLimit?.let { if (it < 99.5) out.add("Ліміт заряду ${Analyzer.fmt0(it)}% — станція не зарядиться до 100%. Виставте 100% у застосунку EcoFlow перед тестом.") }
        dischargeLimit?.let { if (it > 0.5) out.add("Нижній ліміт розряду ${Analyzer.fmt0(it)}% — станція зупиниться раніше нуля. Виставте 0% у застосунку EcoFlow.") }
        charge.socStart?.let { if (phase != CapPhase.IDLE && it > 5) out.add("Заряд почався з ${Analyzer.fmt0(it)}%, а не з 0% — етап 1 покаже менше енергії, ніж уміщує батарея.") }
        if (phase == CapPhase.DONE) {
            discharge.socStart?.let { if (it < 95) out.add("Розряд почався з ${Analyzer.fmt0(it)}%, а не зі 100% — результат неповний.") }
            discharge.socEnd?.let { if (it > 5) out.add("Розряд закінчився на ${Analyzer.fmt0(it)}%, а не на 0% — результат неповний.") }
        }
        for ((name, st) in listOf("заряду" to charge, "розряду" to discharge)) {
            if (st.gapMs > 5 * 60_000) out.add("Під час $name зв'язок пропадав разом на ${st.gapMs / 60_000} хв — цей час не врахований, результат занижений.")
        }
        return out
    }

    fun summaryText(model: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.appendLine("SAVOSH SERVICE — тест реальної ємності")
        sb.appendLine("Дата: $dateText")
        sb.appendLine("Станція: ${model ?: "?"} · SN $sn")
        sb.appendLine()
        sb.appendLine("Етап 1 — заряд: ${stageLine(charge)}")
        if (phase == CapPhase.DONE || phase == CapPhase.DISCHARGE) sb.appendLine("Етап 2 — розряд: ${stageLine(discharge)}")
        measuredWh?.let { m ->
            sb.appendLine()
            sb.append("ВИМІРЯНА ЄМНІСТЬ: ≈${Analyzer.fmt0(m)} Вт·год")
            designWh?.let { sb.append(" з ${Analyzer.fmt0(it)} номіналу BMS") }
            sohMeasured?.let { sb.append(" (${Analyzer.fmt0(it)}%)") }
            sb.appendLine()
            passportWh?.let { sb.appendLine("За паспортом: ${Analyzer.fmt0(it)} Вт·год") }
            extrapolatedWh?.let { sb.appendLine("Оцінка на повні 100%: ≈${Analyzer.fmt0(it)} Вт·год") }
            roundTrip?.let { sb.appendLine("Ефективність циклу (віддано / прийнято): ${Analyzer.fmt0(it)}%") }
        }
        val w = warnings()
        if (w.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Застереження:")
            for (x in w) sb.appendLine("  • $x")
        }
        sb.appendLine()
        sb.appendLine("Енергію пораховано за напругою й струмом BMS; точність близько ±3–5%.")
        return sb.toString()
    }

    private fun stageLine(st: CapStage): String {
        val socTxt = "${st.socStart?.let { Analyzer.fmt0(it) } ?: "?"}% → ${(st.socEnd ?: soc)?.let { Analyzer.fmt0(it) } ?: "?"}%"
        val dur = durationText(if (st.endMs > 0) st.durationMs else if (st.startMs > 0) lastDataMs - st.startMs else 0)
        val station = if (st.stationWh > 0) ", з боку станції ≈${Analyzer.fmt0(st.stationWh)} Вт·год" else ""
        return "≈${Analyzer.fmt0(st.wh)} Вт·год (${Analyzer.fmt1(st.ah)} А·год), $socTxt, $dur$station"
    }

    companion object {
        /** Довший проміжок без даних не інтегруємо — вважаємо, що зв'язок пропадав. */
        const val MAX_GAP_MS = 120_000L
        const val QUIET_A = 0.5
        const val QUIET_MS = 120_000L

        fun durationText(ms: Long): String {
            val m = ms / 60_000
            return if (m >= 60) "${m / 60} год ${m % 60} хв" else "$m хв"
        }

        private fun num(p: Map<String, Any?>, vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { Analyzer.numOf(p[it]) }
    }
}
