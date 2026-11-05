package ua.savosh.ecoflowdiag.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.savosh.ecoflowdiag.net.SimFleet
import ua.savosh.ecoflowdiag.net.StationLink
import java.io.File
import java.util.Locale

/**
 * Обидві мови мають бути повними: новий текст без перекладу зупинить збірку ще до підпису.
 * Тексти в коді — українські ключі, англійський словник — core/I18nEn.kt.
 */
class I18nTest {

    private val root = File("src/main/java/ua/savosh/ecoflowdiag")
    private val cyrillic = Regex("[А-Яа-яІіЇїЄєҐґ]")

    /** Файли, де українські рядки законні: технічні винятки й повідомлення власнику в Telegram. */
    private val exempt = setOf("I18n.kt", "I18nEn.kt", "Json.kt", "TelegramSubmit.kt")

    @After
    fun back() {
        I18n.lang = AppLang.UK
    }

    private fun sources(): List<File> = root.walkTopDown().filter { it.extension == "kt" && it.name !in exempt }.toList()

    private fun unescape(s: String): String = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\")

    private val keyRegex = Regex("""\b(?:tr|tk)\(\s*"((?:[^"\\]|\\.)*)"""")

    private fun allKeys(): Set<String> = sources().flatMap { f -> keyRegex.findAll(f.readText()).map { unescape(it.groupValues[1]) }.toList() }.toSet()

    /** Прибирає коментарі, щоб не плутати їх із текстом інтерфейсу. */
    private fun stripComments(s: String): String = s
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { line ->
            // // усередині рядка (https://) не коментар: ріжемо лише поза лапками
            var inStr = false
            var i = 0
            var cut = line.length
            while (i < line.length - 1) {
                val c = line[i]
                if (c == '\\') { i += 2; continue }
                if (c == '"') inStr = !inStr
                if (!inStr && c == '/' && line[i + 1] == '/') { cut = i; break }
                i++
            }
            line.substring(0, cut)
        }

    @Test
    fun everyTextHasEnglishTranslation() {
        val missing = allKeys().filter { it !in En.map }
        assertTrue("Немає перекладу:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun noUkrainianTextOutsideTranslationCalls() {
        val found = ArrayList<String>()
        val str = Regex(""""(?:[^"\\\n]|\\.)*"""")
        for (f in sources()) {
            val text = stripComments(f.readText())
            for (m in str.findAll(text)) {
                if (!cyrillic.containsMatchIn(m.value)) continue
                val before = text.substring(maxOf(0, m.range.first - 12), m.range.first)
                if (!Regex("""\b(tr|tk)\(\s*$""").containsMatchIn(before)) found.add(f.name + ": " + m.value.take(70))
            }
        }
        assertTrue("Текст поза tr():\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun translationsKeepPlaceholdersAndHaveNoCyrillic() {
        val ph = Regex("""\{\d+}""")
        for ((k, v) in En.map) {
            assertEquals(k, ph.findAll(k).map { it.value }.toSet(), ph.findAll(v).map { it.value }.toSet())
            assertFalse("Кирилиця в перекладі: $v", cyrillic.containsMatchIn(v))
        }
    }

    /** Текст часто склеюється зі значенням: без пробілу виходило «By group, V:1:3.290». Гривня в англійській зникає навмисно. */
    @Test
    fun translationsKeepSpacesAtTheEdges() {
        for ((k, v) in En.map) {
            if (k == " ₴") continue
            assertEquals(k, k.takeWhile { it == ' ' }, v.takeWhile { it == ' ' })
            assertEquals(k, k.takeLastWhile { it == ' ' }, v.takeLastWhile { it == ' ' })
        }
    }

    @Test
    fun switchingLanguageChangesNamesEverywhere() {
        assertEquals("Добре", Level.GOOD.title)
        assertEquals("В черзі", OrderStatus.RED.label)
        I18n.lang = AppLang.EN
        assertEquals("Good", Level.GOOD.title)
        assertEquals("Queued", OrderStatus.RED.label)
        assertEquals("Phone call", OrderChannel.CALL.label)
        assertEquals("not determined yet", Generation.UNKNOWN.title)
        assertEquals("LiFePO4 (lithium iron phosphate)", Chemistry.LFP.title)
        assertEquals("Excellent", Health.of(99.0)?.chip)
        assertEquals("General", KnowledgeSeed.OFFICIAL.let { tr("Загальне") })
        assertFalse(cyrillic.containsMatchIn(KnowledgeSeed.OFFICIAL.first().body))
        assertFalse(cyrillic.containsMatchIn(EcoflowCodes.GENERAL_HELP))
        assertFalse(cyrillic.containsMatchIn(Changelog.ENTRIES.first().changes.first()))
        I18n.lang = AppLang.UK
        assertEquals("Добре", Level.GOOD.title)
    }

    @Test
    fun formatPutsValuesInPlace() {
        assertEquals("a 1 b 2", I18n.format("a {0} b {1}", arrayOf(1, 2)))
        assertEquals("x x", I18n.format("{0} {0}", arrayOf("x")))
        assertEquals("{5} left", I18n.format("{5} left", arrayOf(1)))
        I18n.lang = AppLang.EN
        assertEquals("Charge limit 80%", tr("Ліміт заряду {0}%", 80))
    }

    @Test
    fun wholeReportsAreFullyEnglish_forEverySeries() {
        for (c in SimFleet.series) {
            val data = c.station.quota ?: MessageDecoder.decode(c.station.dataTopic, c.station.proto!!.invoke())!!.params
            val (tech, client) = I18n.with(AppLang.EN) {
                val r = Analyzer.analyze(c.station.sn, data)
                Report.technical(r, null, "05.11.2026") to Report.client(r, null, "05.11.2026")
            }
            assertFalse(c.title + "\n" + tech, cyrillic.containsMatchIn(tech))
            assertFalse(c.title + "\n" + client, cyrillic.containsMatchIn(client))
        }
    }

    @Test
    fun allOrdinaryFindingsAreEnglish() {
        // Рядок із розбалансом, кодами, ключами й високою температурою — щоб пройти якомога більше гілок аналізатора.
        val p = linkedMapOf<String, Any?>(
            "bmsMaster.soc" to 50L, "bmsMaster.soh" to 60L, "bmsMaster.designCap" to 40_000L, "bmsMaster.fullCap" to 60_000L,
            "bmsMaster.cycles" to 2000L, "bmsMaster.vol" to 50_000L, "bmsMaster.amp" to -6000L,
            "bmsMaster.minCellVol" to 3000L, "bmsMaster.maxCellVol" to 3900L, "bmsMaster.maxCellTemp" to 60L, "bmsMaster.minCellTemp" to -5L,
            "bmsMaster.maxMosTemp" to 80L, "bmsMaster.errCode" to 5L, "bmsMaster.allErrCode" to 96L, "bmsMaster.bmsFault" to 3L,
            "bmsMaster.bqSysStatReg" to 133L, "bmsMaster.mosState" to 1L, "bmsMaster.waterInFlag" to 1L, "bmsMaster.bmsAlarmState1" to 4L,
            "bmsMaster.packSn" to "000000", "bmsMaster.hwVersion" to "V0.0.0", "bmsMaster.accuDsgEnergy" to 900_000L,
            "ems.emsIsNormalFlag" to 0L, "ems.bms0Online" to 1L, "ems.chgCmd" to 0L, "ems.dsgCmd" to 0L, "ems.bmsWarningState" to 1L,
            "pd.iconBmsErrState" to 1L, "pd.errCode" to 9L, "inv.errCode" to 7L, "mppt.faultCode" to 8L,
            "inv.inputWatts" to 100L, "inv.outputWatts" to 100L, "pd.invUsedTime" to 100_000L, "pd.chgPowerAC" to 5000L, "pd.dsgPowerAC" to 5000L,
            "pd.relaySwitchCnt" to 5L, "pd.sysVer" to 16909060L,
        )
        val tech = I18n.with(AppLang.EN) { Report.technical(Analyzer.analyze("DAABZ5ZE1000001", p), null, "d") }
        assertFalse(tech, cyrillic.containsMatchIn(tech))
    }

    @Test
    fun checksAndLogsAreEnglish() {
        val t = CapacityTest("SN")
        val b = BalanceCheck("SN")
        val p = mapOf("bms_bmsStatus.soc" to 90L, "bms_bmsStatus.designCap" to 20000L, "bms_bmsStatus.vol" to 55000L, "bms_bmsStatus.amp" to 5000L, "bms_bmsStatus.minCellVol" to 3430L, "bms_bmsStatus.maxCellVol" to 3500L, "bms_emsStatus.maxChargeSoc" to 80L, "bms_emsStatus.minDsgSoc" to 10L)
        val r = Analyzer.analyze("R331ZEB4ZE100001", p)
        t.feed(1000, r, p)
        t.startCharge(1000)
        t.feed(2000, r, p)
        t.finishCharge(3000)
        t.startDischarge(4000)
        t.feed(5000, r, p)
        t.finishDischarge(6000)
        b.start(1000)
        b.feed(2000, r, p)
        b.finish(3000)
        I18n.with(AppLang.EN) {
            for (text in listOf(t.summaryText("X", "d"), b.summaryText("X", "d"), b.shortResult()) + t.warnings() + b.warnings()) {
                assertFalse(text, cyrillic.containsMatchIn(text))
            }
        }
    }

    // ---------------------------------------------------------------- збережені тексти

    @Test
    fun storedTextIsTranslatedOnShow() {
        val r = Analyzer.analyze("R331ZEB4ZE100001", linkedMapOf("bms_bmsStatus.soc" to 50L, "bms_bmsStatus.soh" to 95L, "bms_bmsStatus.designCap" to 20000L, "bms_bmsStatus.vol" to 52640L, "bms_bmsStatus.minCellVol" to 3290L, "bms_bmsStatus.maxCellVol" to 3296L))
        val uk = r.summary
        assertTrue(uk, cyrillic.containsMatchIn(uk))
        I18n.lang = AppLang.EN
        assertFalse(localize(uk), cyrillic.containsMatchIn(localize(uk)))
        assertEquals("Unknown", localize("Невідомо"))
        assertEquals("General", localize("Загальне"))
        assertEquals("spread 12 mV", localize("розкид 12 мВ"))
        assertEquals("charge off, discharge on (mosState = 2)", localize("заряд вимк., розряд увімк. (mosState = 2)"))
        // Текст майстра лишається як написаний
        assertEquals("Клієнт Петро", localize("Клієнт Петро"))
        I18n.lang = AppLang.UK
        assertEquals(uk, localize(uk))
    }

    @Test
    fun chosenValueIsStoredInUkrainian() {
        assertEquals("Загальне", I18n.canonical("General"))
        assertEquals("Моя тема", I18n.canonical("Моя тема"))
    }

    @Test
    fun pluralsFollowTheCurrentLanguage() {
        val a = LogEntry(0, StationLog.DIAG, soh = 90.0, cycles = 21)
        assertTrue(StationLog.line(a), StationLog.line(a).contains("21 цикл"))
        I18n.lang = AppLang.EN
        assertTrue(StationLog.line(a), StationLog.line(a).contains("21 cycles"))
        val one = LogEntry(0, StationLog.DIAG, soh = 90.0, cycles = 1)
        assertTrue(StationLog.line(one), StationLog.line(one).contains("1 cycle"))
    }

    @Test
    fun defaultLanguageFollowsThePhone() {
        assertEquals(AppLang.UK, AppLang.forSystem(Locale("uk")))
        assertEquals(AppLang.UK, AppLang.forSystem(Locale("ru")))
        assertEquals(AppLang.EN, AppLang.forSystem(Locale("de")))
        assertEquals(AppLang.EN, AppLang.forSystem(Locale.US))
    }

    @Test
    fun offlineTextsAreTranslatedOnDemand() {
        I18n.lang = AppLang.EN
        assertFalse(cyrillic.containsMatchIn(StationLink.OFFLINE_TEXT))
        assertFalse(cyrillic.containsMatchIn(StationLink.NO_DATA_TEXT))
    }
}
