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
        Generation.GEN1 -> "Для 1-го покоління (Delta/River оригінальні, Delta Pro) формат команд інший — поки не доданий."
        Generation.GEN3 -> "Для 3-го покоління команди йдуть у protobuf — поки не доданий, щоб не вгадувати формат."
        else -> "Модель не визначена — керування недоступне."
    }

    // ------------------------------------------------------------ 2-ге покоління (JSON)

    private fun g2(moduleType: Int, operateType: String, params: Map<String, Any?>): String {
        val body = linkedMapOf<String, Any?>(
            "id" to Random.nextInt(100000, 999999),
            "version" to "1.0",
            "moduleType" to moduleType,
            "operateType" to operateType,
            "params" to params,
        )
        return Json.stringify(body)
    }

    /** Увімкнення/вимкнення виходу 220 В (інвертор). */
    fun acOut(on: Boolean): DeviceCommand = DeviceCommand(
        key = "ac",
        title = if (on) "Увімкнути 220 В" else "Вимкнути 220 В",
        confirm = if (on) {
            "Увімкнути вихід 220 В на станції? На розетки подасться напруга — переконайтесь, що це безпечно."
        } else {
            "Вимкнути вихід 220 В? Усе, що живиться від розеток станції, знеструмиться."
        },
        json = g2(5, "acOutCfg", linkedMapOf("enabled" to if (on) 1 else 0, "xboost" to 1, "out_voltage" to 4294967295L, "out_freq" to 1)),
        powerAffecting = true,
    )

    /** Увімкнення/вимкнення DC-виходу 12 В (авторозетка). */
    fun dcOut(on: Boolean): DeviceCommand = DeviceCommand(
        key = "dc",
        title = if (on) "Увімкнути DC 12 В" else "Вимкнути DC 12 В",
        confirm = if (on) "Увімкнути DC-вихід 12 В?" else "Вимкнути DC-вихід 12 В?",
        json = g2(5, "dcOutCfg", linkedMapOf("enabled" to if (on) 1 else 0)),
        powerAffecting = true,
    )

    /** Ліміт заряду (максимальний SOC), %. */
    fun maxChargeSoc(soc: Int): DeviceCommand = DeviceCommand(
        key = "maxChg",
        title = "Ліміт заряду $soc%",
        confirm = "Виставити ліміт заряду $soc%?",
        json = g2(2, "upsConfig", linkedMapOf("maxChgSoc" to soc.coerceIn(50, 100))),
        powerAffecting = false,
    )

    /** Нижній ліміт розряду (мінімальний SOC), %. */
    fun minDischargeSoc(soc: Int): DeviceCommand = DeviceCommand(
        key = "minDsg",
        title = "Нижній ліміт розряду $soc%",
        confirm = "Виставити нижній ліміт розряду $soc%?",
        json = g2(2, "dsgCfg", linkedMapOf("minDsgSoc" to soc.coerceIn(0, 30))),
        powerAffecting = false,
    )

    /** Набір швидких дій для 2-го покоління. */
    fun quickActions(): List<DeviceCommand> = listOf(
        acOut(true), acOut(false),
        dcOut(true), dcOut(false),
        maxChargeSoc(100), minDischargeSoc(0),
    )
}
