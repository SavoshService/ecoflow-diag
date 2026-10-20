package ua.savosh.ecoflowdiag.data

import android.content.Context
import android.content.SharedPreferences
import ua.savosh.ecoflowdiag.core.Json

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

    /** Останній результат тесту реальної ємності станції (текст звіту). */
    fun saveCapTest(sn: String, text: String) = sp.edit().putString("capTest_$sn", text).apply()

    fun loadCapTest(sn: String): String? = sp.getString("capTest_$sn", null)

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
}
