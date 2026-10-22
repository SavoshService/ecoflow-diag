package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Діалог подяки з анімованою чашкою запашної кави. */
@Composable
fun CoffeeDonateDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Дякую! ☕", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SteamingCoffee()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Якщо програма стала в пригоді — можна кинути на каву автору. Дрібниця, а приємно й допомагає розвивати застосунок.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("Відкрити банку") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Іншим разом") } },
    )
}

/** Чашка кави з парою, що піднімається хвилями. */
@Composable
fun SteamingCoffee(sizeDp: Int = 140) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            t += 0.08f
            delay(33)
        }
    }
    val cup = Color(0xFFEDE4D3) // порцеляна — світла, не зливається з темним тлом
    val cupEdge = Color(0xFF8A7A5E)
    val coffee = Color(0xFF5A3A26)
    val crema = Color(0xFFC89B6E)
    val steam = MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val w = size.width
            val h = size.height
            val cupW = w * 0.52f
            val cupH = h * 0.34f
            val cupX = w * 0.26f
            val cupY = h * 0.52f

            // пара — три хвилясті струмки
            for (i in 0 until 3) {
                val baseX = cupX + cupW * (0.28f + 0.22f * i)
                val phase = t + i * 1.3f
                val path = Path()
                var first = true
                var y = cupY - 2f
                while (y > cupY - cupH * 1.7f) {
                    val prog = (cupY - y) / (cupH * 1.7f)
                    val x = baseX + sin(phase + prog * 6f) * (w * 0.035f) * (0.4f + prog)
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    y -= 4f
                }
                val alpha = (0.35f + 0.25f * sin(phase)).coerceIn(0.1f, 0.6f)
                drawPath(path, steam.copy(alpha = alpha), style = Stroke(width = w * 0.015f))
            }

            // блюдце
            drawRoundRect(
                cup,
                topLeft = Offset(cupX - cupW * 0.28f, cupY + cupH + h * 0.02f),
                size = Size(cupW * 1.56f, h * 0.05f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.03f, h * 0.03f),
            )
            // ручка
            drawArc(
                cupEdge,
                startAngle = -70f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(cupX + cupW - w * 0.03f, cupY + cupH * 0.1f),
                size = Size(cupW * 0.5f, cupH * 0.7f),
                style = Stroke(width = w * 0.03f),
            )
            // чашка
            drawRoundRect(
                cup,
                topLeft = Offset(cupX, cupY),
                size = Size(cupW, cupH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f),
            )
            // кава всередині
            drawRoundRect(
                coffee,
                topLeft = Offset(cupX + w * 0.02f, cupY + h * 0.015f),
                size = Size(cupW - w * 0.04f, cupH * 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f, w * 0.02f),
            )
            drawRoundRect(
                crema,
                topLeft = Offset(cupX + w * 0.05f, cupY + h * 0.02f),
                size = Size(cupW - w * 0.10f, cupH * 0.07f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f, w * 0.02f),
            )
        }
    }
}
