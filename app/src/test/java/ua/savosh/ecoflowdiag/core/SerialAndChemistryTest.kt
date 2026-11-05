package ua.savosh.ecoflowdiag.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Серійні номери, таблиця моделей, хімія за напругами, стан «онлайн» і команди керування. */
class SerialAndChemistryTest {

    // ---------------------------------------------------------------- серійний номер

    @Test
    fun cyrillicLookalikes_becomeLatin() {
        // С, В, Е, К, М, Н, О, Р, Т, Х, А, І — вигляд той самий, а для хмари це інший номер
        assertEquals("DCGBZ5SGB240204", Models.extractSn("DСGВZ5SGВ240204"))
        assertEquals("R331ZEB4ZE100001", Models.normalizeSn("r331zеb4zе100001"))
        assertTrue(Models.foreignLetters("DСGВZ5SGВ240204").isEmpty())
    }

    @Test
    fun lettersWithoutTwin_areReported() {
        assertEquals(listOf('Д', 'Ж'), Models.foreignLetters("ДЖ31ZEB4ZE100001"))
    }

    @Test
    fun label_url_spaces_areCleaned() {
        assertEquals("R331ZEB4ZE100001", Models.extractSn("SN: R331ZEB4ZE100001"))
        assertEquals("R331ZEB4ZE100001", Models.extractSn("S/N R331ZEB4ZE100001"))
        assertEquals("R331ZEB4ZE100001", Models.extractSn("SNR331ZEB4ZE100001"))
        assertEquals("R331ZEB4ZE100001", Models.extractSn("https://www.ecoflow.com/app?sn=R331ZEB4ZE100001&x=1"))
        assertEquals("R331ZEB4ZE100001", Models.extractSn("  r331 zeb4 ze10 0001 "))
        assertEquals("DAEBZ5KE9110449", Models.extractSn("DAEBZ5KE9110449"))
    }

    @Test
    fun unknownPrefix_isKeptAsIs() {
        assertEquals("QX01ZEB5HX000001", Models.extractSn("QX01ZEB5HX000001"))
    }

    // ---------------------------------------------------------------- таблиця моделей

    @Test
    fun modelsBySerial() {
        val cases = mapOf(
            "DCGBZ5SGB240204" to "Delta Pro", "DCABZ5ZE1000005" to "Delta Pro", "R511ZEB1" to "Delta Pro",
            "DAEBZ5KE9110449" to "Delta / Delta Max", "DAABZ5ZE1000001" to "Delta Max", "DBEBZ5ZE1000004" to "Delta Mini",
            "R331ZEB4ZE100001" to "Delta 2", "R351ZEB4ZE100002" to "Delta 2 Max", "D361ZEB5HX000001" to "Delta 3 1500",
            "R601ZCB5HX000001" to "River 2", "R602ZEB4VF000003" to "River Pro", "R611ZEB1AB123456" to "River 2 Max",
            "R621ZEB1AB123457" to "River 2 Pro", "R501ZEB4VF000004" to "River Mini", "R651ZEB5HX000010" to "River 3",
            "R631ZEB5HX000011" to "River 3 Plus", "P231ZEB5HX000001" to "Delta 3", "MR51ZEB5HX000008" to "Delta Pro 3",
            "Y711ZEB5HX000009" to "Delta Pro Ultra",
        )
        for ((sn, model) in cases) assertEquals(sn, model, Models.bySn(sn)?.name)
        assertNull(Models.bySn("QX01ZEB5HX000001"))
    }

    // ---------------------------------------------------------------- хімія

    private fun params(prefix: String, series: Int, mv: Long, soc: Long, design: Long, amp: Long = 0, top: Long = 5): Map<String, Any?> {
        val cells = List(series - 1) { mv } + (mv + top)
        return linkedMapOf(
            "$prefix.soc" to soc, "$prefix.soh" to 95L, "$prefix.designCap" to design, "$prefix.fullCap" to design * 95 / 100,
            "$prefix.cycles" to 100L, "$prefix.vol" to cells.sum(), "$prefix.amp" to amp,
            "$prefix.minCellVol" to cells.min(), "$prefix.maxCellVol" to cells.max(),
        )
    }

    private fun chem(sn: String, p: Map<String, Any?>) = Analyzer.analyze(sn, p).packs.first().chemistry

    @Test
    fun ncmCells_winOverSerialTable() {
        val r = Analyzer.analyze("R601ZEB4VF000001", params("bmsMaster", 8, 4150, 100, 20_000))
        val p = r.packs.single()
        assertEquals(Chemistry.NCM, p.chemistry)
        assertTrue(p.chemistryConfident)
        assertEquals("River (1-ше покоління)", r.model)
        assertEquals(576.0, p.designWh!!, 0.5)
        assertNull("паспорт River 2 сюди не підходить", p.passport)
        assertTrue(p.findings.any { it.level == Level.UNKNOWN && it.text.contains("Хімію визначено за напругами") })
        assertEquals(Chemistry.NCM, chem("R331ZEB4ZE100001", params("bms_bmsStatus", 16, 4100, 100, 20_000)))
    }

    @Test
    fun ncmAtMiddleCharge_isRecognizedWithoutLoad() {
        assertEquals(Chemistry.NCM, chem("R601ZEB4VF000002", params("bmsMaster", 8, 3620, 30, 20_000)))
    }

    @Test
    fun lfpStaysLfp() {
        // плато, кінець заряду під струмом, одна перезаряджена група, кінець заряду без струму
        assertEquals(Chemistry.LFP, chem("R331ZEB4ZE100001", params("bms_bmsStatus", 16, 3290, 50, 20_000)))
        assertEquals(Chemistry.LFP, chem("R331ZEB4ZE100001", params("bms_bmsStatus", 16, 3550, 95, 20_000, amp = 8000)))
        assertEquals(Chemistry.LFP, chem("DCGBZ5SGB240204", params("bmsMaster", 15, 3500, 100, 80_000, amp = 2000, top = 280)))
        assertEquals(Chemistry.LFP, chem("R351ZEB4ZE100002", params("bms_bmsStatus", 16, 3450, 100, 40_000)))
        // BMS занижує заряд: 60%, а групи вже на коліні, під струмом заряду — це все ще LFP
        assertEquals(Chemistry.LFP, chem("R331ZEB4ZE100001", params("bms_bmsStatus", 16, 3520, 60, 20_000, amp = 5000)))
    }

    @Test
    fun overchargedLfpGroup_onUnknownSerial_isNotNcm() {
        val p = params("bms_bmsStatus", 16, 3500, 100, 20_000, amp = 3000, top = 280)
        assertEquals(Chemistry.LFP, chem("QX01ZEB5HX000001", p))
    }

    @Test
    fun ncmStations_stayNcmAtAnyCharge() {
        for ((soc, mv) in listOf(100L to 4150L, 80L to 4000L, 60L to 3850L, 40L to 3700L, 20L to 3550L, 10L to 3450L, 5L to 3300L)) {
            assertEquals("soc=$soc", Chemistry.NCM, chem("DAEBZ5KE9110449", params("bmsMaster", 14, mv, soc, 40_000)))
        }
    }

    @Test
    fun rebuiltLfpPack_inNcmModel_isLfp() {
        assertEquals(Chemistry.LFP, chem("DBEBZ5ZE1000004", params("bmsMaster", 16, 3320, 80, 20_000)))
    }

    @Test
    fun deltaMaxWithoutFixedEnergy_noFalseWarningFor1600() {
        val p = Analyzer.analyze("DAABZ5ZE1000002", params("bmsMaster", 14, 3800, 60, 32_000)).packs.single()
        assertTrue(p.issues.none { it.text.contains("МЕНШЕ за паспортні") })
    }

    @Test
    fun emptyDuplicateMain_isDropped() {
        // Деякі River 2: bms_bmsStatus порожній, справжні дані в bmsMaster
        val p = params("bmsMaster", 8, 3290, 50, 10_000) + mapOf("bms_bmsStatus.soh" to 0L, "bms_bmsStatus.cycles" to 0L)
        val r = Analyzer.analyze("R601ZCB5HX000001", p)
        assertEquals(1, r.packs.size)
        assertEquals(10_000L, r.packs.single().designCapMah)
    }

    @Test
    fun chargeLimit_readFromEveryGeneration() {
        assertEquals(90.0, Analyzer.chargeLimit(mapOf("ems.maxChargeSoc" to 90L))!!, 0.0)
        assertEquals(80.0, Analyzer.chargeLimit(mapOf("bms_emsStatus.maxChargeSoc" to 80L))!!, 0.0)
        assertEquals(70.0, Analyzer.chargeLimit(mapOf("bmsMaster.maxChargeSoc" to 70L))!!, 0.0)
        assertEquals(60.0, Analyzer.chargeLimit(mapOf("inv.maxChargeSoc" to 60L))!!, 0.0)
    }

    // ---------------------------------------------------------------- онлайн і дані батареї

    private fun decode(json: String) = MessageDecoder.decode("/app/1/SN/thing/property/get_reply", json.toByteArray())!!

    @Test
    fun hasBattery_onlyWithBatteryKeys() {
        val s = QuotaStore()
        s.apply(decode("""{"typeCode":"pdStatus","params":{"soc":50,"wattsOutSum":10}}"""))
        assertTrue(s.size() > 0)
        assertFalse(s.hasBattery())
        s.apply(decode("""{"typeCode":"bmsStatus","params":{"soc":50,"designCap":20000}}"""))
        assertTrue(s.hasBattery())
    }

    @Test
    fun onlineFlag_followsLiveData() {
        val s = QuotaStore()
        s.apply(decode("""{"operateType":"latestQuotas","data":{"online":0,"quotaMap":{"bmsMaster.soc":50,"bmsMaster.soh":90}}}"""))
        assertEquals(false, s.online)
        assertTrue("останній знімок хмари зберігаємо", s.hasBattery())
        s.apply(decode("""{"params":{"pd.soc":51}}"""))
        assertEquals("живе повідомлення доводить, що станція онлайн", true, s.online)
    }

    // ---------------------------------------------------------------- команди

    private fun cmd(c: DeviceCommand) = Json.parseObject(c.json)!!
    private fun params(c: Map<String, Any?>) = c["params"] as Map<*, *>

    @Test
    fun acCommand_changesOnlyOutput() {
        val c = cmd(EcoflowCommands.acOut(true, "R331ZEB4ZE100001"))
        assertEquals(5L, c["moduleType"])
        assertEquals("acOutCfg", c["operateType"])
        assertNull(c["moduleSn"])
        val p = params(c)
        assertEquals(1L, p["enabled"])
        assertEquals("X-Boost не чіпаємо", 255L, p["xboost"])
        assertEquals("частоту не чіпаємо", 255L, p["out_freq"])
        assertEquals(-1L, p["out_voltage"])
    }

    @Test
    fun delta2Max_usesOwnModuleAndSerial() {
        val sn = "R351ZEB4ZE100002"
        val c = cmd(EcoflowCommands.acOut(false, sn))
        assertEquals(3L, c["moduleType"])
        assertEquals(sn, c["moduleSn"])
        assertEquals(sn, cmd(EcoflowCommands.maxChargeSoc(100, sn))["moduleSn"])
        assertEquals(sn, cmd(EcoflowCommands.minDischargeSoc(0, sn))["moduleSn"])
    }

    @Test
    fun dc12v_isCarOutputCommand() {
        val c = cmd(EcoflowCommands.dcOut(true))
        assertEquals(5L, c["moduleType"])
        assertEquals("mpptCar", c["operateType"])
    }
}
