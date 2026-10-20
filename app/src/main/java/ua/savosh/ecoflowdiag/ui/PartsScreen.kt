package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("Ідентифікація та аналоги деталей", fontWeight = FontWeight.Bold, maxLines = 1) },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoonBadge()

            SectionCard("Що тут буде") {
                Text(
                    "Довідник по мікросхемах і деталях плат станцій: знаходиш маркування на платі — бачиш, що це за деталь, " +
                        "її повну назву, призначення й на що її можна замінити (pin-to-pin аналог).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("Для кожної деталі планується:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Bullet("Фото деталі на платі та її маркування")
                Bullet("Модель/плата, де стоїть (напр. Delta 2 — ШІМ-контролер)")
                Bullet("Маркування на корпусі (напр. «1G6P»)")
                Bullet("Повна назва й основні значення/параметри")
                Bullet("Аналог pin-to-pin (чим замінити без переробки плати)")
            }

            SectionCard("Приклад запису") {
                Text("Delta 2 · ШІМ-контролер", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Маркування: 1G6P", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Повна назва: (додасться)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Аналог pin-to-pin: (додасться)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard("Поповнення довідника") {
                Text(
                    "Маєш розшифровку деталі або знайдений робочий аналог? Надішли фото маркування й плату в Телеграм — " +
                        "додам у довідник, і воно прийде всім з наступним оновленням.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openTelegram(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text("✈ Надіслати деталь / аналог")
                }
            }
        }
    }
}

@Composable
internal fun SoonBadge() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Text("🛠", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("СКОРО", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Розділ у розробці — наповнюється. Готове прийде з оновленням програми.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
