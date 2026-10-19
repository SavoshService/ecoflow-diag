package ua.savosh.ecoflowdiag.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.savosh.ecoflowdiag.core.Analyzer
import ua.savosh.ecoflowdiag.core.CapPhase
import ua.savosh.ecoflowdiag.core.CapacityTest
import ua.savosh.ecoflowdiag.core.DeviceCommand
import ua.savosh.ecoflowdiag.core.EcoflowCommands
import ua.savosh.ecoflowdiag.core.CodeEvent
import ua.savosh.ecoflowdiag.core.CodeHistory
import ua.savosh.ecoflowdiag.core.CodeTracker
import ua.savosh.ecoflowdiag.core.Gen3Decoder
import ua.savosh.ecoflowdiag.core.Generation
import ua.savosh.ecoflowdiag.core.MessageDecoder
import ua.savosh.ecoflowdiag.core.Models
import ua.savosh.ecoflowdiag.core.QuotaStore
import ua.savosh.ecoflowdiag.core.Report
import ua.savosh.ecoflowdiag.core.StationReport
import ua.savosh.ecoflowdiag.data.Prefs
import ua.savosh.ecoflowdiag.data.StationEntry
import ua.savosh.ecoflowdiag.net.ApiException
import ua.savosh.ecoflowdiag.net.EcoflowApi
import ua.savosh.ecoflowdiag.net.EcoflowAuth
import ua.savosh.ecoflowdiag.net.EcoflowMqtt
import ua.savosh.ecoflowdiag.net.MqttAuthException
import ua.savosh.ecoflowdiag.net.MqttCreds
import ua.savosh.ecoflowdiag.net.UpdateInfo
import ua.savosh.ecoflowdiag.net.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

enum class Screen { LOGIN, STATIONS, DIAG, RAW, LOG, FLASH, CAPTEST, CONTROL, FEEDBACK }

class LogLine(val time: String, val text: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var screen by mutableStateOf(Screen.LOGIN)
        private set

    // ---------------- вхід
    var email by mutableStateOf(prefs.email)
    var password by mutableStateOf(if (prefs.rememberPassword) prefs.password else "")
    var host by mutableStateOf(prefs.host)
    var remember by mutableStateOf(prefs.rememberPassword)
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var auth by mutableStateOf<EcoflowAuth?>(null)
        private set
    private var creds: MqttCreds? = null

    // ---------------- станції
    val stations = mutableStateListOf<StationEntry>()

    // ---------------- діагностика
    var diagSn by mutableStateOf<String?>(null)
        private set
    var diagStatus by mutableStateOf("")
        private set
    var diagError by mutableStateOf(false)
        private set
    var diagBusy by mutableStateOf(false)
        private set
    var diagConnected by mutableStateOf(false)
        private set
    var diagOnline by mutableStateOf<Boolean?>(null)
        private set
    var report by mutableStateOf<StationReport?>(null)
        private set
    var rawParams by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set
    var messageCount by mutableStateOf(0)
        private set
    var lastUpdateMs by mutableStateOf(0L)
        private set
    val log = mutableStateListOf<LogLine>()

    // ---------------- масове оновлення списку
    var refreshAllBusy by mutableStateOf(false)
        private set
    var refreshAllStatus by mutableStateOf("")
        private set
    private var refreshAllJob: Job? = null

    // ---------------- тест реальної ємності
    var capTest by mutableStateOf<CapacityTest?>(null)
        private set
    /** Лічильник змін тесту — щоб екран перемальовувався (сам CapacityTest не є Compose-станом). */
    var capTick by mutableStateOf(0)
        private set
    val capTestRunning: Boolean
        get() = capTest?.phase.let { it == CapPhase.CHARGE || it == CapPhase.DISCHARGE }

    // ---------------- керування станцією
    var controlSent by mutableStateOf<String?>(null)
        private set
    var controlReply by mutableStateOf<String?>(null)
        private set
    var controlBusy by mutableStateOf(false)
        private set

    /** Власна база кодів помилок ("bms:23" → примітка). */
    var codeNotes by mutableStateOf(prefs.loadCodeNotes())
        private set

    /** Історія змін кодів і захистів поточної станції (новіші першими). */
    var codeHistory by mutableStateOf<List<CodeEvent>>(emptyList())
        private set
    private var tracker: CodeTracker? = null
    private var lastCodeStateSaveMs = 0L

    // ---------------- оновлення застосунку
    var availableUpdate by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateDismissed by mutableStateOf(false)
        private set
    private var autoRefreshDone = false

    // ---------------- монітор заряд/розряд
    /** Історія потужності батареї (час, Вт): >0 заряд, <0 розряд. */
    var powerHistory by mutableStateOf<List<Pair<Long, Double>>>(emptyList())
        private set
    /** «Живий» режим: коли відкрито монітор/керування — опитуємо станцію частіше. */
    var liveMode by mutableStateOf(false)

    private val store = QuotaStore()
    private var mqtt: EcoflowMqtt? = null
    private var diagJob: Job? = null
    private val refreshPending = AtomicBoolean(false)
    private var lastStationSaveMs = 0L
    private var loggedNotes = 0
    private var rawLogged = 0
    private var pausedSn: String? = null

    init {
        stations.addAll(prefs.loadStations())
        viewModelScope.launch {
            val upd = withContext(Dispatchers.IO) { Updater.check() }
            if (upd != null) availableUpdate = upd
        }
        if (prefs.rememberPassword && prefs.email.isNotBlank() && prefs.password.isNotBlank()) {
            screen = Screen.STATIONS
            viewModelScope.launch {
                try {
                    ensureAuth()
                    maybeAutoRefresh()
                } catch (e: ApiException) {
                    loginError = e.message
                    screen = Screen.LOGIN
                }
            }
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    /** Раз за запуск: щойно є вхід і станції — автоматично опитати всі, щоб показати свіжі дані й online. */
    private fun maybeAutoRefresh() {
        if (autoRefreshDone || stations.isEmpty()) return
        autoRefreshDone = true
        refreshAllStations()
    }

    // =========================================================== вхід

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            loginError = "Введіть email і пароль від застосунку EcoFlow"
            return
        }
        loginError = null
        auth = null
        creds = null
        viewModelScope.launch {
            try {
                ensureAuth()
                screen = Screen.STATIONS
                maybeAutoRefresh()
            } catch (e: ApiException) {
                loginError = e.message
            }
        }
    }

    fun logout() {
        stopDiag()
        auth = null
        creds = null
        password = ""
        prefs.password = ""
        screen = Screen.LOGIN
    }

    private suspend fun ensureAuth(): EcoflowAuth {
        auth?.let { return it }
        loginBusy = true
        try {
            val e = email.trim()
            val p = password
            val h = host
            val a = withContext(Dispatchers.IO) { EcoflowApi(h).login(e, p) }
            auth = a
            prefs.email = e
            prefs.host = h
            prefs.rememberPassword = remember
            prefs.password = if (remember) p else ""
            addLog("Вхід виконано: ${a.userName ?: e} (userId ${a.userId}, сервер $h)")
            return a
        } finally {
            loginBusy = false
        }
    }

    private suspend fun ensureCreds(a: EcoflowAuth): MqttCreds {
        creds?.let { return it }
        val c = withContext(Dispatchers.IO) { EcoflowApi(a.host).certification(a) }
        creds = c
        addLog("Отримано доступ до MQTT: ${c.host}:${c.port} (${c.protocol})")
        return c
    }

    // =========================================================== станції

    /** Повертає текст помилки або null, якщо станцію додано. */
    fun addStation(snRaw: String, note: String): String? {
        val sn = Models.normalizeSn(snRaw)
        if (sn.length < 8) return "Серійний номер занадто короткий"
        if (stations.any { it.sn == sn }) return "Така станція вже є у списку"
        val entry = StationEntry(sn, note.trim(), Models.bySn(sn)?.name, null, null, null, null)
        stations.add(0, entry)
        prefs.saveStations(stations.toList())
        return null
    }

    fun updateNote(sn: String, note: String) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        stations[i] = stations[i].copy(note = note.trim())
        prefs.saveStations(stations.toList())
    }

    fun deleteStation(sn: String) {
        stations.removeAll { it.sn == sn }
        prefs.saveStations(stations.toList())
        prefs.deleteReport(sn)
        prefs.deleteHistory(sn)
    }

    fun stationNote(sn: String?): String? = stations.firstOrNull { it.sn == sn }?.note?.takeIf { it.isNotBlank() }

    fun savedReport(sn: String, technical: Boolean = false): String? = prefs.loadReport(sn, technical)

    // =========================================================== діагностика

    fun openDiag(sn: String) {
        pausedSn = null
        screen = Screen.DIAG
        startDiag(sn)
    }

    fun retryDiag() {
        diagSn?.let { startDiag(it, keepData = store.size() > 0) }
    }

    fun refreshNow() {
        val m = mqtt
        if (m == null || !m.isConnected()) return retryDiag()
        addLog("Ручний запит оновлення")
        viewModelScope.launch(Dispatchers.IO) { sendRequest(m, chooseFormat(0), logIt = true) }
    }

    private enum class Fmt { JSON, PROTO }

    private fun startDiag(sn: String, keepData: Boolean = false) {
        stopDiag()
        diagSn = sn
        if (!keepData) {
            store.clear()
            report = null
            rawParams = emptyMap()
            messageCount = 0
            lastUpdateMs = 0
            diagOnline = null
            powerHistory = emptyList()
        }
        if (!keepData || tracker == null) {
            val (states, lastCheck) = CodeHistory.statesFromJson(prefs.loadCodeState(sn))
            tracker = CodeTracker(states, lastCheck)
            codeHistory = CodeHistory.eventsFromJson(prefs.loadCodeHistory(sn))
            lastCodeStateSaveMs = 0L
        }
        diagError = false
        loggedNotes = 0
        rawLogged = 0
        addLog("──── Діагностика $sn ────")
        diagJob = viewModelScope.launch {
            diagBusy = report == null
            var m: EcoflowMqtt? = null
            try {
                diagStatus = "Вхід в акаунт EcoFlow…"
                val a = ensureAuth()
                var failures = 0
                var requests = 0
                var lastRequestMs = 0L
                while (isActive) {
                    // 1. З'єднання з хмарою (власне перепідключення, без автоматики Paho)
                    val cur = m
                    if (cur == null || !cur.isConnected()) {
                        if (cur != null) {
                            cur.close()
                            mqtt = null
                            diagConnected = false
                            diagStatus = "Зв'язок з хмарою втрачено, перепідключення…"
                        } else {
                            diagStatus = "Підключення до хмари EcoFlow…"
                        }
                        m = null
                        try {
                            val c = ensureCreds(a)
                            val nm = EcoflowMqtt(
                                creds = c,
                                userId = a.userId,
                                sn = sn,
                                onMessage = { topic, payload -> onMqttMessage(topic, payload) },
                                onLog = { addLog(it) },
                            )
                            withContext(Dispatchers.IO) { nm.connect() }
                            m = nm
                            mqtt = nm
                            diagConnected = true
                            failures = 0
                            lastRequestMs = 0L // одразу запитати дані
                        } catch (e: MqttAuthException) {
                            creds = null // наступна спроба отримає нові облікові дані
                            failures++
                            addLog(e.message ?: "MQTT: відмова в доступі")
                            if (failures >= 3) throw e
                        } catch (e: ApiException) {
                            failures++
                            addLog(e.message ?: "MQTT: помилка")
                            if (failures >= 6) throw e
                        }
                        if (m == null) {
                            delay(minOf(30000L, 3000L * failures))
                            continue
                        }
                    }
                    val conn = m ?: continue

                    // 2. Запит повного знімка
                    val now = System.currentTimeMillis()
                    val haveData = store.size() > 0
                    val interval = when {
                        haveData && capTestRunning -> 10000L
                        haveData && liveMode -> 8000L
                        haveData -> 30000L
                        store.online == false -> 15000L
                        requests < 2 -> 5000L
                        else -> 10000L
                    }
                    if (now - lastRequestMs >= interval) {
                        val fmt = chooseFormat(requests)
                        val logIt = requests < 4 || requests % 10 == 0
                        withContext(Dispatchers.IO) { sendRequest(conn, fmt, logIt) }
                        requests++
                        lastRequestMs = now
                    }

                    // 3. Стан для екрана
                    if (!haveData) {
                        when {
                            store.online == false -> {
                                diagBusy = false
                                diagError = true
                                diagStatus = OFFLINE_TEXT
                            }
                            requests >= 4 -> {
                                diagBusy = false
                                diagError = true
                                diagStatus = NO_DATA_TEXT
                            }
                            else -> diagStatus = "Запит даних у станції…"
                        }
                    }
                    delay(1000L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                diagBusy = false
                diagError = true
                diagStatus = e.message ?: "Помилка"
                addLog("Помилка: ${e.message}")
            } catch (e: Exception) {
                diagBusy = false
                diagError = true
                diagStatus = "Помилка: ${e.message ?: e.javaClass.simpleName}"
                addLog("Помилка: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                m?.close()
            }
        }
    }

    /** Формат запиту: для відомої моделі — лише її формат, для невідомої — по черзі. */
    private fun chooseFormat(attempt: Int): Fmt {
        val gen = QuotaStore.detectGeneration(store.snapshot().keys).takeIf { it != Generation.UNKNOWN }
            ?: diagSn?.let { Models.bySn(it)?.generation }
        return when (gen) {
            Generation.GEN3 -> Fmt.PROTO
            Generation.GEN1, Generation.GEN2 -> Fmt.JSON
            else -> if (attempt % 2 == 0) Fmt.JSON else Fmt.PROTO
        }
    }

    private fun sendRequest(m: EcoflowMqtt, fmt: Fmt, logIt: Boolean) {
        val seq = 999900000 + Random.nextInt(10000, 99999)
        val payload = when (fmt) {
            Fmt.JSON -> MessageDecoder.buildJsonQuotaRequest(seq)
            Fmt.PROTO -> Gen3Decoder.buildGetAllRequest(seq)
        }
        if (logIt) addLog("→ запит усіх даних (${if (fmt == Fmt.JSON) "JSON latestQuotas" else "protobuf get-all"})")
        m.publish(m.getTopic, payload)
    }

    private fun onMqttMessage(topic: String, payload: ByteArray) {
        if (topic.contains("set_reply")) {
            val txt = payload.toString(Charsets.UTF_8)
            viewModelScope.launch(Dispatchers.Main) {
                controlReply = txt
                controlBusy = false
                addLog("← відповідь на команду: ${txt.take(200)}")
            }
            return
        }
        if (rawLogged < 3) {
            rawLogged++
            addLog("← сирі дані [${topic.substringAfterLast('/')}] ${preview(payload)}")
        }
        val msg = MessageDecoder.decode(topic, payload) ?: return
        store.apply(msg)
        if (msg.note != null && loggedNotes < 25) {
            loggedNotes++
            addLog("← ${msg.note}")
        }
        if (!refreshPending.getAndSet(true)) {
            viewModelScope.launch(Dispatchers.Main) {
                delay(400)
                refreshPending.set(false)
                recompute()
            }
        }
    }

    private fun preview(payload: ByteArray): String {
        if (Gen3Decoder.looksLikeJson(payload)) {
            val t = payload.toString(Charsets.UTF_8).replace(Regex("\\s+"), " ")
            return if (t.length > 300) t.take(300) + "…" else t
        }
        val hex = payload.take(48).joinToString("") { "%02x".format(it) }
        return "двійкові ${payload.size} байт: $hex${if (payload.size > 48) "…" else ""}"
    }

    private fun recompute() {
        val sn = diagSn ?: return
        val snap = store.snapshot()
        rawParams = snap
        messageCount = store.messages
        lastUpdateMs = store.lastUpdateMs
        diagOnline = store.online
        if (snap.isEmpty()) return
        val r = Analyzer.analyze(sn, snap, codeNotes)
        report = r
        run {
            val packs = r.packs.filter { it.volMv != null && it.ampMa != null }
            if (packs.isNotEmpty()) {
                val w = packs.sumOf { it.volMv!! * it.ampMa!! / 1_000_000.0 }
                powerHistory = (powerHistory + (System.currentTimeMillis() to w)).takeLast(300)
            }
        }
        diagBusy = false
        diagError = false
        diagStatus = "Дані отримано: ${snap.size} параметрів"
        val now = System.currentTimeMillis()
        trackCodes(sn, r, now)
        capTest?.takeIf { it.sn == sn }?.let { t ->
            t.feed(now, r, snap)
            capTick++
        }
        if (r.packs.isNotEmpty() && now - lastStationSaveMs > 10000) {
            lastStationSaveMs = now
            saveResult(sn, r)
        }
    }

    /** Порівнює коди з попереднім станом і дописує зміни в історію станції. */
    private fun trackCodes(sn: String, r: StationReport, now: Long) {
        val tr = tracker ?: return
        val events = tr.update(r, now)
        if (events.isNotEmpty()) {
            codeHistory = (events.reversed() + codeHistory).take(CodeHistory.MAX_EVENTS)
            prefs.saveCodeHistory(sn, CodeHistory.eventsToJson(codeHistory))
            for (e in events) addLog("Код: ${CodeHistory.line(e)}")
        }
        // Стан і час "останньої перевірки" оновлюємо при зміні і раз на 30 с — від нього рахується "між перевірками".
        if (tr.dirty || now - lastCodeStateSaveMs > 30000) {
            prefs.saveCodeState(sn, CodeHistory.statesToJson(tr.knownStates(), now))
            tr.markSaved()
            lastCodeStateSaveMs = now
        }
    }

    fun clearCodeHistory() {
        val sn = diagSn ?: return
        codeHistory = emptyList()
        prefs.saveCodeHistory(sn, "[]")
        addLog("Історію кодів станції $sn очищено")
    }

    private fun saveResult(sn: String, r: StationReport) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        val worstSoh = r.packs.mapNotNull { it.sohEffective }.minOrNull()
        val soc = r.packs.firstOrNull { it.isMain }?.soc ?: r.packs.firstOrNull()?.soc
        stations[i] = stations[i].copy(
            model = r.model ?: stations[i].model,
            lastSoh = worstSoh,
            lastLevel = r.level.name,
            lastSummary = r.summary,
            lastSoc = soc ?: stations[i].lastSoc,
            online = true,
            lastCheckMs = System.currentTimeMillis(),
        )
        prefs.saveStations(stations.toList())
        val date = dateText()
        prefs.saveReport(sn, Report.client(r, stations[i].note, date), technical = false)
        prefs.saveReport(sn, Report.technical(r, stations[i].note, date, historyFor(sn)), technical = true)
    }

    // =========================================================== масове оновлення

    private class QuickResult(val report: StationReport?, val online: Boolean?)

    /** Опитує всі станції зі списку по черзі й оновлює їхні картки (заряд, SOH, online/offline). */
    fun refreshAllStations() {
        if (refreshAllBusy) return
        if (stations.isEmpty()) return
        stopDiag()
        refreshAllJob = viewModelScope.launch {
            refreshAllBusy = true
            try {
                diagStatus = ""
                val a = ensureAuth()
                val list = stations.toList()
                for ((idx, st) in list.withIndex()) {
                    if (!isActive) break
                    refreshAllStatus = "Опитую ${idx + 1}/${list.size}: ${st.sn}"
                    val res = try {
                        fetchOne(a, st.sn)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        QuickResult(null, null)
                    }
                    applyQuick(st.sn, res)
                }
                val online = stations.count { it.online == true }
                refreshAllStatus = "Готово: онлайн $online з ${list.size}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                refreshAllStatus = e.message ?: "Помилка входу"
            } catch (e: Exception) {
                refreshAllStatus = "Помилка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                refreshAllBusy = false
            }
        }
    }

    fun stopRefreshAll() {
        refreshAllJob?.cancel()
        refreshAllJob = null
        refreshAllBusy = false
    }

    /** Один короткий сеанс: під'єднатися, запитати знімок, зачекати до ~9 с, проаналізувати. */
    private suspend fun fetchOne(a: EcoflowAuth, sn: String): QuickResult {
        val localStore = QuotaStore()
        var m: EcoflowMqtt? = null
        try {
            val c = ensureCreds(a)
            val nm = EcoflowMqtt(
                creds = c,
                userId = a.userId,
                sn = sn,
                onMessage = { topic, payload -> MessageDecoder.decode(topic, payload)?.let { localStore.apply(it) } },
                onLog = {},
            )
            withContext(Dispatchers.IO) { nm.connect() }
            m = nm
            val gen = Models.bySn(sn)?.generation
            var waited = 0L
            var lastReq = -3000L
            while (waited < 9000L) {
                if (waited - lastReq >= 3000L) {
                    val fmt = when (gen) {
                        Generation.GEN3 -> Fmt.PROTO
                        Generation.GEN1, Generation.GEN2 -> Fmt.JSON
                        else -> if ((waited / 3000) % 2 == 0L) Fmt.JSON else Fmt.PROTO
                    }
                    withContext(Dispatchers.IO) { sendRequest(nm, fmt, false) }
                    lastReq = waited
                }
                if (localStore.size() > 0 || localStore.online == false) break
                delay(500)
                waited += 500
            }
            val snap = localStore.snapshot()
            val report = if (snap.isNotEmpty()) Analyzer.analyze(sn, snap, codeNotes) else null
            val online = when {
                snap.isNotEmpty() -> true
                localStore.online == false -> false
                else -> null
            }
            return QuickResult(report, online)
        } catch (e: MqttAuthException) {
            creds = null
            return QuickResult(null, null)
        } finally {
            m?.close()
        }
    }

    private fun applyQuick(sn: String, res: QuickResult) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        val r = res.report
        val worstSoh = r?.packs?.mapNotNull { it.sohEffective }?.minOrNull()
        val soc = r?.packs?.firstOrNull { it.isMain }?.soc ?: r?.packs?.firstOrNull()?.soc
        stations[i] = stations[i].copy(
            model = r?.model ?: stations[i].model,
            lastSoh = worstSoh ?: stations[i].lastSoh,
            lastLevel = r?.level?.name ?: stations[i].lastLevel,
            lastSummary = r?.summary ?: stations[i].lastSummary,
            lastSoc = soc ?: stations[i].lastSoc,
            online = res.online,
            lastCheckMs = if (r != null) System.currentTimeMillis() else stations[i].lastCheckMs,
        )
        prefs.saveStations(stations.toList())
        if (r != null) {
            val date = dateText()
            prefs.saveReport(sn, Report.client(r, stations[i].note, date), technical = false)
            prefs.saveReport(sn, Report.technical(r, stations[i].note, date), technical = true)
        }
    }

    /** Звіт поточної діагностики: клієнтський (без службових полів) або технічний. */
    fun reportText(technical: Boolean): String? {
        val r = report ?: return null
        val note = stationNote(r.sn)
        return if (technical) Report.technical(r, note, dateText(), historyFor(r.sn)) else Report.client(r, note, dateText())
    }

    private fun historyFor(sn: String): List<CodeEvent> = if (sn == diagSn) codeHistory else emptyList()

    fun codeNote(key: String): String = codeNotes[key] ?: ""

    /** Зберегти примітку до коду (порожній текст — видалити) і одразу перерахувати звіт. */
    fun setCodeNote(key: String, text: String) {
        val t = text.trim()
        val m = LinkedHashMap(codeNotes)
        if (t.isEmpty()) m.remove(key) else m[key] = t
        codeNotes = m
        prefs.saveCodeNotes(m)
        addLog(if (t.isEmpty()) "Примітку до коду $key видалено" else "Примітка до коду $key: $t")
        recompute()
    }

    fun rawJson(): String = ua.savosh.ecoflowdiag.core.Json.stringify(rawParams)

    fun logText(): String = log.joinToString("\n") { "${it.time} ${it.text}" }

    fun stopDiag() {
        diagJob?.cancel()
        diagJob = null
        mqtt?.close()
        mqtt = null
        diagConnected = false
        diagBusy = false
    }

    // =========================================================== життєвий цикл

    /** Застосунок згорнуто: розриваємо з'єднання, щоб воно не "зависало" у фоні. */
    fun onAppBackground() {
        val sn = diagSn
        if (capTestRunning) {
            addLog("Застосунок згорнуто, але йде тест ємності — зв'язок не розриваємо")
            return
        }
        if (diagJob != null && sn != null && screen != Screen.LOGIN && screen != Screen.STATIONS) {
            pausedSn = sn
            stopDiag()
            addLog("Застосунок згорнуто — зв'язок призупинено")
        }
    }

    /** Застосунок знову на екрані: відновлюємо діагностику, зберігаючи вже отримані дані. */
    fun onAppForeground() {
        val sn = pausedSn ?: return
        pausedSn = null
        if (screen == Screen.DIAG || screen == Screen.RAW || screen == Screen.LOG) {
            addLog("Застосунок відкрито — відновлення зв'язку")
            startDiag(sn, keepData = true)
        }
    }

    // =========================================================== навігація

    fun openRaw() {
        screen = Screen.RAW
    }

    // =========================================================== тест ємності

    fun openCapTest() {
        val sn = diagSn ?: return
        if (capTest?.sn != sn) {
            capTest = CapacityTest(sn)
            report?.let { capTest!!.feed(System.currentTimeMillis(), it, rawParams) }
        }
        capTick++
        screen = Screen.CAPTEST
    }

    fun capStartCharge() = capAction { it.startCharge(System.currentTimeMillis()); addLog("Тест ємності: етап 1 (заряд) почато") }
    fun capFinishCharge() = capAction { it.finishCharge(System.currentTimeMillis()); addLog("Тест ємності: етап 1 завершено, ≈${Analyzer.fmt0(it.charge.wh)} Вт·год") }
    fun capStartDischarge() = capAction { it.startDischarge(System.currentTimeMillis()); addLog("Тест ємності: етап 2 (розряд) почато") }
    fun capFinishDischarge() = capAction { t ->
        t.finishDischarge(System.currentTimeMillis())
        addLog("Тест ємності завершено: ≈${Analyzer.fmt0(t.discharge.wh)} Вт·год")
        prefs.saveCapTest(t.sn, t.summaryText(report?.model, dateText()))
    }

    fun capReset() {
        val sn = diagSn ?: return
        capTest = CapacityTest(sn)
        report?.let { capTest!!.feed(System.currentTimeMillis(), it, rawParams) }
        capTick++
    }

    fun capResultText(): String? = capTest?.let { t ->
        if (t.phase == CapPhase.IDLE) null else t.summaryText(report?.model, dateText())
    }

    fun lastCapTest(sn: String): String? = prefs.loadCapTest(sn)

    private inline fun capAction(block: (CapacityTest) -> Unit) {
        val t = capTest ?: return
        block(t)
        capTick++
    }

    // =========================================================== керування станцією

    fun openFeedback() {
        screen = Screen.FEEDBACK
    }

    fun openControl() {
        if (diagSn == null) return
        controlSent = null
        controlReply = null
        controlBusy = false
        screen = Screen.CONTROL
    }

    fun sendCommand(cmd: DeviceCommand) {
        val m = mqtt
        if (m == null || !m.isConnected()) {
            controlReply = "Немає з'єднання зі станцією. Поверніться й зачекайте, поки діагностика під'єднається."
            return
        }
        controlSent = "${cmd.title}\n${cmd.json}"
        controlReply = null
        controlBusy = true
        addLog("→ команда: ${cmd.title} ${cmd.json}")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { m.publish(m.setTopic, cmd.json.toByteArray(Charsets.UTF_8)) }
            if (!ok) {
                controlBusy = false
                controlReply = "Не вдалося надіслати команду (помилка зв'язку)."
                return@launch
            }
            delay(6000)
            if (controlBusy) {
                controlBusy = false
                if (controlReply == null) {
                    controlReply = "Станція не відповіла за 6 с. Можливо, цей формат команди не підходить для цієї моделі — напишіть розробнику, яка модель, і формат підправлять."
                }
            }
        }
    }

    fun openFlash() {
        screen = Screen.FLASH
    }

    fun openLog() {
        screen = Screen.LOG
    }

    /** true — подію "назад" оброблено. */
    fun back(): Boolean = when (screen) {
        Screen.FLASH -> {
            screen = Screen.STATIONS
            true
        }
        Screen.FEEDBACK -> {
            screen = Screen.STATIONS
            true
        }
        Screen.RAW, Screen.LOG, Screen.CAPTEST, Screen.CONTROL -> {
            screen = if (diagSn != null) Screen.DIAG else Screen.STATIONS
            true
        }
        Screen.DIAG -> if (capTestRunning) {
            // поки йде тест ємності, з'єднання не рвемо — повертаємось на екран тесту
            screen = Screen.CAPTEST
            true
        } else {
            diagSn?.let { sn -> report?.let { if (it.packs.isNotEmpty()) saveResult(sn, it) } }
            stopDiag()
            pausedSn = null
            screen = Screen.STATIONS
            true
        }
        else -> false
    }

    fun addLog(text: String) {
        val line = LogLine(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()), text)
        viewModelScope.launch(Dispatchers.Main) {
            log.add(line)
            while (log.size > 400) log.removeAt(0)
        }
    }

    fun dateText(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date())

    companion object {
        const val OFFLINE_TEXT =
            "Хмара EcoFlow каже, що станція з цим SN в цьому акаунті офлайн. Якщо станція точно в мережі по Wi-Fi, перевірте: " +
                "1) SN збігається символ у символ із застосунком EcoFlow (станція → ⚙ → Про пристрій) — легко сплутати 0/O, 8/B, 1/I, 5/S; " +
                "2) станція додана саме в той акаунт (той самий email), яким ви увійшли тут; " +
                "3) спробуйте при вході сервер «Європа». Дані з'являться автоматично, щойно хмара побачить станцію."
        const val NO_DATA_TEXT =
            "Немає даних від станції. Перевірте: станція онлайн через Wi-Fi, прив'язана саме до цього акаунта, " +
                "серійний номер правильний. Спроби тривають автоматично."
    }

    override fun onCleared() {
        stopDiag()
        super.onCleared()
    }
}
