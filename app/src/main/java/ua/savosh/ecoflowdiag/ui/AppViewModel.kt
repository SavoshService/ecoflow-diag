package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
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
import ua.savosh.ecoflowdiag.core.AppLang
import ua.savosh.ecoflowdiag.core.I18n
import ua.savosh.ecoflowdiag.core.localize
import ua.savosh.ecoflowdiag.core.tk
import ua.savosh.ecoflowdiag.core.BalanceCheck
import ua.savosh.ecoflowdiag.core.CellSnapshot
import ua.savosh.ecoflowdiag.core.LogEntry
import ua.savosh.ecoflowdiag.core.StationLog
import ua.savosh.ecoflowdiag.core.CapPhase
import ua.savosh.ecoflowdiag.core.CapacityTest
import ua.savosh.ecoflowdiag.core.Demo
import ua.savosh.ecoflowdiag.core.DeviceCommand
import ua.savosh.ecoflowdiag.core.EcoflowCommands
import ua.savosh.ecoflowdiag.core.CodeEvent
import ua.savosh.ecoflowdiag.core.CodeHistory
import ua.savosh.ecoflowdiag.core.CodeTracker
import ua.savosh.ecoflowdiag.core.Gen3Decoder
import ua.savosh.ecoflowdiag.core.MessageDecoder
import ua.savosh.ecoflowdiag.core.Models
import android.content.Intent
import android.net.Uri
import ua.savosh.ecoflowdiag.core.Article
import ua.savosh.ecoflowdiag.core.Changelog
import ua.savosh.ecoflowdiag.core.ChecklistItem
import ua.savosh.ecoflowdiag.core.KnowledgeSeed
import ua.savosh.ecoflowdiag.core.Note
import ua.savosh.ecoflowdiag.core.Order
import ua.savosh.ecoflowdiag.core.PartItem
import ua.savosh.ecoflowdiag.core.QuotaStore
import ua.savosh.ecoflowdiag.core.Release
import ua.savosh.ecoflowdiag.net.TelegramConfig
import ua.savosh.ecoflowdiag.work.ReminderWorker
import ua.savosh.ecoflowdiag.work.SubmitWorker
import ua.savosh.ecoflowdiag.core.Report
import ua.savosh.ecoflowdiag.core.StationReport
import ua.savosh.ecoflowdiag.data.Prefs
import ua.savosh.ecoflowdiag.data.StationEntry
import android.content.Context
import java.io.File
import ua.savosh.ecoflowdiag.net.ApiException
import ua.savosh.ecoflowdiag.net.ApkUpdater
import ua.savosh.ecoflowdiag.net.AppIntegrity
import ua.savosh.ecoflowdiag.net.CloudSession
import ua.savosh.ecoflowdiag.net.EcoflowAuth
import ua.savosh.ecoflowdiag.net.EcoflowMqtt
import ua.savosh.ecoflowdiag.net.GoogleDrive
import ua.savosh.ecoflowdiag.net.TelegramSubmit
import ua.savosh.ecoflowdiag.core.EcoflowCodes
import ua.savosh.ecoflowdiag.core.Indicator
import ua.savosh.ecoflowdiag.core.Level
import ua.savosh.ecoflowdiag.data.SavedReport
import ua.savosh.ecoflowdiag.net.LinkListener
import ua.savosh.ecoflowdiag.net.LoginRejected
import ua.savosh.ecoflowdiag.net.StationLink
import ua.savosh.ecoflowdiag.net.UpdateInfo
import ua.savosh.ecoflowdiag.net.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

enum class Screen { LOGIN, STATIONS, DIAG, RAW, LOG, FLASH, CAPTEST, BALANCE, CONTROL, FEEDBACK, PARTS, STATIONINFO, CRM, KNOWLEDGE, NOTES, BACKUP, REPORT, MORE }

/** Розділи нижньої панелі: Станції, Замовлення, Склад, Фінанси, Ще. */
enum class MainTab { STATIONS, ORDERS, STOCK, FINANCE, MORE }

class LogLine(val time: String, val text: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val appCtx: Context = app

    init {
        // Першим ділом: нижче вже є тексти, що залежать від мови.
        I18n.lang = prefs.appLang
    }

    /** Мова застосунку: екран перемальовується при її зміні (див. App у MainActivity). */
    // Прокрутка й фільтр списку станцій не скидаються, коли повертаєшся з екрана станції.
    val stationsListState = LazyListState()
    var stationQuery by mutableStateOf("")
    var stationFilter by mutableStateOf(StFilter.ALL)

    var appLang by mutableStateOf(I18n.lang)
        private set

    fun setLanguage(l: AppLang) {
        if (l == appLang) return
        I18n.lang = l
        appLang = l
        prefs.appLang = l
        // Тексти, вже складені старою мовою, перекладати немає сенсу: вони оновляться самі.
        refreshAllStatus = ""
        gdStatus = null
        updateError = null
        loginError = null
        if (store.size() > 0) recompute()
        capTick++
        balTick++
    }

    /** Реліз підписано чужим ключем: на екрані входу буде попередження. */
    val foreignBuild: Boolean = AppIntegrity.isForeignBuild(app)

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

    // ---------------- перевірка балансу наприкінці заряду
    var balanceCheck by mutableStateOf<BalanceCheck?>(null)
        private set
    var balTick by mutableStateOf(0)
        private set
    val balanceRunning: Boolean
        get() = balanceCheck?.phase == BalanceCheck.Phase.RUNNING

    /** Журнал перевірок поточної станції (новіші першими): знімки діагностики, баланс, тест ємності. */
    var stationLog by mutableStateOf<List<LogEntry>>(emptyList())
        private set
    private var lastLogSaveMs = 0L

    /** Останні отримані напруги груп по батареях поточної станції (ключ — PackReport.key). */
    var lastCells by mutableStateOf<Map<String, CellSnapshot>>(emptyMap())
        private set
    private var lastCellsSaveMs = 0L

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
    /** Стан завантаження оновлення в один дотик. */
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0) // 0..100, або -1 якщо розмір невідомий
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    private var downloadedApk: File? = null
    private var autoRefreshDone = false

    // ---------------- монітор заряд/розряд
    /** Історія потужності батареї (час, Вт): >0 заряд, <0 розряд. */
    var powerHistory by mutableStateOf<List<Pair<Long, Double>>>(emptyList())
        private set
    /** «Живий» режим: коли відкрито монітор/керування — опитуємо станцію частіше. */
    var liveMode by mutableStateOf(false)

    // ---------------- демо-станція
    var isDemo by mutableStateOf(false)
        private set
    private var demoJob: Job? = null

    /** Демо-картку сховано зі списку (зберігається між запусками). */
    var demoHidden by mutableStateOf(prefs.demoHidden)
        private set

    fun hideDemo() {
        demoHidden = true
        prefs.demoHidden = true
    }

    // ---------------- Майстерня (CRM замовлень)
    val orders = mutableStateListOf<Order>()

    /** SN/ID замовлення, відкритого на редагування (null — нове). */
    var editingOrderId by mutableStateOf<Long?>(null)
        private set

    /** Вкладка майстерні: 0 — замовлення, 1 — склад, 2 — фінанси, 3 — корзина. */
    var crmTab by mutableStateOf(0)
        private set

    fun openCrm() = openCrmTab(0)

    fun openCrmTab(tab: Int) {
        crmTab = tab
        screen = Screen.CRM
    }

    /** Розділ «Ще»: записник, прошивка, копія, спільнота. */
    fun openMore() { screen = Screen.MORE }

    /** Розділ нижньої панелі, на якому зараз людина. */
    val mainTab: MainTab
        get() = when (screen) {
            Screen.CRM -> when (crmTab) { 1 -> MainTab.STOCK; 2 -> MainTab.FINANCE; else -> MainTab.ORDERS }
            Screen.MORE -> MainTab.MORE
            else -> MainTab.STATIONS
        }

    fun openTab(tab: MainTab) {
        when (tab) {
            MainTab.STATIONS -> screen = Screen.STATIONS
            MainTab.ORDERS -> openCrmTab(0)
            MainTab.STOCK -> openCrmTab(1)
            MainTab.FINANCE -> openCrmTab(2)
            MainTab.MORE -> openMore()
        }
    }

    /** Куди повертатись «назад» з другорядних екранів (талмуд, замітки, копія, прошивка, зв'язок). */
    private var secondaryReturn = Screen.STATIONS

    /** Запам'ятати розділ, з якого відкрили другорядний екран. */
    private fun rememberReturn() {
        if (screen == Screen.STATIONS || screen == Screen.CRM || screen == Screen.MORE) secondaryReturn = screen
    }

    /** Активні замовлення (не в корзині), новіші першими. */
    fun activeOrders(): List<Order> = orders.filter { !it.deleted }.sortedByDescending { it.updatedAt }

    /** Замовлення в корзині. */
    fun trashedOrders(): List<Order> = orders.filter { it.deleted }.sortedByDescending { it.deletedAt ?: it.updatedAt }

    fun orderById(id: Long?): Order? = id?.let { orders.firstOrNull { o -> o.id == it } }

    fun beginNewOrder() { editingOrderId = null }
    fun beginEditOrder(id: Long) { editingOrderId = id }

    /**
     * Створити або оновити замовлення. Якщо [id] == null — нове (отримує номер).
     * Повертає id збереженого замовлення.
     */
    fun saveOrder(
        id: Long?,
        deviceType: String,
        model: String,
        clientName: String,
        phone: String,
        channel: String,
        description: String,
        status: String,
        partsCost: Double,
        totalCost: Double,
    ): Long {
        val now = System.currentTimeMillis()
        val name = clientName.trim().ifBlank { tk("Невідомо") }
        if (id == null) {
            val newId = prefs.nextOrderId
            prefs.nextOrderId = newId + 1
            val o = Order(
                id = newId, createdAt = now, updatedAt = now,
                completedAt = if (status == "green") now else null,
                deviceType = deviceType.trim(), model = model.trim(), clientName = name,
                phone = phone.trim(), channel = channel, description = description.trim(),
                status = status, partsCost = partsCost, totalCost = totalCost,
            )
            orders.add(o)
            persistOrders()
            return newId
        } else {
            val i = orders.indexOfFirst { it.id == id }
            if (i < 0) return id
            val old = orders[i]
            val completedAt = when {
                status == "green" && old.status != "green" -> now
                status != "green" -> null
                else -> old.completedAt ?: now
            }
            orders[i] = old.copy(
                updatedAt = now, completedAt = completedAt,
                deviceType = deviceType.trim(), model = model.trim(), clientName = name,
                phone = phone.trim(), channel = channel, description = description.trim(),
                status = status, partsCost = partsCost, totalCost = totalCost,
            )
            persistOrders()
            return id
        }
    }

    /** Швидка зміна статусу прямо зі списку. */
    fun setOrderStatus(id: Long, status: String) {
        val i = orders.indexOfFirst { it.id == id }
        if (i < 0) return
        val now = System.currentTimeMillis()
        val old = orders[i]
        val completedAt = when {
            status == "green" && old.status != "green" -> now
            status != "green" -> null
            else -> old.completedAt ?: now
        }
        orders[i] = old.copy(status = status, completedAt = completedAt, updatedAt = now)
        persistOrders()
    }

    /** У корзину (м'яке видалення). */
    fun trashOrder(id: Long) {
        val i = orders.indexOfFirst { it.id == id }
        if (i < 0) return
        orders[i] = orders[i].copy(deleted = true, deletedAt = System.currentTimeMillis())
        persistOrders()
    }

    /** Відновити з корзини. */
    fun restoreOrder(id: Long) {
        val i = orders.indexOfFirst { it.id == id }
        if (i < 0) return
        orders[i] = orders[i].copy(deleted = false, deletedAt = null, updatedAt = System.currentTimeMillis())
        persistOrders()
    }

    /** Остаточно видалити одне замовлення. */
    fun deleteOrderForever(id: Long) {
        orders.removeAll { it.id == id }
        persistOrders()
    }

    /** Очистити всю корзину. */
    fun emptyTrash() {
        orders.removeAll { it.deleted }
        persistOrders()
    }

    private fun persistOrders() {
        prefs.saveOrders(orders.toList())
        scheduleBackup()
    }

    // ---------------- налаштування бухгалтерії
    var reservePercent by mutableStateOf(prefs.reservePercent)
        private set
    var simpleAccounting by mutableStateOf(prefs.simpleAccounting)
        private set

    fun updateReservePercent(v: Double) {
        val p = v.coerceIn(0.0, 100.0)
        reservePercent = p
        prefs.reservePercent = p
    }

    fun updateSimpleAccounting(v: Boolean) {
        simpleAccounting = v
        prefs.simpleAccounting = v
    }

    // ---------------- склад деталей
    val parts = mutableStateListOf<PartItem>()

    fun partsSorted(query: String = ""): List<PartItem> {
        val q = query.trim().lowercase()
        val base = parts.sortedBy { it.name.lowercase() }
        if (q.isEmpty()) return base
        return base.filter {
            it.name.lowercase().contains(q) || it.location.lowercase().contains(q) ||
                it.code.lowercase().contains(q) || it.note.lowercase().contains(q)
        }
    }

    fun partById(id: Long?): PartItem? = id?.let { parts.firstOrNull { p -> p.id == it } }

    fun savePart(
        id: Long?,
        name: String,
        quantity: Int,
        location: String,
        code: String,
        note: String,
        price: Double,
    ): Long {
        val now = System.currentTimeMillis()
        val nm = name.trim().ifBlank { tk("Деталь") }
        if (id == null) {
            val newId = prefs.nextPartId
            prefs.nextPartId = newId + 1
            parts.add(
                PartItem(newId, now, now, nm, quantity.coerceAtLeast(0), location.trim(), code.trim(), note.trim(), price),
            )
            persistParts()
            return newId
        }
        val i = parts.indexOfFirst { it.id == id }
        if (i < 0) return id
        parts[i] = parts[i].copy(
            updatedAt = now, name = nm, quantity = quantity.coerceAtLeast(0),
            location = location.trim(), code = code.trim(), note = note.trim(), price = price,
        )
        persistParts()
        return id
    }

    /** Швидка зміна кількості на складі (+1 / −1). */
    fun bumpPartQty(id: Long, delta: Int) {
        val i = parts.indexOfFirst { it.id == id }
        if (i < 0) return
        parts[i] = parts[i].copy(quantity = (parts[i].quantity + delta).coerceAtLeast(0), updatedAt = System.currentTimeMillis())
        persistParts()
    }

    fun deletePart(id: Long) {
        parts.removeAll { it.id == id }
        persistParts()
    }

    private fun persistParts() {
        prefs.saveParts(parts.toList())
        scheduleBackup()
    }

    // ---------------- База знань майстра
    private val userArticles = mutableStateListOf<Article>()

    fun openKnowledge() { rememberReturn(); screen = Screen.KNOWLEDGE }

    /** Усі статті: офіційні (з оновлення) + власні. */
    fun allArticles(): List<Article> = KnowledgeSeed.OFFICIAL + userArticles

    /** Назви розділів (унікальні, за абеткою). */
    fun kbSections(): List<String> = allArticles().map { sectionName(it) }.distinct().sorted()

    /** Назва розділу поточною мовою: власні розділи майстра лишаються як написані. */
    private fun sectionName(a: Article): String = localize(a.section.ifBlank { tk("Загальне") })

    fun articlesIn(section: String): List<Article> =
        allArticles().filter { sectionName(it) == section }.sortedByDescending { it.updatedAt }

    fun articleById(id: Long?): Article? = id?.let { allArticles().firstOrNull { a -> a.id == it } }

    fun saveArticle(
        id: Long?,
        section: String,
        subsection: String,
        title: String,
        marking: String,
        analogs: String,
        body: String,
        photos: List<String>,
    ): Long {
        val now = System.currentTimeMillis()
        val savedId: Long
        if (id == null || id < 0) { // нова (офіційні не редагуються)
            savedId = prefs.nextArticleId
            prefs.nextArticleId = savedId + 1
            userArticles.add(
                Article(savedId, now, now, I18n.canonical(section.trim()).ifBlank { tk("Загальне") }, subsection.trim(),
                    title.trim(), marking.trim(), analogs.trim(), body.trim(), photos, official = false, author = prefs.email),
            )
        } else {
            savedId = id
            val i = userArticles.indexOfFirst { it.id == id }
            if (i < 0) return id
            userArticles[i] = userArticles[i].copy(
                updatedAt = now, section = I18n.canonical(section.trim()).ifBlank { tk("Загальне") }, subsection = subsection.trim(),
                title = title.trim(), marking = marking.trim(), analogs = analogs.trim(), body = body.trim(), photos = photos,
            )
        }
        prefs.saveArticles(userArticles.toList())
        scheduleBackup()
        // Автоматично (тихо, у фоні) ділимося статтею у спільну базу.
        if (submitEnabled) SubmitWorker.enqueue(appCtx, savedId)
        return savedId
    }

    fun deleteArticle(id: Long) {
        val a = userArticles.firstOrNull { it.id == id } ?: return
        for (name in a.photos) runCatching { File(photoPath(name)).delete() }
        userArticles.removeAll { it.id == id }
        prefs.saveArticles(userArticles.toList())
        scheduleBackup()
    }

    private fun photoDir(): File = File(appCtx.filesDir, "kb_photos").apply { mkdirs() }

    /** Копіює вибрані фото у внутрішнє сховище, повертає імена файлів. */
    fun importPhotos(uris: List<Uri>): List<String> {
        val dir = photoDir()
        val cr = appCtx.contentResolver
        val out = ArrayList<String>()
        for (u in uris) {
            try {
                val name = "img_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.jpg"
                val f = File(dir, name)
                cr.openInputStream(u)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                if (f.exists() && f.length() > 0) out.add(name)
            } catch (e: Exception) {
                // пропускаємо проблемне фото
            }
        }
        return out
    }

    // Лише ім'я файлу: стаття з чужої копії не повинна дістатись до інших файлів застосунку через «../».
    fun photoPath(name: String): String = File(photoDir(), File(name).name).absolutePath

    // ---------------- «Що нового» після оновлення
    var whatsNew by mutableStateOf<List<Release>>(emptyList())
        private set

    fun dismissWhatsNew() {
        whatsNew = emptyList()
        prefs.lastWhatsNewVersion = Changelog.CURRENT
    }

    // ---------------- Згода на спільну базу (статті й коди надсилаються автоматично)
    var showShareConsent by mutableStateOf(!prefs.shareConsentShown && TelegramConfig.configured())
        private set

    fun acceptShareConsent() {
        showShareConsent = false
        prefs.shareConsentShown = true
    }

    // ---------------- Резервна копія — лише Google Диск (копії у файл прибрано)
    /** Запропонувати відновлення з Google (чиста інсталяція без даних). */
    var offerRestore by mutableStateOf(false)
        private set

    // ---------------- Google Диск (автоматична хмарна синхронізація)
    var gdStatus by mutableStateOf<String?>(null)
    val gdConnected: Boolean get() = prefs.gdConnected
    val gdEmail: String get() = prefs.gdEmail
    val gdConfigured: Boolean get() = GoogleDrive.configured()
    private var driveJob: Job? = null

    /** Увійти через Google (відкриває браузер) і зробити першу синхронізацію. */
    fun connectGoogle() {
        if (!GoogleDrive.configured()) {
            gdStatus = tr("Синхронізацію Google ще не налаштовано в цій збірці.")
            return
        }
        viewModelScope.launch {
            gdStatus = tr("Відкриваю вхід Google у браузері…")
            val res = withContext(Dispatchers.IO) {
                runCatching { GoogleDrive.signIn(appCtx) }
            }
            res.fold(
                onSuccess = { r ->
                    prefs.saveGoogleTokens(r.refreshToken, r.accessToken, r.expiresInSec, r.email)
                    gdStatus = tr("✓ Підключено: {0}. Синхронізую…", r.email.ifBlank { tr("Google акаунт") })
                    gdStatus = withContext(Dispatchers.IO) { runCatching { initialGoogleSync() }.getOrElse { tr("Підключено, але синхронізація не вдалась: {0}", it.message) } }
                },
                onFailure = { e ->
                    gdStatus = tr("Не вдалося увійти: {0}", e.message ?: tr("невідома помилка"))
                },
            )
        }
    }

    fun disconnectGoogle() {
        prefs.clearGoogle()
        gdStatus = tr("Google відключено.")
    }

    fun syncGoogleNow() {
        if (!prefs.gdConnected) return
        viewModelScope.launch {
            gdStatus = tr("Синхронізую…")
            gdStatus = withContext(Dispatchers.IO) {
                try { pushGoogle(); tr("✓ Синхронізовано з Google.") } catch (e: Exception) { tr("Не вдалося синхронізувати.") }
            }
        }
    }

    /** Перша синхронізація після входу: тягнемо хмару, якщо локально порожньо або хмара новіша. */
    private suspend fun initialGoogleSync(): String {
        val token = freshGoogleToken() ?: return tr("Помилка авторизації Google.")
        val fid = GoogleDrive.findFileId(token)
        if (fid != null) {
            val remote = GoogleDrive.download(token, fid)
            val localEmpty = !prefs.hasAnyData()
            if (remote != null && (localEmpty || bundleTime(remote) > bundleTime(prefs.exportBundle()))) {
                if (prefs.importBundle(remote)) {
                    prefs.gdFileId = fid
                    withContext(Dispatchers.Main) {
                        reloadAll(); offerRestore = false; prefs.restoreOffered = true
                    }
                    return tr("✓ Дані відновлено з Google Диску.")
                }
            }
            prefs.gdFileId = GoogleDrive.upload(token, fid, prefs.exportBundle()) ?: fid
            return tr("✓ Синхронізовано з Google.")
        }
        prefs.gdFileId = GoogleDrive.upload(token, null, prefs.exportBundle()) ?: ""
        return tr("✓ Копію створено в Google.")
    }

    private fun freshGoogleToken(): String? {
        if (System.currentTimeMillis() < prefs.gdTokenExpiry && prefs.gdAccessToken.isNotBlank()) return prefs.gdAccessToken
        val rt = prefs.gdRefreshToken
        if (rt.isBlank()) return null
        val r = GoogleDrive.refresh(rt) ?: return null
        prefs.updateGoogleAccess(r.accessToken, r.expiresInSec)
        return r.accessToken
    }

    private fun pushGoogle() {
        val token = freshGoogleToken() ?: return
        val id = prefs.gdFileId.ifBlank { GoogleDrive.findFileId(token) }
        prefs.gdFileId = GoogleDrive.upload(token, id, prefs.exportBundle()) ?: (id ?: "")
    }

    private fun bundleTime(json: String?): Long {
        if (json == null) return 0
        val m = ua.savosh.ecoflowdiag.core.Json.parseOrNull(json) as? Map<*, *> ?: return 0
        return (m["exportedAt"] as? Number)?.toLong() ?: 0
    }

    /** Запланувати авто-вивантаження в Google (після змін, з невеликою затримкою). */
    private fun scheduleDrivePush() {
        if (!prefs.gdConnected) return
        driveJob?.cancel()
        driveJob = viewModelScope.launch {
            delay(3000)
            withContext(Dispatchers.IO) { try { pushGoogle() } catch (e: Exception) { } }
        }
    }

    fun openBackup() { rememberReturn(); screen = Screen.BACKUP }

    fun dismissRestore() {
        offerRestore = false
        prefs.restoreOffered = true
    }

    /** Перечитати всі дані з пам'яті у стан (після відновлення). */
    private fun reloadAll() {
        stations.clear(); stations.addAll(prefs.loadStations())
        orders.clear(); orders.addAll(prefs.loadOrders())
        parts.clear(); parts.addAll(prefs.loadParts())
        userArticles.clear(); userArticles.addAll(prefs.loadArticles())
        notes.clear(); notes.addAll(prefs.loadNotes())
        reservePercent = prefs.reservePercent
        simpleAccounting = prefs.simpleAccounting
        demoHidden = prefs.demoHidden
    }

    /** Чи є дані, але Google-копію ще не підключено (для нагадування). */
    val needsBackup: Boolean get() = prefs.hasAnyData() && !prefs.gdConnected

    /** Банер нагадування про копію сховано на цю сесію. */
    var backupBannerHidden by mutableStateOf(false)
        private set

    fun hideBackupBanner() { backupBannerHidden = true }

    /** Запланувати авто-копію в Google (після змін, з невеликою затримкою, щоб не писати на кожну літеру). */
    private fun scheduleBackup() {
        scheduleDrivePush()
    }

    /** Чи налаштована відправка в спільну базу (Telegram). */
    val submitEnabled: Boolean get() = TelegramConfig.configured()

    /** Поставити статтю в чергу на відправку у спільну базу (фон, із повтором). */
    fun submitArticle(id: Long) {
        SubmitWorker.enqueue(appCtx, id)
    }

    // ---------------- Записник (нотатки)
    val notes = mutableStateListOf<Note>()

    fun openNotes() { rememberReturn(); screen = Screen.NOTES }

    fun notesSorted(kind: String? = null): List<Note> {
        val base = notes.sortedByDescending { it.updatedAt }
        return if (kind == null) base else base.filter { it.kind == kind }
    }

    fun noteById(id: Long?): Note? = id?.let { notes.firstOrNull { n -> n.id == it } }

    fun saveNote(
        id: Long?,
        kind: String,
        title: String,
        body: String,
        items: List<ChecklistItem>,
        stationSn: String,
        reminderAt: Long?,
    ): Long {
        val now = System.currentTimeMillis()
        val savedId: Long
        if (id == null) {
            savedId = prefs.nextNoteId
            prefs.nextNoteId = savedId + 1
            notes.add(Note(savedId, now, now, kind, title.trim(), body.trim(), items, stationSn, reminderAt))
        } else {
            savedId = id
            val i = notes.indexOfFirst { it.id == id }
            if (i < 0) return id
            notes[i] = notes[i].copy(
                updatedAt = now, kind = kind, title = title.trim(), body = body.trim(),
                items = items, stationSn = stationSn, reminderAt = reminderAt,
            )
        }
        persistNotes()
        ReminderWorker.schedule(appCtx, savedId, title.trim().ifBlank { tr("Замітка") }, reminderAt)
        return savedId
    }

    /** Перемкнути галочку пункту чеклиста. */
    fun toggleNoteItem(noteId: Long, index: Int) {
        val i = notes.indexOfFirst { it.id == noteId }
        if (i < 0) return
        val n = notes[i]
        if (index !in n.items.indices) return
        val newItems = n.items.toMutableList()
        newItems[index] = newItems[index].copy(done = !newItems[index].done)
        notes[i] = n.copy(items = newItems, updatedAt = System.currentTimeMillis())
        persistNotes()
    }

    fun deleteNote(id: Long) {
        notes.removeAll { it.id == id }
        persistNotes()
        ReminderWorker.cancel(appCtx, id)
    }

    private fun persistNotes() {
        prefs.saveNotes(notes.toList())
        scheduleBackup()
    }

    private val store = QuotaStore()
    private var mqtt: EcoflowMqtt? = null
    private var diagJob: Job? = null
    private val refreshPending = AtomicBoolean(false)
    private var lastStationSaveMs = 0L
    private var loggedNotes = 0
    private var rawLogged = 0
    private var pausedSn: String? = null

    private val cloud = CloudSession(
        email = { email },
        password = { password },
        primaryHost = { host },
        onAuthChanged = { auth = it },
        onBusy = { loginBusy = it },
        onLoggedIn = { a, h, onPrimary ->
            prefs.email = email.trim()
            if (onPrimary) prefs.host = h
            prefs.rememberPassword = remember
            prefs.password = if (remember) password else ""
            addLog(tr("Вхід виконано: {0} (userId {1}, сервер {2})", a.userName ?: email.trim(), a.userId, h))
        },
        onServerChanged = {
            host = it
            prefs.host = it
            addLog(tr("Станція відповіла через сервер {0} — запам'ятав його", it))
        },
        log = { addLog(it) },
    )

    private val diagListener = object : LinkListener {
        override fun onStatus(text: String, busy: Boolean, error: Boolean) {
            diagStatus = text
            diagBusy = busy
            diagError = error
        }

        override fun onClient(client: EcoflowMqtt?) {
            mqtt = client
            diagConnected = client != null
        }

        override fun onLoginRejected(message: String) {
            addLog(tr("Вхід відхилено: {0}", message))
            stopDiag()
            pausedSn = null
            cloud.forget()
            loginError = message
            screen = Screen.LOGIN
        }

        override fun log(text: String) = addLog(text)
    }

    private val quietListener = object : LinkListener {
        override fun onStatus(text: String, busy: Boolean, error: Boolean) {}
        override fun onClient(client: EcoflowMqtt?) {}
        override fun onLoginRejected(message: String) {}
        override fun log(text: String) {}
    }

    init {
        stations.addAll(prefs.loadStations())
        orders.addAll(prefs.loadOrders())
        parts.addAll(prefs.loadParts())
        userArticles.addAll(prefs.loadArticles())
        notes.addAll(prefs.loadNotes())
        // «Що нового»: показати один раз для нової версії.
        if (prefs.lastWhatsNewVersion != Changelog.CURRENT) {
            // Новому користувачу порівнювати нема з чим: вікно лише заважало б першому запуску.
            val firstRun = prefs.email.isBlank() && !prefs.hasAnyData()
            whatsNew = if (firstRun) emptyList() else Changelog.newerThan(prefs.lastWhatsNewVersion)
            if (whatsNew.isEmpty()) prefs.lastWhatsNewVersion = Changelog.CURRENT
        }
        // Чиста інсталяція без даних — запропонувати відновити з файлу копії.
        offerRestore = !prefs.hasAnyData() && !prefs.restoreOffered
        viewModelScope.launch {
            val upd = withContext(Dispatchers.IO) { Updater.check() }
            if (upd != null) availableUpdate = upd
        }
        if (prefs.rememberPassword && prefs.email.isNotBlank() && prefs.password.isNotBlank()) {
            screen = Screen.STATIONS
            viewModelScope.launch { startupLogin() }
        }
    }

    /**
     * Вхід зі збереженими даними. На екран входу веде лише відмова сервера (невірний пароль):
     * збій мережі — не привід викидати людину з роботи, просто пробуємо ще раз.
     */
    private suspend fun startupLogin() {
        var wait = 5000L
        while (auth == null && password.isNotBlank()) {
            try {
                cloud.auth()
                refreshAllStatus = ""
                maybeAutoRefresh()
            } catch (e: LoginRejected) {
                loginError = e.message
                if (screen == Screen.STATIONS) screen = Screen.LOGIN
                return
            } catch (e: ApiException) {
                addLog(e.message ?: tr("Помилка входу"))
                refreshAllStatus = tr("Немає зв'язку з EcoFlow, пробую ще раз…")
                delay(wait)
                wait = minOf(60_000L, wait * 2)
            }
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    /**
     * Оновлення в один дотик: завантажує APK і відкриває системний інсталятор.
     * [url]/[version] передаються зі сповіщення; без них береться [availableUpdate].
     */
    fun startUpdate(ctx: Context, url: String? = null, version: String? = null) {
        val link = url?.takeIf { Updater.isTrustedUrl(it) } ?: availableUpdate?.url ?: return
        // Якщо дані прийшли зі сповіщення, а банер ще порожній — покажемо його з прогресом.
        if (availableUpdate == null) availableUpdate = UpdateInfo(version ?: "", link, "")
        if (updateDownloading) return
        updateError = null
        // Уже завантажено й дозвіл є — одразу встановлюємо, без повторного качання.
        val ready = downloadedApk
        if (ready != null && ready.exists() && ApkUpdater.canInstall(ctx)) {
            ApkUpdater.install(ctx, ready)
            return
        }
        updateDownloading = true
        updateProgress = 0
        viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) {
                ApkUpdater.download(ctx, link) { p -> updateProgress = p }
            }
            updateDownloading = false
            if (apk == null) {
                updateError = tr("Не вдалося завантажити. Спробуйте «У браузері».")
                return@launch
            }
            if (withContext(Dispatchers.IO) { ApkUpdater.foreignSigner(ctx, apk) }) {
                apk.delete()
                updateError = tr("Файл оновлення не пройшов перевірку підпису — встановлення скасовано. Завантажуй застосунок лише з офіційної сторінки Savosh на GitHub.")
                return@launch
            }
            downloadedApk = apk
            if (!ApkUpdater.canInstall(ctx)) {
                ApkUpdater.openInstallPermission(ctx)
                updateError = tr("Дозвольте встановлення застосунків і натисніть «Оновити» ще раз.")
                return@launch
            }
            ApkUpdater.install(ctx, apk)
        }
    }

    /** Раз за запуск: щойно є вхід і станції — автоматично опитати всі, щоб показати свіжі дані й online. */
    private fun maybeAutoRefresh() {
        if (autoRefreshDone || stations.isEmpty() || diagJob != null) return
        autoRefreshDone = true
        refreshAllStations()
    }

    // =========================================================== вхід

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            loginError = tr("Введіть email і пароль від застосунку EcoFlow")
            return
        }
        loginError = null
        cloud.forget()
        viewModelScope.launch {
            try {
                cloud.auth()
                screen = Screen.STATIONS
                maybeAutoRefresh()
            } catch (e: ApiException) {
                loginError = e.message
            }
        }
    }

    fun logout() {
        stopDiag()
        stopRefreshAll()
        cloud.forget()
        password = ""
        prefs.password = ""
        // Вихід лише з акаунта EcoFlow — застосунок не закриваємо: майстерня, деталі,
        // замітки й талмуд залишаються доступні. Діагностика станцій вимагатиме входу.
        screen = Screen.STATIONS
    }

    /** Користуватися застосунком без входу в EcoFlow (майстерня, деталі, замітки). */
    fun continueWithoutLogin() {
        loginError = null
        screen = Screen.STATIONS
    }

    /** Перейти на екран входу EcoFlow (для діагностики станцій). */
    fun goToLogin() {
        loginError = null
        screen = Screen.LOGIN
    }

    /** Чи виконано вхід в акаунт EcoFlow. */
    val loggedIn: Boolean get() = auth != null

    // =========================================================== станції

    /** Повертає текст помилки або null, якщо станцію додано. */
    fun addStation(snRaw: String, note: String): String? {
        Models.foreignLetters(snRaw).takeIf { it.isNotEmpty() }?.let {
            return tr("У серійному номері є літери не латиницею: {0}. Перемкніть клавіатуру на англійську.", it.joinToString(" "))
        }
        val sn = Models.extractSn(snRaw)
        if (sn.length < 10) return tr("Серійний номер занадто короткий. Він виглядає приблизно так: R331ZEB4ZE100001")
        if (stations.any { it.sn == sn }) return tr("Така станція вже є у списку")
        val entry = StationEntry(sn, note.trim(), Models.bySn(sn)?.name, null, null, null, null)
        stations.add(0, entry)
        prefs.saveStations(stations.toList())
        scheduleBackup()
        checkNewStation(sn)
        return null
    }

    /** Щойно додану станцію перевіряємо одразу: людина бачить, чи вона онлайн, не заходячи всередину. */
    private fun checkNewStation(sn: String) {
        if (!loggedIn) return
        val onlyNew = !refreshAllBusy
        stopRefreshAll()
        refreshAllStations(if (onlyNew) sn else null)
    }

    fun updateNote(sn: String, note: String) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        stations[i] = stations[i].copy(note = note.trim())
        prefs.saveStations(stations.toList())
        scheduleBackup()
    }

    /** Оновити примітку і теги станції разом. */
    fun updateStationNoteTags(sn: String, note: String, tags: List<String>) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        stations[i] = stations[i].copy(note = note.trim(), tags = tags)
        prefs.saveStations(stations.toList())
        scheduleBackup()
    }

    fun deleteStation(sn: String) {
        stations.removeAll { it.sn == sn }
        prefs.saveStations(stations.toList())
        scheduleBackup()
        prefs.deleteReport(sn)
        prefs.deleteHistory(sn)
        prefs.deleteStationLog(sn)
    }

    fun stationNote(sn: String?): String? = stations.firstOrNull { it.sn == sn }?.note?.takeIf { it.isNotBlank() }

    fun savedReport(sn: String, technical: Boolean = false): String? = prefs.loadReport(sn, technical)

    // =========================================================== діагностика

    fun openDiag(sn: String) {
        // Діагностика потребує входу в EcoFlow. Якщо входу немає й немає збережених даних — на екран входу.
        if (auth == null && (prefs.email.isBlank() || prefs.password.isBlank())) {
            loginError = tr("Увійдіть в акаунт EcoFlow, щоб діагностувати станції.")
            screen = Screen.LOGIN
            return
        }
        if (refreshAllBusy) {
            // Людина обрала конкретну станцію: їй важливіше, а список доопитаємо, коли повернеться.
            stopRefreshAll()
            autoRefreshDone = false
        }
        pausedSn = null
        screen = Screen.DIAG
        startDiag(sn)
    }

    /** Відкрити демо-станцію: несправжні дані, що «оживають», без мережі. */
    fun openDemo() {
        stopDiag()
        isDemo = true
        diagSn = Demo.SN
        report = null
        rawParams = emptyMap()
        powerHistory = emptyList()
        diagError = false
        diagBusy = true
        diagConnected = true
        diagOnline = true
        diagStatus = tr("Демо-режим")
        stationLog = emptyList()
        screen = Screen.DIAG
        var t = 0.0
        demoJob = viewModelScope.launch {
            while (isActive) {
                val p = Demo.params(t)
                val r = Analyzer.analyze(Demo.SN, p, codeNotes)
                report = r
                rawParams = p
                diagBusy = false
                lastUpdateMs = System.currentTimeMillis()
                val packs = r.packs.filter { it.volMv != null && it.ampMa != null }
                if (packs.isNotEmpty()) {
                    val w = packs.sumOf { it.volMv!! * it.ampMa!! / 1_000_000.0 }
                    powerHistory = (powerHistory + (System.currentTimeMillis() to w)).takeLast(300)
                }
                t += 1.0
                delay(2000)
            }
        }
    }

    fun retryDiag() {
        if (isDemo) { openDemo(); return }
        diagSn?.let { startDiag(it, keepData = store.size() > 0) }
    }

    fun refreshNow() {
        if (isDemo) return
        val m = mqtt
        if (m == null || !m.isConnected()) return retryDiag()
        val sn = diagSn ?: return
        addLog(tr("Ручний запит оновлення"))
        viewModelScope.launch(Dispatchers.IO) {
            val fmt = StationLink.requestFormat(
                Models.bySn(sn)?.generation,
                QuotaStore.detectGeneration(store.snapshot().keys),
                store.hasBattery(),
                0,
            )
            m.publish(m.getTopic, StationLink.requestPayload(fmt))
        }
    }

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
            stationLog = prefs.loadStationLog(sn)
            lastLogSaveMs = 0L
            lastCells = if (isDemo) emptyMap() else prefs.loadLastCells(sn)
            lastCellsSaveMs = 0L
        }
        diagError = false
        loggedNotes = 0
        rawLogged = 0
        addLog(tr("──── Діагностика {0} ────", sn))
        addLog(tr("Застосунок {0}, сервер {1}, модель за SN: {2}", ua.savosh.ecoflowdiag.BuildConfig.VERSION_NAME, host, Models.bySn(sn)?.name ?: tr("не впізнано")))
        val link = StationLink(
            sn = sn,
            store = store,
            cloud = cloud,
            listener = diagListener,
            onMessage = { topic, payload -> onMqttMessage(topic, payload) },
            testRunning = { capTestRunning || balanceRunning },
            liveMode = { liveMode },
        )
        diagJob = viewModelScope.launch {
            try {
                link.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagBusy = false
                diagError = true
                diagStatus = tr("Помилка: {0}", e.message ?: e.javaClass.simpleName)
                addLog(tr("Помилка: {0}: {1}", e.javaClass.simpleName, e.message))
            }
        }
    }

    private fun onMqttMessage(topic: String, payload: ByteArray) {
        if (topic.contains("set_reply")) {
            val txt = payload.toString(Charsets.UTF_8)
            viewModelScope.launch(Dispatchers.Main) {
                controlReply = txt
                controlBusy = false
                addLog(tr("← відповідь на команду: {0}", txt.take(200)))
            }
            return
        }
        if (rawLogged < 3) {
            rawLogged++
            addLog(tr("← сирі дані [{0}] {1}", topic.substringAfterLast('/'), preview(payload)))
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
        return tr("двійкові {0} байт: {1}{2}", payload.size, hex, if (payload.size > 48) "…" else "")
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
        diagStatus = tr("Дані отримано: {0} параметрів", snap.size)
        val now = System.currentTimeMillis()
        val withCells = r.packs.filter { it.cellVols.size >= 2 }
        if (withCells.isNotEmpty()) {
            lastCells = lastCells + withCells.associate { it.key to CellSnapshot(now, it.cellVols) }
            if (!isDemo && now - lastCellsSaveMs > 60_000L) {
                lastCellsSaveMs = now
                prefs.saveLastCells(sn, lastCells)
            }
        }
        trackCodes(sn, snap, r, now)
        capTest?.takeIf { it.sn == sn }?.let { t ->
            t.feed(now, r, snap)
            capTick++
        }
        balanceCheck?.takeIf { it.sn == sn }?.let { b ->
            b.feed(now, r, snap)
            balTick++
        }
        if (r.packs.isNotEmpty() && now - lastStationSaveMs > 10000) {
            lastStationSaveMs = now
            saveResult(sn, r)
            logDiag(sn, r, throttle = true)
        }
    }

    /** Порівнює коди з попереднім станом і дописує зміни в історію станції. */
    private fun trackCodes(sn: String, snap: Map<String, Any?>, r: StationReport, now: Long) {
        val trk = tracker ?: return
        // Стани порівнюються як текст, тому історію ведемо однією мовою: зміна мови — не зміна коду.
        val uk = canonicalReport(sn, snap, r)
        val events = I18n.with(AppLang.UK) { trk.update(uk, now) }
        if (events.isNotEmpty()) {
            codeHistory = (events.reversed() + codeHistory).take(CodeHistory.MAX_EVENTS)
            prefs.saveCodeHistory(sn, CodeHistory.eventsToJson(codeHistory))
            for (e in events) addLog(tr("Код: {0}", CodeHistory.line(e)))
        }
        // Стан і час "останньої перевірки" оновлюємо при зміні і раз на 30 с — від нього рахується "між перевірками".
        if (trk.dirty || now - lastCodeStateSaveMs > 30000) {
            prefs.saveCodeState(sn, CodeHistory.statesToJson(trk.knownStates(), now))
            trk.markSaved()
            lastCodeStateSaveMs = now
        }
    }

    fun clearCodeHistory() {
        val sn = diagSn ?: return
        codeHistory = emptyList()
        prefs.saveCodeHistory(sn, "[]")
        addLog(tr("Історію кодів станції {0} очищено", sn))
    }

    private fun saveResult(sn: String, r: StationReport) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        val snap = store.snapshot()
        val uk = canonicalReport(sn, snap, r)
        val worstSoh = r.packs.mapNotNull { it.sohEffective }.minOrNull()
        val soc = r.packs.firstOrNull { it.isMain }?.soc ?: r.packs.firstOrNull()?.soc
        stations[i] = stations[i].copy(
            model = uk.model ?: stations[i].model,
            lastSoh = worstSoh,
            lastLevel = r.level.name,
            lastSummary = uk.summary,
            lastSoc = soc ?: stations[i].lastSoc,
            online = store.online != false,
            lastCheckMs = System.currentTimeMillis(),
        )
        prefs.saveStations(stations.toList())
        saveReports(sn, snap, stations[i].note, historyFor(sn), logFor(sn))
        reportNewCodes(uk)
    }

    /** Те, що зберігається надовго, пишемо українською: показ переводить це поточною мовою (див. I18n.localize). */
    private fun canonicalReport(sn: String, snap: Map<String, Any?>, current: StationReport): StationReport =
        if (I18n.current == AppLang.UK) current else I18n.with(AppLang.UK) { Analyzer.analyze(sn, snap, codeNotes) }

    /** Готові звіти кожною мовою: щоб після зміни мови останній звіт станції не лишився старою. */
    private fun saveReports(sn: String, snap: Map<String, Any?>, note: String, history: List<CodeEvent>, log: List<LogEntry>) {
        val date = dateText()
        for (l in AppLang.entries) {
            I18n.with(l) {
                val rr = Analyzer.analyze(sn, snap, codeNotes)
                prefs.saveReport(sn, Report.client(rr, note, date), technical = false)
                prefs.saveReport(sn, Report.technical(rr, note, date, history, log), technical = true)
            }
        }
    }

    // =========================================================== масове оновлення

    private class QuickResult(val report: StationReport?, val online: Boolean?, val snapshot: Map<String, Any?> = emptyMap())

    /** Опитує всі станції зі списку по черзі й оновлює їхні картки (заряд, SOH, online/offline). */
    fun refreshAllStations(onlySn: String? = null) {
        if (refreshAllBusy) return
        if (stations.isEmpty()) return
        // Відкриту діагностику не чіпаємо: раніше це лишало людину на «Підготовка…» без жодної кнопки.
        if (diagJob != null) return
        refreshAllJob = viewModelScope.launch {
            refreshAllBusy = true
            try {
                cloud.auth()
                val list = stations.filter { onlySn == null || it.sn == onlySn }
                for ((idx, st) in list.withIndex()) {
                    if (!isActive) break
                    refreshAllStatus = tr("Опитую {0}/{1}: {2}", idx + 1, list.size, st.sn)
                    val res = try {
                        fetchOne(st.sn)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        QuickResult(null, null)
                    }
                    applyQuick(st.sn, res)
                }
                val online = stations.count { it.online == true }
                refreshAllStatus = if (onlySn == null) tr("Готово: онлайн {0} з {1}", online, list.size) else ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                refreshAllStatus = e.message ?: tr("Помилка входу")
            } catch (e: Exception) {
                refreshAllStatus = tr("Помилка: {0}", e.message ?: e.javaClass.simpleName)
            } finally {
                refreshAllBusy = false
            }
        }
    }

    fun stopRefreshAll() {
        refreshAllJob?.cancel()
        refreshAllJob = null
        refreshAllBusy = false
        refreshAllStatus = ""
    }

    /** Один короткий сеанс: під'єднатися, дочекатися даних батареї (до ~12 с), проаналізувати. */
    private suspend fun fetchOne(sn: String): QuickResult {
        val localStore = QuotaStore()
        val link = StationLink(
            sn = sn,
            store = localStore,
            cloud = cloud,
            listener = quietListener,
            onMessage = { topic, payload -> MessageDecoder.decode(topic, payload)?.let { localStore.apply(it) } },
        )
        link.pollOnce()
        val snap = localStore.snapshot()
        val report = if (snap.isNotEmpty()) Analyzer.analyze(sn, snap, codeNotes) else null
        val online = when {
            localStore.online == false -> false
            snap.isNotEmpty() -> true
            else -> null
        }
        return QuickResult(report, online, snap)
    }

    private fun applyQuick(sn: String, res: QuickResult) {
        val i = stations.indexOfFirst { it.sn == sn }
        if (i < 0) return
        // Знімок без батареї (прийшла лише потужність виходів) нічого не каже про стан:
        // картку й збережені звіти він не затирає.
        val r = res.report?.takeIf { it.packs.isNotEmpty() }
        val uk = res.report?.let { canonicalReport(sn, res.snapshot, it) }
        val worstSoh = r?.packs?.mapNotNull { it.sohEffective }?.minOrNull()
        val soc = r?.packs?.firstOrNull { it.isMain }?.soc ?: r?.packs?.firstOrNull()?.soc
        stations[i] = stations[i].copy(
            model = uk?.model ?: stations[i].model,
            lastSoh = worstSoh ?: stations[i].lastSoh,
            lastLevel = r?.level?.name ?: stations[i].lastLevel,
            lastSummary = if (r != null) uk?.summary else stations[i].lastSummary,
            lastSoc = soc ?: stations[i].lastSoc,
            online = res.online,
            lastCheckMs = if (r != null) System.currentTimeMillis() else stations[i].lastCheckMs,
        )
        prefs.saveStations(stations.toList())
        if (r != null) {
            logDiag(sn, r, throttle = false)
            saveReports(sn, res.snapshot, stations[i].note, emptyList(), logFor(sn))
        }
    }

    /** Звіт поточної діагностики: клієнтський (без службових полів) або технічний. */
    fun reportText(technical: Boolean): String? {
        val r = report ?: return null
        val note = stationNote(r.sn)
        return if (technical) Report.technical(r, note, dateText(), historyFor(r.sn), logFor(r.sn)) else Report.client(r, note, dateText())
    }

    private fun historyFor(sn: String): List<CodeEvent> = if (sn == diagSn) codeHistory else emptyList()

    private fun logFor(sn: String): List<LogEntry> = if (sn == diagSn) stationLog else prefs.loadStationLog(sn)

    /**
     * Знімок діагностики в журнал станції (один на добу, свіжіший замінює). [throttle] — під час живої
     * діагностики пишемо не частіше разу на 5 хв; при виході з діагностики й масовому оновленні — одразу.
     */
    private fun logDiag(sn: String, r: StationReport, throttle: Boolean) {
        if (isDemo && sn == Demo.SN) return
        val now = System.currentTimeMillis()
        if (throttle && now - lastLogSaveMs < 5 * 60_000L) return
        val main = r.packs.firstOrNull { it.isMain } ?: r.packs.firstOrNull() ?: return
        val soh = r.packs.mapNotNull { it.sohEffective }.minOrNull()
        if (soh == null && main.cycles == null) return
        if (throttle) lastLogSaveMs = now
        appendLog(sn, LogEntry(now, StationLog.DIAG, soh = soh, cycles = main.cycles, deltaMv = main.cellDeltaMv, soc = main.soc), backup = false)
    }

    private fun appendLog(sn: String, e: LogEntry, backup: Boolean = true) {
        val upd = StationLog.add(logFor(sn), e)
        prefs.saveStationLog(sn, upd)
        if (sn == diagSn) stationLog = upd
        if (backup) scheduleBackup()
    }

    // ======================= Звіти: редактор + збережені =======================
    val savedReportDocs = mutableStateListOf<SavedReport>()
    var reportExtended by mutableStateOf<Boolean?>(null) // null = вид ще не обрано (показуємо плитки)
    var reportDraft by mutableStateOf("")
    var reportSavedMsg by mutableStateOf<String?>(null)
    private var reportCtxSn: String? = null
    private var reportCtxModel: String? = null

    /** Відкрити екран звіту (вибір виду + збережені). */
    fun openReport() {
        reportExtended = null
        reportDraft = ""
        reportSavedMsg = null
        reportCtxSn = diagSn ?: report?.sn
        reportCtxModel = report?.model
        reloadReportDocs()
        screen = Screen.REPORT
    }

    /** Обрати вид звіту — згенерувати текст у редактор. */
    fun chooseReport(extended: Boolean) {
        reportExtended = extended
        reportDraft = reportText(extended) ?: tr("ЗВІТ\n\nНемає даних діагностики для звіту.")
        reportSavedMsg = null
    }

    fun editReportDraft(t: String) {
        reportDraft = t
        reportSavedMsg = null
    }

    /** Зберегти (можливо відредагований) звіт у програмі. */
    fun saveReportDraft() {
        if (reportDraft.isBlank()) return
        val id = prefs.nextReportDocId
        prefs.nextReportDocId = id + 1
        val doc = SavedReport(
            id = id,
            sn = reportCtxSn ?: "",
            model = reportCtxModel,
            dateMs = System.currentTimeMillis(),
            extended = reportExtended ?: false,
            text = reportDraft,
        )
        val list = prefs.loadReportDocs().toMutableList()
        list.add(0, doc)
        prefs.saveReportDocs(list)
        reloadReportDocs()
        reportSavedMsg = tr("✓ Звіт збережено в програмі.")
        scheduleBackup()
    }

    fun openSavedReport(doc: SavedReport) {
        reportExtended = doc.extended
        reportDraft = doc.text
        reportCtxSn = doc.sn
        reportCtxModel = doc.model
        reportSavedMsg = null
        screen = Screen.REPORT
    }

    fun deleteSavedReport(id: Long) {
        prefs.saveReportDocs(prefs.loadReportDocs().filter { it.id != id })
        reloadReportDocs()
        scheduleBackup()
    }

    /** Повернутись від редактора до вибору виду. */
    fun backFromReportEditor() {
        reportExtended = null
        reportSavedMsg = null
    }

    private fun reloadReportDocs() {
        savedReportDocs.clear()
        savedReportDocs.addAll(prefs.loadReportDocs())
    }

    /** Тихо надіслати нові коди помилок у особистий чат майстра (щоб вивчити й додати опис). */
    private fun reportNewCodes(r: StationReport) {
        if (!TelegramConfig.configured()) return
        val sent = prefs.sentCodes()
        val collected = LinkedHashMap<String, String>()
        fun consider(ind: Indicator) {
            val key = ind.codeKey ?: return
            if (ind.level.rank < Level.WARN.rank) return
            if (key in sent || key in collected) return
            val known = if (EcoflowCodes.hasExplanation(key)) tr("відомий") else tr("НОВИЙ")
            collected[key] = "• $key = ${ind.value} — ${ind.title} [$known]"
        }
        r.protections.forEach(::consider)
        r.packs.forEach { pk -> pk.codes.forEach(::consider) }
        if (collected.isEmpty()) return
        val msg = buildString {
            append(tr("🔧 Коди зі станції ")).append(r.model ?: "?")
            append(" (").append(r.generation.title).append(")\n")   // без SN: коди анонімні
            append(collected.values.joinToString("\n"))
        }
        val keys = collected.keys.toList()
        viewModelScope.launch(Dispatchers.IO) {
            if (TelegramSubmit.sendCodes(msg)) prefs.addSentCodes(keys)
        }
    }

    fun codeNote(key: String): String = codeNotes[key] ?: ""

    /** Зберегти примітку до коду (порожній текст — видалити) і одразу перерахувати звіт. */
    fun setCodeNote(key: String, text: String) {
        val t = text.trim()
        val m = LinkedHashMap(codeNotes)
        if (t.isEmpty()) m.remove(key) else m[key] = t
        codeNotes = m
        prefs.saveCodeNotes(m)
        addLog(if (t.isEmpty()) tr("Примітку до коду {0} видалено", key) else tr("Примітка до коду {0}: {1}", key, t))
        recompute()
    }

    fun rawJson(): String = ua.savosh.ecoflowdiag.core.Json.stringify(rawParams)

    fun logText(): String = log.joinToString("\n") { "${it.time} ${it.text}" }

    fun stopDiag() {
        diagJob?.cancel()
        diagJob = null
        demoJob?.cancel()
        demoJob = null
        isDemo = false
        mqtt?.close()
        mqtt = null
        diagConnected = false
        diagBusy = false
        cloud.resetServers()
    }

    // =========================================================== життєвий цикл

    /** Застосунок згорнуто: розриваємо з'єднання, щоб воно не "зависало" у фоні. */
    fun onAppBackground() {
        if (prefs.gdConnected) viewModelScope.launch(Dispatchers.IO) { try { pushGoogle() } catch (e: Exception) { } }
        if (refreshAllBusy) {
            // У фоні система ріже мережу: опитування лише позначило б усі станції як «невідомо».
            stopRefreshAll()
            autoRefreshDone = false
        }
        val sn = diagSn
        if (capTestRunning || balanceRunning) {
            addLog(tr("Застосунок згорнуто, але йде {0} — зв'язок не розриваємо", if (capTestRunning) tr("тест ємності") else tr("перевірка балансу")))
            return
        }
        if (diagJob != null && sn != null && screen != Screen.LOGIN && screen != Screen.STATIONS) {
            pausedSn = sn
            stopDiag()
            addLog(tr("Застосунок згорнуто — зв'язок призупинено"))
        }
    }

    /** Застосунок знову на екрані: відновлюємо діагностику, зберігаючи вже отримані дані. */
    fun onAppForeground() {
        val sn = pausedSn
        if (sn == null) {
            if (auth != null) maybeAutoRefresh()
            return
        }
        pausedSn = null
        // і з екранів тесту ємності / перевірки балансу: інакше після згортання дані туди більше не йшли б
        if (screen == Screen.DIAG || screen == Screen.RAW || screen == Screen.LOG || screen == Screen.CAPTEST || screen == Screen.BALANCE) {
            addLog(tr("Застосунок відкрито — відновлення зв'язку"))
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

    fun capStartCharge() = capAction { it.startCharge(System.currentTimeMillis()); addLog(tr("Тест ємності: етап 1 (заряд) почато")) }
    fun capFinishCharge() = capAction { it.finishCharge(System.currentTimeMillis()); addLog(tr("Тест ємності: етап 1 завершено, ≈{0} Вт·год", Analyzer.fmt0(it.charge.wh))) }
    fun capStartDischarge() = capAction { it.startDischarge(System.currentTimeMillis()); addLog(tr("Тест ємності: етап 2 (розряд) почато")) }
    fun capFinishDischarge() = capAction { t ->
        t.finishDischarge(System.currentTimeMillis())
        addLog(tr("Тест ємності завершено: ≈{0} Вт·год", Analyzer.fmt0(t.discharge.wh)))
        for (l in AppLang.entries) I18n.with(l) { prefs.saveCapTest(t.sn, t.summaryText(report?.model, dateText())) }
        t.measuredWh?.let { m ->
            val text = I18n.with(AppLang.UK) {
                val pct = t.sohMeasured?.let { tr(" — {0}% від номіналу BMS", Analyzer.fmt0(it)) } ?: ""
                tr("≈{0} Вт·год ({1} А·год){2}", Analyzer.fmt0(m), Analyzer.fmt1(t.discharge.ah), pct)
            }
            appendLog(t.sn, LogEntry(System.currentTimeMillis(), StationLog.CAPACITY, soh = t.sohMeasured, text = text))
        }
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

    // =========================================================== перевірка балансу

    fun openBalance() {
        val sn = diagSn ?: return
        if (balanceCheck?.sn != sn) {
            balanceCheck = BalanceCheck(sn)
            report?.let { balanceCheck!!.feed(System.currentTimeMillis(), it, rawParams) }
        }
        balTick++
        screen = Screen.BALANCE
    }

    fun balanceStart() = balAction { it.start(System.currentTimeMillis()); addLog(tr("Перевірка балансу почата")) }

    fun balanceFinish() = balAction { b ->
        b.finish(System.currentTimeMillis())
        for (l in AppLang.entries) I18n.with(l) { prefs.saveBalanceCheck(b.sn, b.summaryText(report?.model, dateText())) }
        addLog(tr("Перевірка балансу завершена: {0}", b.shortResult()))
        val w = b.worst()
        appendLog(b.sn, LogEntry(System.currentTimeMillis(), StationLog.BALANCE, deltaMv = w?.deltaMv, soc = w?.soc, text = I18n.with(AppLang.UK) { b.shortResult() }))
    }

    fun balanceReset() {
        val sn = diagSn ?: return
        balanceCheck = BalanceCheck(sn)
        report?.let { balanceCheck!!.feed(System.currentTimeMillis(), it, rawParams) }
        balTick++
    }

    fun balanceResultText(): String? = balanceCheck?.let { b ->
        if (b.phase == BalanceCheck.Phase.IDLE) null else b.summaryText(report?.model, dateText())
    }

    fun lastBalanceCheck(sn: String): String? = prefs.loadBalanceCheck(sn)

    private inline fun balAction(block: (BalanceCheck) -> Unit) {
        val b = balanceCheck ?: return
        block(b)
        balTick++
    }

    // =========================================================== керування станцією

    fun openFeedback() {
        rememberReturn()
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
            controlReply = tr("Немає з'єднання зі станцією. Поверніться й зачекайте, поки діагностика під'єднається.")
            return
        }
        controlSent = "${cmd.title}\n${cmd.json}"
        controlReply = null
        controlBusy = true
        addLog(tr("→ команда: {0} {1}", cmd.title, cmd.json))
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { m.publish(m.setTopic, cmd.json.toByteArray(Charsets.UTF_8)) }
            if (!ok) {
                controlBusy = false
                controlReply = tr("Не вдалося надіслати команду (помилка зв'язку).")
                return@launch
            }
            delay(6000)
            if (controlBusy) {
                controlBusy = false
                if (controlReply == null) {
                    controlReply = tr("Станція не відповіла за 6 с. Можливо, цей формат команди не підходить для цієї моделі — напишіть розробнику, яка модель, і формат підправлять.")
                }
            }
        }
    }

    fun openFlash() {
        rememberReturn()
        screen = Screen.FLASH
    }

    /** Колишній хаб «Деталі» тепер — розділ «Ще». */
    fun openParts() = openMore()

    /** SN станції для екрана «Повна інформація про станцію». */
    var infoSn by mutableStateOf<String?>(null)
        private set

    fun openStationInfo(sn: String) {
        infoSn = sn
        screen = Screen.STATIONINFO
    }

    fun openLog() {
        screen = Screen.LOG
    }

    /** true — подію "назад" оброблено. */
    fun back(): Boolean = when (screen) {
        Screen.FLASH, Screen.KNOWLEDGE, Screen.NOTES, Screen.BACKUP, Screen.FEEDBACK -> {
            screen = secondaryReturn
            true
        }
        Screen.CRM -> {
            if (crmTab == 3) crmTab = 0 else screen = Screen.STATIONS   // з корзини — до замовлень
            true
        }
        Screen.PARTS, Screen.STATIONINFO, Screen.MORE -> {
            screen = Screen.STATIONS
            true
        }
        Screen.RAW, Screen.LOG, Screen.CAPTEST, Screen.BALANCE, Screen.CONTROL -> {
            screen = if (diagSn != null) Screen.DIAG else Screen.STATIONS
            true
        }
        Screen.REPORT -> {
            if (reportExtended != null) {
                reportExtended = null
                reportSavedMsg = null
            } else {
                screen = if (diagSn != null) Screen.DIAG else Screen.STATIONS
            }
            true
        }
        Screen.DIAG -> if (capTestRunning) {
            // поки йде тест ємності, з'єднання не рвемо — повертаємось на екран тесту
            screen = Screen.CAPTEST
            true
        } else if (balanceRunning) {
            // так само під час перевірки балансу
            screen = Screen.BALANCE
            true
        } else {
            diagSn?.let { sn ->
                val r = report
                if (r != null && r.packs.isNotEmpty()) {
                    saveResult(sn, r)
                    logDiag(sn, r, throttle = false)
                } else {
                    // Батареї так і не дочекались: на картці лишаємо хоча б те, що сказала хмара.
                    val i = stations.indexOfFirst { it.sn == sn }
                    if (i >= 0 && store.online != null) {
                        stations[i] = stations[i].copy(online = store.online)
                        prefs.saveStations(stations.toList())
                    }
                }
            }
            stopDiag()
            pausedSn = null
            screen = Screen.STATIONS
            maybeAutoRefresh()
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

    override fun onCleared() {
        stopDiag()
        super.onCleared()
    }
}
