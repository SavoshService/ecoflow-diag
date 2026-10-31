package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.savosh.ecoflowdiag.core.Analyzer.fmt0
import ua.savosh.ecoflowdiag.core.Analyzer.fmt1
import ua.savosh.ecoflowdiag.core.Analyzer.fmt3
import ua.savosh.ecoflowdiag.core.BalanceCheck
import ua.savosh.ecoflowdiag.core.Chemistry
import ua.savosh.ecoflowdiag.core.Level

/** Перевірка балансу груп наприкінці заряду: застосунок сам ловить розкид біля верху і робить висновок. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(vm: AppViewModel) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val tick = vm.balTick // перемальовувати при кожному новому знімку
    val b = vm.balanceCheck
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5000)
        }
    }

    // поки йде перевірка — екран не гасне, інакше телефон «засне» і зв'язок обірветься
    val view = LocalView.current
    val running = vm.balanceRunning
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
                        Text("Перевірка балансу", fontWeight = FontWeight.Bold)
                        Text(vm.diagSn ?: "", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.balanceResultText()?.let { shareText(context, it, "Перевірка балансу ${vm.diagSn}") } },
                        enabled = b != null && b.phase != BalanceCheck.Phase.IDLE,
                    ) { Icon(Icons.Filled.Share, contentDescription = "Поділитися результатом") }
                },
            )
        },
    ) { pad ->
        if (b == null) {
            Text("Спершу відкрийте діагностику станції.", modifier = Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (b.phase) {
                BalanceCheck.Phase.IDLE -> IntroCard(vm, b)
                BalanceCheck.Phase.RUNNING -> RunningCard(vm, b, now, onCancel = { confirmReset = true })
                BalanceCheck.Phase.DONE -> ResultCard(vm, b)
            }
            val warnings = b.warnings()
            if (warnings.isNotEmpty()) {
                SectionCard("Зверніть увагу") {
                    for (w in warnings) FindingRow(Level.WARN, w)
                }
            }
            if (b.phase == BalanceCheck.Phase.IDLE) {
                vm.lastBalanceCheck(b.sn)?.let { last ->
                    SectionCard("Попередня перевірка цієї станції", collapsible = true, initiallyExpanded = false) {
                        Text(last, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Скасувати перевірку?") },
            text = { Text("Зафіксований розкид буде скинуто. Почати можна знову будь-коли.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.balanceReset() }) { Text("Скасувати перевірку") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Продовжити") } },
        )
    }
}

@Composable
private fun IntroCard(vm: AppViewModel, b: BalanceCheck) {
    SectionCard("Як це працює") {
        Text(
            "У LFP розбаланс груп видно лише наприкінці заряду: на плато (≈3.3 В) навіть розбалансовані групи мають " +
                "майже однакову напругу. Застосунок сам зафіксує найбільший розкид, коли групи вийдуть з плато " +
                "(вище 3.45 В; для NCM — від 90% заряду), і лише за ним зробить висновок.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "1. Поставте станцію на заряд від мережі, ліміт заряду — 100%.\n" +
                "2. Натисніть «Почати» і не закривайте застосунок до кінця заряду — екран не гасне.\n" +
                "3. Найшвидше почати з 80–90%: перевірка триває, поки станція не дозарядиться.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        LiveValues(b)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.balanceStart() }, modifier = Modifier.fillMaxWidth()) { Text("Почати перевірку") }
    }
}

@Composable
private fun RunningCard(vm: AppViewModel, b: BalanceCheck, now: Long, onCancel: () -> Unit) {
    SectionCard("Перевірка триває") {
        LiveValues(b)
        Spacer(Modifier.height(8.dp))
        val worst = b.worst()
        val status = when {
            worst != null -> "Зафіксовано розкид біля верху заряду: ${worst.deltaMv} мВ. Оновиться, якщо зросте."
            b.inZoneNow() -> "Батарея в зоні висновку — розкид фіксується."
            b.chemistry == Chemistry.LFP ->
                "Групи ще на плато" + (b.maxCellMv?.let { " (найвища ${fmt3(it / 1000.0)} В)" } ?: "") + " — чекаємо виходу вище 3.45 В."
            else -> "Чекаємо, поки заряд дійде до 90%."
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val stale = b.lastDataMs > 0 && now - b.lastDataMs > 180_000
        if (b.autoDone) {
            Spacer(Modifier.height(6.dp))
            BalanceHint("Станція дозарядилась. Натисніть «Завершити».")
        } else if (stale) {
            Spacer(Modifier.height(6.dp))
            BalanceHint("Дані не надходять кілька хвилин — перевірте зв'язок станції.")
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.balanceFinish() }, modifier = Modifier.fillMaxWidth()) { Text("Завершити") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Скасувати") }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, b: BalanceCheck) {
    SectionCard("Результат") {
        val res = b.results
        if (res.isEmpty()) {
            FindingRow(Level.UNKNOWN, b.noZoneText())
        } else {
            for (t in res) {
                val v = BalanceCheck.verdict(t)
                if (res.size > 1) {
                    Text(t.title, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                }
                LevelChip(v.level, v.title)
                Spacer(Modifier.height(6.dp))
                Text(v.detail, style = MaterialTheme.typography.bodyMedium)
                BalanceHint(
                    "Зафіксовано при заряді ${t.soc?.let { fmt0(it) + "%" } ?: "?"}" +
                        (t.maxCellMv?.let { ", найвища група ${fmt3(it / 1000.0)} В" } ?: "") + ".",
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        OutlinedButton(onClick = { vm.balanceReset() }, modifier = Modifier.fillMaxWidth()) { Text("Нова перевірка") }
    }
}

@Composable
private fun LiveValues(b: BalanceCheck) {
    KeyValue("Заряд", b.soc?.let { "${fmt0(it)}%" } ?: "—")
    KeyValue("Струм батареї", b.ampA?.let { (if (it > 0) "+" else "") + "${fmt1(it)} А" } ?: "—")
    KeyValue("Найвища група", b.maxCellMv?.let { "${fmt3(it / 1000.0)} В" } ?: "—")
    KeyValue("Розкид зараз", b.deltaMv?.let { "$it мВ" } ?: "—")
}

@Composable
private fun BalanceHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
