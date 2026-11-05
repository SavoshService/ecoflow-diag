package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ua.savosh.ecoflowdiag.core.Gen3Decoder
import ua.savosh.ecoflowdiag.core.Generation
import ua.savosh.ecoflowdiag.core.MessageDecoder
import ua.savosh.ecoflowdiag.core.Models
import ua.savosh.ecoflowdiag.core.QuotaStore
import kotlin.random.Random

/** У якому форматі просити дані: JSON (1-ше і 2-ге покоління) чи protobuf (3-тє). */
enum class RequestFormat { JSON, PROTO }

/** Паузи циклу зв'язку, мс. В автотестах стискаються, щоб сценарії не тривали хвилинами. */
class LinkTimings(
    val tickMs: Long = 1000,
    /** Поки немає даних батареї: перші запити і далі. */
    val firstRequestMs: Long = 2500,
    val nextRequestMs: Long = 10_000,
    /** Дані є: звичайне оновлення, «живий» режим, тест ємності чи балансу. */
    val normalRequestMs: Long = 30_000,
    val liveRequestMs: Long = 8000,
    val testRequestMs: Long = 10_000,
    val offlineRequestMs: Long = 15_000,
    /** Коли сказати людині, що даних немає чи що хмара вважає станцію офлайн. Раніше це передчасно. */
    val noDataNoticeMs: Long = 25_000,
    val offlineNoticeMs: Long = 20_000,
    /** Скільки чекати на цьому сервері, перш ніж спробувати інший. */
    val tryOtherServerMs: Long = 15_000,
    /** Мовчанка, після якої з'єднання вважаємо зависшим. */
    val stallMinMs: Long = 40_000,
    val reconnectBaseMs: Long = 3000,
    val reconnectMaxMs: Long = 30_000,
    /** Короткий сеанс для оновлення списку станцій. */
    val pollRequestMs: Long = 2500,
    val pollMaxMs: Long = 12_000,
    val pollSettleMs: Long = 600,
)

/** Вхід і доступ до хмари. Реалізує той, хто зберігає акаунт; тут лише правила, коли що скидати. */
interface CloudAccess {
    suspend fun auth(): EcoflowAuth
    suspend fun creds(auth: EcoflowAuth): MqttCreds
    /** Брокер відхилив облікові дані MQTT: наступного разу отримати нові. */
    fun dropCreds()
    /** Токен входу, схоже, прострочений: наступного разу увійти заново. */
    fun dropAuth()
    /** Перейти на наступний сервер EcoFlow. null — інших немає (тоді повернутись на основний). */
    fun nextServer(): String?
    /** Станція відповіла на цьому сервері: запам'ятати його. */
    fun keepServer()
    /** Чи зараз пробуємо не основний сервер. */
    val onOtherServer: Boolean
}

/** Що цикл зв'язку повідомляє екрану. Усі виклики, крім [log], — з потоку самого циклу. */
interface LinkListener {
    fun onStatus(text: String, busy: Boolean, error: Boolean)
    fun onClient(client: EcoflowMqtt?)
    fun onLoginRejected(message: String)
    fun log(text: String)
}

/**
 * Зв'язок з однією станцією: підключення, запити даних, переп'єднання.
 * Головне правило: поки людина на екрані, спроби не припиняються, а одна відповідь хмари не є вироком.
 */
class StationLink(
    private val sn: String,
    private val store: QuotaStore,
    private val cloud: CloudAccess,
    private val listener: LinkListener,
    private val onMessage: (topic: String, payload: ByteArray) -> Unit,
    private val timings: LinkTimings = LinkTimings(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val testRunning: () -> Boolean = { false },
    private val liveMode: () -> Boolean = { false },
) {
    @Volatile private var lastMessageMs = 0L

    private val knownGeneration: Generation? = Models.bySn(sn)?.generation

    private fun now(): Long = System.currentTimeMillis()

    private fun newClient(a: EcoflowAuth, c: MqttCreds) = EcoflowMqtt(
        creds = c,
        userId = a.userId,
        sn = sn,
        onMessage = { topic, payload ->
            lastMessageMs = now()
            onMessage(topic, payload)
        },
        onLog = { listener.log(it) },
    )

    private fun format(attempt: Int): RequestFormat = requestFormat(
        knownGeneration,
        QuotaStore.detectGeneration(store.snapshot().keys),
        store.hasBattery(),
        attempt,
    )

    private suspend fun request(client: EcoflowMqtt, fmt: RequestFormat, logIt: Boolean) {
        if (logIt) listener.log(tr("→ запит усіх даних ({0})", if (fmt == RequestFormat.JSON) "JSON latestQuotas" else "protobuf get-all"))
        withContext(io) { client.publish(client.getTopic, requestPayload(fmt)) }
    }

    /** Працює, доки корутину не скасують. Завершується сам лише тоді, коли сервер відмовив у вході. */
    suspend fun run() {
        var client: EcoflowMqtt? = null
        // З'єднання, що саме встановлюється: якщо людина вийде в цей момент, його теж треба закрити,
        // інакше воно лишиться висіти й підмішуватиме дані цієї станції в наступну.
        var connecting: EcoflowMqtt? = null
        var failures = 0
        var attempt = 0
        var lastRequestMs = 0L
        var connectedMs = 0L
        // Час очікування відповіді рахуємо від з'єднання: повільний вхід чи мережа — ще не мовчання станції.
        var firstConnectedMs = 0L
        var onServerSinceMs = 0L
        var stalls = 0
        var serversExhausted = false

        fun switchServer(why: String) {
            val other = cloud.nextServer()
            if (other == null) serversExhausted = true
            attempt = 0
            store.forgetOnline()
            listener.log(if (other != null) tr("{0}, пробую сервер {1}", why, other) else tr("{0}, інших серверів немає — лишаюсь на основному", why))
        }

        try {
            listener.onStatus(if (store.size() > 0) "" else tr("Вхід в акаунт EcoFlow…"), store.size() == 0, false)
            while (currentCoroutineContext().isActive) {
                // 1. З'єднання з хмарою
                val cur = client
                if (cur == null || !cur.isConnected()) {
                    if (cur != null) {
                        cur.close()
                        listener.onClient(null)
                        if (store.size() == 0) listener.onStatus(tr("Зв'язок з хмарою втрачено, перепідключення…"), true, false)
                    } else if (store.size() == 0 && failures == 0) {
                        listener.onStatus(tr("Підключення до хмари EcoFlow…"), true, false)
                    }
                    client = null
                    val failure: ApiException? = try {
                        val a = cloud.auth()
                        val nm = newClient(a, cloud.creds(a))
                        connecting = nm
                        withContext(io) { nm.connect() }
                        connecting = null
                        client = nm
                        listener.onClient(nm)
                        failures = 0
                        lastRequestMs = 0L
                        connectedMs = now()
                        onServerSinceMs = connectedMs
                        if (firstConnectedMs == 0L) firstConnectedMs = connectedMs
                        null
                    } catch (e: ApiException) {
                        e
                    }
                    // Людина могла вже закрити екран, поки тривала спроба: тоді нічого не чіпаємо.
                    currentCoroutineContext().ensureActive()
                    if (failure != null) {
                        when (failure) {
                            is LoginRejected -> {
                                // Акаунта може не бути саме на цьому регіональному сервері — це ще не вирок паролю.
                                if (!cloud.onOtherServer) {
                                    listener.onLoginRejected(failure.message ?: tr("Сервер відмовив у вході"))
                                    return
                                }
                                switchServer(tr("На цьому сервері акаунта немає"))
                                continue
                            }
                            is MqttAuthException -> {
                                cloud.dropCreds()
                                if (failures >= 1) cloud.dropAuth()
                            }
                            is NetworkException, is StationDenied -> Unit
                            // Сервер не видав доступ до MQTT — найчастіше токен входу прострочено.
                            else -> cloud.dropAuth()
                        }
                        failures++
                        listener.log(failure.message ?: tr("MQTT: помилка"))
                        if (store.size() == 0) {
                            when {
                                failure is StationDenied -> listener.onStatus(failure.message ?: "", false, true)
                                failures >= 2 -> listener.onStatus(
                                    tr("Немає зв'язку з хмарою EcoFlow (спроба {0}). Спроби тривають самі.\n{1}", failures, failure.message ?: "").trim(), false, true,
                                )
                            }
                        }
                        if (failures % 3 == 0 && !serversExhausted) switchServer(tr("Сервер не відповідає"))
                        delay(minOf(timings.reconnectMaxMs, timings.reconnectBaseMs * failures))
                        continue
                    }
                }
                val conn = client ?: continue

                // 2. Запит даних
                val t = now()
                val hasBattery = store.hasBattery()
                val online = store.online
                val interval = when {
                    hasBattery && online == false -> timings.offlineRequestMs
                    hasBattery && testRunning() -> timings.testRequestMs
                    hasBattery && liveMode() -> timings.liveRequestMs
                    hasBattery -> timings.normalRequestMs
                    attempt < 2 -> timings.firstRequestMs
                    else -> timings.nextRequestMs
                }
                if (t - lastRequestMs >= interval) {
                    request(conn, format(attempt), attempt < 4 || attempt % 10 == 0)
                    attempt++
                    lastRequestMs = t
                }

                // 3. Сервери: станція відповіла тут — лишаємось; мовчить чи «офлайн» — пробуємо інший регіон.
                if (hasBattery) {
                    if (cloud.onOtherServer) cloud.keepServer()
                    serversExhausted = true
                    stalls = 0
                } else if (!serversExhausted && (store.size() == 0 || online == false)) {
                    val waited = t - onServerSinceMs
                    // «Офлайн» приймаємо лише після запиту в обох форматах і паузи на відповідь на другий.
                    val offlineSure = online == false && attempt >= 2 && t - lastRequestMs >= timings.firstRequestMs
                    if (waited >= timings.tryOtherServerMs || offlineSure) {
                        switchServer(if (online == false) tr("Хмара каже, що станція офлайн") else tr("Станція не відповідає"))
                        conn.close()
                        listener.onClient(null)
                        client = null
                        continue
                    }
                }

                // 4. Зависання: з'єднання живе, а повідомлень немає — допомагає лише нове з'єднання.
                val silenceMs = t - maxOf(lastMessageMs, connectedMs)
                val stallMs = maxOf(timings.stallMinMs, interval * 5 / 2) * (stalls + 1).coerceAtMost(3)
                if (attempt > 0 && silenceMs >= stallMs) {
                    stalls++
                    listener.log(tr("Станція мовчить {0} с, відкриваю нове з'єднання", silenceMs / 1000))
                    conn.close()
                    listener.onClient(null)
                    client = null
                    continue
                }

                // 5. Стан для екрана. Лише коли всі сервери й формати перепробувано, а час вийшов.
                if (store.size() == 0 || (online == false && !hasBattery)) {
                    val waited = t - firstConnectedMs
                    when {
                        !serversExhausted && cloud.onOtherServer -> listener.onStatus(tr("Станція не відповідає, пробую інший сервер EcoFlow…"), true, false)
                        online == false && serversExhausted && waited >= timings.offlineNoticeMs -> listener.onStatus(OFFLINE_TEXT, false, true)
                        online != false && serversExhausted && waited >= timings.noDataNoticeMs -> listener.onStatus(NO_DATA_TEXT, false, true)
                        else -> listener.onStatus(tr("Запит даних у станції…"), true, false)
                    }
                }
                delay(timings.tickMs)
            }
        } finally {
            connecting?.close()
            client?.close()
        }
    }

    /** Короткий сеанс для оновлення списку: чекає даних батареї, а не першого-ліпшого повідомлення. */
    suspend fun pollOnce() {
        val a = cloud.auth()
        val c = try {
            cloud.creds(a)
        } catch (e: NetworkException) {
            throw e
        } catch (e: ApiException) {
            cloud.dropAuth()
            throw e
        }
        val nm = newClient(a, c)
        try {
            try {
                withContext(io) { nm.connect() }
            } catch (e: MqttAuthException) {
                cloud.dropCreds()
                throw e
            }
            var attempt = 0
            var lastRequestMs = 0L
            val start = now()
            while (now() - start < timings.pollMaxMs) {
                val t = now()
                if (t - lastRequestMs >= timings.pollRequestMs) {
                    request(nm, format(attempt), false)
                    attempt++
                    lastRequestMs = t
                }
                if (store.hasBattery()) {
                    delay(timings.pollSettleMs)
                    return
                }
                // Офлайн кажемо лише після запиту в обох форматах: JSON-відповідь нічого не значить для protobuf-станцій.
                if (store.online == false && attempt >= 3) return
                delay(timings.tickMs / 4)
            }
        } finally {
            nm.close()
        }
    }

    companion object {
        val OFFLINE_TEXT: String
            get() = tr("Станція не відповідає: хмара EcoFlow каже, що вона офлайн. Застосунок перевірив усі сервери EcoFlow і далі пробує сам. Перевірте: 1) станція увімкнена й онлайн в офіційному застосунку EcoFlow; 2) SN збігається символ у символ (станція → ⚙ → Про пристрій) — легко сплутати 0/O, 8/B, 1/I, 5/S; 3) станція додана саме в той акаунт (той самий email), яким ви увійшли тут. Дані з'являться самі, щойно хмара побачить станцію.")
        val NO_DATA_TEXT: String
            get() = tr("Немає даних від станції. Перевірте: станція онлайн через Wi-Fi, прив'язана саме до цього акаунта, серійний номер правильний. Спроби тривають автоматично.")

        /**
         * Формат запиту. Для 3-го покоління джерела суперечать: одні прошивки відповідають лише на protobuf,
         * інші лише на JSON. Тож поки даних батареї немає, пробуємо обидва по черзі, починаючи з очікуваного.
         */
        fun requestFormat(known: Generation?, detected: Generation, hasBattery: Boolean, attempt: Int): RequestFormat {
            val gen = detected.takeIf { it != Generation.UNKNOWN } ?: known ?: Generation.UNKNOWN
            val expected = if (gen == Generation.GEN3) RequestFormat.PROTO else RequestFormat.JSON
            if (gen != Generation.UNKNOWN && hasBattery) return expected
            if (attempt % 2 == 0) return expected
            return if (expected == RequestFormat.JSON) RequestFormat.PROTO else RequestFormat.JSON
        }

        fun requestPayload(fmt: RequestFormat): ByteArray {
            val seq = 999900000 + Random.nextInt(10000, 99999)
            return when (fmt) {
                RequestFormat.JSON -> MessageDecoder.buildJsonQuotaRequest(seq)
                RequestFormat.PROTO -> Gen3Decoder.buildGetAllRequest(seq)
            }
        }
    }
}
