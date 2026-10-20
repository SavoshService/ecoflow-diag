package ua.savosh.ecoflowdiag.core

/**
 * Визначення моделі за першими символами серійного номера.
 * Префікси взято з відкритих проєктів спільноти (ha-ef-ble). Якщо префікс невідомий,
 * покоління все одно визначається автоматично за форматом даних.
 */
object Models {

    class ModelInfo(val name: String, val generation: Generation)

    private val PREFIXES: List<Pair<String, ModelInfo>> = listOf(
        // 2-ге покоління (JSON, LFP)
        "R331" to ModelInfo("Delta 2", Generation.GEN2),
        "R335" to ModelInfo("Delta 2", Generation.GEN2),
        "R351" to ModelInfo("Delta 2 Max", Generation.GEN2),
        "R354" to ModelInfo("Delta 2 Max", Generation.GEN2),
        "R601" to ModelInfo("River 2", Generation.GEN2),
        "R603" to ModelInfo("River 2", Generation.GEN2),
        "R611" to ModelInfo("River 2 Max", Generation.GEN2),
        "R613" to ModelInfo("River 2 Max", Generation.GEN2),
        "R621" to ModelInfo("River 2 Pro", Generation.GEN2),
        "R623" to ModelInfo("River 2 Pro", Generation.GEN2),
        // 3-тє покоління (protobuf)
        "P231" to ModelInfo("Delta 3", Generation.GEN3),
        "P321" to ModelInfo("Delta 3 Classic", Generation.GEN3),
        "P322" to ModelInfo("Delta 3 Classic", Generation.GEN3),
        "P351" to ModelInfo("Delta 3 Plus", Generation.GEN3),
        "D3N1" to ModelInfo("Delta 3 Max", Generation.GEN3),
        "D3M1" to ModelInfo("Delta 3 Max Plus", Generation.GEN3),
        "D751" to ModelInfo("Delta 3 Ultra", Generation.GEN3),
        "D511" to ModelInfo("Delta 3 Ultra Plus", Generation.GEN3),
        "PR11" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "PR12" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "PR21" to ModelInfo("Delta 3 Air", Generation.GEN3),
        "MR51" to ModelInfo("Delta Pro 3", Generation.GEN3),
        "MR54" to ModelInfo("Delta Pro 3", Generation.GEN3),
        "R651" to ModelInfo("River 3", Generation.GEN3),
        "R653" to ModelInfo("River 3", Generation.GEN3),
        "R654" to ModelInfo("River 3", Generation.GEN3),
        "R655" to ModelInfo("River 3", Generation.GEN3),
        "R631" to ModelInfo("River 3 Plus", Generation.GEN3),
        "R634" to ModelInfo("River 3 Plus", Generation.GEN3),
        "R635" to ModelInfo("River 3 Plus", Generation.GEN3),
        // 1-ше покоління (JSON)
        "R511" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCA" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCK" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCE" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCC" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCU" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCT" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCG" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCS" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCF" to ModelInfo("Delta Pro", Generation.GEN1),
        "DCJ" to ModelInfo("Delta Pro", Generation.GEN1),
    )

    fun bySn(sn: String): ModelInfo? {
        val s = sn.trim().uppercase()
        return PREFIXES.firstOrNull { s.startsWith(it.first) }?.second
    }

    fun normalizeSn(sn: String): String = sn.trim().uppercase().filter { it.isLetterOrDigit() }

    /**
     * Паспортні дані батареї: енергія (Вт·год) і ресурс до 80% ємності.
     * null — виробник не вказує або дані не перевірені (тоді береться типове значення для хімії).
     */
    class Passport(val name: String, val wh: Int?, val cycles80: Int?, val chemistry: Chemistry?, val inferred: Boolean = false)

    private val PASSPORTS: Map<String, Passport> = mapOf(
        "Delta Pro" to Passport("DELTA Pro", 3600, 3500, Chemistry.LFP),
        "Delta Max" to Passport("DELTA Max", 2016, 800, Chemistry.NCM),
        "Delta Mini" to Passport("DELTA mini", 882, 800, Chemistry.NCM),
        "Delta 2" to Passport("DELTA 2", 1024, 3000, Chemistry.LFP),
        "Delta 2 Max" to Passport("DELTA 2 Max", 2048, 3000, Chemistry.LFP),
        "River 2" to Passport("RIVER 2", 256, 3000, Chemistry.LFP),
        "River 2 Max" to Passport("RIVER 2 Max", 512, 3000, Chemistry.LFP),
        "River 2 Pro" to Passport("RIVER 2 Pro", 768, 3000, Chemistry.LFP),
        "Delta Pro 3" to Passport("DELTA Pro 3", 4096, 4000, Chemistry.LFP),
        "Delta 3 Max Plus" to Passport("DELTA 3 Max Plus", 2048, 4000, Chemistry.LFP),
        "Delta 3 Plus" to Passport("DELTA 3 Plus", 1024, null, Chemistry.LFP),
        "Delta 3" to Passport("DELTA 3", 1024, null, Chemistry.LFP),
        "Delta 3 Classic" to Passport("DELTA 3 Classic", 1024, null, Chemistry.LFP),
        "Delta 3 Max" to Passport("DELTA 3 Max", 2048, null, Chemistry.LFP),
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
