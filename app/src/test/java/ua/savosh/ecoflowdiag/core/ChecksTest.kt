package ua.savosh.ecoflowdiag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тест ємності, перевірка балансу і журнал перевірок станції. */
class ChecksTest {

    private val sn = "DCGBZ5SGB240204"

    /** Знімок DELTA Pro з заданими напругами груп, зарядом і струмом. */
    private fun report(cells: List<Long>, soc: Double, ampMa: Long): Pair<StationReport, Map<String, Any?>> {
        val p = linkedMapOf<String, Any?>(
            "bmsMaster.soc" to soc,
            "bmsMaster.soh" to 100L,
            "bmsMaster.designCap" to 80_000L,
            "bmsMaster.fullCap" to 80_000L,
            "bmsMaster.cycles" to 10L,
            "bmsMaster.vol" to cells.sum(),
            "bmsMaster.amp" to ampMa,
            "bmsMaster.cellVol" to cells,
            "bmsMaster.minCellVol" to cells.min(),
            "bmsMaster.maxCellVol" to cells.max(),
            "ems.maxChargeSoc" to 100L,
        )
        return Analyzer.analyze(sn, p) to p
    }

    /** 15 груп по [base] мВ, група №7 вища на [high] мВ. */
    private fun cellsWith(base: Long, high: Long): List<Long> = List(15) { i -> if (i == 6) base + high else base }

    @Test
    fun capacityTest_sohByAmpHours() {
        val t = CapacityTest(sn)
        val cells = List(15) { 3300L }
        val t0 = 1_700_049_600_000L
        t.startDischarge(t0)
        for (i in 0..120) {
            val (r, p) = report(cells, 100.0 - i * (100.0 / 120), -40_000)
            t.feed(t0 + i * 60_000L, r, p)
        }
        t.finishDischarge(t0 + 120 * 60_000L)
        assertEquals(80.0, t.designAh!!, 0.001)
        assertEquals(80.0, t.discharge.ah, 0.5)
        assertEquals(100.0, t.sohMeasured!!, 0.7)
    }

    @Test
    fun balanceCheck_judgesOnlyAtTopOfCharge() {
        val b = BalanceCheck(sn)
        var time = 1_700_049_600_000L
        b.start(time)
        val steps = listOf(3330L to 3L, 3360L to 3L, 3400L to 4L, 3430L to 6L, 3440L to 10L, 3460L to 15L, 3480L to 20L, 3500L to 25L, 3520L to 22L)
        for ((i, s) in steps.withIndex()) {
            val (r, p) = report(cellsWith(s.first, s.second), 80.0 + i * 2, 5_000)
            time += 30_000L
            b.feed(time, r, p)
        }
        val top = b.worst()!!
        assertEquals(25L, top.deltaMv)
        assertEquals(7, top.highCell)
        assertEquals(Level.GOOD, BalanceCheck.verdict(top).level)
        // дозарядилась, струм ~0 понад 2 хв — перевірку можна завершувати
        repeat(4) {
            val (r, p) = report(cellsWith(3400, 5), 100.0, 0)
            time += 60_000L
            b.feed(time, r, p)
        }
        assertTrue(b.autoDone)
        b.finish(time)
        assertEquals(BalanceCheck.Phase.DONE, b.phase)
        assertTrue(b.shortResult(), b.shortResult().startsWith("25 мВ"))
    }

    @Test
    fun balanceCheck_withoutTopZone_givesNoVerdict() {
        val b = BalanceCheck(sn)
        b.start(0L)
        for (i in 0 until 5) {
            val (r, p) = report(cellsWith(3330L + i * 10, 4), 90.0 + i, 3_000)
            b.feed(i * 30_000L, r, p)
        }
        b.finish(200_000L)
        assertTrue(b.results.isEmpty())
        assertTrue(b.noZoneText(), b.noZoneText().contains("висновку немає"))
        assertEquals("зону висновку не досягнуто", b.shortResult())
    }

    @Test
    fun balanceCheck_strongImbalanceAtTop() {
        val b = BalanceCheck(sn)
        b.start(0L)
        val (r, p) = report(cellsWith(3450, 120), 98.0, 4_000)
        b.feed(30_000L, r, p)
        assertEquals(Level.BAD, BalanceCheck.verdict(b.worst()!!).level)
    }

    private val day = 86_400_000L
    private val t0 = 1_700_049_600_000L // 15.11.2023, полудень UTC

    @Test
    fun stationLog_oneDiagSnapshotPerDay() {
        var log = emptyList<LogEntry>()
        log = StationLog.add(log, LogEntry(t0, StationLog.DIAG, soh = 99.0, cycles = 70))
        log = StationLog.add(log, LogEntry(t0 + 60_000, StationLog.DIAG, soh = 98.0, cycles = 71))
        assertEquals(1, log.size)
        assertEquals(98.0, log.first().soh!!, 0.0)
        log = StationLog.add(log, LogEntry(t0 + 60_000, StationLog.BALANCE, deltaMv = 12, text = "12 мВ"))
        log = StationLog.add(log, LogEntry(t0 + 2 * day, StationLog.DIAG, soh = 97.0, cycles = 80))
        assertEquals(3, log.size)
        assertEquals(t0 + 2 * day, log.first().timeMs)
    }

    @Test
    fun stationLog_trendJudgesWearOnlyOverEnoughCycles() {
        val log = listOf(
            LogEntry(t0, StationLog.DIAG, soh = 100.0, cycles = 100),
            LogEntry(t0 + 200 * day, StationLog.DIAG, soh = 96.0, cycles = 400),
        )
        val t = StationLog.trend(log, Chemistry.LFP)!!
        assertTrue(t, t.contains("300 циклів"))
        assertTrue(t, t.contains("1.3% на 100 циклів"))
        assertTrue(t, t.contains("у межах типового для LFP"))
        val short = listOf(
            LogEntry(t0, StationLog.DIAG, soh = 100.0, cycles = 100),
            LogEntry(t0 + 10 * day, StationLog.DIAG, soh = 99.0, cycles = 110),
        )
        assertNull(StationLog.trend(short, Chemistry.LFP))
    }

    @Test
    fun stationLog_jsonRoundTrip() {
        val log = listOf(LogEntry(t0, StationLog.BALANCE, deltaMv = 12, soc = 99.0, text = "12 мВ наприкінці заряду — групи збалансовані"))
        val back = StationLog.fromJson(Json.parse(Json.stringify(StationLog.toJson(log))))
        assertEquals(1, back.size)
        assertEquals(12L, back[0].deltaMv)
        assertEquals(StationLog.BALANCE, back[0].kind)
        assertEquals("12 мВ наприкінці заряду — групи збалансовані", back[0].text)
        assertTrue(StationLog.line(back[0]).contains("перевірка балансу: 12 мВ"))
    }

    @Test
    fun lastCells_jsonRoundTrip() {
        val m = mapOf("bms_bmsStatus" to CellSnapshot(t0, List(16) { 3330L + it }))
        val back = CellSnapshot.fromJson(Json.parseObject(Json.stringify(CellSnapshot.toJson(m)))!!)
        assertEquals(16, back["bms_bmsStatus"]!!.cells.size)
        assertEquals(3345L, back["bms_bmsStatus"]!!.cells.last())
        assertEquals(t0, back["bms_bmsStatus"]!!.timeMs)
        assertTrue(CellSnapshot.fromJson(mapOf("x" to mapOf("t" to 1L, "v" to listOf(3300L)))).isEmpty())
    }
}
