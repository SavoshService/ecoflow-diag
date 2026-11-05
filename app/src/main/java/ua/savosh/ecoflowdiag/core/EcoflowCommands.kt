package ua.savosh.ecoflowdiag.core

import kotlin.random.Random

/**
 * Команди керування станцією (запис у хмару EcoFlow тим самим каналом, що й офіційний застосунок).
 *
 * УВАГА: точні формати команд у EcoFlow залежать від моделі й покоління. Тут зібрано формати для
 * 2-го покоління (Delta 2 / River 2 родина) за відкритими напрацюваннями спільноти. Вони позначені
 * як експериментальні: застосунок показує надіслану команду й відповідь станції, тож їх легко
 * перевірити на конкретній станції, а за потреби — підправити формат під модель.
 *
 * Команди виконуються лише в межах акаунта, яким виконано вхід (інші станції недоступні) — так само,
 * як в офіційному застосунку.
 */
class DeviceCommand(
    val key: String,
    val title: String,
    /** Людський опис дії для діалогу підтвердження. */
    val confirm: String,
    val json: String,
    /** true — дія керує реальним виходом напруги (потрібне підтвердження). */
    val powerAffecting: Boolean,
)

object EcoflowCommands {

    /** Чи підтримуються команди для цього покоління (поки що — лише 2-ге). */
    fun supported(gen: Generation): Boolean = gen == Generation.GEN2

    fun unsupportedNote(gen: Generation): String = when (gen) {
        Generation.GEN1 -> tr("Для 1-го покоління (Delta/River оригінальні, Delta Pro) формат команд інший — поки не доданий.")
        Generation.GEN3 -> tr("Для 3-го покоління команди йдуть у protobuf — поки не доданий, щоб не вгадувати формат.")
        else -> tr("Модель не визначена — керування недоступне.")
    }

    // ------------------------------------------------------------ 2-ге покоління (JSON)

    private fun g2(moduleType: Int, operateType: String, params: Map<String, Any?>, moduleSn: String? = null): String {
        val body = linkedMapOf<String, Any?>(
            "id" to Random.nextInt(100000, 999999),
            "version" to "1.0",
            "moduleType" to moduleType,
            "operateType" to operateType,
        )
        moduleSn?.let { body["moduleSn"] = it }
        body["params"] = params
        return Json.stringify(body)
    }

    /** DELTA 2 Max: інвертор — інший модуль (3, а не 5), а команди адресуються за серійним номером. */
    private fun isDelta2Max(sn: String): Boolean = sn.startsWith("R35")
    private fun moduleSn(sn: String): String? = sn.takeIf { isDelta2Max(it) }

    /** Увімкнення/вимкнення виходу 220 В (інвертор). */
    fun acOut(on: Boolean, sn: String): DeviceCommand = DeviceCommand(
        key = "ac",
        title = if (on) tr("Увімкнути 220 В") else tr("Вимкнути 220 В"),
        confirm = if (on) {
            tr("Увімкнути вихід 220 В на станції? На розетки подасться напруга — переконайтесь, що це безпечно.")
        } else {
            tr("Вимкнути вихід 220 В? Усе, що живиться від розеток станції, знеструмиться.")
        },
        // 255 і -1 означають «не змінювати»: інакше разом із виходом перемикались би X-Boost і частота.
        json = g2(
            if (isDelta2Max(sn)) 3 else 5,
            "acOutCfg",
            linkedMapOf("enabled" to if (on) 1 else 0, "out_voltage" to -1, "out_freq" to 255, "xboost" to 255),
            moduleSn(sn),
        ),
        powerAffecting = true,
    )

    /** Увімкнення/вимкнення DC-виходу 12 В (авторозетка). */
    fun dcOut(on: Boolean): DeviceCommand = DeviceCommand(
        key = "dc",
        title = if (on) tr("Увімкнути DC 12 В") else tr("Вимкнути DC 12 В"),
        confirm = if (on) tr("Увімкнути DC-вихід 12 В?") else tr("Вимкнути DC-вихід 12 В?"),
        json = g2(5, "mpptCar", linkedMapOf("enabled" to if (on) 1 else 0)),
        powerAffecting = true,
    )

    /** Ліміт заряду (максимальний SOC), %. */
    fun maxChargeSoc(soc: Int, sn: String): DeviceCommand = DeviceCommand(
        key = "maxChg",
        title = tr("Ліміт заряду {0}%", soc),
        confirm = tr("Виставити ліміт заряду {0}%?", soc),
        json = g2(2, "upsConfig", linkedMapOf("maxChgSoc" to soc.coerceIn(50, 100)), moduleSn(sn)),
        powerAffecting = false,
    )

    /** Нижній ліміт розряду (мінімальний SOC), %. */
    fun minDischargeSoc(soc: Int, sn: String): DeviceCommand = DeviceCommand(
        key = "minDsg",
        title = tr("Нижній ліміт розряду {0}%", soc),
        confirm = tr("Виставити нижній ліміт розряду {0}%?", soc),
        json = g2(2, "dsgCfg", linkedMapOf("minDsgSoc" to soc.coerceIn(0, 30)), moduleSn(sn)),
        powerAffecting = false,
    )

    /** Набір швидких дій для 2-го покоління. */
    fun quickActions(sn: String): List<DeviceCommand> = listOf(
        acOut(true, sn), acOut(false, sn),
        dcOut(true), dcOut(false),
        maxChargeSoc(100, sn), minDischargeSoc(0, sn),
    )
}
