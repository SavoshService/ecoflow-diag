package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import ua.savosh.ecoflowdiag.core.CapPhase
import ua.savosh.ecoflowdiag.core.CapStage
import ua.savosh.ecoflowdiag.core.CapacityTest
import ua.savosh.ecoflowdiag.core.Health
import ua.savosh.ecoflowdiag.core.Level

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapTestScreen(vm: AppViewModel) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = vm.capTick // перемальовувати при кожному новому знімку
    val t = vm.capTest
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5000)
        }
    }

    // поки йде тест — екран не гасне, інакше телефон «засне» і зв'язок обірветься
    val view = LocalView.current
    val running = vm.capTestRunning
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = {
                    Column {
                        Text("Тест реальної ємності", fontWeight = FontWeight.Bold)
                        Text(vm.diagSn ?: "", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.capResultText()?.let { shareText(context, it, "Тест ємності ${vm.diagSn}") } },
                        enabled = t != null && t.phase != CapPhase.IDLE,
                    ) { Icon(Icons.Filled.Share, contentDescription = "Поділитися результатом") }
                },
            )
        },
    ) { pad ->
        if (t == null) {
            Text("Спершу відкрийте діагностику станції.", modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LiveCard(vm, t, now) }
            item { StageCard(vm, t, now, onCancel = { confirmReset = true }) }
            val warnings = t.warnings()
            if (warnings.isNotEmpty()) {
                item {
                    SectionCard("Зверніть увагу") {
                        for (w in warnings) FindingRow(Level.WARN, w)
                    }
                }
            }
            if (t.phase == CapPhase.IDLE) {
                vm.lastCapTest(t.sn)?.let { last ->
                    item {
                        SectionCard("Попередній тест цієї станції", collapsible = true, initiallyExpanded = false) {
                            Text(last, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
                        }
                    }
                }
            }
            item {
                HintLine(
                    "Енергія рахується за напругою й струмом BMS кожної батареї (разом із додатковими), точність близько ±3–5%. " +
                        "Поки йде тест, екран не гасне — тримайте телефон на зарядці й не закривайте програму.",
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Скасувати тест?") },
            text = { Text("Набрані дані тесту буде втрачено.") },
            confirmButton = { TextButton(onClick = { vm.capReset(); confirmReset = false }) { Text("Скасувати тест") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Продовжити") } },
        )
    }
}

/** Живі показники батареї. */
@Composable
private fun LiveCard(vm: AppViewModel, t: CapacityTest, now: Long) {
    SectionCard("Зараз") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.soc?.let { "${fmt0(it)}%" } ?: "—",
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            )
            Column(Modifier.padding(start = 16.dp)) {
                val w = t.batteryW
                Text(
                    when {
                        w == null -> "немає даних"
                        w > 5 -> "заряд +${fmt0(w)} Вт"
                        w < -5 -> "розряд ${fmt0(-w)} Вт"
                        else -> "без струму"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        w == null -> levelColor(Level.UNKNOWN)
                        w > 5 -> levelColor(Level.GOOD)
                        w < -5 -> levelColor(Level.WARN)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    "${t.batteryV?.let { fmt1(it) + " В" } ?: "—"} · ${t.batteryA?.let { fmt1(it) + " А" } ?: "—"}",
                    fontFamily = Mono,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val age = if (t.lastDataMs > 0) (now - t.lastDataMs) / 1000 else -1
        Text(
            when {
                !vm.diagConnected -> "Немає зв'язку з хмарою — перепідключення…"
                age < 0 -> "Очікування даних від станції…"
                age < 90 -> "Дані оновлено $age с тому"
                else -> "Дані не надходять ${age / 60} хв"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (age >= 90 || !vm.diagConnected) levelColor(Level.WARN) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Інструкція й кнопки поточного етапу. */
@Composable
private fun StageCard(vm: AppViewModel, t: CapacityTest, now: Long, onCancel: () -> Unit) {
    when (t.phase) {
        CapPhase.IDLE -> SectionCard("Підготовка") {
            Step(1, "У застосунку EcoFlow виставте ліміт заряду 100% і нижній ліміт розряду 0% — інакше станція зупиниться раніше, і тест покаже меншу ємність.")
            Step(2, "Розрядіть станцію до 0% (до автовимкнення), потім увімкніть її знову, щоб вона була онлайн.")
            Step(3, "Підключіть станцію до мережі 220 В на заряд і натисніть «Старт етапу 1».")
            Step(4, "Тримайте телефон на зарядці з відкритою програмою: тест триває кілька годин.")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.capStartCharge() }, modifier = Modifier.fillMaxWidth()) { Text("Старт етапу 1 — заряд") }
        }

        CapPhase.CHARGE -> SectionCard("Етап 1 — заряд") {
            Text("Станція заряджається. Тест сам помітить, коли заряд дійде до 100% і струм упаде.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            StageProgress(t.charge, t, now, inbound = true)
            if (t.autoDone) DoneHint("Схоже, станція зарядилась повністю. Натисніть «Завершити етап 1».")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.capFinishCharge() }, modifier = Modifier.fillMaxWidth()) { Text("Завершити етап 1") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Скасувати тест") }
        }

        CapPhase.CHARGE_DONE -> SectionCard("Етап 1 завершено") {
            BigResult("У батарею зайшло", "≈${fmt0(t.charge.wh)} Вт·год", "${fmt1(t.charge.ah)} А·год за ${CapacityTest.durationText(t.charge.durationMs)}")
            Spacer(Modifier.height(10.dp))
            Text("Етап 2 — фінальна перевірка розрядом", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Step(1, "Відключіть станцію від заряду.")
            Step(2, "Підключіть навантаження (лампи, обігрівач тощо) приблизно на 300–1000 Вт — не більше номіналу інвертора. Не лишайте обігрівач без нагляду.")
            Step(3, "Натисніть «Старт етапу 2». Тест закінчиться, коли станція розрядиться до 0%.")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.capStartDischarge() }, modifier = Modifier.fillMaxWidth()) { Text("Старт етапу 2 — розряд") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Скасувати тест") }
        }

        CapPhase.DISCHARGE -> SectionCard("Етап 2 — розряд") {
            Text("Станція віддає енергію в навантаження. Тест закінчиться на 0%.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            StageProgress(t.discharge, t, now, inbound = false)
            val stale = t.lastDataMs > 0 && now - t.lastDataMs > 180_000
            if (t.autoDone) DoneHint("Станція розрядилась до 0%. Натисніть «Завершити етап 2».")
            else if (stale) DoneHint("Дані не надходять кілька хвилин. Якщо станція вимкнулась на 0% — натисніть «Завершити етап 2».")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.capFinishDischarge() }, modifier = Modifier.fillMaxWidth()) { Text("Завершити етап 2") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Скасувати тест") }
        }

        CapPhase.DONE -> SectionCard("Результат") {
            val m = t.measuredWh
            if (m == null) {
                Text("Розряд не набрав енергії — результату немає. Перевірте, що станція справді розряджалась.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val soh = t.sohMeasured
                // Та сама шкала, що й в оцінці батареї та звітах (Health).
                val grade = Health.of(soh)
                val lv = grade?.level ?: Level.UNKNOWN
                val base = t.designAh?.let { "${fmt1(it)} А·год" } ?: t.designWh?.let { "${fmt0(it)} Вт·год" }
                BigResult(
                    "Реальна ємність",
                    "≈${fmt0(m)} Вт·год",
                    "${fmt1(t.discharge.ah)} А·год" + (if (soh != null && base != null) " — ${fmt0(soh)}% від номіналу BMS ($base)" else ""),
                )
                Spacer(Modifier.height(6.dp))
                LevelChip(lv, if (grade != null && soh != null) "${grade.chip} · ${fmt0(soh)}%" else lv.statusTitle)
                Spacer(Modifier.height(10.dp))
                KeyValue("Заряд (етап 1)", "≈${fmt0(t.charge.wh)} Вт·год")
                KeyValue("Розряд (етап 2)", "≈${fmt0(t.discharge.wh)} Вт·год")
                t.passportWh?.let { KeyValue("За паспортом", "${fmt0(it)} Вт·год") }
                t.extrapolatedWh?.let { KeyValue("Оцінка на повні 100%", "≈${fmt0(it)} Вт·год") }
                t.roundTrip?.let { KeyValue("Ефективність циклу", "${fmt0(it)}%") }
                KeyValue("Тривалість розряду", CapacityTest.durationText(t.discharge.durationMs))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.capReset() }, modifier = Modifier.fillMaxWidth()) { Text("Новий тест") }
        }
    }
}

@Composable
private fun StageProgress(st: CapStage, t: CapacityTest, now: Long, inbound: Boolean) {
    KeyValue(if (inbound) "Зайшло в батарею" else "Віддано батареєю", "≈${fmt0(st.wh)} Вт·год")
    KeyValue("Заряд", "${fmt1(st.ah)} А·год")
    KeyValue("SOC", "${st.socStart?.let { fmt0(it) } ?: "?"}% → ${t.soc?.let { fmt0(it) } ?: "?"}%")
    KeyValue("Минуло", CapacityTest.durationText(if (st.startMs > 0) now - st.startMs else 0))
    if (st.stationWh > 0) KeyValue(if (inbound) "З мережі (з втратами)" else "На виходах (з втратами)", "≈${fmt0(st.stationWh)} Вт·год")
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BigResult(label: String, value: String, sub: String?) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
    sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun DoneHint(text: String) {
    Spacer(Modifier.height(8.dp))
    FindingRow(Level.GOOD, text)
}

@Composable
private fun HintLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
