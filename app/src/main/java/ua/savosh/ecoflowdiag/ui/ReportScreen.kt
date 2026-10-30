package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val kind = vm.reportExtended

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (kind != null) vm.backFromReportEditor() else vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Звіт", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        if (kind == null) {
            // ---- Вибір виду + збережені звіти ----
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Оберіть вид звіту", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportTile(
                        "🟢 Короткий",
                        "Зрозумілий клієнту: головні показники й стан",
                        Modifier.weight(1f),
                    ) { vm.chooseReport(false) }
                    ReportTile(
                        "🔵 Розширений",
                        "Повна технічна картина станції",
                        Modifier.weight(1f),
                    ) { vm.chooseReport(true) }
                }
                Text(
                    "Після вибору звіт відкриється для редагування — виправиш під себе й збережеш або поділишся.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (vm.savedReportDocs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Збережені звіти", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    for (doc in vm.savedReportDocs) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f).padding(vertical = 6.dp),
                                ) {
                                    Text(
                                        "${if (doc.extended) "🔵 Розширений" else "🟢 Короткий"} · ${doc.model ?: doc.sn}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(doc.dateMs)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.openSavedReport(doc) }) { Text("Відкрити") }
                                TextButton(onClick = { vm.deleteSavedReport(doc.id) }) { Text("Видалити") }
                            }
                        }
                    }
                }
            }
        } else {
            // ---- Редактор звіту (як блокнот) ----
            Column(
                Modifier.fillMaxSize().padding(pad).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Відредагуй звіт під себе (впиши назву сервісу, висновок тощо), тоді збережи або поділись.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = vm.reportDraft,
                    onValueChange = { vm.editReportDraft(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                vm.reportSavedMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.saveReportDraft() }, modifier = Modifier.weight(1f)) {
                        Text("💾 Зберегти")
                    }
                    OutlinedButton(
                        onClick = { shareText(ctx, vm.reportDraft, "Звіт про стан станції") },
                        modifier = Modifier.weight(1f),
                    ) { Text("📤 Поділитись") }
                }
            }
        }
    }
}

@Composable
private fun ReportTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Обрати") }
        }
    }
}
