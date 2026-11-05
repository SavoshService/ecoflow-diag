package ua.savosh.ecoflowdiag.core

import java.util.Locale

/** Мови застосунку. [title] пишеться мовою самої мови, щоб її впізнали незалежно від поточної. */
enum class AppLang(val code: String, val title: String) {
    UK("uk", "Українська"),
    EN("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLang? = entries.firstOrNull { it.code == code }

        /** Мова для нового встановлення: українська для України, англійська для решти світу. */
        fun forSystem(locale: Locale = Locale.getDefault()): AppLang =
            if (locale.language in setOf("uk", "ru", "be")) UK else EN
    }
}

/**
 * Переклад: український текст у коді — це ключ, англійський беремо зі словника [En].
 * Значення підставляються в {0}, {1}…, бо порядок слів у мовах різний.
 * Якщо перекладу немає, показуємо український текст: автотест I18nTest не пропустить таке в збірку.
 */
object I18n {
    @Volatile var lang: AppLang = AppLang.UK

    private val override = ThreadLocal<AppLang?>()

    val current: AppLang get() = override.get() ?: lang

    /** Виконати блок іншою мовою: те, що зберігається надовго, пишемо завжди українською (див. [localize]). */
    fun <T> with(l: AppLang, block: () -> T): T {
        val old = override.get()
        override.set(l)
        try {
            return block()
        } finally {
            override.set(old)
        }
    }

    fun format(template: String, args: Array<out Any?>): String {
        val sb = StringBuilder(template.length + 16)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{') {
                val end = template.indexOf('}', i)
                val n = if (end > i) template.substring(i + 1, end).toIntOrNull() else null
                if (n != null && n in args.indices) {
                    sb.append(args[n])
                    i = end + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private class Template(val key: String, val value: String) {
        val parts: List<String> = key.split(PLACEHOLDER)
        val indexes: List<Int> = PLACEHOLDER.findAll(key).map { it.groupValues[1].toInt() }.toList()
        val literalLength: Int = parts.sumOf { it.length }
        val regex: Regex by lazy {
            Regex(parts.joinToString("(.*?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL)
        }
    }

    private val PLACEHOLDER = Regex("\\{(\\d+)\\}")

    private val templates: List<Template> by lazy {
        En.map.entries.filter { PLACEHOLDER.containsMatchIn(it.key) }
            .map { Template(it.key, it.value) }
            .sortedByDescending { it.literalLength }
    }

    private val cache = HashMap<String, String>()

    private val reverse: Map<String, String> by lazy { En.map.entries.associate { it.value to it.key } }

    /** Український варіант вибраного людиною значення (наприклад, розділу): зберігаємо завжди його. */
    fun canonical(text: String): String = reverse[text] ?: text

    /**
     * Переклад уже готового тексту, збереженого раніше (історія кодів, журнал, підсумок на картці).
     * Такі тексти завжди українські: знаходимо шаблон, з якого вони зібрані, і збираємо англійською.
     * Невідоме (наприклад, примітка майстра) лишається як є.
     */
    fun localize(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        if (current == AppLang.UK) return text
        synchronized(cache) { cache[text] }?.let { return it }
        val out = translateStored(text, 0)
        synchronized(cache) {
            if (cache.size > 2000) cache.clear()
            cache[text] = out
        }
        return out
    }

    private fun hasCyrillic(text: String): Boolean = text.any { it in 'А'..'я' || it in "ІіЇїЄєҐґ" }

    private fun translateStored(text: String, depth: Int): String {
        En.map[text]?.let { return it }
        if (depth > 5 || !hasCyrillic(text)) return text
        // 1. Шаблон, що покриває більшу частину тексту, — надійний збіг.
        matchTemplate(text, depth, strongOnly = true)?.let { return it }
        // 2. Зібране частинами: перекладаємо кожну окремо.
        for (sep in SEPARATORS) {
            if (sep in text) return text.split(sep).joinToString(sep) { translateStored(it, depth + 1) }
        }
        // 3. Числа й дужки навколо відомого тексту: «Основна батарея (SOH 95%)».
        val first = text.indexOfFirst { it in 'А'..'я' || it in "ІіЇїЄєҐґ" }
        val last = text.indexOfLast { it in 'А'..'я' || it in "ІіЇїЄєҐґ" }
        En.map[text.substring(first, last + 1)]?.let { return text.substring(0, first) + it + text.substring(last + 1) }
        return matchTemplate(text, depth, strongOnly = false) ?: text
    }

    private val SEPARATORS = listOf("\n", " · ", " — ", "; ", ", ")

    private fun matchTemplate(text: String, depth: Int, strongOnly: Boolean): String? {
        for (t in templates) {
            if (strongOnly && t.literalLength * 2 < text.length) break
            if (!text.startsWith(t.parts.first()) || !text.endsWith(t.parts.last())) continue
            val m = t.regex.matchEntire(text) ?: continue
            val args = arrayOfNulls<Any?>((t.indexes.maxOrNull() ?: -1) + 1)
            for ((g, idx) in t.indexes.withIndex()) {
                if (args[idx] == null) args[idx] = translateStored(m.groupValues[g + 1], depth + 1)
            }
            return format(t.value, args)
        }
        return null
    }
}

fun tr(uk: String): String = if (I18n.current == AppLang.UK) uk else En.map[uk] ?: uk

fun tr(uk: String, vararg args: Any?): String = I18n.format(tr(uk), args)

/** Позначка: текст перекладається пізніше, під час показу (назви в enum і значення, що зберігаються). */
fun tk(uk: String): String = uk

/** Переклад збереженого тексту: див. [I18n.localize]. */
fun localize(text: String?): String = I18n.localize(text)
