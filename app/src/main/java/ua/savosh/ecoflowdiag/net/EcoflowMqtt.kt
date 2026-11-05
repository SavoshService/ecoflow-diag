package ua.savosh.ecoflowdiag.net

import ua.savosh.ecoflowdiag.core.tr
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
    companion object {
        /** Без цього publish і subscribe чекають підтвердження брокера без обмеження. */
        private const val WAIT_MS = 15_000L
        private const val SUBACK_DENIED = 0x80
    }

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
        onLog(tr("MQTT: підключення до {0}", uri))
        val c = try {
            MqttClient(uri, clientId, MemoryPersistence())
        } catch (e: Exception) {
            throw ApiException(tr("MQTT: некоректна адреса брокера {0}: {1}", uri, e.message))
        }
        c.timeToWait = WAIT_MS
        c.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                if (closed) return
                val why = when (cause) {
                    is MqttException -> describe(cause)
                    null -> tr("без причини")
                    else -> "${cause.javaClass.simpleName}: ${cause.message}"
                }
                onLog(tr("MQTT: з'єднання втрачено — {0}", why))
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (closed || topic == null || message == null) return
                try {
                    onMessage(topic, message.payload)
                } catch (e: Exception) {
                    onLog(tr("Помилка обробки повідомлення: {0}", e.message))
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        val opts = MqttConnectOptions().apply {
            userName = creds.username
            password = creds.password.toCharArray()
            isCleanSession = true
            keepAliveInterval = 20
            connectionTimeout = 20
            isAutomaticReconnect = false
            socketFactory = SSLSocketFactory.getDefault()
            isHttpsHostnameVerificationEnabled = true
        }
        try {
            c.connect(opts)
            val granted = intArrayOf(1, 1, 1)
            c.subscribe(arrayOf(dataTopic, getReplyTopic, setReplyTopic), granted)
            if (granted.all { it == SUBACK_DENIED }) {
                shutdown(c)
                throw StationDenied(tr("Хмара EcoFlow не дозволила читати станцію {0}: перевірте серійний номер і чи додана вона саме в цей акаунт", sn))
            }
        } catch (e: MqttException) {
            shutdown(c)
            val code = e.reasonCode
            if (code == MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt() ||
                code == MqttException.REASON_CODE_NOT_AUTHORIZED.toInt()
            ) {
                throw MqttAuthException("MQTT: ${describe(e)}")
            }
            throw NetworkException("MQTT: ${describe(e)}")
        }
        // Спершу запам'ятати, потім перевірити: так close() з іншого потоку не розминеться з новим з'єднанням.
        client = c
        if (closed) {
            client = null
            shutdown(c)
            return
        }
        onLog(tr("MQTT: підключено, підписка на станцію {0}", sn))
    }

    fun publish(topic: String, payload: ByteArray): Boolean {
        val c = client ?: return false
        return try {
            c.publish(topic, payload, 1, false)
            true
        } catch (e: MqttException) {
            onLog(tr("MQTT: не вдалося надіслати запит: {0}", describe(e)))
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
        -> tr("брокер відхилив облікові дані (код {0})", e.reasonCode)
        MqttException.REASON_CODE_SERVER_CONNECT_ERROR.toInt(),
        MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt(),
        -> tr("брокер недоступний (код {0}): {1}", e.reasonCode, e.cause?.message ?: e.message)
        MqttException.REASON_CODE_INVALID_CLIENT_ID.toInt() -> tr("брокер не прийняв ідентифікатор клієнта (код {0})", e.reasonCode)
        MqttException.REASON_CODE_CONNECTION_LOST.toInt() ->
            tr("зв'язок обірвано (код {0})", e.reasonCode) + (e.cause?.let { ": ${it.javaClass.simpleName} ${it.message ?: ""}" } ?: "")
        else -> tr("{0} (код {1})", e.message ?: tr("помилка"), e.reasonCode) + (e.cause?.let { ": ${it.message}" } ?: "")
    }
}
