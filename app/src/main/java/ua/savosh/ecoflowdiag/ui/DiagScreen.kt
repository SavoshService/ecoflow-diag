package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.core.Analyzer.energyString
import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import ua.savosh.ecoflowdiag.core.Analyzer.fmt3
import ua.savosh.ecoflowdiag.core.Chemistry
import ua.savosh.ecoflowdiag.core.CodeEvent
import ua.savosh.ecoflowdiag.core.CodeHistory
import ua.savosh.ecoflowdiag.core.EcoflowCodes
import ua.savosh.ecoflowdiag.core.Indicator
import ua.savosh.ecoflowdiag.core.Level
import ua.savosh.ecoflowdiag.core.PackReport
import ua.savosh.ecoflowdiag.core.StationReport

private class CodeEdit(val key: String, val title: String, val value: String)

private val TABS = listOf("Огляд", "Монітор", "Коди", "Керування")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val r = vm.report
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var editCode by remember { mutableStateOf<CodeEdit?>(null) }
    var explain by remember { mutableStateOf<EcoflowCodes.Doc?>(null) }

    fun share(technical: Boolean) {
        vm.reportText(technical)?.let { shareText(context, it, "${if (technical) "Технічний звіт" else "Діагностика"} ${vm.diagSn}") }
    }
    val onEditCode: (Indicator) -> Unit = { ind -> ind.codeKey?.let { editCode = CodeEdit(it, ind.title, ind.value) } }
    val onExplain: (String?) -> Unit = { key -> EcoflowCodes.forKey(key)?.let { explain = it } }

    // «Живий» режим — частіше опитувати станцію, поки відкрито монітор або керування
    DisposableEffect(tab) {
        vm.liveMode = (tab == 1 || tab == 3)
        onDispose { vm.liveMode = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = {
                    Column {
                        Text(r?.model ?: "Діагностика", fontWeight = FontWeight.Bold)
                        Text(vm.diagSn ?: "", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshNow() }) { Icon(Icons.Filled.Refresh, contentDescription = "Оновити") }
                    Box {
                        IconButton(onClick = { shareMenu = true }, enabled = r != null) { Icon(Icons.Filled.Share, contentDescription = "Поділитися") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(text = { Text("Звіт клієнту") }, onClick = { shareMenu = false; share(false) })
                            DropdownMenuItem(text = { Text("Технічний звіт (усі коди й поля)") }, onClick = { shareMenu = false; share(true) })
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Ще") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("🔋 Тест реальної ємності") }, onClick = { menu = false; vm.openCapTest() }, enabled = r != null)
                            DropdownMenuItem(text = { Text("Сирі дані (усі параметри)") }, onClick = { menu = false; vm.openRaw() })
                            DropdownMenuItem(text = { Text("Журнал підключення") }, onClick = { menu = false; vm.openLog() })
                            if (vm.codeHistory.isNotEmpty()) {
                                DropdownMenuItem(text = { Text("Очистити історію кодів") }, onClick = { menu = false; vm.clearCodeHistory() })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (r != null) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                    TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (r == null) {
                    ConnectingCard(vm)
                } else {
                    when (tab) {
                        0 -> OverviewTab(vm, r, onEditCode)
                        1 -> MonitorTab(vm, r)
                        2 -> CodesTab(vm, r, onEditCode, onExplain)
                        else -> ControlContent(vm)
                    }
                }
            }
        }
    }

    editCode?.let { ec ->
        CodeNoteDialog(ec, vm.codeNote(ec.key), onDismiss = { editCode = null }, onSave = { vm.setCodeNote(ec.key, it); editCode = null })
    }
    explain?.let { doc -> ExplainDialog(doc) { explain = null } }
}

// ============================================================ підключення

@Composable
private fun ConnectingCard(vm: AppViewModel) {
    SectionCard("Підключення") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.diagBusy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                vm.diagStatus.ifBlank { "Підготовка…" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (vm.diagError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (vm.diagError) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.retryDiag() }) { Text("Повторити") }
                OutlinedButton(onClick = { vm.openLog() }) { Text("Журнал") }
            }
        }
        if (vm.messageCount > 0) {
            Spacer(Modifier.height(8.dp))
            HintText("Отримано повідомлень: ${vm.messageCount}")
        }
    }
}

// ============================================================ Огляд

@Composable
private fun OverviewTab(vm: AppViewModel, r: StationReport, onEditCode: (Indicator) -> Unit) {
    val ctx = LocalContext.current
    SectionCard("Стан станції") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelChip(r.level)
            Spacer(Modifier.width(10.dp))
            ConnectionDot(vm.diagConnected, vm.diagOnline)
        }
        Spacer(Modifier.height(10.dp))
        Text(r.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (r.packs.size > 1 && r.totalDesignWh != null && r.totalFullWh != null) {
            Spacer(Modifier.height(8.dp))
            KeyValue("Разом ємність", "≈${fmt0(r.totalFullWh)} / ${fmt0(r.totalDesignWh)} Вт·год")
        }
    }
    val single = r.packs.size == 1
    for (p in r.packs) {
        PackCard(p, single, onEditCode)
    }
    if (r.packs.isEmpty()) {
        SectionCard("Батарея") {
            HintText("Дані станції надходять, але параметрів батареї поки немає. Зачекайте 10–30 с або натисніть «Оновити».")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { vm.openCapTest() }, modifier = Modifier.weight(1f)) {
            Text(if (vm.capTestRunning) "🔋 Тест триває" else "🔋 Тест ємності")
        }
        OutlinedButton(
            onClick = { vm.reportText(false)?.let { shareText(ctx, it, "Діагностика ${vm.diagSn}") } },
            modifier = Modifier.weight(1f),
        ) { Text("Звіт клієнту") }
    }
}

/** Картка батареї: коротко завжди + «Розширена інформація» розкриває деталі. */
@Composable
private fun PackCard(p: PackReport, single: Boolean, onEditCode: (Indicator) -> Unit) {
    val title = if (single) "Батарея" else p.title
    var expanded by rememberSaveable(key = "exp_${p.key}") { mutableStateOf(false) }
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SohGauge(p.sohEffective, p.level)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                p.soc?.let {
                    Text("🔋 ${fmt0(it)}%", fontFamily = Mono, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (p.fullWh != null && p.designWh != null) {
                    Text("≈${fmt0(p.fullWh)} / ${fmt0(p.designWh)} Вт·год", fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Циклів: ${p.cycles?.toString() ?: "—"}", fontFamily = Mono, style = MaterialTheme.typography.bodyMedium)
                Text(chemistryText(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(p.verdict, style = MaterialTheme.typography.titleSmall, color = levelColor(p.level), fontWeight = FontWeight.SemiBold)

        // короткий поточний стан
        Spacer(Modifier.height(10.dp))
        p.ampMa?.let {
            val a = it / 1000.0
            KeyValue("Струм зараз", (if (a > 0) "+" else "") + "${fmt1(a)} А" + (p.volMv?.let { v -> " · ${fmt1(v / 1000.0)} В" } ?: ""))
        }
        if (p.maxCellTemp != null) KeyValue("Температура", "${p.minCellTemp ?: "?"}…${p.maxCellTemp} °C")

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "▲ Згорнути" else "▼ Розширена інформація")
        }
        if (expanded) PackDetails(p)
    }
}

/** Повні деталі батареї (ховаються під «Розширена інформація»). */
@Composable
private fun PackDetails(p: PackReport) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    p.sn?.let { KeyValue("SN пакета", it) }
    p.sohBms?.let { KeyValue("SOH за BMS", "${fmt0(it)}%") }
    p.sohCapacity?.let { KeyValue("SOH за вивченою ємністю", "${fmt0(it)}%") }
    if (p.designCapMah != null) {
        KeyValue("Ємність", "${p.fullCapMah?.let { fmt1(it / 1000.0) } ?: "?"} / ${fmt1(p.designCapMah / 1000.0)} А·год")
    }
    if (p.designWh != null && p.packVnom != null && p.designCapMah != null) {
        NoteKeyValue("Номінал за BMS", "${fmt0(p.designWh)} Вт·год",
            "${fmt1(p.designCapMah / 1000.0)} А·год × ${fmt1(p.packVnom)} В (${p.seriesCells ?: "?"} груп)")
    }
    p.passport?.wh?.let { KeyValue("За паспортом", "$it Вт·год") }
    if (p.cycles != null && p.ratedCycles != null && p.resourceUsedPct != null) {
        NoteKeyValue("Використано ресурсу", "≈${fmt0(p.resourceUsedPct)}%", "${p.cycles} з ~${p.ratedCycles} циклів до 80%")
    }
    p.remainCapMah?.let { KeyValue("Залишок", "${fmt1(it / 1000.0)} А·год") }
    if (p.maxMosTemp != null) KeyValue("Температура MOSFET", "${p.minMosTemp ?: "?"}…${p.maxMosTemp} °C")
    p.accuChgEnergyWh?.let { KeyValue("Накопичено заряду (BMS)", energyString(it.toDouble())) }
    p.accuDsgEnergyWh?.let { KeyValue("Накопичено розряду (BMS)", energyString(it.toDouble())) }

    if (p.minCellMv != null && p.maxCellMv != null) {
        Spacer(Modifier.height(12.dp))
        Text("Напруги груп", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        KeyValue("Мін / макс", "${fmt3(p.minCellMv / 1000.0)} / ${fmt3(p.maxCellMv / 1000.0)} В")
        val delta = p.cellDeltaMv ?: (p.maxCellMv - p.minCellMv)
        KeyValue("Розкид", "$delta мВ", valueColor = levelColor(deltaLevel(delta)))
        p.weakestCell?.let { KeyValue("Найнижча група", "№$it") }
        if (p.cellVols.size >= 2) {
            Spacer(Modifier.height(8.dp))
            CellBars(p.cellVols)
        }
    }
    if (p.findings.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Висновки", style = MaterialTheme.typography.labelLarge)
        for (f in p.findings) FindingRow(f.level, f.text)
    }
}

// ============================================================ Монітор

@Composable
private fun MonitorTab(vm: AppViewModel, r: StationReport) {
    val packs = r.packs.filter { it.volMv != null && it.ampMa != null }
    val w = packs.sumOf { it.volMv!! * it.ampMa!! / 1_000_000.0 }
    val main = r.packs.firstOrNull { it.isMain } ?: r.packs.firstOrNull()
    SectionCard("Зараз") {
        Text(
            when {
                w > 5 -> "⚡ Заряд +${fmt0(w)} Вт"
                w < -5 -> "🔌 Розряд ${fmt0(-w)} Вт"
                else -> "⏸ Без струму"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                w > 5 -> levelColor(Level.GOOD)
                w < -5 -> levelColor(Level.WARN)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Mini("Заряд", main?.soc?.let { "${fmt0(it)}%" } ?: "—")
            Mini("Напруга", main?.volMv?.let { "${fmt1(it / 1000.0)} В" } ?: "—")
            Mini("Струм", main?.ampMa?.let { "${fmt1(it / 1000.0)} А" } ?: "—")
        }
    }
    SectionCard("Потужність батареї (останні хвилини)") {
        PowerChart(vm.powerHistory)
        Spacer(Modifier.height(6.dp))
        HintText("Вгору — заряд, вниз — розряд. Поки відкрито цю вкладку, дані оновлюються частіше (кожні ~8 с).")
    }
}

@Composable
private fun Mini(label: String, value: String) {
    Column {
        Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PowerChart(history: List<Pair<Long, Double>>) {
    val good = levelColor(Level.GOOD)
    val warn = levelColor(Level.WARN)
    val axis = MaterialTheme.colorScheme.outline
    if (history.size < 2) {
        HintText("Збираю дані… графік з'явиться за кілька оновлень.")
        return
    }
    val vals = history.map { it.second }
    val maxAbs = maxOf(50.0, vals.maxOf { kotlin.math.abs(it) })
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val wdt = size.width
        val hgt = size.height
        val mid = hgt / 2f
        drawLine(axis, Offset(0f, mid), Offset(wdt, mid), strokeWidth = 1.dp.toPx())
        val n = vals.size
        val step = if (n > 1) wdt / (n - 1) else wdt
        for (i in 0 until n - 1) {
            val x1 = i * step
            val x2 = (i + 1) * step
            val y1 = mid - (vals[i] / maxAbs * (mid - 4)).toFloat()
            val y2 = mid - (vals[i + 1] / maxAbs * (mid - 4)).toFloat()
            val c = if ((vals[i] + vals[i + 1]) / 2 >= 0) good else warn
            drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2.5.dp.toPx())
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("↑ +${fmt0(maxAbs)} Вт", style = MaterialTheme.typography.labelSmall, color = good)
        Text("↓ -${fmt0(maxAbs)} Вт", style = MaterialTheme.typography.labelSmall, color = warn)
    }
}

// ============================================================ Коди

@Composable
private fun CodesTab(vm: AppViewModel, r: StationReport, onEditCode: (Indicator) -> Unit, onExplain: (String?) -> Unit) {
    for (p in r.packs) {
        if (p.mos != null || p.codes.isNotEmpty()) {
            SectionCard("Коди BMS${if (r.packs.size > 1) " · ${p.title}" else ""}") {
                p.mos?.let { m ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(top = 1.dp).size(8.dp).clip(CircleShape).background(levelColor(m.level)))
                            Spacer(Modifier.width(10.dp))
                            Text(m.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(m.value, style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
                        m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                for (c in p.codes) {
                    IndicatorRow(c.title, c.value, c.level, c.note, onEditNote = c.codeKey?.let { { onEditCode(c) } })
                    if (EcoflowCodes.hasExplanation(c.codeKey)) {
                        TextButton(onClick = { onExplain(c.codeKey) }, modifier = Modifier.padding(start = 18.dp)) {
                            Text("❓ Що це за помилка")
                        }
                    }
                }
            }
        }
        if (p.notProvided.isNotEmpty()) {
            SectionCard("Не передається цією BMS${if (r.packs.size > 1) " · ${p.title}" else ""}", collapsible = true, initiallyExpanded = false) {
                for (n in p.notProvided) HintText("· $n")
                HintText("Це заглушки прошивки, а не несправність.")
            }
        }
    }
    if (r.protections.isNotEmpty()) {
        SectionCard("Захисти й зв'язок", collapsible = true) {
            for (ind in r.protections) {
                IndicatorRow(ind.title, ind.value, ind.level, ind.note, onEditNote = ind.codeKey?.let { { onEditCode(ind) } })
            }
        }
    }
    SectionCard("Історія кодів і захистів", collapsible = true, initiallyExpanded = false) {
        val h = vm.codeHistory
        if (h.isEmpty()) {
            HintText("Змін поки не зафіксовано. Поки відкрита діагностика, кожна поява/зникнення коду записується з часом і станом станції.")
        } else {
            for (e in h.take(HISTORY_PREVIEW)) CodeEventRow(e)
            if (h.size > HISTORY_PREVIEW) HintText("…та ще ${h.size - HISTORY_PREVIEW}. Повністю — у технічному звіті.")
        }
    }
}

@Composable
private fun ExplainDialog(doc: EcoflowCodes.Doc, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(doc.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(levelColor(doc.level)))
                    Spacer(Modifier.width(8.dp))
                    Text(if (doc.confident) "Підтверджено на реальних станціях" else "Попередній висновок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(doc.detail, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Зрозуміло") } },
    )
}

// ============================================================ спільне

private const val HISTORY_PREVIEW = 12

@Composable
private fun ConnectionDot(connected: Boolean, online: Boolean?) {
    val (color, text) = when {
        !connected -> levelColor(Level.UNKNOWN) to "не підключено"
        online == false -> levelColor(Level.BAD) to "станція офлайн"
        else -> levelColor(Level.GOOD) to "онлайн"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CodeEventRow(e: CodeEvent) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(levelColor(e.level)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${CodeHistory.whenText(e)} · ${e.scope}", style = MaterialTheme.typography.labelMedium,
                color = if (e.betweenChecks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.title, style = MaterialTheme.typography.bodyMedium)
            Text(CodeHistory.changeText(e), style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, color = levelColor(e.level))
            e.context?.let { HintText(it) }
        }
    }
}

@Composable
private fun NoteKeyValue(title: String, value: String, note: String?) {
    Column(Modifier.padding(bottom = if (note.isNullOrBlank()) 0.dp else 4.dp)) {
        KeyValue(title, value)
        if (!note.isNullOrBlank()) HintText(note)
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CodeNoteDialog(ec: CodeEdit, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Примітка до коду") },
        text = {
            Column {
                Text("${ec.title}: ${ec.value}", style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                HintText("Ключ «${ec.key}». Примітка зберігається на телефоні й показується для цього коду на всіх станціях та у технічному звіті. Порожнє поле — видалити.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Що означає / що допомогло") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private fun chemistryText(p: PackReport): String {
    if (p.chemistry == Chemistry.UNKNOWN) return "Хімія: не визначено" + (p.seriesCells?.let { " · ${it}S" } ?: "")
    val sure = if (p.chemistryConfident) "" else " (ймовірно)"
    return "${p.chemistry.title}$sure" + (p.seriesCells?.let { " · ${it}S" } ?: "")
}

private fun deltaLevel(delta: Long): Level = when {
    delta <= 30 -> Level.GOOD
    delta <= 70 -> Level.WARN
    delta <= 150 -> Level.BAD
    else -> Level.CRITICAL
}
