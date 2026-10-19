package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Заглушка розділу прошивання: анімований «зламаний робот, якого ремонтують» і напис «у розробці».
 * Жодного прошивання тут поки немає — лише вигляд майбутнього розділу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashScreen(vm: AppViewModel) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            t += 1f
            delay(32)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Прошивка станцій", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrokenRobot(t)
            Spacer(Modifier.height(28.dp))
            Text(
                "🤖 Розділ у розробці",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Скоро можна буде прошивати станції прямо з телефону.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Savosh Service",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Зламаний робот (тріщина, збите око) із шестернею-ремонтом, що крутиться, та іскрами. */
@Composable
private fun BrokenRobot(t: Float) {
    val body = MaterialTheme.colorScheme.primary
    val metal = MaterialTheme.colorScheme.onSurfaceVariant
    val crack = levelColor(ua.savosh.ecoflowdiag.core.Level.CRITICAL)
    val spark = levelColor(ua.savosh.ecoflowdiag.core.Level.WARN)
    val eyeOk = levelColor(ua.savosh.ecoflowdiag.core.Level.GOOD)

    Canvas(Modifier.size(200.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.42f
        val cy = h * 0.46f
        val headW = w * 0.46f
        val headH = h * 0.40f
        val r = headW * 0.16f

        // --- антена з блимаючим кінчиком
        val blink = (sin(t * 0.18f) + 1f) / 2f
        val antX = cx
        val antTopY = cy - headH * 0.62f - h * 0.10f
        drawLine(metal, Offset(antX, cy - headH * 0.62f), Offset(antX, antTopY), strokeWidth = w * 0.02f)
        drawCircle(spark.copy(alpha = 0.35f + 0.65f * blink), radius = w * 0.035f, center = Offset(antX, antTopY))

        // --- голова
        drawRoundRect(
            body,
            topLeft = Offset(cx - headW / 2f, cy - headH / 2f),
            size = Size(headW, headH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )

        // --- очі: ліве ціле, праве «збите» (червоний хрестик)
        val eyeY = cy - headH * 0.08f
        drawCircle(eyeOk, radius = headW * 0.09f, center = Offset(cx - headW * 0.20f, eyeY))
        val ex = cx + headW * 0.20f
        val es = headW * 0.09f
        drawLine(crack, Offset(ex - es, eyeY - es), Offset(ex + es, eyeY + es), strokeWidth = w * 0.02f)
        drawLine(crack, Offset(ex + es, eyeY - es), Offset(ex - es, eyeY + es), strokeWidth = w * 0.02f)

        // --- рот (решітка)
        val mouthY = cy + headH * 0.26f
        val mouthW = headW * 0.44f
        for (i in 0..3) {
            val x = cx - mouthW / 2f + mouthW * i / 3f
            drawLine(metal, Offset(x, mouthY - headH * 0.05f), Offset(x, mouthY + headH * 0.05f), strokeWidth = w * 0.012f)
        }

        // --- тріщина через голову (зигзаг)
        var px = cx - headW * 0.30f
        var py = cy - headH * 0.42f
        val zig = listOf(
            Offset(cx - headW * 0.12f, cy - headH * 0.10f),
            Offset(cx - headW * 0.22f, cy + headH * 0.06f),
            Offset(cx - headW * 0.05f, cy + headH * 0.30f),
        )
        for (p in zig) {
            drawLine(crack, Offset(px, py), p, strokeWidth = w * 0.015f)
            px = p.x; py = p.y
        }

        // --- шестерня «ремонту», що крутиться (правий нижній кут)
        val gx = w * 0.78f
        val gy = h * 0.72f
        val gR = w * 0.14f
        val teeth = 8
        val rot = t * 0.06f
        for (i in 0 until teeth) {
            val a = rot + i * (2f * Math.PI.toFloat() / teeth)
            val x1 = gx + cos(a) * gR
            val y1 = gy + sin(a) * gR
            val x2 = gx + cos(a) * (gR * 1.35f)
            val y2 = gy + sin(a) * (gR * 1.35f)
            drawLine(metal, Offset(x1, y1), Offset(x2, y2), strokeWidth = w * 0.045f)
        }
        drawCircle(metal, radius = gR, center = Offset(gx, gy))
        drawCircle(body, radius = gR * 0.45f, center = Offset(gx, gy))

        // --- іскри біля шестерні (мерехтять)
        for (i in 0 until 3) {
            val ph = (sin(t * 0.3f + i * 2f) + 1f) / 2f
            val a = -0.8f - i * 0.5f
            val d = gR * (1.4f + ph * 0.8f)
            val sx = gx + cos(a) * d
            val sy = gy + sin(a) * d
            drawCircle(spark.copy(alpha = ph), radius = w * 0.012f, center = Offset(sx, sy))
        }
    }
}
