package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

open class ApiException(message: String) : Exception(message)

class EcoflowAuth(val token: String, val userId: String, val userName: String?, val host: String)

class MqttCreds(val host: String, val port: Int, val protocol: String, val username: String, val password: String)

/**
 * Вхід в акаунт EcoFlow тим самим способом, що й мобільний застосунок ("private API"),
 * і отримання облікових даних для MQTT-брокера EcoFlow.
 * Ключі розробника (developer.ecoflow.com) НЕ потрібні.
 */
class EcoflowApi(private val host: String) {

    fun login(email: String, password: String): EcoflowAuth {
        val body = linkedMapOf<String, Any?>(
            "email" to email.trim(),
            "password" to java.util.Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)),
            "scene" to "IOT_APP",
            "userType" to "ECOFLOW",
        )
        val resp = request("POST", "/auth/login", Json.stringify(body), null)
        val data = resp["data"] as? Map<*, *> ?: throw ApiException("Відповідь входу без поля data")
        val token = data["token"]?.toString() ?: throw ApiException("Сервер не повернув token")
        val user = data["user"] as? Map<*, *> ?: throw ApiException("Сервер не повернув user")
        val userId = user["userId"]?.toString() ?: throw ApiException("Сервер не повернув userId")
        return EcoflowAuth(token, userId, user["name"]?.toString(), host)
    }

    fun certification(auth: EcoflowAuth): MqttCreds {
        val q = "?userId=" + URLEncoder.encode(auth.userId, "UTF-8")
        val resp = request("GET", "/iot-auth/app/certification$q", null, auth.token)
        val data = resp["data"] as? Map<*, *> ?: throw ApiException("Відповідь без поля data (MQTT)")
        val url = data["url"]?.toString() ?: throw ApiException("Немає адреси MQTT-брокера")
        val port = data["port"]?.toString()?.toIntOrNull() ?: 8883
        val protocol = data["protocol"]?.toString() ?: "mqtts"
        val user = data["certificateAccount"]?.toString() ?: throw ApiException("Немає certificateAccount")
        val pass = data["certificatePassword"]?.toString() ?: throw ApiException("Немає certificatePassword")
        return MqttCreds(url, port, protocol, user, pass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun request(method: String, path: String, body: String?, token: String?): Map<String, Any?> {
        val conn = URI("https://$host$path").toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("lang", "en_US")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException("HTTP $code від $host: ${text.take(200)}")
            val json = Json.parseObject(text) ?: throw ApiException("Незрозуміла відповідь сервера: ${text.take(200)}")
            val message = json["message"]?.toString() ?: ""
            val apiCode = json["code"]?.toString()
            if (!message.equals("success", ignoreCase = true) && apiCode != "0") {
                throw ApiException(humanize(message, apiCode))
            }
            return json
        } catch (e: IOException) {
            throw ApiException("Немає з'єднання з $host: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    private fun humanize(message: String, code: String?): String {
        val m = message.lowercase()
        val ua = when {
            "password" in m || "account" in m && "error" in m -> "Невірний email або пароль"
            "not exist" in m || "not registered" in m -> "Акаунт не знайдено на цьому сервері — спробуйте інший регіон"
            "frequent" in m || "too many" in m -> "Забагато спроб — зачекайте кілька хвилин"
            else -> null
        }
        val raw = if (code != null) "$message (код $code)" else message
        return if (ua != null) "$ua. [$raw]" else "Помилка EcoFlow: $raw"
    }

    companion object {
        val SERVERS: List<Pair<String, String>> = listOf(
            "api.ecoflow.com" to "Авто",
            "api-e.ecoflow.com" to "Європа",
            "api-a.ecoflow.com" to "Америка",
        )
    }
}
