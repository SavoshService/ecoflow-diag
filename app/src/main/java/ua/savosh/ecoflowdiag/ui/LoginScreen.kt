package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.net.EcoflowApi

@Composable
fun LoginScreen(vm: AppViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                "SAVOSH SERVICE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "EcoFlow Діагностика",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Здоров'я батарей, цикли, напруги груп і напрацювання станцій EcoFlow усіх поколінь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text("Email акаунта EcoFlow") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Сховати" else "Показати")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SecurityNote(onMore = { showSecurity = true })
            Spacer(Modifier.height(16.dp))
            Text("Сервер", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((h, title) in EcoflowApi.SERVERS) {
                    FilterChip(
                        selected = vm.host == h,
                        onClick = { vm.host = h },
                        label = { Text(title) },
                    )
                }
            }
            Text(
                "Якщо вхід не вдається — спробуйте інший регіон. Для України зазвичай підходить «Авто» або «Європа».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.remember, onCheckedChange = { vm.remember = it })
                Text("Запам'ятати пароль (зашифровано, лише на цьому телефоні)")
            }

            vm.loginError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.login() },
                enabled = !vm.loginBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (vm.loginBusy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Увійти")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.continueWithoutLogin() }, modifier = Modifier.fillMaxWidth()) {
                Text("Продовжити без входу — майстерня, деталі, замітки")
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Вхід такий самий, як у застосунку EcoFlow. Ключі розробника не потрібні. " +
                    "Станція має бути додана в цей акаунт і бути онлайн через Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showSecurity) SecurityDialog(onDismiss = { showSecurity = false })
}

/** Коротко про безпеку входу — одразу під полями логіна й пароля. */
@Composable
private fun SecurityNote(onMore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "🔒 Пароль бачить лише EcoFlow",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Логін і пароль надсилаються тільки на сервер EcoFlow захищеним з'єднанням — так само, як в офіційному " +
                    "застосунку. У програми немає власного сервера, реклами чи аналітики, тож більше ніхто їх не отримує.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onMore) { Text("Детальніше про безпеку") }
        }
    }
}

@Composable
private fun SecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Безпека входу") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((head, body) in SECURITY_POINTS) {
                    Text(head, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Зрозуміло") } },
    )
}

private val SECURITY_POINTS: List<Pair<String, String>> = listOf(
    "Куди йде пароль" to
        "Лише на сервер входу EcoFlow (api.ecoflow.com або регіональний api-e / api-a.ecoflow.com) через HTTPS " +
        "з перевіркою сертифіката сервера. Якщо хтось у Wi-Fi спробує підмінити сервер, програма відхилить з'єднання.",
    "З чим ще з'єднується програма" to
        "Тільки з сервером даних EcoFlow (MQTT, шифроване TLS) — тим самим, яким користується офіційний застосунок. " +
        "Власного сервера, реклами, аналітики чи сторонніх бібліотек відстеження в програмі немає.",
    "Що програма робить в акаунті" to
        "Лише читає дані станцій: заряд, цикли, напруги, коди BMS. Команд керування станцією вона не надсилає " +
        "і налаштувань акаунта не змінює.",
    "Що зберігається на телефоні" to
        "Email, вибраний сервер, список станцій і звіти. Пароль — тільки якщо увімкнено «Запам'ятати пароль», " +
        "і тоді він зашифрований ключем Android Keystore, який не можна витягти з телефона. Без галочки пароль " +
        "не зберігається взагалі. «Вийти» стирає збережений пароль. Резервне копіювання програми в хмару вимкнене.",
    "Журнал підключення" to
        "Пароля й токенів у журналі немає, але є email або ім'я акаунта та userId — пам'ятайте про це, коли ним ділитесь.",
    "Порада" to
        "Використовуйте для EcoFlow пароль, який не збігається з паролями від пошти чи банку. Це правило для будь-якого " +
        "застосунку, не лише для цього.",
    "Про програму" to
        "Savosh Service. Програма незалежна, не пов'язана з EcoFlow; вхід працює так само, як у застосунку EcoFlow.",
)
