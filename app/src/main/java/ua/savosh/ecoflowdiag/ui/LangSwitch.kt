package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.core.AppLang
import ua.savosh.ecoflowdiag.core.tr

/** Кружок із прапором мови: намальований, бо емодзі-прапорів на частині телефонів немає. */
@Composable
fun LangFlag(lang: AppLang, dim: Dp = 26.dp) {
    Canvas(Modifier.size(dim).clip(CircleShape)) {
        val w = size.width
        val h = size.height
        when (lang) {
            AppLang.UK -> {
                drawRect(Color(0xFF0057B7), size = Size(w, h / 2f))
                drawRect(Color(0xFFFFD700), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
            }
            AppLang.EN -> {
                val navy = Color(0xFF012169)
                val red = Color(0xFFC8102E)
                drawRect(navy)
                drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.22f)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.22f)
                drawLine(red, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.08f)
                drawLine(red, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.08f)
                drawRect(Color.White, topLeft = Offset(w * 0.38f, 0f), size = Size(w * 0.24f, h))
                drawRect(Color.White, topLeft = Offset(0f, h * 0.38f), size = Size(w, h * 0.24f))
                drawRect(red, topLeft = Offset(w * 0.43f, 0f), size = Size(w * 0.14f, h))
                drawRect(red, topLeft = Offset(0f, h * 0.43f), size = Size(w, h * 0.14f))
            }
        }
        drawCircle(Color(0x33000000), style = Stroke(1.dp.toPx()))
    }
}

/** Вибір мови: прапор поточної мови, а по дотику — обидві мови на вибір. */
@Composable
fun LanguageButton(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val label = tr("Мова")
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            LangFlag(vm.appLang)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (l in AppLang.entries) {
                DropdownMenuItem(
                    leadingIcon = { LangFlag(l) },
                    text = { Text(l.title) },
                    trailingIcon = if (l == vm.appLang) ({ Text("✓") }) else null,
                    onClick = {
                        open = false
                        vm.setLanguage(l)
                    },
                )
            }
        }
    }
}
