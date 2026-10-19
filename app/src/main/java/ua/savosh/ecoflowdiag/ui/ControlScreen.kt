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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.core.DeviceCommand
import ua.savosh.ecoflowdiag.core.EcoflowCommands
import ua.savosh.ecoflowdiag.core.Generation
import ua.savosh.ecoflowdiag.core.Level

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = {
                    Column {
                        Text("Керування станцією", fontWeight = FontWeight.Bold)
                        Text(vm.diagSn ?: "", fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { ControlContent(vm) }
    }
}

/** Вміст керування станцією — використовується і як окремий екран, і як вкладка в діагностиці. */
@Composable
fun ControlContent(vm: AppViewModel) {
    val gen = vm.report?.generation ?: Generation.UNKNOWN
    var confirm by remember { mutableStateOf<DeviceCommand?>(null) }

    SectionCard("Увага") {
        Text(
            "Команди виконуються у вашому акаунті EcoFlow — тих самих станціях, що й в офіційному застосунку. " +
                "Це бета: формати команд залежать від моделі. Застосунок показує, що надіслав і що станція відповіла. " +
                "Якщо команда не спрацювала — напишіть у Телеграм модель станції, і формат підправлять.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!EcoflowCommands.supported(gen)) {
        SectionCard("Поки недоступно") {
            Text(EcoflowCommands.unsupportedNote(gen), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Покоління визначається після підключення. Якщо тут «модель не визначена» — дочекайтесь даних на вкладці «Огляд».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SectionCard("Вихід 220 В") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerButton("Увімкнути", true, Modifier.weight(1f)) { confirm = EcoflowCommands.acOut(true) }
                PowerButton("Вимкнути", false, Modifier.weight(1f)) { confirm = EcoflowCommands.acOut(false) }
            }
        }
        SectionCard("DC-вихід 12 В") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerButton("Увімкнути", true, Modifier.weight(1f)) { confirm = EcoflowCommands.dcOut(true) }
                PowerButton("Вимкнути", false, Modifier.weight(1f)) { confirm = EcoflowCommands.dcOut(false) }
            }
        }
        SectionCard("Ліміти (зручно перед тестом ємності)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { confirm = EcoflowCommands.maxChargeSoc(100) }, modifier = Modifier.weight(1f)) { Text("Заряд 100%") }
                OutlinedButton(onClick = { confirm = EcoflowCommands.maxChargeSoc(80) }, modifier = Modifier.weight(1f)) { Text("Заряд 80%") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { confirm = EcoflowCommands.minDischargeSoc(0) }, modifier = Modifier.weight(1f)) { Text("Розряд до 0%") }
                OutlinedButton(onClick = { confirm = EcoflowCommands.minDischargeSoc(20) }, modifier = Modifier.weight(1f)) { Text("Розряд до 20%") }
            }
        }
    }

    if (vm.controlSent != null || vm.controlBusy) {
        SectionCard("Остання команда") {
            vm.controlSent?.let {
                Text(it, fontFamily = Mono, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            when {
                vm.controlBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Чекаю відповіді станції…", style = MaterialTheme.typography.bodyMedium)
                }
                vm.controlReply != null -> {
                    val ok = vm.controlReply!!.contains("\"code\":0") || vm.controlReply!!.contains("success", ignoreCase = true)
                    FindingRow(if (ok) Level.GOOD else Level.WARN, if (ok) "Станція прийняла команду." else "Відповідь станції:")
                    Text(vm.controlReply!!, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirm?.let { cmd ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(cmd.title) },
            text = { Text(cmd.confirm) },
            confirmButton = { TextButton(onClick = { vm.sendCommand(cmd); confirm = null }) { Text("Виконати") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun PowerButton(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) levelColor(Level.GOOD) else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { Text(text) }
}
