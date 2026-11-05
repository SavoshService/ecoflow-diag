package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Акаунт EcoFlow у цьому запуску застосунку: вхід, доступ до MQTT і вибір сервера.
 * Основний сервер обирає людина. Якщо станція там не відповідає, [StationLink] просить наступний:
 * акаунт і станція можуть жити в іншому регіоні, а людина цього не знає.
 */
class CloudSession(
    private val email: () -> String,
    private val password: () -> String,
    private val primaryHost: () -> String,
    private val allHosts: List<String> = EcoflowApi.HOSTS,
    /** Змінився стан входу (null — вхід скинуто). */
    private val onAuthChanged: (EcoflowAuth?) -> Unit = {},
    private val onBusy: (Boolean) -> Unit = {},
    /** Вхід удався; [onPrimary] — на основному сервері, а не на пробному. */
    private val onLoggedIn: (auth: EcoflowAuth, host: String, onPrimary: Boolean) -> Unit = { _, _, _ -> },
    /** Станція відповіла на іншому сервері: тепер він основний. */
    private val onServerChanged: (String) -> Unit = {},
    private val log: (String) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CloudAccess {

    private val mutex = Mutex()

    @Volatile private var currentAuth: EcoflowAuth? = null
    @Volatile private var currentCreds: MqttCreds? = null

    private var activeHost: String? = null
    private var untried: List<String> = emptyList()
    private var rejected: Set<String> = emptySet()
    private var primaryAuth: EcoflowAuth? = null
    private var primaryCreds: MqttCreds? = null

    private fun setAuth(a: EcoflowAuth?) {
        currentAuth = a
        onAuthChanged(a)
    }

    /** Один вхід за раз: запуск застосунку й відкриття станції часто збігаються в часі. */
    override suspend fun auth(): EcoflowAuth = mutex.withLock {
        currentAuth?.let { return it }
        onBusy(true)
        try {
            val h = activeHost ?: primaryHost()
            val a = withContext(io) { EcoflowApi(h).login(email().trim(), password()) }
            setAuth(a)
            onLoggedIn(a, h, activeHost == null)
            a
        } catch (e: LoginRejected) {
            activeHost?.let { rejected = rejected + it }
            throw e
        } finally {
            onBusy(false)
        }
    }

    override suspend fun creds(auth: EcoflowAuth): MqttCreds {
        currentCreds?.let { return it }
        val c = withContext(io) { EcoflowApi(auth.host).certification(auth) }
        currentCreds = c
        log(tr("Отримано доступ до MQTT: {0}:{1} ({2})", c.host, c.port, c.protocol))
        return c
    }

    override fun dropCreds() {
        currentCreds = null
    }

    override fun dropAuth() {
        currentCreds = null
        setAuth(null)
    }

    override val onOtherServer: Boolean get() = activeHost != null

    override fun nextServer(): String? {
        if (activeHost == null) {
            primaryAuth = currentAuth
            primaryCreds = currentCreds
        }
        val next = untried.firstOrNull()
        untried = untried.drop(1)
        activeHost = next
        currentCreds = if (next == null) primaryCreds else null
        // Без сповіщення: людина й далі в акаунті, це лише пробний перехід на інший сервер.
        currentAuth = if (next == null) primaryAuth else null
        return next
    }

    override fun keepServer() {
        activeHost?.let(onServerChanged)
        activeHost = null
    }

    /** Нова спроба зв'язку зі станцією: повернутись на основний сервер і знову можна пробувати решту. */
    fun resetServers() {
        if (activeHost != null) {
            currentCreds = primaryCreds
            setAuth(primaryAuth)
            activeHost = null
        }
        untried = allHosts - primaryHost() - rejected
    }

    /** Новий вхід (інший акаунт чи пароль): усе попереднє недійсне. */
    fun forget() {
        activeHost = null
        rejected = emptySet()
        primaryAuth = null
        primaryCreds = null
        currentCreds = null
        setAuth(null)
    }
}
