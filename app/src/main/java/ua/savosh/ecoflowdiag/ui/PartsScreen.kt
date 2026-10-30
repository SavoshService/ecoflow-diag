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
