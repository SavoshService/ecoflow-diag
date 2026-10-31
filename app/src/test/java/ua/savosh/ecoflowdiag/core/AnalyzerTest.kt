package ua.savosh.ecoflowdiag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Висновки аналізатора на типових даних станцій (ключі — як у реальних повідомленнях EcoFlow).
 * Якщо логіка «з'їде», збірка в GitHub зупиниться ще до підпису й релізу.
 */
class AnalyzerTest {

    private val deltaProSn = "DCGBZ5SGB240204"

    /** Напруги груп DELTA Pro зі скріна користувача: 99%, середня 3.327 В, розкид 5 мВ. */
    private val screenshotCells = listOf<Long>(3326, 3327, 3327, 3327, 3326, 3326, 3329, 3328, 3329, 3328, 3325, 3328, 3327, 3327, 3324)

    /** Батарея 1-го покоління: [cells] — напруги груп, мВ; [extra] — додаткові поля без префікса. */
    private fun pack(
        prefix: String,
        cells: List<Long>,
        soc: Long = 99,
        soh: Long = 100,
        design: Long = 80_000,
        full: Long = 79_400,
        cycles: Long = 70,
        amp: Long = 0,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>(
            "$prefix.soc" to soc,
            "$prefix.soh" to soh,
            "$prefix.designCap" to design,
            "$prefix.fullCap" to full,
            "$prefix.remainCap" to full * soc / 100,
            "$prefix.cycles" to cycles,
            "$prefix.vol" to cells.sum(),
            "$prefix.amp" to amp,
            "$prefix.cellVol" to cells,
            "$prefix.minCellVol" to cells.min(),
            "$prefix.maxCellVol" to cells.max(),
            "$prefix.maxCellTemp" to 25L,
            "$prefix.minCellTemp" to 23L,
        )
        for ((k, v) in extra) m["$prefix.$k"] = v
        return m
    }

    @Test
    fun deltaProFromComplaint_isExcellentWithoutRemarks() {
        val r = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells))
        val p = r.packs.single()
        assertEquals("Delta Pro", r.model)
        assertEquals(Chemistry.LFP, p.chemistry)
        assertEquals(15, p.seriesCells)
        assertEquals(3840.0, p.designWh!!, 0.5)
        assertEquals("Відмінно", p.health?.chip)
        assertEquals(Level.GOOD, p.level)
        assertEquals("Без зауважень", r.level.statusTitle)
        assertTrue(p.issues.isEmpty())
        // примітка про паспорт — довідкова (сіра) і зі знаком: номінал BMS БІЛЬШИЙ за паспорт
        val passport = p.findings.single { it.text.startsWith("Номінал за BMS") }
        assertEquals(Level.UNKNOWN, passport.level)
        assertTrue(passport.text, passport.text.contains("БІЛЬШЕ"))
        // плато LFP: чесне формулювання і нижчі пороги
        assertEquals(20, p.cellOkMv)
        assertTrue(p.findings.any { it.text.contains("явного розбалансу не видно") })
        assertTrue(r.summary, r.summary.contains("Відмінний стан батареї"))
    }

    @Test
    fun overchargedLfpGroup_staysLfp_andHealthIsNotAffected() {
        val cells = List(14) { 3500L } + 3780L
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", cells, amp = 2000)).packs.single()
        assertEquals(Chemistry.LFP, p.chemistry)
        assertEquals(3500, p.ratedCycles)
        assertEquals("Відмінно", p.health?.chip)
        assertEquals(Level.CRITICAL, p.balanceLevel)
        assertTrue(p.issues.any { it.level == Level.CRITICAL })
        assertEquals(Level.CRITICAL, p.level)
    }

    @Test
    fun deltaProExtraBatteryAtLowSoc_isLfp() {
        val eb = List(15) { 3250L }
        val params = pack("bmsMaster", screenshotCells) + pack("bmsSlave1", eb, soc = 20, soh = 98, full = 78_000, cycles = 40)
        val slave = Analyzer.analyze(deltaProSn, params).packs.single { !it.isMain }
        assertEquals(Chemistry.LFP, slave.chemistry)
        assertEquals(3500, slave.ratedCycles)
    }

    @Test
    fun ncmMidSoc_usesNormalThresholds() {
        val cells = List(13) { 3900L } + 3860L
        val p = Analyzer.analyze("TEST0000001", pack("bmsMaster", cells, soc = 60, soh = 90, design = 40_000, full = 36_000)).packs.single()
        assertEquals(Chemistry.NCM, p.chemistry)
        assertEquals(30, p.cellOkMv)
        assertEquals(Level.WARN, p.balanceLevel)
        assertEquals("Добре", p.health?.chip)
        assertTrue(p.verdict, p.verdict.contains("але помірний дисбаланс груп"))
        assertEquals(1, p.issues.size)
    }

    @Test
    fun healthScale_usesRoundedSoh() {
        assertEquals("Відмінно", Health.of(94.6)?.chip)
        assertEquals("Добре", Health.of(94.4)?.chip)
        assertEquals("Добре", Health.of(79.6)?.chip)
        assertEquals("Увага", Health.of(79.4)?.chip)
        assertEquals("Погано", Health.of(64.4)?.chip)
        assertEquals("Незадовільний", Health.of(50.0)?.report)
        assertNull(Health.of(null))
    }

    @Test
    fun unknownBmsCode_isRemark_notHealth() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, extra = mapOf("errCode" to 5L))).packs.single()
        assertEquals("Відмінно", p.health?.chip)
        assertEquals(Level.WARN, p.level)
        assertTrue(p.issues.any { it.short == "BMS передає незадокументований код 5" })
        assertTrue(p.verdict, p.verdict.startsWith("Відмінний стан батареї, але"))
    }

    @Test
    fun cyclesOverPassportWithGoodSoh_isOnlyANote() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, soh = 90, full = 72_000, cycles = 4000)).packs.single()
        assertEquals("Добре", p.health?.chip)
        val f = p.findings.single { it.text.contains("циклів із ~3500") }
        assertEquals(Level.UNKNOWN, f.level)
        assertTrue(f.text, f.text.contains("ще тримається"))
        assertTrue(p.issues.isEmpty())
    }

    @Test
    fun learnedCapacityAboveNominal_isCappedAt100() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, full = 82_000)).packs.single()
        assertEquals(100.0, p.sohEffective!!, 0.001)
        assertTrue(p.findings.any { it.level == Level.UNKNOWN && it.text.contains("трохи більша за номінал") })
    }

    @Test
    fun implausibleLearnedCapacity_isIgnored() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, full = 100_000)).packs.single()
        assertNull(p.sohCapacity)
        assertEquals(100.0, p.sohEffective!!, 0.001)
        assertTrue(p.findings.any { it.text.contains("так не буває") })
    }

    @Test
    fun mosBitsContradictingCurrent_doNotClaimDischargeCut() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, soc = 50, amp = -5000, extra = mapOf("mosState" to 1L))).packs.single()
        assertEquals(Level.UNKNOWN, p.mos?.level)
        assertTrue(p.issues.none { it.short == "BMS вимкнула розряд" })
    }

    @Test
    fun dischargeKeyOffWithoutCurrent_isRemark() {
        val p = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells, soc = 50, amp = 0, extra = mapOf("mosState" to 1L))).packs.single()
        assertEquals(Level.WARN, p.mos?.level)
        assertTrue(p.issues.any { it.short == "BMS вимкнула розряд" })
    }

    @Test
    fun reports_useSameScale() {
        val r = Analyzer.analyze(deltaProSn, pack("bmsMaster", screenshotCells))
        val client = Report.client(r, null, "31.10.2026")
        assertTrue(client, client.contains("Стан батареї: Відмінний"))
        val tech = Report.technical(r, null, "31.10.2026")
        assertTrue(tech, tech.contains("ПІДСУМОК: БЕЗ ЗАУВАЖЕНЬ"))
        assertTrue(tech, tech.contains("Оцінка батареї (за SOH): Відмінний"))
        assertTrue(tech, tech.contains("Зауважень немає"))
    }
}
