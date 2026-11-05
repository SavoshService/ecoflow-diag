package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.net.APP_VERSION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var showCoffee by remember { mutableStateOf(false) }
    if (showCoffee) {
        CoffeeDonateDialog(onOpen = { showCoffee = false; openDonate(ctx) }, onDismiss = { showCoffee = false })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) } },
                title = { FitText(tr("Зворотний зв'язок"), fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(tr("Питання, ідеї, баги")) {
                Text(
                    tr("Є питання по станції, не спрацювала команда чи код не пояснився? Напишіть у Телеграм-канал Savosh Service — там можна поставити питання, надіслати модель станції й сирі дані, запропонувати нову функцію."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("✈ Відкрити Телеграм-канал"))
                }
                Spacer(Modifier.height(4.dp))
                Text(TELEGRAM_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (donateEnabled()) {
                SectionCard(tr("Підтримати автора")) {
                    Text(tr("Програма безкоштовна. Якщо стала в пригоді — можна кинути на каву."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SteamingCoffee(sizeDp = 110) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showCoffee = true }, modifier = Modifier.fillMaxWidth()) { Text(DONATE_TEXT) }
                }
            }
            SectionCard(tr("Про програму")) {
                Text(tr("Power Station Diag — діагностика портативних зарядних станцій"), fontWeight = FontWeight.SemiBold)
                Text(tr("Версія {0}", APP_VERSION), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Незалежний застосунок, не пов'язаний з EcoFlow. Вхід — тим самим акаунтом, що й офіційний застосунок."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
