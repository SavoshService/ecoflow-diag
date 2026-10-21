package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.Json
import java.net.HttpURLConnection
import java.net.URI

/** Поточна версія застосунку (тримати в синхроні з versionName у build.gradle). */
const val APP_VERSION = "2.9"

/**
 * АДРЕСА МАНІФЕСТА ОНОВЛЕННЯ на GitHub.
 *
 * Оновлення реалізовано через GitHub Releases: workflow .github/workflows/build-apk.yml при кожній
 * збірці публікує реліз з тегом "latest", куди кладе APK і latest.json. Стабільне посилання
 * ".../releases/download/latest/latest.json" завжди вказує на найсвіжіший реліз.
 *
 * ЗАМІНИ OWNER/REPO на свій репозиторій (напр. savosh/ecoflow-diag). Доки стоїть OWNER/REPO —
 * застосунок оновлень не перевіряє (вважається ненастроєним).
 */
// Один публічний репозиторій: CI збирає APK і публікує його в релізи цього ж репозиторію.
const val UPDATE_MANIFEST_URL = "https://github.com/SavoshService/ecoflow-diag/releases/download/latest/latest.json"

class UpdateInfo(val version: String, val url: String, val notes: String)

object Updater {

    fun configured(): Boolean =
        UPDATE_MANIFEST_URL.startsWith("https://") &&
            !UPDATE_MANIFEST_URL.contains(".invalid") &&
            !UPDATE_MANIFEST_URL.contains("OWNER/REPO")

    /** Перевіряє сервер. Повертає опис, якщо є новіша версія, інакше null. Безпечно ковтає помилки мережі. */
    fun check(current: String = APP_VERSION): UpdateInfo? {
        if (!configured()) return null
        return try {
            val text = httpsGet(UPDATE_MANIFEST_URL)
            val m = Json.parseObject(text) ?: return null
            val version = m["version"]?.toString() ?: return null
            val url = m["url"]?.toString() ?: return null
            if (!url.startsWith("https://")) return null
            if (!isNewer(version, current)) return null
            UpdateInfo(version, url, m["notes"]?.toString() ?: "")
        } catch (e: Exception) {
            null
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val a = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** GET з ручним проходом по редіректах (GitHub Releases веде на objects.githubusercontent.com). */
    private fun httpsGet(startUrl: String, hop: Int = 0): String {
        require(startUrl.startsWith("https://")) { "Дозволено лише HTTPS" }
        if (hop > 5) throw RuntimeException("Забагато перенаправлень")
        val conn = URI(startUrl).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "SavoshDiag/$APP_VERSION")
        conn.setRequestProperty("Accept", "application/json, */*")
        val code = conn.responseCode
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location") ?: throw RuntimeException("Редірект без Location")
            conn.disconnect()
            val next = if (loc.startsWith("http")) loc else URI(startUrl).resolve(loc).toString()
            require(next.startsWith("https://")) { "Редірект лише на HTTPS" }
            return httpsGet(next, hop + 1)
        }
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
