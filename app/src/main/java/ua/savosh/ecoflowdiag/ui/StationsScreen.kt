package ua.savosh.ecoflowdiag.ui

import ua.savosh.ecoflowdiag.core.localize
import ua.savosh.ecoflowdiag.core.tk
import ua.savosh.ecoflowdiag.core.tr
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.core.Level
import ua.savosh.ecoflowdiag.core.Models
import ua.savosh.ecoflowdiag.core.Health
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.data.StationEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var showCoffee by remember { mutableStateOf(false) }
    var editNoteFor by remember { mutableStateOf<StationEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<StationEntry?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = vm.stationsListState
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { LanguageButton(vm) },
                title = {
                    Column {
                        FitText(tr("Станції"), fontWeight = FontWeight.Bold)
                        Text(
                            vm.auth?.userName ?: vm.email.ifBlank { tr("не виконано вхід") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    CompactActions {
                        // Кнопка входу/синхронізації Google — завжди видима, веде на екран копії
                        IconButton(onClick = { vm.openBackup() }) {
                            GoogleIcon()
                        }
                        // Instagram Savosh Service
                        IconButton(onClick = { openUrl(context, "https://instagram.com/savosh_service") }) {
                            InstagramIcon()
                        }
                        if (vm.loggedIn) {
                            IconButton(
                                onClick = { if (vm.refreshAllBusy) vm.stopRefreshAll() else vm.refreshAllStations() },
                                enabled = vm.stations.isNotEmpty(),
                            ) {
                                if (vm.refreshAllBusy) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = tr("Оновити всі станції"))
                                }
                            }
                            IconButton(onClick = { confirmLogout = true }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = tr("Вийти з акаунта"))
                            }
                        } else {
                            IconButton(onClick = { vm.goToLogin() }) {
                                Text("🔑", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                },
            )
        },
        bottomBar = { MainNavBar(vm) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = tr("Додати станцію")) },
                text = { Text(tr("Додати станцію")) },
                expanded = fabExpanded,
            )
        },
    ) { pad ->
        val showBackup = vm.needsBackup && !vm.backupBannerHidden
        val attentionCount = vm.stations.count { it.online == false || sohNeedsAttention(it.lastSoh) }
        // Фільтри лишаються, поки щось відфільтровано: інакше з однією станцією його не було б чим скинути.
        val showFilters = vm.stations.size > 1 || vm.stationFilter != StFilter.ALL || vm.stationQuery.isNotEmpty()
        val showStatus = vm.refreshAllBusy || vm.refreshAllStatus.isNotBlank()
        val shown = vm.stations.filter { stationMatches(it, vm.stationQuery, vm.stationFilter) }
        // Нова станція стає першою: показуємо її одразу, інакше вона лишалась за банерами,
        // а повторна спроба додати писала «вже є у списку».
        val firstSn = vm.stations.firstOrNull()?.sn
        var knownFirstSn by remember { mutableStateOf(firstSn) }
        LaunchedEffect(firstSn) {
            if (firstSn != null && firstSn != knownFirstSn) {
                val update = vm.availableUpdate != null && !vm.updateDismissed
                val above = listOf(donateEnabled(), update, showBackup, attentionCount > 0, showFilters, showStatus).count { it }
                listState.scrollToItem(above)
            }
            knownFirstSn = firstSn
        }
        // Усе прокручується разом: на малому екрані банери й фільтри не лишали місця для самих станцій.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().formPadding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Плитка «КАВА» лишається нагорі; зв'язок, деталі, прошивка і «Майстерня» — на нижній панелі та в «Ще».
            if (donateEnabled()) {
                item(key = "coffee") { CoffeeTile { showCoffee = true } }
            }
            // Банер оновлення — завжди видимий (навіть коли ще немає станцій).
            vm.availableUpdate?.takeIf { !vm.updateDismissed }?.let { upd ->
                item(key = "update") {
                    UpdateBanner(
                        version = upd.version,
                        notes = upd.notes,
                        downloading = vm.updateDownloading,
                        progress = vm.updateProgress,
                        error = vm.updateError,
                        onUpdate = { vm.startUpdate(context) },
                        onBrowser = { openUrl(context, upd.url) },
                        onDismiss = { vm.dismissUpdate() },
                    )
                }
            }
            // Нагадування увімкнути резервну копію (є дані, але копію не налаштовано).
            if (showBackup) {
                item(key = "backup") {
                    BackupReminder(onSetup = { vm.openBackup() }, onHide = { vm.hideBackupBanner() })
                }
            }
            if (vm.stations.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!vm.loggedIn) {
                            Text(tr("Вхід в EcoFlow — для діагностики"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("Замовлення, склад і замітки працюють без входу. Щоб бачити й діагностувати свої станції — увійдіть в акаунт EcoFlow."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.goToLogin() }) { Text(tr("Увійти в EcoFlow")) }
                        } else {
                            Text(tr("Поки що немає станцій"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("Додайте станцію за серійним номером. Він є на наклейці станції та в застосунку EcoFlow у налаштуваннях пристрою. Станцію спершу треба додати в цей акаунт EcoFlow."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        DemoCard(onOpen = { vm.openDemo() })
                    }
                }
            } else {
                if (attentionCount > 0) {
                    item(key = "attention") {
                        AttentionBanner(count = attentionCount, onClick = { vm.stationFilter = StFilter.ATTENTION })
                    }
                }
                if (showFilters) {
                    item(key = "filters") {
                        StationFilters(vm.stationQuery, { vm.stationQuery = it }, vm.stationFilter, { vm.stationFilter = it })
                    }
                }
                if (showStatus) {
                    item(key = "refresh_status") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            if (vm.refreshAllBusy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                vm.refreshAllStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "no_match") {
                        Text(
                            tr("Нічого не знайдено за фільтром."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(shown, key = { it.sn }) { st ->
                    StationCard(
                        st = st,
                        onOpen = { vm.openDiag(st.sn) },
                        onEditNote = { editNoteFor = st },
                        onShare = { tech ->
                            vm.savedReport(st.sn, tech)?.let { shareText(context, it, "${if (tech) tr("Розширений звіт") else tr("Короткий звіт")} ${st.sn}") }
                        },
                        hasReport = vm.savedReport(st.sn) != null,
                        onInfo = { vm.openStationInfo(st.sn) },
                        onDelete = { confirmDelete = st },
                    )
                }
                if (!vm.demoHidden) {
                    item(key = "demo_card") { DemoCard(onOpen = { vm.openDemo() }, onDismiss = { vm.hideDemo() }) }
                }
            }
        }
    }

    if (showCoffee) {
        CoffeeDonateDialog(onOpen = { showCoffee = false; openDonate(context) }, onDismiss = { showCoffee = false })
    }
    if (showAdd) {
        AddStationDialog(
            onDismiss = { showAdd = false },
            onAdd = { sn, note -> vm.addStation(sn, note).also { if (it == null) showAdd = false } },
        )
    }
    editNoteFor?.let { st ->
        NoteDialog(st.note, st.tags, onDismiss = { editNoteFor = null }) { note, tags ->
            vm.updateStationNoteTags(st.sn, note, tags)
            editNoteFor = null
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(tr("Вийти з акаунта?")) },
            text = { DialogText(tr("Ви вийдете з акаунта EcoFlow. Збережений пароль буде стерто — для наступного входу введіть його знову. Станції, звіти й примітки залишаться.")) },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text(tr("Вийти")) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(tr("Скасувати")) } },
        )
    }
    confirmDelete?.let { st ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Видалити станцію?")) },
            text = { DialogText(tr("{0}{1}\nЗбережений звіт теж буде видалено.", st.sn, if (st.note.isNotBlank()) " — ${st.note}" else "")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStation(st.sn)
                    confirmDelete = null
                }) { Text(tr("Видалити")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Скасувати")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationCard(
    st: StationEntry,
    onOpen: () -> Unit,
    onEditNote: () -> Unit,
    onShare: (technical: Boolean) -> Unit,
    hasReport: Boolean,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Акцент за станом (здоров'я за єдиною шкалою + онлайн), без впливу кодів помилок
    val accent = levelColor(
        when {
            st.lastCheckMs == null -> Level.UNKNOWN
            st.online == false -> Level.BAD
            else -> Health.of(st.lastSoh)?.level ?: Level.UNKNOWN
        },
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(
                Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Column(Modifier.weight(1f)) {
                Text(st.model?.let { localize(it) } ?: tr("Модель визначиться після підключення"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(st.sn, fontFamily = Mono, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (st.note.isNotBlank()) {
                    Text(st.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (st.tags.isNotEmpty()) {
                    Text(
                        st.tags.joinToString("  ") { "#" + tr(it) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OnlineDot(st.online)
                    st.lastSoc?.let {
                        Spacer(Modifier.width(10.dp))
                        Text("🔋 ${it.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontFamily = Mono)
                    }
                }
                if (st.lastCheckMs != null) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Значок — оцінка батареї лише за SOH; «⚠» — є зауваження (коди, дисбаланс тощо).
                        val overall = levelFromName(st.lastLevel)
                        val grade = Health.of(st.lastSoh)
                        val soh = st.lastSoh
                        if (grade != null && soh != null) {
                            LevelChip(grade.level, "${grade.chip} · SOH ${soh.roundToInt()}%")
                            if (overall.rank >= Level.WARN.rank) {
                                Text("⚠", color = levelColor(overall), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            LevelChip(overall, overall.statusTitle)
                        }
                        Text(
                            SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(st.lastCheckMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Ще не перевірялась"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = tr("Меню")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Про станцію")) }, onClick = { menu = false; onInfo() })
                    DropdownMenuItem(text = { Text(tr("Змінити примітку")) }, onClick = { menu = false; onEditNote() })
                    if (hasReport) {
                        DropdownMenuItem(text = { Text(tr("Звіт клієнту (останній)")) }, onClick = { menu = false; onShare(false) })
                        DropdownMenuItem(text = { Text(tr("Технічний звіт (останній)")) }, onClick = { menu = false; onShare(true) })
                    }
                    DropdownMenuItem(text = { Text(tr("Видалити")) }, onClick = { menu = false; onDelete() })
                }
            }
            }
        }
    }
}

@Composable
private fun OnlineDot(online: Boolean?) {
    val (txt, color) = when (online) {
        true -> tr("● онлайн") to levelColor(Level.GOOD)
        false -> tr("● офлайн") to levelColor(Level.BAD)
        null -> tr("● невідомо") to levelColor(Level.UNKNOWN)
    }
    Text(txt, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
}

/** Іконка Google як на робочому столі Android: біла плитка з кольоровою «G» (червоний, жовтий, зелений, синій). */
@Composable
private fun GoogleIcon(dim: Dp = 28.dp) {
    Canvas(Modifier.size(dim)) {
        val s = size.minDimension
        drawRoundRect(color = Color.White, cornerRadius = CornerRadius(s * 0.24f, s * 0.24f))
        val d = s * 0.6f                       // діаметр «G»
        val w = d * 0.2f                       // товщина лінії
        val r = d / 2f - w / 2f
        val c = Offset(s / 2f, s / 2f)
        val tl = Offset(c.x - r, c.y - r)
        val arc = Size(r * 2f, r * 2f)
        val st = Stroke(w)
        // кути: 0° — праворуч, за годинниковою стрілкою; проміж -40°..0° — виріз «G»
        drawArc(Color(0xFFEA4335), -140f, 100f, false, tl, arc, style = st)   // червоний — верх
        drawArc(Color(0xFFFBBC05), 140f, 80f, false, tl, arc, style = st)     // жовтий — ліво
        drawArc(Color(0xFF34A853), 40f, 100f, false, tl, arc, style = st)     // зелений — низ
        drawArc(Color(0xFF4285F4), 0f, 40f, false, tl, arc, style = st)       // синій — низ праворуч
        drawRect(Color(0xFF4285F4), topLeft = Offset(c.x, c.y - w / 2f), size = Size(d / 2f, w))  // перекладина
    }
}

/** Іконка Instagram як на робочому столі Android: градієнтна плитка з білим фотоапаратом. */
@Composable
private fun InstagramIcon(dim: Dp = 28.dp) {
    Canvas(Modifier.size(dim)) {
        val s = size.minDimension
        val bg = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFDC80), Color(0xFFFCAF45), Color(0xFFF77737), Color(0xFFF56040),
                Color(0xFFFD1D1D), Color(0xFFE1306C), Color(0xFFC13584), Color(0xFF833AB4), Color(0xFF5851DB),
            ),
            center = Offset(s * 0.28f, s * 1.05f),
            radius = s * 1.25f,
        )
        drawRoundRect(brush = bg, cornerRadius = CornerRadius(s * 0.24f, s * 0.24f))
        val stroke = Stroke(s * 0.075f)
        val pad = s * 0.22f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(pad, pad),
            size = Size(s - pad * 2f, s - pad * 2f),
            cornerRadius = CornerRadius(s * 0.16f, s * 0.16f),
            style = stroke,
        )
        drawCircle(Color.White, radius = s * 0.135f, center = Offset(s / 2f, s / 2f), style = stroke)
        drawCircle(Color.White, radius = s * 0.04f, center = Offset(s * 0.665f, s * 0.335f))
    }
}

@Composable
private fun AttentionBanner(count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Потребують уваги: {0}. Торкніться, щоб показати.", count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Здоров'я батареї потребує уваги — за тією ж шкалою (і округленням), що й значок на картці. */
private fun sohNeedsAttention(soh: Double?): Boolean = (Health.of(soh)?.level?.rank ?: -1) >= Level.WARN.rank

enum class StFilter(private val titleKey: String) {
    ALL(tk("Усі")), ONLINE(tk("Онлайн")), OFFLINE(tk("Офлайн")), ATTENTION(tk("Увага"));

    val title: String get() = tr(titleKey)
}

private fun stationMatches(st: StationEntry, query: String, filter: StFilter): Boolean {
    val q = query.trim()
    val mq = q.isEmpty() ||
        st.sn.contains(q, ignoreCase = true) ||
        st.note.contains(q, ignoreCase = true) ||
        (st.model?.contains(q, ignoreCase = true) == true)
    val attention = st.online == false || sohNeedsAttention(st.lastSoh)
    val mf = when (filter) {
        StFilter.ALL -> true
        StFilter.ONLINE -> st.online == true
        StFilter.OFFLINE -> st.online == false
        StFilter.ATTENTION -> attention
    }
    return mq && mf
}

@Composable
private fun StationFilters(
    query: String,
    onQuery: (String) -> Unit,
    filter: StFilter,
    onFilter: (StFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Пошук за SN або приміткою")) },
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val inner = PaddingValues(horizontal = 4.dp)
            for (f in StFilter.entries) {
                if (f == filter) {
                    Button(onClick = { onFilter(f) }, modifier = Modifier.weight(1f), contentPadding = inner) {
                        FitText(f.title, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(onClick = { onFilter(f) }, modifier = Modifier.weight(1f), contentPadding = inner) {
                        FitText(f.title, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Демо-станція: пульсуюча картка «спробувати програму на несправжніх даних». */
@Composable
private fun DemoCard(onOpen: () -> Unit, onDismiss: (() -> Unit)? = null) {
    var pulse by remember { mutableFloatStateOf(0f) }
    // Пульсує лише перші секунди: безкінечна анімація на головному екрані марно садила б батарею.
    LaunchedEffect(Unit) {
        repeat(360) { pulse += 0.06f; kotlinx.coroutines.delay(33) }
    }
    val glow = (0.35f + 0.35f * kotlin.math.sin(pulse.toDouble()).toFloat()).coerceIn(0.1f, 0.8f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = if (onDismiss != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = glow)), contentAlignment = Alignment.Center) {
                Text("🔬", fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Демо станція"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(tr("Торкніться, щоб подивитись можливості: здоров'я, цикли, коди й пояснення, монітор заряду/розряду — на несправжніх даних."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Сховати демо"), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/** Нагадування увімкнути резервну копію, щоб не втратити дані при видаленні застосунку. */
@Composable
private fun BackupReminder(modifier: Modifier = Modifier, onSetup: () -> Unit, onHide: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                tr("⚠️ Увімкни резервну копію"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Якщо видалити застосунок — замовлення, склад і замітки пропадуть. Копія на твоєму Google Диску безкоштовна й оновлюється сама."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSetup, modifier = Modifier.weight(1f)) { FitText(tr("Налаштувати копію")) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onHide) { FitText(tr("Пізніше")) }
            }
        }
    }
}

/** Плитка «КАВА» на головному екрані: підтримати автора (вікно з чашкою). */
@Composable
private fun CoffeeTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "coffeePress")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☕", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("КАВА"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FitText(
                    tr("Програма безкоштовна — підтримай автора"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateBanner(
    version: String,
    notes: String,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onUpdate: () -> Unit,
    onBrowser: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("⬆ Доступна версія {0}", version), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                if (!downloading) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = tr("Закрити")) }
                }
            }
            if (notes.isNotBlank()) {
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                if (progress in 0..100) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (progress in 0..100) tr("Завантаження… {0}%", progress) else tr("Завантаження…"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                if (!error.isNullOrBlank()) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onUpdate) { FitText(tr("Оновити")) }
                    TextButton(onClick = onBrowser) { FitText(tr("У браузері")) }
                }
            }
        }
    }
}

@Composable
private fun AddStationDialog(onDismiss: () -> Unit, onAdd: (String, String) -> String?) {
    var sn by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { sn = Models.extractSn(it); error = null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Нова станція")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = sn,
                    onValueChange = { sn = it.uppercase(); error = null },
                    label = { Text(tr("Серійний номер (SN)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setPrompt(tr("Наведіть на штрих-код / QR серійного номера станції"))
                                    .setBeepEnabled(true)
                                    .setOrientationLocked(false),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) { FitText(tr("📷 Сканувати")) }
                    OutlinedButton(
                        onClick = { showHelp = !showHelp },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        FitText(if (showHelp) tr("Сховати") else tr("📖 Інструкція"))
                    }
                }
                if (showHelp) {
                    Spacer(Modifier.height(8.dp))
                    AddStationHelp()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(tr("Примітка (клієнт, телефон…)")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { error = onAdd(sn, note) }) { Text(tr("Додати")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Скасувати")) } },
    )
}

@Composable
private fun AddStationHelp() {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text(tr("Як дізнатися серійний номер"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            HelpStep("1", tr("Спочатку додайте станцію в офіційний застосунок EcoFlow — тим самим акаунтом, що й тут."))
            HelpStep("2", tr("У EcoFlow відкрийте станцію → Налаштування → Технічні характеристики."))
            HelpStep("3", tr("Натисніть і утримуйте серійний номер угорі, поки він не виділиться і не з'явиться «Копіювати» — скопіюйте."))
            HelpStep("4", tr("Поверніться сюди і вставте SN у поле вище. Або просто відскануйте штрих-код чи QR на самій станції кнопкою «Сканувати»."))
        }
    }
}

@Composable
private fun HelpStep(n: String, text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$n. ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Зберігаються українською, щоб не губились при зміні мови; показуються через переклад. */
private val PRESET_TAGS = listOf("BMS", tk("АКБ"), tk("Інвертер"), tk("Плата"), tk("Дисплей"), tk("Зарядка"))

@Composable
private fun NoteDialog(
    initialNote: String,
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initialTags) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Примітка й теги")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(tr("Клієнт, телефон, скарга…")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(tr("Теги (що саме):"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                PRESET_TAGS.chunked(3).forEach { rowTags ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { t ->
                            if (selected.contains(t)) {
                                Button(onClick = { selected.remove(t) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    FitText("#" + tr(t), style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(onClick = { selected.add(t) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    FitText("#" + tr(t), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note, selected.toList()) }) { Text(tr("Зберегти")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Скасувати")) } },
    )
}

fun shareText(context: Context, text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, tr("Поділитися")))
}
