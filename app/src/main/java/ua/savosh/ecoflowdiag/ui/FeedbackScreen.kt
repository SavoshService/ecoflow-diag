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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.net.APP_VERSION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("Зворотний зв'язок", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Питання, ідеї, баги") {
                Text(
                    "Є питання по станції, не спрацювала команда чи код не пояснився? Напишіть у Телеграм-канал Savosh Service — " +
                        "там можна поставити питання, надіслати модель станції й сирі дані, запропонувати нову функцію.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text("✈ Відкрити Телеграм-канал")
                }
                Spacer(Modifier.height(4.dp))
                Text(TELEGRAM_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (donateEnabled()) {
                SectionCard("Підтримати автора") {
                    Text("Програма безкоштовна. Якщо стала в пригоді — можна кинути на каву.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    SteamingCoffee(sizeDp = 110)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { openDonate(ctx) }, modifier = Modifier.fillMaxWidth()) { Text(DONATE_TEXT) }
                }
            }
            SectionCard("Про програму") {
                Text("Savosh Service — діагностика EcoFlow", fontWeight = FontWeight.SemiBold)
                Text("Версія $APP_VERSION", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Незалежний застосунок, не пов'язаний з EcoFlow. Вхід — тим самим акаунтом, що й офіційний застосунок.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
