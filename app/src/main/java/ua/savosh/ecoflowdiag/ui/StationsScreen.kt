package ua.savosh.ecoflowdiag.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.core.Level
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
    var editNoteFor by remember { mutableStateOf<StationEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<StationEntry?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showCoffee by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Станції", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            vm.auth?.userName ?: vm.email.ifBlank { "не виконано вхід" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (vm.loggedIn) {
                        IconButton(
                            onClick = { if (vm.refreshAllBusy) vm.stopRefreshAll() else vm.refreshAllStations() },
                            enabled = vm.stations.isNotEmpty(),
                        ) {
                            if (vm.refreshAllBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Оновити всі станції")
                            }
                        }
                        IconButton(onClick = { confirmLogout = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Вийти з акаунта")
                        }
                    } else {
                        IconButton(onClick = { vm.goToLogin() }) {
                            Text("🔑", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Додати станцію") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Видимі плитки швидких дій — усе на виду, без прихованого меню «⋮».
            QuickActions(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                onDonate = { showCoffee = true },
                onFeedback = { vm.openFeedback() },
                onParts = { vm.openParts() },
                onFlash = { vm.openFlash() },
            )
            // Вхід у «Майстерню» (CRM замовлень) — на видноті, окремою широкою карткою.
            WorkshopEntry(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                activeCount = vm.activeOrders().size,
                inWorkCount = vm.activeOrders().count { it.status != "green" },
                onOpen = { vm.openCrm() },
            )
            // Банер оновлення — завжди видимий (навіть коли ще немає станцій).
            vm.availableUpdate?.let { upd ->
                if (!vm.updateDismissed) {
                    UpdateBanner(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
            if (vm.needsBackup && !vm.backupBannerHidden) {
                BackupReminder(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onSetup = { vm.openBackup() },
                    onHide = { vm.hideBackupBanner() },
                )
            }
            if (vm.stations.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!vm.loggedIn) {
                        Text("Вхід в EcoFlow — для діагностики", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Майстерня, деталі й замітки працюють без входу. Щоб бачити й діагностувати свої станції — " +
                                "увійдіть в акаунт EcoFlow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.goToLogin() }) { Text("Увійти в EcoFlow") }
                        Spacer(Modifier.height(20.dp))
                        DemoCard(onOpen = { vm.openDemo() })
                    } else {
                    Text("Поки що немає станцій", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Додайте станцію за серійним номером. Він є на наклейці станції та в застосунку EcoFlow у налаштуваннях пристрою. " +
                            "Станцію спершу треба додати в цей акаунт EcoFlow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    DemoCard(onOpen = { vm.openDemo() })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                if (vm.refreshAllBusy || vm.refreshAllStatus.isNotBlank()) {
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
                items(vm.stations, key = { it.sn }) { st ->
                    StationCard(
                        st = st,
                        onOpen = { vm.openDiag(st.sn) },
                        onEditNote = { editNoteFor = st },
                        onShare = { tech ->
                            vm.savedReport(st.sn, tech)?.let { shareText(context, it, "${if (tech) "Технічний звіт" else "Звіт"} ${st.sn}") }
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
        NoteDialog(st.note, onDismiss = { editNoteFor = null }) {
            vm.updateNote(st.sn, it)
            editNoteFor = null
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Вийти з акаунта?") },
            text = { Text("Ви вийдете з акаунта EcoFlow. Збережений пароль буде стерто — для наступного входу введіть його знову. Станції, звіти й примітки залишаться.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Вийти") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Скасувати") } },
        )
    }
    confirmDelete?.let { st ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Видалити станцію?") },
            text = { Text("${st.sn}${if (st.note.isNotBlank()) " — ${st.note}" else ""}\nЗбережений звіт теж буде видалено.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStation(st.sn)
                    confirmDelete = null
                }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Скасувати") } },
        )
    }
}

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(st.model ?: "Модель визначиться після підключення", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(st.sn, fontFamily = Mono, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (st.note.isNotBlank()) {
                    Text(st.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val lv = levelFromName(st.lastLevel)
                        LevelChip(lv, st.lastSoh?.let { "${lv.title} · SOH ${it.toInt()}%" } ?: lv.title)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(st.lastCheckMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text("Ще не перевірялась", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Меню") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Повна інформація про станцію") }, onClick = { menu = false; onInfo() })
                    DropdownMenuItem(text = { Text("Змінити примітку") }, onClick = { menu = false; onEditNote() })
                    if (hasReport) {
                        DropdownMenuItem(text = { Text("Звіт клієнту (останній)") }, onClick = { menu = false; onShare(false) })
                        DropdownMenuItem(text = { Text("Технічний звіт (останній)") }, onClick = { menu = false; onShare(true) })
                    }
                    DropdownMenuItem(text = { Text("Видалити") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun OnlineDot(online: Boolean?) {
    val (txt, color) = when (online) {
        true -> "● онлайн" to levelColor(Level.GOOD)
        false -> "● офлайн" to levelColor(Level.BAD)
        null -> "● невідомо" to levelColor(Level.UNKNOWN)
    }
    Text(txt, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
}

/** Демо-станція: пульсуюча картка «спробувати програму на несправжніх даних». */
@Composable
private fun DemoCard(onOpen: () -> Unit, onDismiss: (() -> Unit)? = null) {
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { pulse += 0.06f; kotlinx.coroutines.delay(33) }
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
                Text("🧪", fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Демо станція", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Торкніться, щоб подивитись можливості: здоров'я, цикли, коди й пояснення, монітор заряду/розряду — на несправжніх даних.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Сховати демо", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(
    modifier: Modifier = Modifier,
    onDonate: () -> Unit,
    onFeedback: () -> Unit,
    onParts: () -> Unit,
    onFlash: () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (donateEnabled()) {
            QuickTile("☕", "КАВА", Modifier.weight(1f), onDonate)
        }
        QuickTile("✈", "Зв'язок", Modifier.weight(1f), onFeedback)
        QuickTile(label = "Деталі", modifier = Modifier.weight(1f), onClick = onParts) { SpinningChip(sizeDp = 30) }
        QuickTile(label = "Прошивка", modifier = Modifier.weight(1f), onClick = onFlash) { BinaryRain(cols = 5, rows = 3, fontSizeSp = 8) }
    }
}

/** Широка картка-вхід у Майстерню: замовлення на ремонт і бухгалтерія. */
@Composable
private fun WorkshopEntry(
    modifier: Modifier = Modifier,
    activeCount: Int,
    inWorkCount: Int,
    onOpen: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧰", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Майстерня",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    if (activeCount == 0) "Замовлення на ремонт і бухгалтерія"
                    else "Активних: $activeCount · у роботі: $inWorkCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                "⚠️ Увімкни резервну копію",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Якщо видалити застосунок — замовлення, склад і замітки пропадуть. Збережи копію на Google Диск " +
                    "(безкоштовно, у твоєму власному хмарному сховищі) — і далі вона оновлюватиметься сама.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSetup) { Text("Налаштувати копію") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onHide) { Text("Пізніше") }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickTile(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    QuickTile(label = label, modifier = modifier, onClick = onClick) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
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
                Text("⬆ Доступна версія $version", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                if (!downloading) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрити") }
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
                    if (progress in 0..100) "Завантаження… $progress%" else "Завантаження…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                if (!error.isNullOrBlank()) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onUpdate) { Text("Оновити") }
                    TextButton(onClick = onBrowser) { Text("У браузері") }
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
        result.contents?.let { sn = it.trim().uppercase(); error = null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Нова станція") },
        text = {
            Column {
                OutlinedTextField(
                    value = sn,
                    onValueChange = { sn = it.uppercase(); error = null },
                    label = { Text("Серійний номер (SN)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setPrompt("Наведіть на штрих-код / QR серійного номера станції")
                                    .setBeepEnabled(true)
                                    .setOrientationLocked(false),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("📷 Сканувати") }
                    OutlinedButton(onClick = { showHelp = !showHelp }, modifier = Modifier.weight(1f)) {
                        Text(if (showHelp) "Сховати" else "📖 Інструкція")
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
                    label = { Text("Примітка (клієнт, телефон…)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { error = onAdd(sn, note) }) { Text("Додати") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
private fun AddStationHelp() {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text("Як дізнатися серійний номер", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            HelpStep("1", "Спочатку додайте станцію в офіційний застосунок EcoFlow — тим самим акаунтом, що й тут.")
            HelpStep("2", "У EcoFlow відкрийте станцію → Налаштування → Технічні характеристики.")
            HelpStep("3", "Натисніть і утримуйте серійний номер угорі, поки він не виділиться і не з'явиться «Копіювати» — скопіюйте.")
            HelpStep("4", "Поверніться сюди і вставте SN у поле вище. Або просто відскануйте штрих-код чи QR на самій станції кнопкою «Сканувати».")
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

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Примітка") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Клієнт, телефон, скарга…") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

fun shareText(context: Context, text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Поділитися"))
}
