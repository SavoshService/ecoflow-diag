package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.BuildConfig
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Налаштування відправки статей у спільну базу через Telegram-бота Savosh Service.
 * Усе з талмуда відправляється автоматично у фоні (без кнопок і написів).
 */
object TelegramConfig {
    // токен — з local.properties / GitHub Secrets (див. app/build.gradle.kts), у коді його немає
    val BOT_TOKEN: String = BuildConfig.TG_BOT_TOKEN
    const val CHAT_ID = "-1003744655236" // група «Савош_Сервіс суспільство»
    const val ADMIN_CHAT_ID = "8067993283" // особистий чат майстра — для кодів помилок

    fun configured(): Boolean = BOT_TOKEN.contains(":") && CHAT_ID.isNotBlank()
}

object TelegramSubmit {

    private const val CAPTION_LIMIT = 1000

    private fun api(method: String): String = "https://api.telegram.org/bot${TelegramConfig.BOT_TOKEN}/$method"

    /**
     * Надсилає статтю ботy: текст + усі фото. Кидає виняток на мережевій помилці першого
     * повідомлення — щоб WorkManager повторив пізніше. Фото — best-effort (не зривають відправку).
     */
    fun sendArticle(
        title: String,
        section: String,
        subsection: String,
        marking: String,
        analogs: String,
        body: String,
        photos: List<File>,
    ): Boolean {
        if (!TelegramConfig.configured()) return false
        val text = buildString {
            append("📖 Нова стаття в талмуд\n\n")
            append("Розділ: ").append(section)
            if (subsection.isNotBlank()) append(" · ").append(subsection)
            append('\n')
            append("Назва: ").append(title).append('\n')
            if (marking.isNotBlank()) append("Маркування: ").append(marking).append('\n')
            if (analogs.isNotBlank()) append("Аналоги: ").append(analogs).append('\n')
            append('\n').append(body)
        }
        val files = photos.filter { it.exists() && it.length() > 0 }

        if (files.isEmpty()) {
            sendMessage(text) // мережева помилка → повтор
        } else if (text.length <= CAPTION_LIMIT) {
            // перше фото з підписом-текстом; решта — окремо
            sendPhoto(files.first(), text) // мережева помилка → повтор
            for (f in files.drop(1)) runCatching { sendPhoto(f, null) }
        } else {
            sendMessage(text) // мережева помилка → повтор
            for (f in files) runCatching { sendPhoto(f, null) }
        }
        return true
    }

    /** Відправити коди помилок у особистий чат майстра (для вивчення). true — успіх. */
    fun sendCodes(text: String): Boolean {
        if (!TelegramConfig.BOT_TOKEN.contains(":") || TelegramConfig.ADMIN_CHAT_ID.isBlank()) return false
        return try {
            sendMessageTo(TelegramConfig.ADMIN_CHAT_ID, text)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun sendMessage(text: String) = sendMessageTo(TelegramConfig.CHAT_ID, text)

    private fun sendMessageTo(chatId: String, text: String) {
        val body = "chat_id=" + enc(chatId) + "&text=" + enc(text) + "&disable_web_page_preview=true"
        val conn = (URI(api("sendMessage")).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("sendMessage HTTP $code")
    }

    private fun sendPhoto(file: File, caption: String?) {
        val boundary = "----savosh" + System.currentTimeMillis()
        val conn = (URI(api("sendPhoto")).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 40000
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        DataOutputStream(conn.outputStream).use { out ->
            formField(out, boundary, "chat_id", TelegramConfig.CHAT_ID)
            if (!caption.isNullOrBlank()) formField(out, boundary, "caption", caption)
            // файл photo
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            file.inputStream().use { it.copyTo(out) }
            out.writeBytes("\r\n")
            out.writeBytes("--$boundary--\r\n")
            out.flush()
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("sendPhoto HTTP $code")
    }

    private fun formField(out: DataOutputStream, boundary: String, name: String, value: String) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
        out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
        out.write(value.toByteArray(Charsets.UTF_8))
        out.writeBytes("\r\n")
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
