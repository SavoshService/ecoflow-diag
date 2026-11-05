package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Резервна копія — лише через Google-акаунт (прихована тека застосунку на Google Диску). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) } },
                title = { FitText(tr("💾 Резервна копія"), fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Навіщо це"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Коли видаляєш застосунок, Android стирає всі його дані. Увійди через Google — і станції, замовлення, склад, замітки й талмуд зберігатимуться у твоєму Google Диску самі. Пароль EcoFlow у копію НЕ потрапляє."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("☁️ Google-акаунт (автоматично)"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (vm.gdConnected) {
                        Text(
                            tr("✓ Підключено: {0}", vm.gdEmail.ifBlank { tr("Google акаунт") }),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Дані самі зберігаються в хмарі після кожної зміни. Перевстановив застосунок — увійди цим самим акаунтом, і все підтягнеться автоматично."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.syncGoogleNow() }, modifier = Modifier.weight(1f)) { FitText(tr("Синхронізувати зараз")) }
                            TextButton(onClick = { vm.disconnectGoogle() }) { FitText(tr("Відключити")) }
                        }
                    } else if (vm.gdConfigured) {
                        Text(
                            tr("Увійди один раз своїм Google-акаунтом — і копія оновлюватиметься сама. Нічого вибирати вручну не треба."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.connectGoogle() }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Увійти через Google"))
                        }
                    } else {
                        Text(
                            tr("Синхронізацію Google не налаштовано в цій збірці."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    vm.gdStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

/** Вікно при чистій інсталяції: увійти через Google — і дані підтягнуться з хмари. */
@Composable
fun RestoreDialog(vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissRestore() },
        title = { FitText(tr("Відновити дані?"), fontWeight = FontWeight.Bold) },
        text = {
            DialogText(
                tr("Якщо раніше ти входив через Google — увійди тим самим акаунтом, і я відновлю станції, замовлення, склад, замітки й талмуд. Якщо копії немає — просто почни з чистого."),
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.dismissRestore(); vm.connectGoogle() }) { Text(tr("Увійти через Google")) }
        },
        dismissButton = { TextButton(onClick = { vm.dismissRestore() }) { Text(tr("Почати заново")) } },
    )
}
