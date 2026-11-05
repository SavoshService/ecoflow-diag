package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Імітатор хмари EcoFlow: HTTPS (вхід, доступ до MQTT) і MQTT-брокер зі станціями всередині.
 * Працює локально по TLS, тож застосунок проходить весь справжній шлях: той самий Paho, ті самі теми, той самий протокол.
 * Поведінка хмари тут — те, що відомо зі спільноти (hassio-ecoflow-cloud, ha-ef-ble), а не перевірка на справжній хмарі.
 */
class FakeCloud(
    ssl: SSLContext,
    httpPort: Int = 0,
    mqttPort: Int = 0,
    /** Яку адресу брокера повідомляти клієнту. */
    private val brokerHost: String = "localhost",
    private val brokerPort: Int? = null,
    /** Чи знає цей регіональний сервер акаунт (інакше — код 1003). */
    var accountHere: Boolean = true,
) : Closeable {

    companion object {
        const val EMAIL = "master@savosh.test"
        const val PASSWORD = "fake-cloud-only"
        const val USER_ID = "1234567890"
    }

    val stations = HashMap<String, SimStation>()
    val logins = AtomicInteger()
    val certifications = AtomicInteger()
    val mqttConnects = AtomicInteger()
    val conns = CopyOnWriteArrayList<Conn>()

    /** Скільки наступних HTTP-запитів отримають 503 (сервер «лежить»). */
    val httpDown = AtomicInteger()

    /** Затримка входу: повільна мережа, коли людина встигає натиснути на станцію раніше. */
    @Volatile var loginDelayMs = 0L
    private var tokenGen = 0
    private var validToken = ""
    private var mqttPassword = "mqtt-pass-0"

    private val httpServer: ServerSocket = ssl.serverSocketFactory.createServerSocket(httpPort, 50, InetAddress.getLoopbackAddress())
    private val mqttServer: ServerSocket = ssl.serverSocketFactory.createServerSocket(mqttPort, 50, InetAddress.getLoopbackAddress())
    val httpHost: String get() = "localhost:${httpServer.localPort}"
    val mqttPortActual: Int get() = mqttServer.localPort

    init {
        thread("http") { while (!httpServer.isClosed) { val s = runCatching { httpServer.accept() }.getOrNull() ?: break; thread("http-c") { serveHttp(s) } } }
        thread("mqtt") { while (!mqttServer.isClosed) { val s = runCatching { mqttServer.accept() }.getOrNull() ?: break; thread("mqtt-c") { serveMqtt(s) } } }
        thread("push") {
            while (!mqttServer.isClosed) {
                val now = System.currentTimeMillis()
                for (st in stations.values) if (st.pushEveryMs > 0 && st.online && now - st.lastPushMs >= st.pushEveryMs) {
                    st.lastPushMs = now
                    st.push()?.let { publishTo(st.dataTopic, it) }
                }
                Thread.sleep(20)
            }
        }
    }

    private fun thread(name: String, body: () -> Unit) = Thread({ runCatching(body) }, "fake-$name").apply { isDaemon = true; start() }

    fun expireTokens() { validToken = "" }
    fun rotateMqttPassword() { mqttPassword = "mqtt-pass-${System.nanoTime()}" }
    fun kickAll() { conns.forEach { runCatching { it.sock.close() } } }
    fun add(vararg list: SimStation) { list.forEach { stations[it.sn] = it } }

    override fun close() {
        runCatching { httpServer.close() }
        runCatching { mqttServer.close() }
        kickAll()
    }

    // ---------------------------------------------------------------- HTTPS

    private fun serveHttp(s: Socket) = s.use {
        val input = BufferedInputStream(s.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: return
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(len).also { readFully(input, it) }.toString(Charsets.UTF_8)
        val path = requestLine.split(" ").getOrElse(1) { "" }
        val (code, json) = route(path, headers, body)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val out = s.getOutputStream()
        out.write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun route(path: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        if (httpDown.get() > 0) {
            httpDown.decrementAndGet()
            return 503 to "{}"
        }
        return when {
            path.startsWith("/auth/login") -> {
                logins.incrementAndGet()
                if (loginDelayMs > 0) Thread.sleep(loginDelayMs)
                val m = Json.parseObject(body) ?: emptyMap()
                val pass = runCatching { String(Base64.getDecoder().decode(m["password"].toString())) }.getOrDefault("")
                if (!accountHere || m["email"] != EMAIL || pass != PASSWORD) {
                    200 to """{"code":"1003","message":"Account doesn't exist or incorrect password"}"""
                } else {
                    validToken = "tok-${++tokenGen}"
                    200 to """{"code":"0","message":"Success","data":{"token":"$validToken","user":{"userId":"$USER_ID","name":"Майстер"}}}"""
                }
            }
            path.startsWith("/iot-auth/app/certification") -> {
                certifications.incrementAndGet()
                if (headers["authorization"] != "Bearer $validToken" || validToken.isEmpty()) {
                    200 to """{"code":"401","message":"token expired"}"""
                } else {
                    200 to """{"code":"0","message":"Success","data":{"url":"$brokerHost","port":"${brokerPort ?: mqttPortActual}","protocol":"mqtts","certificateAccount":"app-$USER_ID","certificatePassword":"$mqttPassword"}}"""
                }
            }
            else -> 404 to """{"code":"404","message":"not found"}"""
        }
    }

    private fun readLine(i: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = i.read()
            if (c < 0) return null
            if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            b.write(c)
        }
    }

    private fun readFully(i: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = i.read(buf, off, buf.size - off)
            if (n < 0) return
            off += n
        }
    }

    // ---------------------------------------------------------------- MQTT 3.1.1

    class Conn(val sock: Socket, private val out: OutputStream) {
        val subs = CopyOnWriteArrayList<String>()
        /** Скільки відповідей дала кожна станція саме цьому з'єднанню: «зависання» лікує нове з'єднання. */
        val replies = HashMap<String, Int>()

        fun write(type: Int, body: ByteArray) = synchronized(this) {
            val o = ByteArrayOutputStream()
            o.write(type)
            var len = body.size
            do {
                var d = len % 128
                len /= 128
                if (len > 0) d = d or 128
                o.write(d)
            } while (len > 0)
            o.write(body)
            runCatching {
                out.write(o.toByteArray())
                out.flush()
            }
            Unit
        }
    }

    private fun serveMqtt(s: Socket) {
        val input = BufferedInputStream(s.getInputStream())
        val c = Conn(s, s.getOutputStream())
        try {
            while (true) {
                val h = input.read()
                if (h < 0) break
                var len = 0
                var mul = 1
                while (true) {
                    val b = input.read()
                    if (b < 0) return
                    len += (b and 127) * mul
                    mul *= 128
                    if (b and 128 == 0) break
                }
                val body = ByteArray(len).also { readFully(input, it) }
                when (h shr 4) {
                    1 -> if (!onConnect(c, body)) return
                    3 -> onPublish(c, h, body)
                    8 -> onSubscribe(c, body)
                    12 -> c.write(0xD0, ByteArray(0))
                    14 -> return
                }
            }
        } finally {
            conns.remove(c)
            runCatching { s.close() }
        }
    }

    private class Cursor(val b: ByteArray) {
        var p = 0
        fun u16(): Int = ((b[p].toInt() and 255) shl 8 or (b[p + 1].toInt() and 255)).also { p += 2 }
        fun str(): String { val n = u16(); return String(b, p, n, Charsets.UTF_8).also { p += n } }
    }

    private fun onConnect(c: Conn, body: ByteArray): Boolean {
        val r = Cursor(body)
        r.str()
        r.p++ // рівень протоколу
        val flags = body[r.p++].toInt() and 255
        r.u16()
        val clientId = r.str()
        val user = if (flags and 0x80 != 0) r.str() else ""
        val pass = if (flags and 0x40 != 0) r.str() else ""
        val rc = when {
            !clientId.startsWith("ANDROID_") -> 2
            user != "app-$USER_ID" || pass != mqttPassword -> 5
            else -> 0
        }
        c.write(0x20, byteArrayOf(0, rc.toByte()))
        if (rc != 0) return false
        mqttConnects.incrementAndGet()
        conns.add(c)
        return true
    }

    private fun onSubscribe(c: Conn, body: ByteArray) {
        val r = Cursor(body)
        val id = r.u16()
        val codes = ByteArrayOutputStream()
        val fresh = ArrayList<String>()
        while (r.p < body.size) {
            val t = r.str()
            r.p++
            val st = stations.values.firstOrNull { t.contains(it.sn) }
            if (st?.denied == true) {
                codes.write(0x80)
            } else {
                codes.write(1)
                c.subs.add(t)
                fresh.add(t)
            }
        }
        c.write(0x90, byteArrayOf((id shr 8).toByte(), id.toByte()) + codes.toByteArray())
        for (t in fresh) stations.values.firstOrNull { it.dataTopic == t && it.online }?.partialOnSubscribe?.let { publishOne(c, t, it) }
    }

    private fun onPublish(c: Conn, header: Int, body: ByteArray) {
        val qos = (header shr 1) and 3
        val r = Cursor(body)
        val topic = r.str()
        if (qos > 0) {
            val id = r.u16()
            c.write(0x40, byteArrayOf((id shr 8).toByte(), id.toByte()))
        }
        val payload = body.copyOfRange(r.p, body.size)
        val m = Regex("^/app/([^/]+)/([^/]+)/thing/property/(get|set)$").find(topic) ?: return
        val (uid, sn, op) = m.destructured
        if (uid != USER_ID) return
        val reply = "/app/$uid/$sn/thing/property/${op}_reply"
        val st = stations[sn]
        if (op == "set") {
            st?.commands?.add(payload.toString(Charsets.UTF_8))
            publishOne(c, reply, """{"code":0,"message":"success"}""".toByteArray())
            return
        }
        val isJson = payload.firstOrNull() == '{'.code.toByte()
        if (st == null) {
            // Чужий або помилковий SN: хмара каже, що такої станції тут немає (online=0).
            if (isJson) publishOne(c, reply, SimStation.quotaReply(false, emptyMap()))
            return
        }
        val done = c.replies[sn] ?: 0
        if (st.stallAfterReplies != null && done >= st.stallAfterReplies) return
        val answer = st.answer(isJson) ?: return
        c.replies[sn] = done + 1
        thread("reply") {
            if (st.replyDelayMs > 0) Thread.sleep(st.replyDelayMs)
            publishOne(c, reply, answer)
        }
    }

    private fun publishOne(c: Conn, topic: String, payload: ByteArray) {
        val t = topic.toByteArray(Charsets.UTF_8)
        c.write(0x30, byteArrayOf((t.size shr 8).toByte(), t.size.toByte()) + t + payload)
    }

    private fun publishTo(topic: String, payload: ByteArray) {
        for (c in conns) if (topic in c.subs) publishOne(c, topic, payload)
    }
}

/** TLS для імітатора: самопідписаний сертифікат на localhost і *.ecoflow.com, згенерований на місці. */
object FakeTls {
    private const val PASS = "fake-cloud"

    fun keystoreFile(dir: File): File {
        val f = File(dir, "fake-cloud.p12")
        if (f.exists()) return f
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val p = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "fake", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
            "-dname", "CN=Savosh Fake Cloud", "-ext", "SAN=dns:localhost,dns:*.ecoflow.com,ip:127.0.0.1",
            "-ext", "BC=ca:true", "-keystore", f.path, "-storetype", "PKCS12", "-storepass", PASS,
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "keytool: $out" }
        return f
    }

    fun context(file: File): SSLContext {
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { ks.load(it, PASS.toCharArray()) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASS.toCharArray()) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, tmf.trustManagers, null) }
    }
}
