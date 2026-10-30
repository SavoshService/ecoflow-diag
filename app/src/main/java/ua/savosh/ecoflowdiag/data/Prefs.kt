package ua.savosh.ecoflowdiag.data

import android.content.Context
import android.content.SharedPreferences
import ua.savosh.ecoflowdiag.core.Article
import ua.savosh.ecoflowdiag.core.ChecklistItem
import ua.savosh.ecoflowdiag.core.Json
import ua.savosh.ecoflowdiag.core.Note
import ua.savosh.ecoflowdiag.core.Order
import ua.savosh.ecoflowdiag.core.PartItem

/** Збережена станція клієнта. */
data class StationEntry(
    val sn: String,
    val note: String,
    val model: String?,
    val lastSoh: Double?,
    val lastLevel: String?,
    val lastSummary: String?,
    val lastCheckMs: Long?,
    val lastSoc: Double? = null,
    val online: Boolean? = null,
    val tags: List<String> = emptyList(),
)

/** Збережений (можливо відредагований майстром) звіт. */
data class SavedReport(
    val id: Long,
    val sn: String,
    val model: String?,
    val dateMs: Long,
    val extended: Boolean,
    val text: String,
)

/** Налаштування та список станцій. Зберігається лише локально на телефоні. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("savosh_ecoflow", Context.MODE_PRIVATE)

    var email: String
        get() = sp.getString("email", "") ?: ""
        set(v) = sp.edit().putString("email", v).apply()

    var host: String
        get() = sp.getString("host", "api.ecoflow.com") ?: "api.ecoflow.com"
        set(v) = sp.edit().putString("host", v).apply()

    var rememberPassword: Boolean
        get() = sp.getBoolean("remember", true)
        set(v) = sp.edit().putBoolean("remember", v).apply()

    private val secure = SecureStore(context)

    /**
     * Пароль EcoFlow — лише зашифрований ключем Android Keystore (див. [SecureStore]).
     * Пароль зі старих версій, збережений відкритим текстом, при першому читанні переноситься в шифроване сховище і стирається.
     */
    var password: String
        get() {
            val legacy = sp.getString("password", null)
            if (!legacy.isNullOrEmpty()) {
                secure.put("password", legacy)
                sp.edit().remove("password").apply()
            }
            return secure.get("password") ?: ""
        }
        set(v) {
            secure.put("password", v)
            sp.edit().remove("password").apply()
        }

    fun loadStations(): List<StationEntry> {
        val text = sp.getString("stations", null) ?: return emptyList()
        val list = Json.parseOrNull(text) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val sn = m["sn"]?.toString() ?: return@mapNotNull null
            StationEntry(
                sn = sn,
                note = m["note"]?.toString() ?: "",
                model = m["model"]?.toString(),
                lastSoh = (m["lastSoh"] as? Number)?.toDouble(),
                lastLevel = m["lastLevel"]?.toString(),
                lastSummary = m["lastSummary"]?.toString(),
                lastCheckMs = (m["lastCheckMs"] as? Number)?.toLong(),
                lastSoc = (m["lastSoc"] as? Number)?.toDouble(),
                online = m["online"] as? Boolean,
                tags = (m["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            )
        }
    }

    fun saveStations(list: List<StationEntry>) {
        val arr = list.map {
            linkedMapOf<String, Any?>(
                "sn" to it.sn,
                "note" to it.note,
                "model" to it.model,
                "lastSoh" to it.lastSoh,
                "lastLevel" to it.lastLevel,
                "lastSummary" to it.lastSummary,
                "lastCheckMs" to it.lastCheckMs,
                "lastSoc" to it.lastSoc,
                "online" to it.online,
                "tags" to it.tags,
            )
        }
        sp.edit().putString("stations", Json.stringify(arr)).apply()
    }

    /** Останній звіт станції: клієнтський (report_) або технічний (reportTech_). */
    fun saveReport(sn: String, text: String, technical: Boolean = false) =
        sp.edit().putString(reportKey(sn, technical), text).apply()

    fun loadReport(sn: String, technical: Boolean = false): String? = sp.getString(reportKey(sn, technical), null)

    fun deleteReport(sn: String) = sp.edit().remove(reportKey(sn, false)).remove(reportKey(sn, true)).remove("capTest_$sn").apply()

    private fun reportKey(sn: String, technical: Boolean) = if (technical) "reportTech_$sn" else "report_$sn"

    // ===== Збережені звіти майстра (список) =====
    fun loadReportDocs(): List<SavedReport> {
        val arr = Json.parseOrNull(sp.getString("saved_docs", "[]") ?: "[]") as? List<*> ?: return emptyList()
        return arr.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            SavedReport(
                id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null,
                sn = m["sn"] as? String ?: "",
                model = m["model"] as? String,
                dateMs = (m["dateMs"] as? Number)?.toLong() ?: 0L,
                extended = m["extended"] as? Boolean ?: false,
                text = m["text"] as? String ?: "",
            )
        }
    }

    fun saveReportDocs(list: List<SavedReport>) {
        val arr = list.map {
            linkedMapOf<String, Any?>(
                "id" to it.id, "sn" to it.sn, "model" to it.model,
                "dateMs" to it.dateMs, "extended" to it.extended, "text" to it.text,
            )
        }
        sp.edit().putString("saved_docs", Json.stringify(arr)).apply()
    }

    var nextReportDocId: Long
        get() = sp.getLong("saved_docs_next", 1L)
        set(v) = sp.edit().putLong("saved_docs_next", v).apply()

    // ===== Коди, вже відправлені в бот (щоб не слати двічі) =====
    fun sentCodes(): Set<String> =
        (sp.getString("sent_codes", "") ?: "").split('\n').filter { it.isNotBlank() }.toSet()

    fun addSentCodes(keys: Collection<String>) {
        val s = LinkedHashSet(sentCodes())
        s.addAll(keys)
        sp.edit().putString("sent_codes", s.joinToString("\n")).apply()
    }

    /** Останній результат тесту реальної ємності станції (текст звіту). */
    fun saveCapTest(sn: String, text: String) = sp.edit().putString("capTest_$sn", text).apply()

    fun loadCapTest(sn: String): String? = sp.getString("capTest_$sn", null)

    /** Чи сховав користувач демо-станцію зі списку. */
    var demoHidden: Boolean
        get() = sp.getBoolean("demoHidden", false)
        set(v) = sp.edit().putBoolean("demoHidden", v).apply()

    /** Остання версія, про яку вже показали сповіщення (щоб не повторювати). */
    var lastNotifiedVersion: String
        get() = sp.getString("lastNotifiedVersion", "") ?: ""
        set(v) = sp.edit().putString("lastNotifiedVersion", v).apply()

    /** Історія змін кодів станції (JSON, новіші першими) і останній відомий стан кодів. */
    fun loadCodeHistory(sn: String): String? = sp.getString("codeHistory_$sn", null)

    fun saveCodeHistory(sn: String, json: String) = sp.edit().putString("codeHistory_$sn", json).apply()

    fun loadCodeState(sn: String): String? = sp.getString("codeState_$sn", null)

    fun saveCodeState(sn: String, json: String) = sp.edit().putString("codeState_$sn", json).apply()

    fun deleteHistory(sn: String) = sp.edit().remove("codeHistory_$sn").remove("codeState_$sn").apply()

    /**
     * Власна база кодів: ключ "bms:23", "inv:…", "pd:…", "mppt:…" → ваша примітка.
     * Поповнюється з екрана діагностики і застосовується до всіх станцій.
     */
    fun loadCodeNotes(): Map<String, String> {
        val text = sp.getString("code_notes", null) ?: return emptyMap()
        val m = Json.parseOrNull(text) as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val note = v?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            key to note
        }.toMap()
    }

    fun saveCodeNotes(notes: Map<String, String>) {
        sp.edit().putString("code_notes", Json.stringify(LinkedHashMap<String, Any?>(notes))).apply()
    }

    // ======================= Майстерня (CRM) =======================

    /** Наступний номер замовлення (монотонно зростає, не повторюється навіть після видалення). */
    var nextOrderId: Long
        get() = sp.getLong("crm_next_id", 1L)
        set(v) = sp.edit().putLong("crm_next_id", v).apply()

    fun loadOrders(): List<Order> {
        val text = sp.getString("crm_orders", null) ?: return emptyList()
        val list = Json.parseOrNull(text) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
            Order(
                id = id,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                completedAt = (m["completedAt"] as? Number)?.toLong(),
                deviceType = m["deviceType"]?.toString() ?: "",
                model = m["model"]?.toString() ?: "",
                clientName = m["clientName"]?.toString() ?: "Невідомо",
                phone = m["phone"]?.toString() ?: "",
                channel = m["channel"]?.toString() ?: "call",
                description = m["description"]?.toString() ?: "",
                status = m["status"]?.toString() ?: "red",
                partsCost = (m["partsCost"] as? Number)?.toDouble() ?: 0.0,
                totalCost = (m["totalCost"] as? Number)?.toDouble() ?: 0.0,
                deleted = m["deleted"] as? Boolean ?: false,
                deletedAt = (m["deletedAt"] as? Number)?.toLong(),
                photos = (m["photos"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            )
        }
    }

    fun saveOrders(list: List<Order>) {
        val arr = list.map {
            linkedMapOf<String, Any?>(
                "id" to it.id,
                "createdAt" to it.createdAt,
                "updatedAt" to it.updatedAt,
                "completedAt" to it.completedAt,
                "deviceType" to it.deviceType,
                "model" to it.model,
                "clientName" to it.clientName,
                "phone" to it.phone,
                "channel" to it.channel,
                "description" to it.description,
                "status" to it.status,
                "partsCost" to it.partsCost,
                "totalCost" to it.totalCost,
                "deleted" to it.deleted,
                "deletedAt" to it.deletedAt,
                "photos" to it.photos,
            )
        }
        sp.edit().putString("crm_orders", Json.stringify(arr)).apply()
    }

    // ----- Налаштування бухгалтерії -----

    /** Власний відсоток відрахування (резерв/податок) від чистого прибутку. 0 — вимкнено. */
    var reservePercent: Double
        get() = sp.getString("crm_reserve_pct", "0")?.toDoubleOrNull() ?: 0.0
        set(v) = sp.edit().putString("crm_reserve_pct", v.toString()).apply()

    /** Спрощена бухгалтерія: ховати «Запчастини» й «Оборот», показувати лише чистий прибуток. */
    var simpleAccounting: Boolean
        get() = sp.getBoolean("crm_simple_acc", false)
        set(v) = sp.edit().putBoolean("crm_simple_acc", v).apply()

    // ----- Склад деталей майстерні -----

    var nextPartId: Long
        get() = sp.getLong("crm_next_part_id", 1L)
        set(v) = sp.edit().putLong("crm_next_part_id", v).apply()

    fun loadParts(): List<PartItem> {
        val text = sp.getString("crm_parts", null) ?: return emptyList()
        val list = Json.parseOrNull(text) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
            PartItem(
                id = id,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                name = m["name"]?.toString() ?: "",
                quantity = (m["quantity"] as? Number)?.toInt() ?: 0,
                location = m["location"]?.toString() ?: "",
                code = m["code"]?.toString() ?: "",
                note = m["note"]?.toString() ?: "",
                price = (m["price"] as? Number)?.toDouble() ?: 0.0,
            )
        }
    }

    fun saveParts(list: List<PartItem>) {
        val arr = list.map {
            linkedMapOf<String, Any?>(
                "id" to it.id,
                "createdAt" to it.createdAt,
                "updatedAt" to it.updatedAt,
                "name" to it.name,
                "quantity" to it.quantity,
                "location" to it.location,
                "code" to it.code,
                "note" to it.note,
                "price" to it.price,
            )
        }
        sp.edit().putString("crm_parts", Json.stringify(arr)).apply()
    }

    // ----- База знань майстра -----

    /** Остання версія, для якої вже показали «Що нового». */
    var lastWhatsNewVersion: String
        get() = sp.getString("whatsnew_version", "") ?: ""
        set(v) = sp.edit().putString("whatsnew_version", v).apply()

    /** Чи бачила людина вікно згоди про надсилання статей і кодів у спільну базу. */
    var shareConsentShown: Boolean
        get() = sp.getBoolean("share_consent_shown", false)
        set(v) = sp.edit().putBoolean("share_consent_shown", v).apply()

    var nextArticleId: Long
        get() = sp.getLong("kb_next_id", 1L)
        set(v) = sp.edit().putLong("kb_next_id", v).apply()

    fun loadArticles(): List<Article> {
        val text = sp.getString("kb_articles", null) ?: return emptyList()
        val list = Json.parseOrNull(text) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
            Article(
                id = id,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                section = m["section"]?.toString() ?: "Загальне",
                subsection = m["subsection"]?.toString() ?: "",
                title = m["title"]?.toString() ?: "",
                marking = m["marking"]?.toString() ?: "",
                analogs = m["analogs"]?.toString() ?: "",
                body = m["body"]?.toString() ?: "",
                photos = (m["photos"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                official = false,
                author = m["author"]?.toString() ?: "",
            )
        }
    }

    fun saveArticles(list: List<Article>) {
        val arr = list.filter { !it.official }.map {
            linkedMapOf<String, Any?>(
                "id" to it.id,
                "createdAt" to it.createdAt,
                "updatedAt" to it.updatedAt,
                "section" to it.section,
                "subsection" to it.subsection,
                "title" to it.title,
                "marking" to it.marking,
                "analogs" to it.analogs,
                "body" to it.body,
                "photos" to it.photos,
                "author" to it.author,
            )
        }
        sp.edit().putString("kb_articles", Json.stringify(arr)).apply()
    }

    // ----- Записник (нотатки) -----

    var nextNoteId: Long
        get() = sp.getLong("notes_next_id", 1L)
        set(v) = sp.edit().putLong("notes_next_id", v).apply()

    fun loadNotes(): List<Note> {
        val text = sp.getString("notes", null) ?: return emptyList()
        val list = Json.parseOrNull(text) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
            val items = (m["items"] as? List<*>)?.mapNotNull { it0 ->
                val im = it0 as? Map<*, *> ?: return@mapNotNull null
                ChecklistItem(im["text"]?.toString() ?: "", im["done"] as? Boolean ?: false)
            } ?: emptyList()
            Note(
                id = id,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                kind = m["kind"]?.toString() ?: "plain",
                title = m["title"]?.toString() ?: "",
                body = m["body"]?.toString() ?: "",
                items = items,
                stationSn = m["stationSn"]?.toString() ?: "",
                reminderAt = (m["reminderAt"] as? Number)?.toLong(),
            )
        }
    }

    fun saveNotes(list: List<Note>) {
        val arr = list.map { n ->
            linkedMapOf<String, Any?>(
                "id" to n.id,
                "createdAt" to n.createdAt,
                "updatedAt" to n.updatedAt,
                "kind" to n.kind,
                "title" to n.title,
                "body" to n.body,
                "items" to n.items.map { linkedMapOf<String, Any?>("text" to it.text, "done" to it.done) },
                "stationSn" to n.stationSn,
                "reminderAt" to n.reminderAt,
            )
        }
        sp.edit().putString("notes", Json.stringify(arr)).apply()
    }

    // ======================= Резервна копія =======================

    /** Збережена адреса файлу копії (SAF Uri). Порожньо — копію ще не налаштовано. */
    var backupUri: String
        get() = sp.getString("backup_uri", "") ?: ""
        set(v) = sp.edit().putString("backup_uri", v).apply()

    /** Чи пропонували вже відновлення після встановлення (щоб не нагадувати щоразу). */
    var restoreOffered: Boolean
        get() = sp.getBoolean("restore_offered", false)
        set(v) = sp.edit().putBoolean("restore_offered", v).apply()

    fun hasAnyData(): Boolean =
        !sp.getString("stations", null).isNullOrBlank() ||
            !sp.getString("crm_orders", null).isNullOrBlank() ||
            !sp.getString("crm_parts", null).isNullOrBlank() ||
            !sp.getString("kb_articles", null).isNullOrBlank() ||
            !sp.getString("notes", null).isNullOrBlank()

    /** Уся база застосунку одним JSON (для файлу копії / Google Drive). */
    fun exportBundle(): String {
        fun section(key: String): Any? = Json.parseOrNull(sp.getString(key, "[]") ?: "[]")
        val m = linkedMapOf<String, Any?>(
            "app" to APP_TAG,
            "schema" to 1,
            "exportedAt" to System.currentTimeMillis(),
            "stations" to section("stations"),
            "crm_orders" to section("crm_orders"),
            "crm_parts" to section("crm_parts"),
            "kb_articles" to section("kb_articles"),
            "notes" to section("notes"),
            "saved_docs" to section("saved_docs"),
            "settings" to linkedMapOf<String, Any?>(
                "email" to email,
                "host" to host,
                "reservePercent" to reservePercent,
                "simpleAccounting" to simpleAccounting,
                "demoHidden" to demoHidden,
                "crm_next_id" to nextOrderId,
                "crm_next_part_id" to nextPartId,
                "kb_next_id" to nextArticleId,
                "notes_next_id" to nextNoteId,
                "saved_docs_next" to nextReportDocId,
            ),
        )
        return Json.stringify(m)
    }

    /** Відновлює базу з JSON копії. Повертає true, якщо файл валідний. */
    fun importBundle(json: String): Boolean {
        val m = Json.parseOrNull(json) as? Map<*, *> ?: return false
        if (m["app"] != APP_TAG) return false
        val e = sp.edit()
        fun put(key: String) { m[key]?.let { e.putString(key, Json.stringify(it)) } }
        put("stations"); put("crm_orders"); put("crm_parts"); put("kb_articles"); put("notes"); put("saved_docs")
        (m["settings"] as? Map<*, *>)?.let { s ->
            (s["email"] as? String)?.let { e.putString("email", it) }
            (s["host"] as? String)?.let { e.putString("host", it) }
            (s["reservePercent"] as? Number)?.let { e.putString("crm_reserve_pct", it.toString()) }
            (s["simpleAccounting"] as? Boolean)?.let { e.putBoolean("crm_simple_acc", it) }
            (s["demoHidden"] as? Boolean)?.let { e.putBoolean("demoHidden", it) }
            (s["crm_next_id"] as? Number)?.let { e.putLong("crm_next_id", it.toLong()) }
            (s["crm_next_part_id"] as? Number)?.let { e.putLong("crm_next_part_id", it.toLong()) }
            (s["kb_next_id"] as? Number)?.let { e.putLong("kb_next_id", it.toLong()) }
            (s["notes_next_id"] as? Number)?.let { e.putLong("notes_next_id", it.toLong()) }
            (s["saved_docs_next"] as? Number)?.let { e.putLong("saved_docs_next", it.toLong()) }
        }
        e.apply()
        return true
    }

    // ===================== Google Диск (синхронізація) =====================

    var gdEmail: String
        get() = sp.getString("gd_email", "") ?: ""
        set(v) = sp.edit().putString("gd_email", v).apply()

    var gdFileId: String
        get() = sp.getString("gd_file_id", "") ?: ""
        set(v) = sp.edit().putString("gd_file_id", v).apply()

    var gdTokenExpiry: Long
        get() = sp.getLong("gd_token_exp", 0L)
        set(v) = sp.edit().putLong("gd_token_exp", v).apply()

    /** Токени зберігаються в зашифрованому сховищі (Android Keystore). */
    val gdRefreshToken: String get() = secure.get("gd_refresh") ?: ""
    val gdAccessToken: String get() = secure.get("gd_access") ?: ""
    val gdConnected: Boolean get() = gdRefreshToken.isNotBlank() || gdAccessToken.isNotBlank()

    fun saveGoogleTokens(refresh: String, access: String, expiresInSec: Long, email: String) {
        secure.put("gd_refresh", refresh)
        secure.put("gd_access", access)
        gdTokenExpiry = System.currentTimeMillis() + expiresInSec * 1000 - 60_000
        gdEmail = email
    }

    fun updateGoogleAccess(access: String, expiresInSec: Long) {
        secure.put("gd_access", access)
        gdTokenExpiry = System.currentTimeMillis() + expiresInSec * 1000 - 60_000
    }

    fun clearGoogle() {
        secure.put("gd_refresh", null)
        secure.put("gd_access", null)
        gdEmail = ""
        gdFileId = ""
        gdTokenExpiry = 0
    }

    private companion object {
        const val APP_TAG = "savosh-ecoflow"
    }
}
