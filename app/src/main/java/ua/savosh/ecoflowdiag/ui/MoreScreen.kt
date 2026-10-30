package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.net.APP_VERSION

/** Розділ «Ще»: усе, що не потрапило на нижню панель, — записник, прошивка, копія, спільнота. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var showCoffee by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ще", fontWeight = FontWeight.Bold) }) },
        bottomBar = { MainNavBar(vm) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Записник майстра")
            HubCard("📖", "Талмуд майстрів", "Статті з фото, маркуванням і аналогами — бачить уся спільнота.", Color(0xFF7C3AED)) {
                vm.openKnowledge()
            }
            HubCard("📝", "Замітки майстра", "Нотатки, чеклисти й нагадування до станцій. Лише для тебе.", Color(0xFF2563EB)) {
                vm.openNotes()
            }

            SectionLabel("Майстерня")
            val trashed = vm.trashedOrders().size
            HubCard(
                "🗑", "Корзина",
                if (trashed == 0) "Видалені замовлення з'являтимуться тут — їх можна відновити." else "Видалених замовлень: $trashed. Можна відновити.",
                Color(0xFF64748B),
            ) { vm.openCrmTab(3) }

            SectionLabel("Інструменти")
            HubCard("🔧", "Прошивка станцій", "Скоро: прошивка станцій з телефону через ESP32 + ST-Link V2.", Color(0xFF0EA5E9)) {
                vm.openFlash()
            }

            SectionLabel("Дані")
            HubCard(
                "💾", "Резервна копія",
                if (vm.gdConnected) "✓ Google: ${vm.gdEmail.ifBlank { "підключено" }}. Зберігається сама." else "Увійди через Google — і дані не загубляться при перевстановленні.",
                Color(0xFFEA580C),
            ) { vm.openBackup() }

            SectionLabel("Спільнота")
            HubCard("📣", "Канал спільноти", "Новини, обговорення й спільна база знань майстрів.", Color(0xFF16A34A)) {
                openUrl(ctx, COMMUNITY_URL)
            }
            HubCard("✈️", "Зворотний зв'язок", "Питання, ідеї, баги — у Телеграм-канал Savosh Service.", Color(0xFF0284C7)) {
                vm.openFeedback()
            }
            if (donateEnabled()) {
                HubCard("☕", "Підтримати автора", "Програма безкоштовна. Якщо стала в пригоді — можна кинути на каву.", Color(0xFFB45309)) {
                    showCoffee = true
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Power Station Diag · версія $APP_VERSION",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showCoffee) {
        CoffeeDonateDialog(onOpen = { showCoffee = false; openDonate(ctx) }, onDismiss = { showCoffee = false })
    }
}

/** Підпис групи пунктів — у тому ж стилі, що й заголовки карток (SectionCard). */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
    )
}

/** Плитка-розділ: кольорова іконка, назва, опис і стрілка. */
@Composable
internal fun HubCard(emoji: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
