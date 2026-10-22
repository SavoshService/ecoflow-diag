package ua.savosh.ecoflowdiag.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val BACKUP_FILE_NAME = "Savosh_Service_backup.json"
private val OPEN_TYPES = arrayOf("application/json", "text/*", "*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel) {
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportToUri(it) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFromUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("💾 Резервна копія", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Навіщо це", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Коли видаляєш застосунок, Android стирає всі його дані. Збережи копію у файл — і замовлення, " +
                            "склад, замітки й талмуд не загубляться. Обери місце «Google Диск» у вікні — і копія буде в хмарі. " +
                            "Пароль EcoFlow у копію НЕ потрапляє.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(onClick = { createLauncher.launch(BACKUP_FILE_NAME) }, modifier = Modifier.fillMaxWidth()) {
                Text("💾 Зберегти копію (у файл / на Google Диск)")
            }
            OutlinedButton(onClick = { openLauncher.launch(OPEN_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Text("📂 Відновити з файлу")
            }

            if (vm.hasBackupTarget) {
                Text(
                    "✓ Авто-копія увімкнена: файл оновлюється сам щоразу, коли згортаєш застосунок.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    "Після першого збереження копія оновлюватиметься автоматично при виході із застосунку.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            vm.backupStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                "Порада: зберігай копію на Google Диск — тоді навіть при заміні телефона дані легко відновити " +
                    "через «Відновити з файлу».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Вікно при чистій інсталяції: запропонувати знайти файл копії й відновити дані. */
@Composable
fun RestoreDialog(vm: AppViewModel) {
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFromUri(uri) else vm.dismissRestore()
    }
    AlertDialog(
        onDismissRequest = { vm.dismissRestore() },
        title = { Text("Відновити дані?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Якщо раніше ти зберігав копію даних (у файл або на Google Диск) — знайди її, і я відновлю " +
                    "замовлення, склад, замітки й талмуд. Якщо копії немає — просто почни з чистого.",
            )
        },
        confirmButton = { TextButton(onClick = { openLauncher.launch(OPEN_TYPES) }) { Text("Знайти файл копії") } },
        dismissButton = { TextButton(onClick = { vm.dismissRestore() }) { Text("Почати заново") } },
    )
}
