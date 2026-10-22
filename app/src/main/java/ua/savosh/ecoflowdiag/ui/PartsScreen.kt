package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpinningChip(sizeDp = 120)

            Text("Записник майстра", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Довідник, замітки й нагадування — усе в одному місці. Веди свій талмуд, чеклисти ремонтів " +
                    "і нотатки до станцій, а новинами діліться у спільноті.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))
            HubCard(
                emoji = "📖", title = "Талмуд майстрів", accent = Color(0xFF7C3AED),
                subtitle = "Статті з фото, маркуванням і аналогами. Пиши — і це побачить уся спільнота.",
            ) { vm.openKnowledge() }
            HubCard(
                emoji = "📝", title = "Замітки майстра", accent = Color(0xFF2563EB),
                subtitle = "Нотатки, чеклисти з галочками, замітки до станцій і нагадування. Лише для тебе.",
            ) { vm.openNotes() }
            HubCard(
                emoji = "📣", title = "Канал спільноти", accent = Color(0xFF16A34A),
                subtitle = "Новини, обговорення й спільна база знань майстрів — приєднуйся.",
            ) { openUrl(ctx, COMMUNITY_URL) }
            HubCard(
                emoji = "💾", title = "Резервна копія", accent = Color(0xFFEA580C),
                subtitle = "Збережи дані у файл або на Google Диск, щоб не втратити при перевстановленні.",
            ) { vm.openBackup() }
        }
    }
}

/** Приваблива плитка-розділ: кольорова іконка, назва, опис і стрілка. */
@Composable
private fun HubCard(emoji: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text("→", style = MaterialTheme.typography.titleLarge, color = accent)
        }
    }
}

/** Яскрава кольорова кнопка, що виділяється на тлі теми застосунку. */
@Composable
private fun ColorButton(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/** Анімована мікросхема, що повільно обертається. */
@Composable
fun SpinningChip(sizeDp: Int = 110) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            angle = (angle + 1.2f) % 360f
            delay(33)
        }
    }
    val body = MaterialTheme.colorScheme.primary
    val die = MaterialTheme.colorScheme.primaryContainer
    val pin = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(sizeDp.dp)) {
        val s = size.minDimension
        val bodySize = s * 0.46f
        val left = (size.width - bodySize) / 2f
        val top = (size.height - bodySize) / 2f
        val pinLen = s * 0.11f
        val pinW = s * 0.055f
        rotate(angle, pivot = Offset(size.width / 2f, size.height / 2f)) {
            for (i in 0 until 4) {
                val frac = (i + 1f) / 5f
                val x = left + bodySize * frac
                drawRoundRect(pin, topLeft = Offset(x - pinW / 2f, top - pinLen), size = Size(pinW, pinLen), cornerRadius = CornerRadius(pinW * 0.3f))
                drawRoundRect(pin, topLeft = Offset(x - pinW / 2f, top + bodySize), size = Size(pinW, pinLen), cornerRadius = CornerRadius(pinW * 0.3f))
                val y = top + bodySize * frac
                drawRoundRect(pin, topLeft = Offset(left - pinLen, y - pinW / 2f), size = Size(pinLen, pinW), cornerRadius = CornerRadius(pinW * 0.3f))
                drawRoundRect(pin, topLeft = Offset(left + bodySize, y - pinW / 2f), size = Size(pinLen, pinW), cornerRadius = CornerRadius(pinW * 0.3f))
            }
            drawRoundRect(body, topLeft = Offset(left, top), size = Size(bodySize, bodySize), cornerRadius = CornerRadius(s * 0.04f))
            drawRoundRect(die, topLeft = Offset(left + bodySize * 0.28f, top + bodySize * 0.28f), size = Size(bodySize * 0.44f, bodySize * 0.44f), cornerRadius = CornerRadius(s * 0.02f))
            drawCircle(dot, radius = s * 0.035f, center = Offset(left + bodySize * 0.2f, top + bodySize * 0.2f))
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
                Text("СКОРО — наповнюється", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Розділ у розробці. Готове прийде з оновленням програми.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("•  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
