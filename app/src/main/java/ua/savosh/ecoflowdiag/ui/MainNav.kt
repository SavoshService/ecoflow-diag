package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Нижня панель головних розділів. Показується лише на головних екранах
 * (Станції, Замовлення/Склад/Фінанси, Ще); на внутрішніх екранах її немає.
 */
@Composable
fun MainNavBar(vm: AppViewModel) {
    val current = vm.mainTab
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        for (tab in MainTab.entries) {
            NavigationBarItem(
                selected = tab == current,
                onClick = { if (tab != current) vm.openTab(tab) },
                icon = { TabGlyph(tab) },
                label = { Text(tab.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private val MainTab.title: String
    get() = when (this) {
        MainTab.STATIONS -> "Станції"
        MainTab.ORDERS -> "Замовлення"
        MainTab.STOCK -> "Склад"
        MainTab.FINANCE -> "Фінанси"
        MainTab.MORE -> "Ще"
    }

// ---------- Іконки розділів: прості лінійні значки, фарбуються кольором панелі ----------

@Composable
private fun TabGlyph(tab: MainTab) {
    val c = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        when (tab) {
            MainTab.STATIONS -> battery(s, c)
            MainTab.ORDERS -> clipboard(s, c)
            MainTab.STOCK -> box(s, c)
            MainTab.FINANCE -> wallet(s, c)
            MainTab.MORE -> dots(s, c)
        }
    }
}

private fun line(s: Float) = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Зарядна станція: акумулятор із блискавкою. */
private fun DrawScope.battery(s: Float, c: Color) {
    drawRoundRect(c, Offset(s * 0.27f, s * 0.2f), Size(s * 0.46f, s * 0.7f), CornerRadius(s * 0.09f), style = line(s))
    drawRoundRect(c, Offset(s * 0.39f, s * 0.09f), Size(s * 0.22f, s * 0.08f), CornerRadius(s * 0.03f))
    val bolt = Path().apply {
        moveTo(s * 0.54f, s * 0.31f); lineTo(s * 0.40f, s * 0.57f); lineTo(s * 0.50f, s * 0.57f)
        lineTo(s * 0.46f, s * 0.79f); lineTo(s * 0.61f, s * 0.50f); lineTo(s * 0.51f, s * 0.50f); close()
    }
    drawPath(bolt, c)
}

/** Замовлення: планшет зі списком. */
private fun DrawScope.clipboard(s: Float, c: Color) {
    drawRoundRect(c, Offset(s * 0.2f, s * 0.16f), Size(s * 0.6f, s * 0.74f), CornerRadius(s * 0.09f), style = line(s))
    drawRoundRect(c, Offset(s * 0.36f, s * 0.09f), Size(s * 0.28f, s * 0.13f), CornerRadius(s * 0.04f))
    for (y in floatArrayOf(0.42f, 0.57f, 0.72f)) {
        drawLine(c, Offset(s * 0.33f, s * y), Offset(s * 0.67f, s * y), strokeWidth = s * 0.075f, cap = StrokeCap.Round)
    }
}

/** Склад: коробка. */
private fun DrawScope.box(s: Float, c: Color) {
    val outline = Path().apply {
        moveTo(s * 0.5f, s * 0.11f); lineTo(s * 0.86f, s * 0.29f); lineTo(s * 0.86f, s * 0.71f)
        lineTo(s * 0.5f, s * 0.89f); lineTo(s * 0.14f, s * 0.71f); lineTo(s * 0.14f, s * 0.29f); close()
    }
    drawPath(outline, c, style = line(s))
    val inner = Path().apply {
        moveTo(s * 0.14f, s * 0.29f); lineTo(s * 0.5f, s * 0.47f); lineTo(s * 0.86f, s * 0.29f)
        moveTo(s * 0.5f, s * 0.47f); lineTo(s * 0.5f, s * 0.89f)
    }
    drawPath(inner, c, style = line(s))
}

/** Фінанси: гаманець. */
private fun DrawScope.wallet(s: Float, c: Color) {
    drawRoundRect(c, Offset(s * 0.13f, s * 0.24f), Size(s * 0.74f, s * 0.58f), CornerRadius(s * 0.1f), style = line(s))
    drawRoundRect(c, Offset(s * 0.55f, s * 0.43f), Size(s * 0.32f, s * 0.2f), CornerRadius(s * 0.06f), style = line(s))
    drawCircle(c, radius = s * 0.04f, center = Offset(s * 0.66f, s * 0.53f))
}

/** Ще: три крапки. */
private fun DrawScope.dots(s: Float, c: Color) {
    for (x in floatArrayOf(0.24f, 0.5f, 0.76f)) drawCircle(c, radius = s * 0.075f, center = Offset(s * x, s * 0.5f))
}
