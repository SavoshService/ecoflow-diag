package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.localize
import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.core.Analyzer.energyString
import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import ua.savosh.ecoflowdiag.core.Analyzer.fmt3
import ua.savosh.ecoflowdiag.core.CellSnapshot
import ua.savosh.ecoflowdiag.core.Chemistry
import ua.savosh.ecoflowdiag.core.CodeEvent
import ua.savosh.ecoflowdiag.core.CodeHistory
import ua.savosh.ecoflowdiag.core.EcoflowCodes
import ua.savosh.ecoflowdiag.core.Indicator
import ua.savosh.ecoflowdiag.core.Level
import ua.savosh.ecoflowdiag.core.LogEntry
import ua.savosh.ecoflowdiag.core.StationLog
import ua.savosh.ecoflowdiag.core.PackReport
import ua.savosh.ecoflowdiag.core.StationReport

private class CodeEdit(val key: String, val title: String, val value: String)

private val TABS get() = listOf(tr("Огляд"), tr("Монітор"), tr("Коди"), tr("Керування"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val r = vm.report
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editCode by remember { mutableStateOf<CodeEdit?>(null) }
    var explain by remember { mutableStateOf<EcoflowCodes.Doc?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) }
                },
                title = {
                    Column {
                        FitText(r?.model ?: tr("Діагностика"), fontWeight = FontWeight.Bold)
                        FitText(vm.diagSn ?: "", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    CompactActions {
                        IconButton(onClick = { vm.refreshNow() }) { Icon(Icons.Filled.Refresh, contentDescription = tr("Оновити")) }
                        IconButton(onClick = { vm.openReport() }, enabled = r != null) {
                            Icon(Icons.Filled.Share, contentDescription = tr("Звіт"))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = tr("Ще")) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(tr("🔋 Тест реальної ємності")) }, onClick = { menu = false; vm.openCapTest() }, enabled = r != null)
                                DropdownMenuItem(text = { Text(tr("⚖️ Перевірка балансу")) }, onClick = { menu = false; vm.openBalance() }, enabled = r != null)
                                DropdownMenuItem(text = { Text(tr("Сирі дані (усі параметри)")) }, onClick = { menu = false; vm.openRaw() })
                                DropdownMenuItem(text = { Text(tr("Журнал підключення")) }, onClick = { menu = false; vm.openLog() })
                                if (vm.codeHistory.isNotEmpty()) {
                                    DropdownMenuItem(text = { Text(tr("Очистити історію кодів")) }, onClick = { menu = false; confirmClear = true })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (r != null) {
                // Усі вкладки видно одразу: у прокручуваному рядку остання обрізалась і здавалась зламаною.
                TabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }) {
                            FitText(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp))
                        }
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
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Очистити історію кодів?")) },
            text = { DialogText(tr("Уся збережена історія появи/зникнення кодів цієї станції буде стерта безповоротно. Відновити її не вийде.")) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearCodeHistory() }) {
                    Text(tr("Стерти"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Скасувати")) } },
        )
    }
}

// ============================================================ підключення

@Composable
private fun ConnectingCard(vm: AppViewModel) {
    SectionCard(tr("Підключення")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.diagBusy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                vm.diagStatus.ifBlank { tr("Підготовка…") },
                style = MaterialTheme.typography.bodyLarge,
                color = if (vm.diagError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Кнопки є завжди, коли нічого не крутиться: людина не повинна лишатись на порожньому екрані.
        if (vm.diagError || !vm.diagBusy) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.retryDiag() }) { FitText(tr("Повторити")) }
                OutlinedButton(onClick = { vm.openLog() }) { FitText(tr("Журнал")) }
            }
        }
        if (vm.messageCount > 0) {
            Spacer(Modifier.height(8.dp))
            HintText(tr("Отримано повідомлень: {0}", vm.messageCount))
        }
    }
}

// ============================================================ Огляд

@Composable
private fun OverviewTab(vm: AppViewModel, r: StationReport, onEditCode: (Indicator) -> Unit) {
    val ctx = LocalContext.current
    if (vm.isDemo) {
        SectionCard(tr("🔬 Демо станція")) {
            Text(
                tr("Це несправжні дані, щоб роздивитись програму без реальної станції. Числа самі трохи «оживають»."),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            HintText(tr("Вкладки вгорі: «Огляд» — головне про батарею; «Монітор» — графік заряду/розряду наживо; «Коди» — помилки BMS, тисни «❓ Що це за помилка» для пояснення; «Керування» — вмик/вимк виходів. Унизу кнопка «🔋 Тест ємності»."))
        }
    }
    SectionCard(tr("Стан станції")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelChip(r.level, r.level.statusTitle)
            Spacer(Modifier.width(10.dp))
            ConnectionDot(vm.diagConnected, vm.diagOnline)
        }
        Spacer(Modifier.height(10.dp))
        Text(r.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (vm.diagOnline == false) {
            Spacer(Modifier.height(6.dp))
            HintText(tr("Хмара EcoFlow каже, що станція зараз офлайн, тому це останні збережені нею дані, а не поточні."))
        }
        if (r.packs.size > 1 && r.totalDesignWh != null && r.totalFullWh != null) {
            Spacer(Modifier.height(8.dp))
            KeyValue(tr("Разом ємність"), tr("≈{0} / {1} Вт·год", fmt0(r.totalFullWh), fmt0(r.totalDesignWh)))
        }
    }
    val single = r.packs.size == 1
    val lastBalance = vm.stationLog.firstOrNull { it.kind == StationLog.BALANCE }
    for (p in r.packs) {
        PackCard(p, single, onEditCode, onBalance = { vm.openBalance() }, lastBalance = lastBalance, cached = vm.lastCells[p.key])
    }
    if (r.packs.isEmpty()) {
        SectionCard(tr("Батарея")) {
            HintText(tr("Дані станції надходять, але параметрів батареї поки немає. Зачекайте 10–30 с або натисніть «Оновити»."))
        }
    }
    if (vm.stationLog.isNotEmpty()) {
        SectionCard(tr("Історія перевірок"), collapsible = true, initiallyExpanded = false) {
            val chem = r.packs.firstOrNull { it.isMain }?.chemistry ?: Chemistry.UNKNOWN
            StationLog.trend(vm.stationLog, chem)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
            for (e in vm.stationLog.take(LOG_PREVIEW)) HintText(StationLog.line(e))
            if (vm.stationLog.size > LOG_PREVIEW) HintText(tr("…та ще {0}. Більше — у технічному звіті.", vm.stationLog.size - LOG_PREVIEW))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { vm.openCapTest() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
            FitText(if (vm.capTestRunning) tr("🔋 Тест триває") else tr("🔋 Тест ємності"))
        }
        OutlinedButton(
            onClick = { vm.openReport() },
            modifier = Modifier.weight(1f),
        ) { FitText(tr("📄 Звіт")) }
    }
}

/** Картка батареї: коротко завжди + «Розширена інформація» розкриває деталі. */
@Composable
private fun PackCard(
    p: PackReport,
    single: Boolean,
    onEditCode: (Indicator) -> Unit,
    onBalance: () -> Unit,
    lastBalance: LogEntry?,
    cached: CellSnapshot?,
) {
    val title = if (single) tr("Батарея") else p.title
    var expanded by rememberSaveable(key = "exp_${p.key}") { mutableStateOf(true) }
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SohGauge(p.sohEffective, p.healthLevel)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                p.soc?.let {
                    Text("🔋 ${fmt0(it)}%", fontFamily = Mono, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (p.fullWh != null && p.designWh != null) {
                    Text(tr("≈{0} / {1} Вт·год", fmt0(p.fullWh), fmt0(p.designWh)), fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tr("Циклів: {0}", p.cycles?.toString() ?: "—"), fontFamily = Mono, style = MaterialTheme.typography.bodyMedium)
                Text(chemistryText(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Оцінка батареї — лише за SOH; зауваження (дисбаланс, коди, температура) — окремими рядками.
        Text(p.capacityVerdict, style = MaterialTheme.typography.titleSmall, color = levelColor(p.healthLevel), fontWeight = FontWeight.SemiBold)
        for (f in p.issues.take(3)) {
            Text(
                "⚠ ${(f.short ?: f.text).replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = levelColor(f.level),
                fontWeight = FontWeight.SemiBold,
            )
        }
        p.findings.firstOrNull { it.codeInfo && it.short != null && it.level.rank < Level.WARN.rank }?.let {
            HintText("ℹ ${it.short}")
        }

        // короткий поточний стан
        Spacer(Modifier.height(10.dp))
        p.ampMa?.let {
            val a = it / 1000.0
            KeyValue(tr("Струм зараз"), (if (a > 0) "+" else "") + tr("{0} А", fmt1(a)) + (p.volMv?.let { v -> tr(" · {0} В", fmt1(v / 1000.0)) } ?: ""))
        }
        if (p.maxCellTemp != null) KeyValue(tr("Температура"), "${p.minCellTemp ?: "?"}…${p.maxCellTemp} °C")

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) tr("▲ Згорнути") else tr("▼ Розширена інформація"))
        }
        if (expanded) PackDetails(p, onBalance, lastBalance, cached)
    }
}

/** Повні деталі батареї (ховаються під «Розширена інформація»). */
@Composable
private fun PackDetails(p: PackReport, onBalance: () -> Unit, lastBalance: LogEntry?, cached: CellSnapshot?) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    p.sn?.let { KeyValue(tr("SN пакета"), it) }
    p.sohBms?.let { KeyValue(tr("SOH за BMS"), "${fmt0(it)}%") }
    p.sohCapacity?.let { KeyValue(tr("SOH за вивченою ємністю"), "${fmt0(it)}%") }
    if (p.designCapMah != null) {
        KeyValue(tr("Ємність"), tr("{0} / {1} А·год", p.fullCapMah?.let { fmt1(it / 1000.0) } ?: "?", fmt1(p.designCapMah / 1000.0)))
    }
    if (p.designWh != null && p.packVnom != null && p.designCapMah != null) {
        NoteKeyValue(tr("Номінал за BMS"), tr("{0} Вт·год", fmt0(p.designWh)),
            tr("{0} А·год × {1} В ({2} груп)", fmt1(p.designCapMah / 1000.0), fmt1(p.packVnom), p.seriesCells ?: "?"))
    }
    p.passport?.wh?.let { KeyValue(tr("За паспортом"), tr("{0} Вт·год", it)) }
    if (p.cycles != null && p.ratedCycles != null && p.resourceUsedPct != null) {
        NoteKeyValue(tr("Використано ресурсу"), "≈${fmt0(p.resourceUsedPct)}%", tr("{0} з ~{1} циклів до 80%", p.cycles, p.ratedCycles))
    }
    p.remainCapMah?.let { KeyValue(tr("Залишок"), tr("{0} А·год", fmt1(it / 1000.0))) }
    if (p.maxMosTemp != null) KeyValue(tr("Температура MOSFET"), "${p.minMosTemp ?: "?"}…${p.maxMosTemp} °C")
    p.accuChgEnergyWh?.let { KeyValue(tr("Накопичено заряду (BMS)"), energyString(it.toDouble())) }
    p.accuDsgEnergyWh?.let { KeyValue(tr("Накопичено розряду (BMS)"), energyString(it.toDouble())) }

    if (p.minCellMv != null && p.maxCellMv != null) {
        Spacer(Modifier.height(12.dp))
        Text(tr("Напруги груп"), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        KeyValue(tr("Мін / макс"), tr("{0} / {1} В", fmt3(p.minCellMv / 1000.0), fmt3(p.maxCellMv / 1000.0)))
        val delta = p.cellDeltaMv ?: (p.maxCellMv - p.minCellMv)
        KeyValue(tr("Розкид"), tr("{0} мВ", delta), valueColor = levelColor(p.balanceLevel))
        p.weakestCell?.let { KeyValue(tr("Найнижча група"), "№$it") }
        if (p.cellVols.size >= 2) {
            Spacer(Modifier.height(8.dp))
            CellBars(p.cellVols, p.cellOkMv, p.cellWarnMv)
        } else if (cached != null) {
            // у цьому знімку списку немає — показуємо останній отриманий, чесно з часом
            Spacer(Modifier.height(8.dp))
            HintText(tr("Напруги по групах — останні отримані {0} (у свіжих даних станція їх ще не надіслала):", cached.timeText()))
            CellBars(cached.cells, p.cellOkMv, p.cellWarnMv)
        } else {
            HintText(tr("Напруг по кожній групі станція поки не надіслала — є лише мін/макс. Частина моделей шле їх окремими повідомленнями: зачекайте до хвилини на цьому екрані або натисніть «Оновити»."))
        }
        if (p.isMain) {
            // Остаточно баланс видно лише наприкінці заряду — показуємо останню таку перевірку й кнопку запуску.
            lastBalance?.let {
                Spacer(Modifier.height(6.dp))
                HintText(StationLog.line(it))
            }
            TextButton(onClick = onBalance) { FitText(tr("⚖️ Перевірити баланс наприкінці заряду")) }
        }
    }
    if (p.findings.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(tr("Висновки"), style = MaterialTheme.typography.labelLarge)
        for (f in p.findings) FindingRow(f.level, f.text)
    }
}

// ============================================================ Монітор

@Composable
private fun MonitorTab(vm: AppViewModel, r: StationReport) {
    val packs = r.packs.filter { it.volMv != null && it.ampMa != null }
    val w = packs.sumOf { it.volMv!! * it.ampMa!! / 1_000_000.0 }
    val main = r.packs.firstOrNull { it.isMain } ?: r.packs.firstOrNull()
    SectionCard(tr("Зараз")) {
        Text(
            when {
                w > 5 -> tr("⚡ Заряджання +{0} Вт", fmt0(w))
                w < -5 -> tr("🔌 Розряджання {0} Вт", fmt0(-w))
                else -> tr("⏸ Без струму")
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
            Mini(tr("Стан"), main?.soc?.let { "${fmt0(it)}%" } ?: "—")
            Mini(tr("Напруга"), main?.volMv?.let { tr("{0} В", fmt1(it / 1000.0)) } ?: "—")
            Mini(tr("Струм"), main?.ampMa?.let { tr("{0} А", fmt1(it / 1000.0)) } ?: "—")
        }
    }
    SectionCard(tr("Потужність батареї (останні хвилини)")) {
        PowerChart(vm.powerHistory)
        Spacer(Modifier.height(6.dp))
        HintText(tr("Вгору — заряд, вниз — розряд. Поки відкрито цю вкладку, дані оновлюються частіше (кожні ~8 с)."))
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
        HintText(tr("Збираю дані… графік з'явиться за кілька оновлень."))
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
        Text(tr("↑ +{0} Вт", fmt0(maxAbs)), style = MaterialTheme.typography.labelSmall, color = good)
        Text(tr("↓ -{0} Вт", fmt0(maxAbs)), style = MaterialTheme.typography.labelSmall, color = warn)
    }
}

// ============================================================ Коди

@Composable
private fun CodesTab(vm: AppViewModel, r: StationReport, onEditCode: (Indicator) -> Unit, onExplain: (String?) -> Unit) {
    for (p in r.packs) {
        if (p.mos != null || p.codes.isNotEmpty()) {
            SectionCard(tr("Коди BMS{0}", if (r.packs.size > 1) " · ${p.title}" else "")) {
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
                            Text(tr("❓ Що це за помилка"))
                        }
                    }
                }
            }
        }
        if (p.notProvided.isNotEmpty()) {
            SectionCard(tr("Не передається цією BMS{0}", if (r.packs.size > 1) " · ${p.title}" else ""), collapsible = true, initiallyExpanded = false) {
                for (n in p.notProvided) HintText("· $n")
                HintText(tr("Це заглушки прошивки, а не несправність."))
            }
        }
    }
    if (r.protections.isNotEmpty()) {
        SectionCard(tr("Захисти й зв'язок"), collapsible = true) {
            for (ind in r.protections) {
                IndicatorRow(ind.title, ind.value, ind.level, ind.note, onEditNote = ind.codeKey?.let { { onEditCode(ind) } })
            }
        }
    }
    SectionCard(tr("Історія кодів і захистів"), collapsible = true, initiallyExpanded = false) {
        val h = vm.codeHistory
        if (h.isEmpty()) {
            HintText(tr("Змін поки не зафіксовано. Поки відкрита діагностика, кожна поява/зникнення коду записується з часом і станом станції."))
        } else {
            for (e in h.take(HISTORY_PREVIEW)) CodeEventRow(e)
            if (h.size > HISTORY_PREVIEW) HintText(tr("…та ще {0}. Повністю — у технічному звіті.", h.size - HISTORY_PREVIEW))
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
                    Text(if (doc.confident) tr("Підтверджено на реальних станціях") else tr("Попередній висновок"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(doc.detail, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Зрозуміло")) } },
    )
}

// ============================================================ спільне

private const val HISTORY_PREVIEW = 12
private const val LOG_PREVIEW = 12

@Composable
private fun ConnectionDot(connected: Boolean, online: Boolean?) {
    val (color, text) = when {
        !connected -> levelColor(Level.UNKNOWN) to tr("не підключено")
        online == false -> levelColor(Level.BAD) to tr("станція офлайн")
        else -> levelColor(Level.GOOD) to tr("онлайн")
    }
    val live = connected && online != false
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(live) { while (live) { pulse += 0.12f; kotlinx.coroutines.delay(40) } }
    val a = if (live) (0.45f + 0.55f * kotlin.math.sin(pulse.toDouble()).toFloat()).coerceIn(0.2f, 1f) else 1f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = a)))
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
            Text("${CodeHistory.whenText(e)} · ${localize(e.scope)}", style = MaterialTheme.typography.labelMedium,
                color = if (e.betweenChecks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(localize(e.title), style = MaterialTheme.typography.bodyMedium)
            Text(CodeHistory.changeText(e), style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, color = levelColor(e.level))
            e.context?.let { HintText(localize(it)) }
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
        title = { Text(tr("Примітка до коду")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${ec.title}: ${ec.value}", style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                HintText(tr("Ключ «{0}». Примітка зберігається на телефоні й показується для цього коду на всіх станціях та у технічному звіті. Порожнє поле — видалити.", ec.key))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(tr("Що означає / що допомогло")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text(tr("Зберегти")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Скасувати")) } },
    )
}

private fun chemistryText(p: PackReport): String {
    if (p.chemistry == Chemistry.UNKNOWN) return tr("Хімія: не визначено") + (p.seriesCells?.let { " · ${it}S" } ?: "")
    val sure = if (p.chemistryConfident) "" else tr(" (ймовірно)")
    return "${p.chemistry.title}$sure" + (p.seriesCells?.let { " · ${it}S" } ?: "")
}
