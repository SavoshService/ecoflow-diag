package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                .formPadding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageButton(vm)
                Spacer(Modifier.width(4.dp))
                Text(
                    "SAVOSH SERVICE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Power Station Diag",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Здоров'я батарей, цикли, напруги груп і напрацювання станцій EcoFlow усіх поколінь."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            if (vm.foreignBuild) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        tr("⚠️ Цю копію застосунку перезібрав хтось інший: вона підписана не ключем Savosh. Не вводьте пароль. Завантажте застосунок з офіційної сторінки Savosh на GitHub."),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text(tr("Email акаунта EcoFlow")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text(tr("Пароль")) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) tr("Сховати") else tr("Показати"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SecurityNote(onMore = { showSecurity = true })
            Spacer(Modifier.height(16.dp))
            Text(tr("Сервер"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((h, title) in EcoflowApi.SERVERS) {
                    FilterChip(
                        selected = vm.host == h,
                        onClick = { vm.host = h },
                        label = { FitText(title) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                tr("Якщо вхід не вдається — спробуйте інший регіон. Для України зазвичай підходить «Авто» або «Європа»."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.remember, onCheckedChange = { vm.remember = it })
                Text(tr("Запам'ятати пароль (зашифровано, лише на цьому телефоні)"))
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
                    Text(tr("Увійти"))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.continueWithoutLogin() }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Продовжити без входу — майстерня, деталі, замітки"))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                tr("Вхід такий самий, як у застосунку EcoFlow. Ключі розробника не потрібні. Станція має бути додана в цей акаунт і бути онлайн через Wi-Fi."),
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
                tr("🔒 Пароль бачить лише EcoFlow"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Логін і пароль надсилаються тільки на сервер EcoFlow захищеним з'єднанням — так само, як в офіційному застосунку. У програми немає власного сервера, реклами чи аналітики, тож більше ніхто їх не отримує."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onMore) { Text(tr("Детальніше про безпеку")) }
        }
    }
}

@Composable
private fun SecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Безпека входу")) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Зрозуміло")) } },
    )
}

private val SECURITY_POINTS: List<Pair<String, String>> get() = listOf(
    tr("Куди йде пароль") to
        tr("Лише на сервер входу EcoFlow (api.ecoflow.com або регіональний api-e / api-a.ecoflow.com) через HTTPS з перевіркою сертифіката сервера. Якщо хтось у Wi-Fi спробує підмінити сервер, програма відхилить з'єднання."),
    tr("З чим ще з'єднується програма") to
        tr("Дані станцій — із сервером EcoFlow (MQTT, шифроване TLS). Нові версії застосунку — із GitHub. Якщо ви ввімкнули копію — із Google Диском. Статті талмуду й нові коди помилок (без серійного номера) — у спільноту Savosh Service. Реклами, аналітики чи сторонніх бібліотек відстеження в програмі немає. Пароль EcoFlow нікуди, крім сервера EcoFlow, не надсилається."),
    tr("Що програма робить в акаунті") to
        tr("Читає дані станцій: заряд, цикли, напруги, коди BMS. Команди керування (вихід 220 В, ліміти заряду) надсилає лише тоді, коли ви самі натиснули кнопку й підтвердили дію. Налаштувань акаунта не змінює."),
    tr("Що зберігається на телефоні") to
        tr("Email, вибраний сервер, список станцій і звіти. Пароль — тільки якщо увімкнено «Запам'ятати пароль», і тоді він зашифрований ключем Android Keystore, який не можна витягти з телефона. Без галочки пароль не зберігається взагалі. «Вийти» стирає збережений пароль. Системне резервне копіювання Android вимкнене, а в копії на Google Диску пароля немає."),
    tr("Журнал підключення") to
        tr("Пароля й токенів у журналі немає, але є email або ім'я акаунта та userId — пам'ятайте про це, коли ним ділитесь."),
    tr("Порада") to
        tr("Використовуйте для EcoFlow пароль, який не збігається з паролями від пошти чи банку. Це правило для будь-якого застосунку, не лише для цього."),
    tr("Про програму") to
        tr("Savosh Service. Програма незалежна, не пов'язана з EcoFlow; вхід працює так само, як у застосунку EcoFlow."),
)
