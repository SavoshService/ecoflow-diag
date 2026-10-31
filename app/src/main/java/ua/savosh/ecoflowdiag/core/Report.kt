package ua.savosh.ecoflowdiag.core

import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import ua.savosh.ecoflowdiag.core.Analyzer.fmt3

/**
 * Текстові звіти:
 * - [client] — для клієнта: стан батарей, ємність, цикли, напрацювання, без службових полів;
 * - [technical] — для майстра: усе, включно з кодами BMS, захистами, режимом роботи й прошивками.
 */
object Report {

    fun client(r: StationReport, note: String?, dateText: String): String = buildShort(r, note, dateText)

    /** Оцінка батареї ЛИШЕ за здоров'ям (SOH), єдина шкала [Health]. Помилки/коди станції на це не впливають. */
    private fun batteryBySoh(soh: Double?): String = Health.of(soh)?.report ?: "Недостатньо даних"

    /** Загальний стан для короткого звіту — теж за здоров'ям батареї, без помилок. */
    private fun overallBySoh(soh: Double?): String = when {
        soh == null -> "Недостатньо даних"
        soh >= 80 -> "Працездатна"
        soh >= 65 -> "Працездатна (помітний знос батареї)"
        else -> "Батарея зношена"
    }

    /** Порожній блок підпису — майстер вписує свою назву сервісу. Без автопідпису. */
    private fun signatureBlock(): String =
        "──────────────────────\n" +
            "Назва сервісу: ____________________\n" +
            "Контакти: ____________________\n" +
            "Майстер: ____________________\n" +
            "Підпис: ____________________\n"

    /** Короткий звіт — зрозумілий клієнту, лише ключові показники, без вигаданих значень. */
    private fun buildShort(r: StationReport, note: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.appendLine("ЗВІТ ПРО СТАН ЗАРЯДНОЇ СТАНЦІЇ")
        sb.appendLine()
        sb.appendLine("Модель: ${r.model ?: "не визначена"}")
        sb.appendLine("Серійний номер: ${r.sn}")
        sb.appendLine("Дата перевірки: $dateText")
        sb.appendLine()

        // Стан батареї — ТІЛЬКИ за SOH (здоров'ям). Помилки/коди станції на оцінку не впливають.
        val soh = r.packs.mapNotNull { it.sohEffective }.minOrNull()
        sb.appendLine("Стан батареї: ${batteryBySoh(soh)}")
        if (soh != null) sb.appendLine("Здоров'я батареї (SOH): ${fmt0(soh)}%")
        if (r.totalFullWh != null && r.totalDesignWh != null && r.totalDesignWh > 0) {
            sb.appendLine(
                "Залишкова ємність: ≈${fmt0(r.totalFullWh)} з ${fmt0(r.totalDesignWh)} Вт·год " +
                    "(${fmt0(r.totalFullWh * 100 / r.totalDesignWh)}%)",
            )
        }
        val cycles = r.packs.mapNotNull { it.cycles }.maxOrNull()
        sb.appendLine("Кількість циклів: ${cycles?.toString() ?: "Немає даних"}")
        // З підписами: без них «120 год; 30 год» незрозуміло, що саме працювало.
        sb.appendLine("Напрацювання: ${if (r.usage.isNotEmpty()) r.usage.joinToString("; ") { "${it.first} — ${it.second}" } else "Немає даних"}")
        // «вих» ловить і «AC-вихід», і «DC/USB-виходи» (раніше USB/DC сюди не потрапляли).
        val out = r.energy.filter { it.first.contains("вих", ignoreCase = true) }
        sb.appendLine("Видано енергії: ${if (out.isNotEmpty()) out.joinToString("; ") { "${it.first} — ${it.second}" } else "Немає даних"}")
        sb.appendLine()

        sb.appendLine("Загальний стан: ${overallBySoh(soh)}")
        sb.appendLine("Висновок майстра: ${note?.takeIf { it.isNotBlank() } ?: "______________________________"}")
        sb.appendLine()

        sb.appendLine("Перевірено майстром (заповнити за потреби):")
        sb.appendLine("  Вихід 230 В: ____________")
        sb.appendLine("  USB-виходи: ____________")
        sb.appendLine("  Заряджання від мережі: ____________")
        sb.appendLine()

        sb.append(signatureBlock())
        return sb.toString()
    }

    /** [history] — історія змін кодів станції (новіші першими); у звіт іде не більше [HISTORY_IN_REPORT] подій. */
    fun technical(r: StationReport, note: String?, dateText: String, history: List<CodeEvent> = emptyList()): String =
        build(r, note, dateText, technical = true, history = history)

    const val HISTORY_IN_REPORT = 30

    private fun build(
        r: StationReport,
        note: String?,
        dateText: String,
        technical: Boolean,
        history: List<CodeEvent> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.appendLine("РОЗШИРЕНИЙ ЗВІТ — діагностика станції")
        sb.appendLine("Дата перевірки: $dateText")
        sb.appendLine("Станція: ${r.model ?: "модель не визначена"} · SN ${r.sn}")
        if (!note.isNullOrBlank()) sb.appendLine("Примітка: $note")
        if (technical) sb.appendLine("Покоління: ${r.generation.title} · параметрів: ${r.paramCount}")
        sb.appendLine()
        sb.appendLine("ПІДСУМОК: ${r.level.statusTitle.uppercase()} — ${r.summary}")
        if (r.totalDesignWh != null && r.totalFullWh != null) {
            sb.append("Загальна ємність: ≈${fmt0(r.totalFullWh)} з ${fmt0(r.totalDesignWh)} Вт·год (${fmt0(r.totalFullWh * 100 / r.totalDesignWh)}%)")
            r.totalPassportWh?.let { sb.append("; за паспортом ${fmt0(it)} Вт·год") }
            sb.appendLine()
        }

        for (p in r.packs) {
            sb.appendLine()
            sb.appendLine("■ ${p.title}${p.sn?.let { " (SN $it)" } ?: ""}")
            sb.appendLine("  Оцінка батареї (за SOH): ${p.health?.report ?: "недостатньо даних"}")
            sb.appendLine(
                if (p.issues.isEmpty()) "  Зауважень немає" else "  Зауваження: " + p.issues.joinToString("; ") { it.short ?: it.text },
            )
            p.sohEffective?.let { sb.appendLine("  Здоров'я (SOH): ${fmt0(it)}%") }
            if (technical && (p.sohBms != null || p.sohCapacity != null)) {
                sb.appendLine(
                    "  SOH за BMS: ${p.sohBms?.let { fmt0(it) + "%" } ?: "—"} · за вивченою ємністю: ${p.sohCapacity?.let { fmt0(it) + "%" } ?: "—"}",
                )
            }
            if (p.designCapMah != null) {
                sb.append("  Ємність: ${p.fullCapMah?.let { fmt1(it / 1000.0) } ?: "?"} / ${fmt1(p.designCapMah / 1000.0)} А·год")
                if (p.fullWh != null && p.designWh != null) sb.append(" (≈${fmt0(p.fullWh)} / ${fmt0(p.designWh)} Вт·год)")
                sb.appendLine()
            }
            p.passport?.wh?.let { sb.appendLine("  Паспорт: ${p.passport.name}, $it Вт·год" + if (p.passport.inferred) " (тип визначено за параметрами)" else "") }
            p.cycles?.let { c ->
                sb.append("  Циклів: $c")
                if (p.ratedCycles != null && p.resourceUsedPct != null) {
                    sb.append(" (≈${fmt0(p.resourceUsedPct)}% з ~${p.ratedCycles} до 80% ємності — оцінка за лічильником BMS)")
                }
                sb.appendLine()
            }
            sb.appendLine(
                "  Хімія: ${p.chemistry.title}${if (!p.chemistryConfident && p.chemistry != Chemistry.UNKNOWN) " (ймовірно)" else ""}" +
                    (p.seriesCells?.let { ", ${it}S" } ?: ""),
            )
            p.soc?.let { sb.appendLine("  Заряд зараз: ${fmt0(it)}%") }
            if (p.minCellMv != null && p.maxCellMv != null) {
                sb.appendLine("  Напруга груп: ${fmt3(p.minCellMv / 1000.0)}…${fmt3(p.maxCellMv / 1000.0)} В, розкид ${p.cellDeltaMv ?: (p.maxCellMv - p.minCellMv)} мВ")
            }
            if (technical && p.cellVols.isNotEmpty()) {
                sb.appendLine("  По групах, В: " + p.cellVols.mapIndexed { i, v -> "${i + 1}:${fmt3(v / 1000.0)}" }.joinToString(" "))
            }
            if (p.maxCellTemp != null) sb.appendLine("  Температура елементів: ${p.minCellTemp ?: "?"}…${p.maxCellTemp} °C")
            for (f in p.findings) sb.appendLine("  • ${f.text}")

            if (technical) {
                p.mos?.let { sb.appendLine("  Силові ключі: ${it.value}${it.note?.let { n -> " — $n" } ?: ""}") }
                if (p.codes.isNotEmpty()) {
                    sb.appendLine("  Коди BMS:")
                    for (c in p.codes) sb.appendLine("    ${mark(c.level)} ${c.title}: ${c.value}${c.note?.let { " — $it" } ?: ""}")
                }
                if (p.notProvided.isNotEmpty()) {
                    sb.appendLine("  Не передається цією BMS: ${p.notProvided.joinToString("; ")}")
                }
            }
        }

        if (technical && r.mode.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Режим роботи й налаштування:")
            for (m in r.mode) sb.appendLine("  ${m.title}: ${m.value}${m.note?.let { " — $it" } ?: ""}")
        }
        if (technical && r.protections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Захисти й зв'язок:")
            for (m in r.protections) sb.appendLine("  ${mark(m.level)} ${m.title}: ${m.value}${m.note?.let { " — $it" } ?: ""}")
        }
        if (technical && history.isNotEmpty()) {
            sb.appendLine()
            val shown = history.take(HISTORY_IN_REPORT)
            sb.appendLine("Історія кодів і захистів (${if (shown.size < history.size) "останні ${shown.size} з ${history.size}" else "${history.size}"}):")
            for (e in shown) sb.appendLine("  ${mark(e.level)} ${CodeHistory.line(e)}")
        }
        if (r.usage.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Напрацювання станції:")
            for ((k, v) in r.usage) sb.appendLine("  $k: $v")
        }
        if (technical && r.energy.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Енергія за весь час:")
            for ((k, v) in r.energy) sb.appendLine("  $k: $v")
            r.energyNote?.let { sb.appendLine("  ($it)") }
        }
        if (technical && r.firmware.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Прошивки:")
            for ((k, v) in r.firmware) sb.appendLine("  $k: $v")
        }
        if (technical && r.other.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Інше:")
            for ((k, v) in r.other) sb.appendLine("  $k: $v")
        }
        sb.appendLine()
        sb.appendLine("Дані зчитано з BMS станції.")
        sb.appendLine()
        sb.append(signatureBlock())
        return sb.toString()
    }

    private fun mark(l: Level): String = when (l) {
        Level.GOOD, Level.OK -> "✓"
        Level.WARN -> "!"
        Level.BAD, Level.CRITICAL -> "✗"
        Level.UNKNOWN -> "·"
    }
}
