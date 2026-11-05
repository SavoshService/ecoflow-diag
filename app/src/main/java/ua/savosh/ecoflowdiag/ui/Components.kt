package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.tr
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.core.Level

val Mono = FontFamily.Monospace

@Composable
fun LevelChip(level: Level, text: String = level.title) {
    val c = levelColor(level)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(text, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (collapsible) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    content()
                }
            } else {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/** Вміст екрана з полями вводу: клавіатура не закриває поле, у яке пишеш. */
fun Modifier.formPadding(pad: PaddingValues): Modifier = padding(pad).consumeWindowInsets(pad).imePadding()

/** Вміст по центру екрана; якщо не вміщається (ландшафт, великий шрифт) — прокручується, а не обрізається. */
@Composable
fun CenteredScroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        val minHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** На вузькому екрані кнопки верхньої панелі трохи менші: інакше заголовку лишалось місце на «Статі…». */
@Composable
fun CompactActions(content: @Composable () -> Unit) {
    val narrow = LocalConfiguration.current.screenWidthDp < 400
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (narrow) 40.dp else 48.dp, content = content)
}

/** Текст діалогу: на малому екрані з великим шрифтом він прокручується, а не обрізається. */
@Composable
fun DialogText(text: String) {
    Text(text, Modifier.verticalScroll(rememberScrollState()))
}

/**
 * Напис в один рядок для кнопок і вузьких місць: якщо не вміщається, шрифт трохи зменшується,
 * а не переносить останні літери на новий рядок.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    fontFamily: FontFamily? = null,
    minScale: Float = 0.68f,
) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    val baseSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        fontSize = baseSize * scale,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow && scale > minScale) scale = (scale - 0.06f).coerceAtLeast(minScale) },
    )
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified, mono: Boolean = true) {
    LabelValueRow(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        label = {
            Text(
                key,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        value = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) Mono else FontFamily.Default,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                textAlign = TextAlign.End,
            )
        },
    )
}

/**
 * Підпис і значення. Вміщаються разом — один рядок; коротке значення лишається збоку, а підпис переноситься;
 * довге значення йде окремим рядком — інакше підпис ламається на вузьку колонку по слову в рядку.
 */
@Composable
private fun LabelValueRow(modifier: Modifier, label: @Composable () -> Unit, value: @Composable () -> Unit) {
    Layout(modifier = modifier, content = { label(); value() }) { items, c ->
        val gap = 12.dp.roundToPx()
        val w = c.maxWidth
        val wantLabel = items[0].maxIntrinsicWidth(Constraints.Infinity)
        val wantValue = items[1].maxIntrinsicWidth(Constraints.Infinity)
        if (wantLabel + gap + wantValue <= w || wantValue <= w * 0.45f) {
            val l = items[0].measure(Constraints(maxWidth = (w - wantValue - gap).coerceAtLeast(0)))
            val v = items[1].measure(Constraints(minWidth = wantValue, maxWidth = wantValue))
            layout(w, maxOf(l.height, v.height)) {
                l.place(0, 0)
                v.place(w - wantValue, 0)
            }
        } else {
            val l = items[0].measure(Constraints(maxWidth = w))
            val v = items[1].measure(Constraints(maxWidth = w))
            layout(w, l.height + v.height) {
                l.place(0, 0)
                v.place(w - v.width, l.height)
            }
        }
    }
}

/** Кругова шкала SOH. */
@Composable
fun SohGauge(percent: Double?, level: Level, sizeDp: Int = 96) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val color = levelColor(level)
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val stroke = size.minDimension * 0.10f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val p = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            if (percent != null) drawArc(color, 135f, 270f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                // Округлення як у висновках (99.6 → 100), а не обрізання — щоб числа на екрані збігались.
                percent?.let { "${kotlin.math.round(it).toLong()}%" } ?: "—",
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                // Не залежить від шрифту системи: число має вміщатися в кільце.
                fontSize = with(LocalDensity.current) { (sizeDp / 4.2).dp.toSp() },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("SOH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Відхилення напруги кожної групи від середнього (мВ): стовпчик угору — вища за середню, униз — нижча.
 * Колір узгоджений із висновком про баланс (ті самі пороги розкиду [okMv]/[warnMv], на плато LFP — нижчі):
 * розкид у нормі — усі стовпчики зелені; інакше групи, що відхилились, фарбуються кольором висновку.
 */
@Composable
fun CellBars(cells: List<Long>, okMv: Int = 30, warnMv: Int = 70) {
    if (cells.size < 2) return
    val avg = cells.average()
    val devs = cells.map { it - avg }
    val maxAbs = devs.maxOf { kotlin.math.abs(it) }
    val range = maxOf(25.0, maxAbs * 1.15)
    val spread = cells.max() - cells.min()
    val good = levelColor(Level.GOOD)
    val outlier = levelColor(
        when {
            spread <= okMv -> Level.GOOD
            spread <= warnMv -> Level.WARN
            spread <= 150 -> Level.BAD
            else -> Level.CRITICAL
        },
    )
    val axis = MaterialTheme.colorScheme.outline
    // Підписи стовпчиків прив'язані до ширини стовпчика, а не до шрифту системи: інакше вони переносяться.
    val tiny = with(LocalDensity.current) { (if (cells.size > 12) 7 else 8).dp.toSp() }
    val small = with(LocalDensity.current) { 9.dp.toSp() }
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            val n = cells.size
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val mid = size.height / 2f
            val half = mid - 2.dp.toPx()
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            for ((i, d) in devs.withIndex()) {
                val a = kotlin.math.abs(d)
                val c = if (spread <= okMv || a <= okMv / 2.0) good else outlier
                val h = maxOf(2.dp.toPx(), (a / range * half).toFloat())
                val x = i * (w + gap)
                val top = if (d >= 0) mid - h else mid
                drawRoundRect(c, Offset(x, top), Size(w, h), CornerRadius(3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            for (d in devs) {
                val v = kotlin.math.round(d).toLong()
                Text(
                    if (v > 0) "+$v" else "$v",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = tiny,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in cells.indices) {
                Text(
                    "${i + 1}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = small,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Відхилення від середнього {0} В, мВ · шкала ±{1} мВ · розкид ≤ {2} мВ — норма, ≤ {3} мВ — увага, більше — погано", String.format(java.util.Locale.US, "%.3f", avg / 1000.0), range.toLong(), okMv, warnMv),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Рядок показника: кольорова мітка стану, назва, значення, пояснення.
 * Якщо передано [onEditNote] — показується кнопка для власної примітки до коду.
 */
@Composable
fun IndicatorRow(
    title: String,
    value: String,
    level: Level,
    note: String?,
    onEditNote: (() -> Unit)? = null,
) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(levelColor(level)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LabelValueRow(
                Modifier.fillMaxWidth(),
                label = {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                value = {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = Mono,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            if (!note.isNullOrBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEditNote != null) {
                Text(
                    tr("✎ Моя примітка до коду"),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onEditNote),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun FindingRow(level: Level, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(levelColor(level)),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
