package ua.savosh.ecoflowdiag.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import ua.savosh.ecoflowdiag.core.Analyzer
import ua.savosh.ecoflowdiag.core.Generation
import ua.savosh.ecoflowdiag.core.MessageDecoder
import ua.savosh.ecoflowdiag.core.QuotaStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * Застосунок проти імітатора хмари: справжні TLS, HTTP і MQTT на локальній машині.
 * Перевіряє весь шлях для кожної серії станцій і збої, на які скаржились люди.
 */
class StationLinkTest {

    companion object {
        private lateinit var tls: SSLContext

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val dir: File = Files.createTempDirectory("fake-cloud").toFile()
            tls = FakeTls.context(FakeTls.keystoreFile(dir))
            // Застосунок довіряє лише системним сертифікатам; у тесті «система» довіряє імітатору.
            SSLContext.setDefault(tls)
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
        }

        /** Ті самі правила, що в застосунку, але в 20–50 разів швидше. */
        val FAST = LinkTimings(
            tickMs = 20, firstRequestMs = 150, nextRequestMs = 300, normalRequestMs = 600, liveRequestMs = 200,
            testRequestMs = 200, offlineRequestMs = 300, noDataNoticeMs = 4000, offlineNoticeMs = 3000,
            tryOtherServerMs = 2000, stallMinMs = 2000, reconnectBaseMs = 60, reconnectMaxMs = 300,
            pollRequestMs = 150, pollMaxMs = 5000, pollSettleMs = 60,
        )
    }

    private class Status(val text: String, val busy: Boolean, val error: Boolean)

    private inner class Run(
        val sn: String,
        primary: String,
        hosts: List<String> = listOf(primary),
        password: String = FakeCloud.PASSWORD,
    ) {
        val store = QuotaStore()
        val statuses = CopyOnWriteArrayList<Status>()
        val logs = CopyOnWriteArrayList<String>()
        @Volatile var loginRejected: String? = null
        @Volatile var kept: String? = null
        @Volatile var client: EcoflowMqtt? = null
        private val messages = java.util.concurrent.atomic.AtomicInteger()
        val messageCount get() = messages.get()

        val session = CloudSession(
            email = { FakeCloud.EMAIL },
            password = { password },
            primaryHost = { primary },
            allHosts = hosts,
            onServerChanged = { kept = it },
            log = { logs.add(it) },
        ).also { it.resetServers() }

        private val listener = object : LinkListener {
            override fun onStatus(text: String, busy: Boolean, error: Boolean) {
                if (statuses.lastOrNull()?.text != text) statuses.add(Status(text, busy, error))
            }
            override fun onClient(client: EcoflowMqtt?) { this@Run.client = client }
            override fun onLoginRejected(message: String) { loginRejected = message }
            override fun log(text: String) { logs.add(text) }
        }

        val link = StationLink(
            sn = sn, store = store, cloud = session, listener = listener,
            onMessage = { t, p ->
                messages.incrementAndGet()
                MessageDecoder.decode(t, p)?.let { store.apply(it) }
            },
            timings = FAST,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        lateinit var job: Job

        fun start() = apply { job = scope.launch { link.run() } }
        fun stop() = runBlocking { job.cancelAndJoin() }
        fun poll() = runBlocking { link.pollOnce() }
        fun report() = Analyzer.analyze(sn, store.snapshot())
        fun debug() = "статуси: ${statuses.map { it.text.take(50) }}\nжурнал: ${logs.toList()}"

        fun await(what: String, timeoutMs: Long = 15_000, cond: () -> Boolean) {
            val end = System.currentTimeMillis() + timeoutMs
            while (!cond()) {
                if (System.currentTimeMillis() > end) fail("Не дочекались: $what\n${debug()}")
                Thread.sleep(10)
            }
        }
    }

    private fun cloud(vararg st: SimStation, accountHere: Boolean = true) = FakeCloud(tls, accountHere = accountHere).also { it.add(*st) }

    private fun gen2Station(sn: String = "R331ZEB4ZE100001", online: Boolean = true, cached: Boolean = false) =
        SimStation(sn, quota = SimData.gen2(16, 3290, 50, 20_000), online = online, cachedWhenOffline = cached)

    // ---------------------------------------------------------------- усі серії

    @Test
    fun everySeries_connectsAndShowsRightChemistryAndModel() {
        val fc = cloud(*SimFleet.series.map { it.station }.toTypedArray())
        fc.use {
            val problems = ArrayList<String>()
            println(String.format("%-38s | %-22s | %-3s | %-6s | %5s", "СЕРІЯ", "МОДЕЛЬ", "ХІМ", "ВТ·ГОД", "МС"))
            for (c in SimFleet.series) {
                val r = Run(c.station.sn, fc.httpHost)
                val t0 = System.currentTimeMillis()
                r.start()
                try {
                    r.await(c.title) { r.store.hasBattery() }
                    val ms = System.currentTimeMillis() - t0
                    Thread.sleep(60)
                    val rep = r.report()
                    val p = rep.packs.first()
                    println(String.format("%-38s | %-22s | %-3s | %-6s | %5d", c.title, rep.model ?: "—", p.chemistry.short, p.designWh?.let { Analyzer.fmt0(it) } ?: "—", ms))
                    if (rep.packs.size != c.packs) problems.add("${c.title}: батарей ${rep.packs.size}, очікувалось ${c.packs}")
                    if (rep.model != c.model) problems.add("${c.title}: модель ${rep.model}, очікувалось ${c.model}")
                    if (p.chemistry.short != c.chem) problems.add("${c.title}: хімія ${p.chemistry.short}, очікувалось ${c.chem}")
                    if (c.wh != null && kotlin.math.abs((p.designWh ?: 0.0) - c.wh) > 2) problems.add("${c.title}: ${p.designWh} Вт·год, очікувалось ${c.wh}")
                    r.statuses.firstOrNull { it.error }?.let { problems.add("${c.title}: показано помилку «${it.text.take(60)}»") }
                } catch (e: AssertionError) {
                    problems.add(e.message ?: c.title)
                } finally {
                    r.stop()
                }
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
        }
    }

    @Test
    fun everySeries_listRefreshGetsBatteryInOneShortSession() {
        val fc = cloud(*SimFleet.series.map { it.station }.toTypedArray())
        fc.use {
            val problems = ArrayList<String>()
            for (c in SimFleet.series) {
                val r = Run(c.station.sn, fc.httpHost)
                r.poll()
                if (!r.store.hasBattery()) problems.add("${c.title}: немає даних батареї")
                if (r.store.online == false) problems.add("${c.title}: позначено офлайн")
            }
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
            Thread.sleep(300)
            assertEquals("усі короткі сеанси мають закрити з'єднання", 0, fc.conns.size)
        }
    }

    // ---------------------------------------------------------------- те, на що скаржились

    @Test
    fun protobufStation_jsonSaysOffline_isNotCalledOffline() {
        val st = SimStation("QX04ZEB5HX000014", proto = { SimData.gen3(16, 3290, 55, 20_000) }, jsonSaysOffline = true)
        cloud(st).use { fc ->
            val poll = Run(st.sn, fc.httpHost)
            poll.poll()
            assertTrue(poll.store.hasBattery())
            assertTrue("живі дані сильніші за відповідь на JSON", poll.store.online != false)

            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("дані") { r.store.hasBattery() }
                assertTrue(r.debug(), r.statuses.none { it.error })
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun firstMessageWithoutBattery_doesNotEndRefresh() {
        val partial = SimStation.typeCodePush("pdStatus", mapOf("soc" to 50L, "wattsOutSum" to 120L))
        val st = SimStation("R351ZEB4ZE100002", quota = SimData.gen2(16, 3290, 50, 40_000), replyDelayMs = 300, partialOnSubscribe = partial)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost)
            r.poll()
            val rep = r.report()
            assertTrue("список не повинен зупинятись на «Даних про батареї ще немає»", rep.packs.isNotEmpty())
            assertEquals("Delta 2 Max", rep.model)
        }
    }

    @Test
    fun slowStation_gotRequestAgainAndNoFalseAlarm() {
        val partial = SimStation.typeCodePush("pdStatus", mapOf("soc" to 50L))
        val st = SimStation("R351ZEB4ZE100002", quota = SimData.gen2(16, 3290, 50, 40_000), replyDelayMs = 500, partialOnSubscribe = partial)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("батарея") { r.store.hasBattery() }
                assertTrue(r.debug(), r.statuses.none { it.error })
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun reallyOffline_isReportedAfterEverythingTried_andKeepsTrying() {
        val st = gen2Station(online = false)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("повідомлення про офлайн") { r.statuses.any { it.text == StationLink.OFFLINE_TEXT } }
                assertEquals(false, r.store.online)
                // Станцію ввімкнули: дані приходять самі, без жодного натискання.
                st.online = true
                r.await("дані після ввімкнення") { r.store.hasBattery() }
                assertTrue(r.store.online == true)
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun offlineWithCachedData_isNotMarkedOnline() {
        val st = gen2Station(online = false, cached = true)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost)
            r.poll()
            assertTrue(r.store.hasBattery())
            assertEquals("застарілий знімок не робить станцію онлайн", false, r.store.online)
        }
    }

    @Test
    fun livePushAfterOfflineAnswer_marksOnline() {
        val push = SimStation.typeCodePush("bmsStatus", SimData.pack("x", 16, 3290, 50, 20_000).mapKeys { it.key.removePrefix("x.") })
        val st = SimStation("R331ZEB4ZE100020", quota = SimData.gen2(16, 3290, 50, 20_000), online = false)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("відповідь «офлайн»") { r.store.online == false }
                r.store.apply(MessageDecoder.decode(st.dataTopic, push)!!)
                assertEquals(true, r.store.online)
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun unknownSerial_cloudSaysOffline_messageOnlyAfterAllTried() {
        cloud().use { fc ->
            val r = Run("R331ZEB4ZE199999", fc.httpHost).start()
            try {
                r.await("текст про офлайн") { r.statuses.any { it.text == StationLink.OFFLINE_TEXT } }
                assertFalse(r.store.hasBattery())
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun brokerDeniesSubscription_explainsAndKeepsTrying() {
        val st = SimStation("R331ZEB4ZE100012", quota = SimData.gen2(16, 3290, 50, 20_000), denied = true)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("пояснення") { r.statuses.any { it.error && it.text.contains("цей акаунт") } }
            } finally {
                r.stop()
            }
        }
    }

    // ---------------------------------------------------------------- збої зв'язку

    @Test
    fun serverDownOnStart_recoversByItself() {
        val st = gen2Station()
        cloud(st).use { fc ->
            fc.httpDown.set(5)
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("помилка на екрані") { r.statuses.any { it.error } }
                r.await("дані після відновлення") { r.store.hasBattery() }
                assertTrue("вхід мав пройти автоматично", r.loginRejected == null)
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun wrongPassword_goesToLogin_noEndlessRetries() {
        val st = gen2Station()
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost, password = "wrong").start()
            r.await("відмова у вході") { r.loginRejected != null }
            assertTrue(r.loginRejected!!, r.loginRejected!!.contains("Невірний email або пароль"))
            Thread.sleep(200)
            assertEquals("після відмови не повинно бути нових спроб", 1, fc.logins.get())
            r.stop()
        }
    }

    @Test
    fun mqttPasswordChanged_getsNewAccessByItself() {
        val st = gen2Station()
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("перші дані") { r.store.hasBattery() }
                fc.rotateMqttPassword()
                fc.kickAll()
                val before = fc.certifications.get()
                r.await("новий доступ") { fc.certifications.get() > before && fc.conns.isNotEmpty() }
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun loginTokenExpired_logsInAgainByItself() {
        val st = gen2Station()
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("перші дані") { r.store.hasBattery() }
                val logins = fc.logins.get()
                fc.expireTokens()
                fc.rotateMqttPassword()
                fc.kickAll()
                r.await("повторний вхід") { fc.logins.get() > logins }
                r.await("з'єднання після входу") { fc.conns.isNotEmpty() }
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun brokerDropsConnection_reconnectsAndAsksAgain() {
        val st = gen2Station()
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("дані") { r.store.hasBattery() }
                val n = r.messageCount
                fc.kickAll()
                r.await("нове з'єднання") { fc.mqttConnects.get() >= 2 }
                r.await("нові дані") { r.messageCount > n }
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun stationGoesSilent_newConnectionBringsItBack() {
        val st = SimStation("R331ZEB4ZE100021", quota = SimData.gen2(16, 3290, 50, 20_000), stallAfterReplies = 1)
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            try {
                r.await("перші дані") { r.store.hasBattery() }
                val n = r.messageCount
                r.await("нове з'єднання після мовчанки", 20_000) { fc.mqttConnects.get() >= 2 && r.messageCount > n }
            } finally {
                r.stop()
            }
        }
    }

    @Test
    fun stationInOtherRegion_isFoundAndRemembered() {
        val st = gen2Station()
        val primary = cloud() // тут станції немає: хмара каже online=0
        val second = cloud(accountHere = false) // тут немає навіть акаунта
        val third = cloud(st)
        try {
            val r = Run(st.sn, primary.httpHost, listOf(primary.httpHost, second.httpHost, third.httpHost)).start()
            try {
                r.await("станція на третьому сервері") { r.store.hasBattery() }
                r.await("сервер запам'ятано") { r.kept == third.httpHost }
                assertTrue(r.debug(), r.statuses.none { it.text == StationLink.OFFLINE_TEXT })
            } finally {
                r.stop()
            }
        } finally {
            primary.close()
            second.close()
            third.close()
        }
    }

    @Test
    fun primaryServerUnreachable_triesOthers() {
        val st = gen2Station()
        val down = cloud()
        val ok = cloud(st)
        try {
            down.httpDown.set(1_000_000)
            val r = Run(st.sn, down.httpHost, listOf(down.httpHost, ok.httpHost)).start()
            try {
                r.await("станція через інший сервер") { r.store.hasBattery() }
                r.await("сервер запам'ятано") { r.kept == ok.httpHost }
            } finally {
                r.stop()
            }
        } finally {
            down.close()
            ok.close()
        }
    }

    @Test
    fun closingScreen_closesConnection_noParallelSessions() {
        val st = gen2Station()
        cloud(st).use { fc ->
            val r = Run(st.sn, fc.httpHost).start()
            r.await("дані") { r.store.hasBattery() }
            assertEquals(1, fc.conns.size)
            r.stop()
            val end = System.currentTimeMillis() + 5000
            while (fc.conns.isNotEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
            assertEquals("після виходу з екрана з'єднання не лишається", 0, fc.conns.size)
        }
    }

    @Test
    fun leavingWhileConnecting_leavesNothingBehind() {
        val st = gen2Station()
        cloud(st).use { fc ->
            // Людина відкриває станцію і одразу виходить: у різні моменти встановлення з'єднання.
            for (delayMs in listOf(0L, 5L, 20L, 50L, 100L, 200L, 300L, 450L, 700L)) {
                val r = Run(st.sn, fc.httpHost).start()
                Thread.sleep(delayMs)
                r.stop()
            }
            val end = System.currentTimeMillis() + 8000
            while (fc.conns.isNotEmpty() && System.currentTimeMillis() < end) Thread.sleep(50)
            assertEquals("жодне з'єднання не лишилось висіти", 0, fc.conns.size)
        }
    }

    // ---------------------------------------------------------------- декодер

    @Test
    fun protobufLength91or123_isNotTakenForJson() {
        // Перший блок завдовжки 91 чи 123 байти дає початок «\\n[» чи «\\n{»: схоже на JSON, але це protobuf.
        val samples = (8..16).flatMap { series -> (5000L..60000L step 1000L).map { SimData.gen3(series, 3290, 55, it) to it } }
        val tricky = samples.filter { (m, _) -> m[1].toInt() == '{'.code || m[1].toInt() == '['.code }
        assertTrue("у вибірці є такі довжини", tricky.isNotEmpty())
        for ((m, design) in tricky) {
            val d = MessageDecoder.decode("/app/device/property/SN", m)!!
            assertEquals("protobuf", d.kind)
            assertEquals(design, d.params["bmsPack1.designCap"])
        }
    }

    // ---------------------------------------------------------------- формат запиту

    @Test
    fun requestFormat_triesBothUntilBatteryKnown() {
        fun seq(known: Generation?, detected: Generation = Generation.UNKNOWN, battery: Boolean = false) =
            (0..5).map { StationLink.requestFormat(known, detected, battery, it).name.first() }.joinToString("")
        assertEquals("JPJPJP", seq(Generation.GEN2))
        assertEquals("PJPJPJ", seq(Generation.GEN3))
        assertEquals("JPJPJP", seq(null))
        assertEquals("PPPPPP", seq(null, Generation.GEN3, battery = true))
        assertEquals("JJJJJJ", seq(Generation.GEN3, Generation.GEN2, battery = true))
    }
}
