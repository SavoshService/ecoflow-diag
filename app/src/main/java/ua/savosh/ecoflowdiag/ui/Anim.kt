package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** «Дощ» із нулів та одиниць, що біжать стовпчиками — ніби термінал/хакерська атака. */
@Composable
fun BinaryRain(modifier: Modifier = Modifier, cols: Int = 8, rows: Int = 6, fontSizeSp: Int = 12) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            frame++
            delay(110)
        }
    }
    val on = MaterialTheme.colorScheme.primary
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (c in 0 until cols) {
            val head = (frame + c * 3) % (rows + 4)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (rr in 0 until rows) {
                    val dist = head - rr
                    val alpha = when {
                        dist == 0 -> 1f
                        dist in 1..3 -> (0.7f - dist * 0.17f)
                        else -> 0.12f
                    }.coerceIn(0.1f, 1f)
                    val bit = if (((frame + c * 7 + rr * 13) % 2) == 0) "0" else "1"
                    Text(
                        bit,
                        color = on.copy(alpha = alpha),
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 2).sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
