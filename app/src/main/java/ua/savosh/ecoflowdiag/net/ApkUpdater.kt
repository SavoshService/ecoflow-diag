package ua.savosh.ecoflowdiag.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Оновлення в один дотик: застосунок сам завантажує APK і відкриває системний інсталятор.
 * Повністю «тихого» встановлення на звичайному телефоні немає — Android один раз питає підтвердження.
 * Якщо щось не вдалося — лишається запасний шлях «відкрити в браузері» (там теж авто-завантаження).
 */
object ApkUpdater {

    /** Завантажує APK у приватний кеш застосунку. [onProgress] — 0..100 або -1, якщо розмір невідомий. */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? {
        return try {
            require(url.startsWith("https://")) { "Дозволено лише HTTPS" }
            val out = File(ctx.externalCacheDir ?: ctx.cacheDir, "update.apk")
            if (out.exists()) out.delete()
            fetch(url, out, onProgress, 0)
            if (out.length() > 0L) out else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetch(url: String, out: File, onProgress: (Int) -> Unit, hop: Int) {
        if (hop > 5) throw RuntimeException("Забагато перенаправлень")
        require(url.startsWith("https://")) { "Редірект лише на HTTPS" }
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "SavoshDiag/$APP_VERSION")
        val code = conn.responseCode
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location") ?: throw RuntimeException("Редірект без Location")
            conn.disconnect()
            val next = if (loc.startsWith("http")) loc else URI(url).resolve(loc).toString()
            fetch(next, out, onProgress, hop + 1)
            return
        }
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        val total = conn.contentLength.toLong()
        conn.inputStream.use { input ->
            out.outputStream().use { fo ->
                val buf = ByteArray(16 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    fo.write(buf, 0, n)
                    read += n
                    onProgress(if (total > 0L) ((read * 100L) / total).toInt() else -1)
                }
            }
        }
    }

    /** Чи дозволено застосунку встановлювати APK (Android 8+ вимагає окремого дозволу). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Відкриває системний екран «Встановлення з цього джерела» для нашого застосунку. */
    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val i = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    /** Запускає системний інсталятор для завантаженого APK (користувач підтверджує один раз). */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
