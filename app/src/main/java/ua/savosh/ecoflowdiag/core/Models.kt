package ua.savosh.ecoflowdiag.core

/**
 * Визначення моделі за першими символами серійного номера.
 * Префікси взято з відкритих проєктів спільноти (ha-ef-ble, ha-ecoflow-iot, hassio-ecoflow-eu).
 * EcoFlow не публікує цієї таблиці, тож вона лише підказка: хімію перевіряє за напругами [Analyzer],
 * а покоління все одно визначається автоматично за форматом даних.
 */
object Models {

    /** [ncmName] — як назвати станцію, якщо за напругами в неї NCM: той самий префікс спільнота приписує і старим River. */
    class ModelInfo(val name: String, val generation: Generation, val ncmName: String? = null)

    private val OLD_RIVER = tk("River (1-ше покоління)")

    private val PREFIXES: List<Pair<String, ModelInfo>> = listOf(
        // 2-ге покоління (JSON, LFP)
        "R33" to ModelInfo("Delta 2", Generation.GEN2),
        "R35" to ModelInfo("Delta 2 Max", Generation.GEN2),
        "R70" to ModelInfo("Delta 2 (E980)", Generation.GEN2),
        "D361" to ModelInfo("Delta 3 1500", Generation.GEN2),
        "D365" to ModelInfo("Delta 3 1500", Generation.GEN2),
        "R60" to ModelInfo("River 2", Generation.GEN2, OLD_RIVER),
        "R61" to ModelInfo("River 2 Max", Generation.GEN2, OLD_RIVER),
        "R62" to ModelInfo("River 2 Pro", Generation.GEN2, OLD_RIVER),
        // 3-тє покоління (protobuf)
        "P231" to ModelInfo("Delta 3", Generation.GEN3),
        "P321" to ModelInfo("Delta 3 Classic", Generation.GEN3),
        "P322" to ModelInfo("Delta 3 Classic", Generation.GEN3),
        "P351" to ModelInfo("Delta 3 Plus", Generation.GEN3),
        "D362" to ModelInfo("Delta 3 Plus", Generation.GEN3),
        "D381" to ModelInfo("Delta 3 Max", Generation.GEN3),
        "D3N" to ModelInfo("Delta 3 Max", Generation.GEN3),
        "D3M" to ModelInfo("Delta 3 Max Plus", Generation.GEN3),
        "D751" to ModelInfo("Delta 3 Ultra", Generation.GEN3),
        "D511" to ModelInfo("Delta 3 Ultra Plus", Generation.GEN3),
        "PR11" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "PR12" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "PR21" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "MR5" to ModelInfo("Delta Pro 3", Generation.GEN3),
        "DGEA" to ModelInfo("Delta Pro 3", Generation.GEN3),
        "Y71" to ModelInfo("Delta Pro Ultra", Generation.GEN3),
        "P101" to ModelInfo("Delta Pro Ultra X", Generation.GEN3),
        "R64" to ModelInfo("River 3", Generation.GEN3),
        "R65" to ModelInfo("River 3", Generation.GEN3),
        "R63" to ModelInfo("River 3 Plus", Generation.GEN3),
        // 1-ше покоління (JSON). DELTA Pro — LFP, решта — NCM.
        "DC" to ModelInfo("Delta Pro", Generation.GEN1),
        "R511" to ModelInfo("Delta Pro", Generation.GEN1),
        "Z1" to ModelInfo("Delta Pro", Generation.GEN1),
        "DAAB" to ModelInfo("Delta Max", Generation.GEN1),
        // Спільнота бачила DAEB і на DELTA, і на DELTA Max: ємність уточнює passportForPack.
        "DAEB" to ModelInfo("Delta / Delta Max", Generation.GEN1),
        "DAAZ" to ModelInfo("Delta Mini", Generation.GEN1),
        "DBAB" to ModelInfo("Delta Mini", Generation.GEN1),
        "DBEB" to ModelInfo("Delta Mini", Generation.GEN1),
        "DBKB" to ModelInfo("Delta Mini", Generation.GEN1),
        "R602" to ModelInfo("River Pro", Generation.GEN1),
        "R501" to ModelInfo("River Mini", Generation.GEN1),
    )

    /** Найдовший префікс перемагає: R602 — River Pro, решта R60… — River 2. */
    fun bySn(sn: String): ModelInfo? {
        val s = normalizeSn(sn)
        return PREFIXES.filter { s.startsWith(it.first) }.maxByOrNull { it.first.length }?.second
    }

    /** Кирилиця, схожа на латиницю: на українській розкладці легко набрати С замість C — вигляд той самий, номер інший. */
    private val LOOKALIKE: Map<Char, Char> = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'З' to '3', 'І' to 'I', 'Ї' to 'I', 'К' to 'K', 'М' to 'M',
        'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'У' to 'Y', 'Х' to 'X',
    )

    private fun toLatin(raw: String): String = raw.trim().uppercase().map { LOOKALIKE[it] ?: it }.joinToString("")

    private fun isSnChar(c: Char): Boolean = c in 'A'..'Z' || c in '0'..'9'

    /** Лише великі латинські літери й цифри. Кирилицю-двійника замінено на латиницю, решту символів прибрано. */
    fun normalizeSn(sn: String): String = toLatin(sn).filter { isSnChar(it) }

    /** Літери, яких у серійному номері не буває й нема чим замінити (наприклад, Д чи Ж). */
    fun foreignLetters(raw: String): List<Char> = toLatin(raw).filter { it.isLetter() && !isSnChar(it) }.toList().distinct()

    private val LABEL_TOKENS = setOf("S", "N", "SN", "SERIAL", "NO", "NUMBER", "NUM")

    /**
     * Серійний номер із того, що людина вставила чи відсканувала: копія з підписом («SN: R331…»),
     * посилання з QR-коду, номер із пробілами. Без цього підпис склеювався з номером («SNR331…»),
     * і хмара чесно казала, що такої станції немає.
     */
    fun extractSn(raw: String): String {
        val tokens = toLatin(raw).split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        val whole = tokens.filter { it.length in 12..24 && it.any(Char::isDigit) && it.any(Char::isLetter) }.map(::stripLabel)
        whole.firstOrNull { bySn(it) != null }?.let { return it }
        whole.firstOrNull()?.let { return it }
        return stripLabel(tokens.filter { it !in LABEL_TOKENS }.joinToString(""))
    }

    private fun stripLabel(token: String): String =
        if (token.startsWith("SN") && bySn(token) == null && bySn(token.drop(2)) != null) token.drop(2) else token

    /**
     * Паспортні дані батареї: енергія (Вт·год) і ресурс до 80% ємності.
     * null — виробник не вказує або дані не перевірені (тоді береться типове значення для хімії).
     */
    class Passport(val name: String, val wh: Int?, val cycles80: Int?, val chemistry: Chemistry?, val inferred: Boolean = false)

    private val PASSPORTS: Map<String, Passport> = mapOf(
        "Delta Pro" to Passport("DELTA Pro", 3600, 3500, Chemistry.LFP),
        // Без енергії: DELTA Max буває 1600 і 2000, а під DAEB трапляється ще й DELTA 1300.
        "Delta Max" to Passport("DELTA Max", null, 800, Chemistry.NCM),
        "Delta / Delta Max" to Passport("DELTA / DELTA Max", null, 800, Chemistry.NCM),
        "Delta Mini" to Passport("DELTA mini", 882, 800, Chemistry.NCM),
        "River Pro" to Passport("RIVER Pro", null, 800, Chemistry.NCM),
        "River Mini" to Passport("RIVER mini", null, null, Chemistry.NCM),
        "Delta 2" to Passport("DELTA 2", 1024, 3000, Chemistry.LFP),
        "Delta 2 Max" to Passport("DELTA 2 Max", 2048, 3000, Chemistry.LFP),
        "Delta 2 (E980)" to Passport("DELTA 2 (E980)", null, null, Chemistry.LFP),
        "River 2" to Passport("RIVER 2", 256, 3000, Chemistry.LFP),
        "River 2 Max" to Passport("RIVER 2 Max", 512, 3000, Chemistry.LFP),
        "River 2 Pro" to Passport("RIVER 2 Pro", 768, 3000, Chemistry.LFP),
        "River 3" to Passport("RIVER 3", null, null, Chemistry.LFP),
        "River 3 Plus" to Passport("RIVER 3 Plus", null, null, Chemistry.LFP),
        "Delta Pro 3" to Passport("DELTA Pro 3", 4096, 4000, Chemistry.LFP),
        "Delta Pro Ultra" to Passport("DELTA Pro Ultra", null, null, Chemistry.LFP),
        "Delta Pro Ultra X" to Passport("DELTA Pro Ultra X", null, null, Chemistry.LFP),
        "Delta 3 Max Plus" to Passport("DELTA 3 Max Plus", 2048, 4000, Chemistry.LFP),
        "Delta 3 Plus" to Passport("DELTA 3 Plus", 1024, null, Chemistry.LFP),
        "Delta 3" to Passport("DELTA 3", 1024, null, Chemistry.LFP),
        "Delta 3 Classic" to Passport("DELTA 3 Classic", 1024, null, Chemistry.LFP),
        "Delta 3 Max" to Passport("DELTA 3 Max", 2048, null, Chemistry.LFP),
        "Delta 3 1500" to Passport("DELTA 3 1500", 1536, null, Chemistry.LFP),
        "Delta 3 Air" to Passport("DELTA 3 Air", null, null, Chemistry.LFP),
        "Delta 3 Ultra" to Passport("DELTA 3 Ultra", null, null, Chemistry.LFP),
        "Delta 3 Ultra Plus" to Passport("DELTA 3 Ultra Plus", null, null, Chemistry.LFP),
    )

    fun passportForModel(model: String?): Passport? = model?.let { PASSPORTS[it] }

    /**
     * Визначення типу батареї за її параметрами (для додаткових батарей і станцій з невідомим префіксом SN).
     */
    fun passportForPack(chem: Chemistry, designMah: Long?, series: Int?): Passport? {
        if (designMah == null || series == null) return null
        return when {
            chem == Chemistry.LFP && series == 15 && designMah in 76_000L..84_000L ->
                Passport("DELTA Pro / DELTA Pro EB", 3600, 3500, Chemistry.LFP, inferred = true)
            chem == Chemistry.NCM && series == 14 && designMah in 38_000L..42_000L ->
                Passport("DELTA Max / DELTA Max EB", 2016, 800, Chemistry.NCM, inferred = true)
            chem == Chemistry.LFP && series == 16 && designMah in 38_000L..42_000L ->
                Passport("DELTA 2 Max / DELTA 2 Max EB", 2048, 3000, Chemistry.LFP, inferred = true)
            chem == Chemistry.LFP && series == 16 && designMah in 19_000L..21_000L ->
                Passport("DELTA 2 / DELTA 2 EB", 1024, 3000, Chemistry.LFP, inferred = true)
            chem == Chemistry.NCM && series == 14 && designMah in 16_500L..18_500L ->
                Passport("DELTA mini", 882, 800, Chemistry.NCM, inferred = true)
            else -> null
        }
    }
}
