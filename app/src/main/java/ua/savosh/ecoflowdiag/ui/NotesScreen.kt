package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.savosh.ecoflowdiag.core.ChecklistItem
import ua.savosh.ecoflowdiag.core.Note
import ua.savosh.ecoflowdiag.core.Notes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(vm: AppViewModel) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    if (editorOpen) {
        NoteEditor(vm, editingId, onClose = { editorOpen = false })
        return
    }

    val list = vm.notesSorted()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("📝 Замітки", fontWeight = FontWeight.Bold) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingId = null; editorOpen = true },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                text = { Text("Нова замітка") },
            )
        },
    ) { pad ->
        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📝", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Поки що немає заміток", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Створюйте прості замітки, чеклисти з галочками або нотатки до конкретної станції. " +
                        "Можна додати нагадування, щоб не забути.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { n ->
                NoteCard(n, vm, onOpen = { editingId = n.id; editorOpen = true })
            }
        }
    }
}

@Composable
private fun NoteCard(n: Note, vm: AppViewModel, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Notes.kindEmoji(n.kind) + " ", style = MaterialTheme.typography.titleMedium)
            Text(n.title.ifBlank { Notes.kindLabel(n.kind) }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val prog = Notes.progress(n)
            if (prog.isNotBlank()) {
                Text(prog, style = MaterialTheme.typography.labelMedium, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (n.kind == Notes.STATION && n.stationSn.isNotBlank()) {
            Text("🔋 ${n.stationSn}", style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (n.kind) {
            Notes.CHECKLIST -> {
                Spacer(Modifier.height(6.dp))
                n.items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                        Checkbox(checked = item.done, onCheckedChange = { vm.toggleNoteItem(n.id, i) })
                        Text(item.text, style = MaterialTheme.typography.bodyMedium,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            else -> {
                if (n.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 4)
                }
            }
        }
        n.reminderAt?.let {
            Spacer(Modifier.height(6.dp))
            Text("🔔 ${fmtTime(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ============================= Редактор замітки =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(vm: AppViewModel, noteId: Long?, onClose: () -> Unit) {
    val existing = vm.noteById(noteId)
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: Notes.PLAIN) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(existing?.body ?: "") }
    var stationSn by rememberSaveable { mutableStateOf(existing?.stationSn ?: "") }
    var reminderAt by rememberSaveable { mutableStateOf(existing?.reminderAt) }
    val items = remember { mutableStateListOf<ChecklistItem>().apply { existing?.items?.let { addAll(it) } } }
    var confirmDel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text(if (noteId == null) "Нова замітка" else "Замітка", fontWeight = FontWeight.Bold) },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDel = true }) { Text("🗑", style = MaterialTheme.typography.titleMedium) }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Тип", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChip("📝 Замітка", kind == Notes.PLAIN, { kind = Notes.PLAIN }, Modifier.weight(1f))
                    KindChip("☑ Чеклист", kind == Notes.CHECKLIST, { kind = Notes.CHECKLIST }, Modifier.weight(1f))
                    KindChip("🔋 Станція", kind == Notes.STATION, { kind = Notes.STATION }, Modifier.weight(1f))
                }
            }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Заголовок") }, singleLine = true) }

            if (kind == Notes.STATION) {
                item {
                    Text("Станція", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vm.stations.isEmpty()) {
                        Text("Спершу додайте станцію у списку станцій.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(vm.stations, key = { it.sn }) { st ->
                    val sel = stationSn == st.sn
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { stationSn = st.sn }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (sel) "🔘 " else "⚪ ", style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.weight(1f)) {
                            Text(st.sn, fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (st.note.isNotBlank()) Text(st.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (kind == Notes.CHECKLIST) {
                item {
                    Text("Пункти", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(items.size) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = items[i].done, onCheckedChange = { items[i] = items[i].copy(done = it) })
                        OutlinedTextField(
                            items[i].text, { items[i] = items[i].copy(text = it) },
                            Modifier.weight(1f), singleLine = true, placeholder = { Text("Пункт…") },
                        )
                        IconButton(onClick = { items.removeAt(i) }) { Text("✕") }
                    }
                }
                item {
                    TextButton(onClick = { items.add(ChecklistItem("")) }) { Text("＋ Додати пункт") }
                }
            } else {
                item {
                    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(180.dp), label = { Text("Текст") })
                }
            }

            item {
                Text("Нагадування", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChip("Немає", reminderAt == null, { reminderAt = null }, Modifier.weight(1f))
                    KindChip("+1 год", false, { reminderAt = presetTime(1, 0) }, Modifier.weight(1f))
                    KindChip("+3 год", false, { reminderAt = presetTime(3, 0) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindChip("Завтра 9:00", false, { reminderAt = tomorrow9() }, Modifier.weight(1f))
                    KindChip("+3 дні", false, { reminderAt = presetTime(0, 3) }, Modifier.weight(1f))
                }
                reminderAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("🔔 Нагадаю: ${fmtTime(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                ExtendedFloatingActionButton(
                    onClick = {
                        val cleanItems = items.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotBlank() }
                        vm.saveNote(noteId, kind, title, body, cleanItems, stationSn, reminderAt)
                        onClose()
                    },
                    icon = { Text("✔", style = MaterialTheme.typography.titleMedium) },
                    text = { Text(if (noteId == null) "Зберегти" else "Зберегти зміни") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmDel && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("Видалити замітку?") },
            text = { Text("«${existing.title.ifBlank { "Без назви" }}» буде стерто.") },
            confirmButton = { TextButton(onClick = { confirmDel = false; vm.deleteNote(existing.id); onClose() }) { Text("Видалити") } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) c else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private fun presetTime(hours: Int, days: Int): Long {
    val c = Calendar.getInstance()
    if (hours > 0) c.add(Calendar.HOUR_OF_DAY, hours)
    if (days > 0) c.add(Calendar.DAY_OF_MONTH, days)
    return c.timeInMillis
}

private fun tomorrow9(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun fmtTime(ms: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(java.util.Date(ms))
