package ua.savosh.ecoflowdiag.net

import java.io.File

/**
 * Запуск імітатора окремо, для ручної перевірки застосунку на емуляторі:
 * java -cp … ua.savosh.ecoflowdiag.net.FakeCloudMainKt <тека для сертифіката> <порт HTTPS> <порт MQTT> [затримка входу, мс]
 */
fun main(args: Array<String>) {
    val dir = File(args[0]).apply { mkdirs() }
    val tls = FakeTls.context(FakeTls.keystoreFile(dir))
    val cloud = FakeCloud(tls, httpPort = args[1].toInt(), mqttPort = args[2].toInt(), brokerHost = "mqtt.ecoflow.com", brokerPort = 8883)
    cloud.add(*SimFleet.all().toTypedArray())
    cloud.loginDelayMs = args.getOrNull(3)?.toLongOrNull() ?: 0L
    println("Імітатор хмари працює: HTTPS ${args[1]}, MQTT ${args[2]}, станцій ${cloud.stations.size}")
    while (true) Thread.sleep(60_000)
}
