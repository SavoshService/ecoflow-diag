package ua.savosh.ecoflowdiag.net

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/** Брокер відхилив облікові дані — треба отримати нові через /iot-auth/app/certification. */
class MqttAuthException(message: String) : ApiException(message)

/**
 * Підключення до MQTT-брокера EcoFlow (той самий, що використовує мобільний застосунок).
 *
 * Автоматичне перепідключення Paho вимкнене: ним керує цикл діагностики, щоб після виходу
 * з екрана чи згортання застосунку не лишалось "завислих" з'єднань, які заважають новим.
 * Усі методи, крім [close], блокуючі — викликати з фонового потоку.
 */
class EcoflowMqtt(
    private val creds: MqttCreds,
    private val userId: String,
    private val sn: String,
    private val onMessage: (topic: String, payload: ByteArray) -> Unit,
    private val onLog: (String) -> Unit,
) {
    val dataTopic = "/app/device/property/$sn"
    val getTopic = "/app/$userId/$sn/thing/property/get"
    val getReplyTopic = "/app/$userId/$sn/thing/property/get_reply"
    val setTopic = "/app/$userId/$sn/thing/property/set"
    val setReplyTopic = "/app/$userId/$sn/thing/property/set_reply"

    private val clientId = "ANDROID_" + UUID.randomUUID().toString().replace("-", "").uppercase() + "_" + userId

    @Volatile private var client: MqttClient? = null
    @Volatile private var closed = false

    /** Підключається (або перепідключається) і підписується на теми станції. */
    fun connect() {
        if (closed) return
        client?.let { old ->
            client = null
            shutdown(old)
        }
        val scheme = if (creds.protocol.contains("ws", ignoreCase = true)) "wss" else "ssl"
        val uri = "$scheme://${creds.host}:${creds.port}"
        onLog("MQTT: підключення до $uri")
        val c = MqttClient(uri, clientId, MemoryPersistence())
        c.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                if (closed) return
                val why = when (cause) {
                    is MqttException -> describe(cause)
                    null -> "без причини"
                    else -> "${cause.javaClass.simpleName}: ${cause.message}"
                }
                onLog("MQTT: з'єднання втрачено — $why")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (closed || topic == null || message == null) return
                try {
                    onMessage(topic, message.payload)
                } catch (e: Exception) {
                    onLog("Помилка обробки повідомлення: ${e.message}")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        val opts = MqttConnectOptions().apply {
            userName = creds.username
            password = creds.password.toCharArray()
            isCleanSession = true
            keepAliveInterval = 30
            connectionTimeout = 20
            isAutomaticReconnect = false
            socketFactory = SSLSocketFactory.getDefault()
            isHttpsHostnameVerificationEnabled = true
        }
        try {
            c.connect(opts)
            c.subscribe(arrayOf(dataTopic, getReplyTopic, setReplyTopic), intArrayOf(1, 1, 1))
        } catch (e: MqttException) {
            shutdown(c)
            val code = e.reasonCode
            if (code == MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt() ||
                code == MqttException.REASON_CODE_NOT_AUTHORIZED.toInt()
            ) {
                throw MqttAuthException("MQTT: ${describe(e)}")
            }
            throw ApiException("MQTT: ${describe(e)}")
        }
        if (closed) {
            shutdown(c)
            return
        }
        client = c
        onLog("MQTT: підключено, підписка на станцію $sn")
    }

    fun publish(topic: String, payload: ByteArray): Boolean {
        val c = client ?: return false
        return try {
            c.publish(topic, payload, 1, false)
            true
        } catch (e: MqttException) {
            onLog("MQTT: не вдалося надіслати запит: ${describe(e)}")
            false
        }
    }

    fun isConnected(): Boolean = client?.isConnected == true

    /** Остаточно закриває з'єднання (неблокуюче). Після цього об'єкт не використовується. */
    fun close() {
        closed = true
        val c = client ?: return
        client = null
        Thread { shutdown(c) }.start()
    }

    private fun shutdown(c: MqttClient) {
        try {
            c.disconnectForcibly(300, 1000)
        } catch (_: Exception) {
        }
        try {
            c.close(true)
        } catch (_: Exception) {
        }
    }

    private fun describe(e: MqttException): String = when (e.reasonCode) {
        MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt(),
        MqttException.REASON_CODE_NOT_AUTHORIZED.toInt(),
        -> "брокер відхилив облікові дані (код ${e.reasonCode})"
        MqttException.REASON_CODE_SERVER_CONNECT_ERROR.toInt(),
        MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt(),
        -> "брокер недоступний (код ${e.reasonCode}): ${e.cause?.message ?: e.message}"
        MqttException.REASON_CODE_CONNECTION_LOST.toInt() ->
            "зв'язок обірвано (код ${e.reasonCode})" + (e.cause?.let { ": ${it.javaClass.simpleName} ${it.message ?: ""}" } ?: "")
        else -> "${e.message ?: "помилка"} (код ${e.reasonCode})" + (e.cause?.let { ": ${it.message}" } ?: "")
    }
}
