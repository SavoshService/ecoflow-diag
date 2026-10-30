package ua.savosh.ecoflowdiag.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import ua.savosh.ecoflowdiag.BuildConfig
import ua.savosh.ecoflowdiag.core.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Автоматична синхронізація з Google Диском без Google Play SDK.
 *
 * Вхід — OAuth через браузер (loopback + PKCE): застосунок піднімає локальний порт 127.0.0.1,
 * відкриває сторінку входу Google, ловить відповідь на цей порт. Клієнтського секрета в
 * «installed app» не вважають таємним, тож PKCE захищає обмін коду на токени.
 *
 * Дані зберігаються у приховану теку акаунта appDataFolder: не видно в інтерфейсі Диска,
 * не засмічує файли користувача, переживає перевстановлення застосунку.
 */
object GoogleDrive {

    // ===== Облікові дані OAuth (тип «Додаток для ПК», проєкт savosh-service) =====
    const val CLIENT_ID = "513137089324-jpvvu93lbc4nv0vub8vk668jigds3pl8.apps.googleusercontent.com"
    // секрет — з local.properties / GitHub Secrets (див. app/build.gradle.kts), у коді його немає
    val CLIENT_SECRET: String = BuildConfig.GOOGLE_CLIENT_SECRET

    private const val SCOPE = "openid email https://www.googleapis.com/auth/drive.appdata"
    private const val AUTH_EP = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_EP = "https://oauth2.googleapis.com/token"
    private const val USERINFO_EP = "https://openidconnect.googleapis.com/v1/userinfo"
    private const val FILE_NAME = "savosh-backup.json"

    fun configured(): Boolean = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()

    data class SignIn(val refreshToken: String, val accessToken: String, val expiresInSec: Long, val email: String)
    data class Refreshed(val accessToken: String, val expiresInSec: Long)

    /** Вхід: відкриває браузер і чекає на redirect, повертає токени. Кидає виняток із причиною. */
    fun signIn(ctx: Context): SignIn {
        val verifier = randomUrl(64)
        val challenge = b64(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        val state = randomUrl(32)   // відповідь має повернути саме його — інакше це чужий запит на наш порт
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 180_000
            val port = server.localPort
            val redirect = "http://127.0.0.1:$port"
            val url = AUTH_EP + "?client_id=" + enc(CLIENT_ID) +
                "&redirect_uri=" + enc(redirect) +
                "&response_type=code&scope=" + enc(SCOPE) +
                "&code_challenge=" + challenge + "&code_challenge_method=S256" +
                "&state=" + state + "&access_type=offline&prompt=consent"
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            // Чекаємо саме відповідь Google з нашим state; сторонні звернення на порт (favicon,
            // інші застосунки) отримують 404 і не зривають вхід.
            var line = ""
            for (attempt in 1..10) {
                val got = server.accept().use { sock ->
                    val reqLine = BufferedReader(InputStreamReader(sock.getInputStream())).readLine() ?: ""
                    val ours = parseParam(reqLine, "state") == state
                    if (ours) respondDone(sock) else respond404(sock)
                    if (ours) reqLine else null
                }
                if (got != null) { line = got; break }
            }
            if (line.isEmpty()) throw RuntimeException("Не отримано відповідь від Google. Спробуй увійти ще раз.")

            parseParam(line, "error")?.let { throw RuntimeException("Google відмовив: $it") }
            val code = parseParam(line, "code")
                ?: throw RuntimeException("Не отримано код авторизації від Google.")
            return exchange(code, redirect, verifier)
        }
    }

    private fun respond404(sock: java.net.Socket) {
        sock.getOutputStream().apply {
            write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            flush()
        }
    }

    private fun respondDone(sock: java.net.Socket) {
        val html = "<html><head><meta charset='utf-8'></head>" +
            "<body style='font-family:sans-serif;text-align:center;padding-top:48px'>" +
            "<h2>Майже готово ✅</h2><p>Повернись у застосунок Power Station Diag — " +
            "вхід завершиться там за кілька секунд.</p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = sock.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
        )
        out.write(bytes)
        out.flush()
    }

    /** Обмін коду авторизації на токени. */
    private fun exchange(code: String, redirect: String, verifier: String): SignIn {
        val tokenForm = form(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "redirect_uri" to redirect,
                "code_verifier" to verifier,
            ),
        )
        // У цю мить застосунок ще у фоні (на екрані браузер), а Android з економією трафіку
        // чи VPN ріже мережу фоновим застосункам. Тому пробуємо кілька разів ~1 хв —
        // поки людина повернеться. Код не згорає: запит до Google просто не дійшов.
        var tok: Pair<Int, String>? = null
        for (wait in longArrayOf(0, 2_000, 3_000, 5_000, 10_000, 15_000, 25_000)) {
            if (wait > 0) Thread.sleep(wait)
            tok = post(TOKEN_EP, tokenForm, "application/x-www-form-urlencoded", null)
            if (tok != null) break
        }
        if (tok == null) {
            throw RuntimeException("Немає зв'язку з Google при обміні токена ($lastError). " +
                "Перевір, чи не обмежено фонові дані для застосунку.")
        }
        if (tok.first !in 200..299) {
            throw RuntimeException("Токен HTTP ${tok.first}: ${tok.second.take(250)}")
        }
        val m = Json.parseOrNull(tok.second) as? Map<*, *>
            ?: throw RuntimeException("Несподівана відповідь токена Google.")
        val access = m["access_token"] as? String
            ?: throw RuntimeException("Відповідь без access_token: ${tok.second.take(200)}")
        val refresh = (m["refresh_token"] as? String) ?: ""
        val exp = (m["expires_in"] as? Number)?.toLong() ?: 3600L
        val email = try { fetchEmail(access) } catch (e: Exception) { "" }
        return SignIn(refresh, access, exp, email)
    }

    /** Оновити access-токен через refresh-токен. */
    fun refresh(refreshToken: String): Refreshed? {
        val r = post(
            TOKEN_EP,
            form(
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                ),
            ),
            "application/x-www-form-urlencoded",
            null,
        ) ?: return null
        val m = Json.parseOrNull(r.second) as? Map<*, *> ?: return null
        val access = m["access_token"] as? String ?: return null
        val exp = (m["expires_in"] as? Number)?.toLong() ?: 3600L
        return Refreshed(access, exp)
    }

    /** Знайти id файлу копії в appDataFolder (або null). */
    fun findFileId(access: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=" +
            enc("name='$FILE_NAME'") + "&fields=" + enc("files(id)") + "&pageSize=1"
        val r = get(url, access) ?: return null
        val m = Json.parseOrNull(r.second) as? Map<*, *> ?: return null
        val files = m["files"] as? List<*> ?: return null
        val f = files.firstOrNull() as? Map<*, *> ?: return null
        return f["id"] as? String
    }

    /** Створити або оновити файл копії. Повертає його id. */
    fun upload(access: String, fileId: String?, json: String): String? {
        if (fileId.isNullOrBlank()) {
            val boundary = "savoshBoundary" + System.currentTimeMillis()
            val meta = "{\"name\":\"$FILE_NAME\",\"parents\":[\"appDataFolder\"]}"
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary--"
            val r = post(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                body.toByteArray(Charsets.UTF_8),
                "multipart/related; boundary=$boundary",
                access,
            ) ?: return null
            val m = Json.parseOrNull(r.second) as? Map<*, *>
            return m?.get("id") as? String
        }
        // оновлення медіа: PATCH через override (Android HttpURLConnection не підтримує PATCH напряму)
        val r = post(
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            json.toByteArray(Charsets.UTF_8),
            "application/json; charset=UTF-8",
            access,
            override = "PATCH",
        ) ?: return null
        return if (r.first in 200..299) fileId else null
    }

    /** Завантажити вміст файлу копії. */
    fun download(access: String, fileId: String): String? {
        val r = get("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", access) ?: return null
        return if (r.first in 200..299) r.second else null
    }

    private fun fetchEmail(access: String): String {
        val r = get(USERINFO_EP, access) ?: return ""
        val m = Json.parseOrNull(r.second) as? Map<*, *> ?: return ""
        return (m["email"] as? String) ?: ""
    }

    // ===================== HTTP =====================
    private fun get(url: String, bearer: String?): Pair<Int, String>? =
        request("GET", url, null, null, bearer, null)

    private fun post(url: String, body: ByteArray, contentType: String, bearer: String?, override: String? = null): Pair<Int, String>? =
        request("POST", url, body, contentType, bearer, override)

    private fun request(method: String, url: String, body: ByteArray?, contentType: String?, bearer: String?, override: String?): Pair<Int, String>? {
        return try {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 60_000
                if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
                if (override != null) setRequestProperty("X-HTTP-Method-Override", override)
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()
            code to text
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName + (e.message?.let { ": " + it.take(120) } ?: "")
            null
        }
    }

    /** Остання мережева помилка — щоб показати людині справжню причину, а не просто «немає зв'язку». */
    @Volatile private var lastError = ""

    private fun form(params: Map<String, String>): ByteArray =
        params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }.toByteArray(Charsets.UTF_8)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun parseParam(requestLine: String, name: String): String? {
        val q = requestLine.substringAfter("?", "").substringBefore(" ")
        for (p in q.split("&")) {
            val i = p.indexOf('=')
            if (i > 0 && p.substring(0, i) == name) {
                return try {
                    URLDecoder.decode(p.substring(i + 1), "UTF-8")
                } catch (e: Exception) {
                    p.substring(i + 1)
                }
            }
        }
        return null
    }

    private fun randomUrl(n: Int): String {
        val bytes = ByteArray(n)
        SecureRandom().nextBytes(bytes)
        return b64(bytes).take(n)
    }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}
