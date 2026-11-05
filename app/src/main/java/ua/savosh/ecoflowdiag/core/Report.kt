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
    private fun batteryBySoh(soh: Double?): String = Health.of(soh)?.report ?: tr("Недостатньо даних")

    /** Загальний стан для короткого звіту — теж за здоров'ям батареї, без помилок. */
    private fun overallBySoh(soh: Double?): String = when {
        soh == null -> tr("Недостатньо даних")
        soh >= 80 -> tr("Працездатна")
        soh >= 65 -> tr("Працездатна (помітний знос батареї)")
        else -> tr("Батарея зношена")
    }

    /** Порожній блок підпису — майстер вписує свою назву сервісу. Без автопідпису. */
    private fun signatureBlock(): String =
        tr("──────────────────────\nНазва сервісу: ____________________\nКонтакти: ____________________\nМайстер: ____________________\nПідпис: ____________________\n")

    /** Короткий звіт — зрозумілий клієнту, лише ключові показники, без вигаданих значень. */
    private fun buildShort(r: StationReport, note: String?, dateText: String): String {
        val sb = StringBuilder()
        sb.appendLine(tr("ЗВІТ ПРО СТАН ЗАРЯДНОЇ СТАНЦІЇ"))
        sb.appendLine()
        sb.appendLine(tr("Модель: {0}", r.model ?: tr("не визначена")))
        sb.appendLine(tr("Серійний номер: {0}", r.sn))
        sb.appendLine(tr("Дата перевірки: {0}", dateText))
        sb.appendLine()

        // Стан батареї — ТІЛЬКИ за SOH (здоров'ям). Помилки/коди станції на оцінку не впливають.
        val soh = r.packs.mapNotNull { it.sohEffective }.minOrNull()
        sb.appendLine(tr("Стан батареї: {0}", batteryBySoh(soh)))
        if (soh != null) sb.appendLine(tr("Здоров'я батареї (SOH): {0}%", fmt0(soh)))
        if (r.totalFullWh != null && r.totalDesignWh != null && r.totalDesignWh > 0) {
            sb.appendLine(
                tr("Залишкова ємність: ≈{0} з {1} Вт·год ({2}%)", fmt0(r.totalFullWh), fmt0(r.totalDesignWh), fmt0(r.totalFullWh * 100 / r.totalDesignWh)),
            )
        }
        val cycles = r.packs.mapNotNull { it.cycles }.maxOrNull()
        sb.appendLine(tr("Кількість циклів: {0}", cycles?.toString() ?: tr("Немає даних")))
        // З підписами: без них «120 год; 30 год» незрозуміло, що саме працювало.
        sb.appendLine(tr("Напрацювання: {0}", if (r.usage.isNotEmpty()) r.usage.joinToString("; ") { "${it.first} — ${it.second}" } else tr("Немає даних")))
        val out = r.energy.filter { it.first in Analyzer.energyOutTitles() }
        sb.appendLine(tr("Видано енергії: {0}", if (out.isNotEmpty()) out.joinToString("; ") { "${it.first} — ${it.second}" } else tr("Немає даних")))
        sb.appendLine()

        sb.appendLine(tr("Загальний стан: {0}", overallBySoh(soh)))
        sb.appendLine(tr("Висновок майстра: {0}", note?.takeIf { it.isNotBlank() } ?: "______________________________"))
        sb.appendLine()

        sb.appendLine(tr("Перевірено майстром (заповнити за потреби):"))
        sb.appendLine(tr("  Вихід 230 В: ____________"))
        sb.appendLine(tr("  USB-виходи: ____________"))
        sb.appendLine(tr("  Заряджання від мережі: ____________"))
        sb.appendLine()

        sb.append(signatureBlock())
        return sb.toString()
    }

    /**
     * [history] — історія змін кодів станції (новіші першими); у звіт іде не більше [HISTORY_IN_REPORT] подій.
     * [log] — журнал перевірок станції між візитами (знімки діагностики, баланс, тест ємності).
     */
    fun technical(
        r: StationReport,
        note: String?,
        dateText: String,
        history: List<CodeEvent> = emptyList(),
        log: List<LogEntry> = emptyList(),
    ): String = build(r, note, dateText, technical = true, history = history, log = log)

    const val HISTORY_IN_REPORT = 30
    const val LOG_IN_REPORT = 15

    private fun build(
        r: StationReport,
        note: String?,
        dateText: String,
        technical: Boolean,
        history: List<CodeEvent> = emptyList(),
        log: List<LogEntry> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.appendLine(tr("РОЗШИРЕНИЙ ЗВІТ — діагностика станції"))
        sb.appendLine(tr("Дата перевірки: {0}", dateText))
        sb.appendLine(tr("Станція: {0} · SN {1}", r.model ?: tr("модель не визначена"), r.sn))
        if (!note.isNullOrBlank()) sb.appendLine(tr("Примітка: {0}", note))
        if (technical) sb.appendLine(tr("Покоління: {0} · параметрів: {1}", r.generation.title, r.paramCount))
        sb.appendLine()
        sb.appendLine(tr("ПІДСУМОК: {0} — {1}", r.level.statusTitle.uppercase(), r.summary))
        if (r.totalDesignWh != null && r.totalFullWh != null) {
            sb.append(tr("Загальна ємність: ≈{0} з {1} Вт·год ({2}%)", fmt0(r.totalFullWh), fmt0(r.totalDesignWh), fmt0(r.totalFullWh * 100 / r.totalDesignWh)))
            r.totalPassportWh?.let { sb.append(tr("; за паспортом {0} Вт·год", fmt0(it))) }
            sb.appendLine()
        }

        for (p in r.packs) {
            sb.appendLine()
            sb.appendLine("■ ${p.title}${p.sn?.let { " (SN $it)" } ?: ""}")
            sb.appendLine(tr("  Оцінка батареї (за SOH): {0}", p.health?.report ?: tr("недостатньо даних")))
            sb.appendLine(
                if (p.issues.isEmpty()) tr("  Зауважень немає") else tr("  Зауваження: ") + p.issues.joinToString("; ") { it.short ?: it.text },
            )
            p.sohEffective?.let { sb.appendLine(tr("  Здоров'я (SOH): {0}%", fmt0(it))) }
            if (technical && (p.sohBms != null || p.sohCapacity != null)) {
                sb.appendLine(
                    tr("  SOH за BMS: {0} · за вивченою ємністю: {1}", p.sohBms?.let { fmt0(it) + "%" } ?: "—", p.sohCapacity?.let { fmt0(it) + "%" } ?: "—"),
                )
            }
            if (p.designCapMah != null) {
                sb.append(tr("  Ємність: {0} / {1} А·год", p.fullCapMah?.let { fmt1(it / 1000.0) } ?: "?", fmt1(p.designCapMah / 1000.0)))
                if (p.fullWh != null && p.designWh != null) sb.append(tr(" (≈{0} / {1} Вт·год)", fmt0(p.fullWh), fmt0(p.designWh)))
                sb.appendLine()
            }
            p.passport?.wh?.let { sb.appendLine(tr("  Паспорт: {0}, {1} Вт·год", p.passport.name, it) + if (p.passport.inferred) tr(" (тип визначено за параметрами)") else "") }
            p.cycles?.let { c ->
                sb.append(tr("  Циклів: {0}", c))
                if (p.ratedCycles != null && p.resourceUsedPct != null) {
                    sb.append(tr(" (≈{0}% з ~{1} до 80% ємності — оцінка за лічильником BMS)", fmt0(p.resourceUsedPct), p.ratedCycles))
                }
                sb.appendLine()
            }
            sb.appendLine(
                tr("  Хімія: {0}{1}", p.chemistry.title, if (!p.chemistryConfident && p.chemistry != Chemistry.UNKNOWN) tr(" (ймовірно)") else "") +
                    (p.seriesCells?.let { ", ${it}S" } ?: ""),
            )
            p.soc?.let { sb.appendLine(tr("  Заряд зараз: {0}%", fmt0(it))) }
            if (p.minCellMv != null && p.maxCellMv != null) {
                sb.appendLine(tr("  Напруга груп: {0}…{1} В, розкид {2} мВ", fmt3(p.minCellMv / 1000.0), fmt3(p.maxCellMv / 1000.0), p.cellDeltaMv ?: (p.maxCellMv - p.minCellMv)))
            }
            if (technical && p.cellVols.isNotEmpty()) {
                sb.appendLine(tr("  По групах, В: ") + p.cellVols.mapIndexed { i, v -> "${i + 1}:${fmt3(v / 1000.0)}" }.joinToString(" "))
            }
            if (p.maxCellTemp != null) sb.appendLine(tr("  Температура елементів: {0}…{1} °C", p.minCellTemp ?: "?", p.maxCellTemp))
            for (f in p.findings) sb.appendLine("  • ${f.text}")

            if (technical) {
                p.mos?.let { sb.appendLine(tr("  Силові ключі: {0}{1}", it.value, it.note?.let { n -> " — $n" } ?: "")) }
                if (p.codes.isNotEmpty()) {
                    sb.appendLine(tr("  Коди BMS:"))
                    for (c in p.codes) sb.appendLine("    ${mark(c.level)} ${c.title}: ${c.value}${c.note?.let { " — $it" } ?: ""}")
                }
                if (p.notProvided.isNotEmpty()) {
                    sb.appendLine(tr("  Не передається цією BMS: {0}", p.notProvided.joinToString("; ")))
                }
            }
        }

        if (technical && r.mode.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Режим роботи й налаштування:"))
            for (m in r.mode) sb.appendLine("  ${m.title}: ${m.value}${m.note?.let { " — $it" } ?: ""}")
        }
        if (technical && r.protections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Захисти й зв'язок:"))
            for (m in r.protections) sb.appendLine("  ${mark(m.level)} ${m.title}: ${m.value}${m.note?.let { " — $it" } ?: ""}")
        }
        if (technical && history.isNotEmpty()) {
            sb.appendLine()
            val shown = history.take(HISTORY_IN_REPORT)
            sb.appendLine(tr("Історія кодів і захистів ({0}):", if (shown.size < history.size) tr("останні {0} з {1}", shown.size, history.size) else "${history.size}"))
            for (e in shown) sb.appendLine("  ${mark(e.level)} ${CodeHistory.line(e)}")
        }
        if (technical && log.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Історія перевірок станції:"))
            val chem = r.packs.firstOrNull { it.isMain }?.chemistry ?: Chemistry.UNKNOWN
            StationLog.trend(log, chem)?.let { sb.appendLine("  $it") }
            for (e in log.take(LOG_IN_REPORT)) sb.appendLine("  ${StationLog.line(e)}")
        }
        if (r.usage.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Напрацювання станції:"))
            for ((k, v) in r.usage) sb.appendLine("  $k: $v")
        }
        if (technical && r.energy.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Енергія за весь час:"))
            for ((k, v) in r.energy) sb.appendLine("  $k: $v")
            r.energyNote?.let { sb.appendLine("  ($it)") }
        }
        if (technical && r.firmware.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Прошивки:"))
            for ((k, v) in r.firmware) sb.appendLine("  $k: $v")
        }
        if (technical && r.other.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(tr("Інше:"))
            for ((k, v) in r.other) sb.appendLine("  $k: $v")
        }
        sb.appendLine()
        sb.appendLine(tr("Дані зчитано з BMS станції."))
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
