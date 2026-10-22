package ua.savosh.ecoflowdiag.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.savosh.ecoflowdiag.core.Article

// --- Палітра «старовинного паперу» (однакова в будь-якій темі застосунку) ---
private val Paper = Color(0xFFF2E7CE)
private val PaperDim = Color(0xFFE8DABB)
private val Ink = Color(0xFF2A2018)
private val InkSoft = Color(0xFF5A4C38)
private val Seal = Color(0xFF8B2E22)
private val Serif = FontFamily.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(vm: AppViewModel) {
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    if (editorOpen) {
        ArticleEditor(vm, editingId, onClose = { editorOpen = false })
        return
    }
    val viewing = vm.articleById(viewingId)
    if (viewing != null) {
        ArticleView(
            viewing,
            photoPath = { vm.photoPath(it) },
            onBack = { viewingId = null },
            onEdit = { editingId = viewing.id; editorOpen = true },
        )
        return
    }

    val sections = vm.kbSections()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("📖 Талмуд майстра", fontWeight = FontWeight.Bold) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingId = null; editorOpen = true },
                icon = { Text("✒", style = MaterialTheme.typography.titleMedium) },
                text = { Text("Нова стаття") },
            )
        },
    ) { pad ->
        if (sections.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📖", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Талмуд поки порожній", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Натисніть «Нова стаття», щоб записати знання про деталь: маркування, аналоги, нюанси ремонту — " +
                        "з фото плати. Стаття оформиться як сторінка наукового журналу.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (section in sections) {
                item(key = "sec_$section") {
                    Text(
                        section.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(vm.articlesIn(section), key = { it.id }) { a ->
                    ArticleRow(a, onClick = { viewingId = a.id })
                }
            }
        }
    }
}

@Composable
private fun ArticleRow(a: Article, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.title.ifBlank { "Без назви" }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (a.official) {
                Text("офіц.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (a.photos.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text("📷${a.photos.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val sub = listOf(a.subsection, a.marking).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ============================= Стаття (вигляд «старий журнал») =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleView(
    a: Article,
    photoPath: (String) -> String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text("Стаття", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!a.official) TextButton(onClick = onEdit) { Text("✎ Редагувати") }
                },
            )
        },
    ) { pad ->
        Box(
            Modifier.fillMaxSize().padding(pad).background(Paper)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text(
                    (a.section + if (a.subsection.isNotBlank()) " · " + a.subsection else "").uppercase(),
                    style = MaterialTheme.typography.labelMedium, color = Seal, fontFamily = Serif, letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    a.title.ifBlank { "Без назви" },
                    fontFamily = Serif, fontWeight = FontWeight.Bold, color = Ink,
                    fontSize = 26.sp, lineHeight = 32.sp,
                )
                Spacer(Modifier.height(6.dp))
                if (a.author.isNotBlank()) {
                    Text("автор: ${a.author}", fontFamily = Serif, color = InkSoft, fontSize = 13.sp)
                }
                OrnamentRule()
                if (a.marking.isNotBlank()) FieldLine("Маркування", a.marking)
                if (a.analogs.isNotBlank()) FieldLine("Аналоги", a.analogs)
                if (a.marking.isNotBlank() || a.analogs.isNotBlank()) Spacer(Modifier.height(10.dp))

                if (a.body.isNotBlank()) {
                    Text(a.body, fontFamily = Serif, color = Ink, fontSize = 17.sp, lineHeight = 26.sp)
                }

                a.photos.forEachIndexed { i, name ->
                    Spacer(Modifier.height(16.dp))
                    ArticlePhoto(path = photoPath(name), caption = "Рис. ${i + 1}")
                }

                OrnamentRule()
                Text(
                    "❦  Savosh Service  ❦",
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontFamily = Serif, color = Seal, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", fontFamily = Serif, color = Seal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(value, fontFamily = Serif, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun OrnamentRule() {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(InkSoft.copy(alpha = 0.5f)))
        Spacer(Modifier.height(6.dp))
        Text("✦ ✦ ✦", color = Seal, fontFamily = Serif, fontSize = 12.sp)
    }
}

@Composable
private fun ArticlePhoto(path: String, caption: String) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().background(PaperDim).padding(6.dp)) {
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = caption, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            } else {
                Text("(фото недоступне)", Modifier.padding(20.dp), color = InkSoft, fontFamily = Serif)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(caption, fontFamily = Serif, color = InkSoft, fontSize = 13.sp)
    }
}

// ============================= Редактор статті =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleEditor(vm: AppViewModel, articleId: Long?, onClose: () -> Unit) {
    val existing = vm.articleById(articleId)?.takeIf { !it.official }
    var section by rememberSaveable { mutableStateOf(existing?.section ?: "Загальне") }
    var subsection by rememberSaveable { mutableStateOf(existing?.subsection ?: "") }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var marking by rememberSaveable { mutableStateOf(existing?.marking ?: "") }
    var analogs by rememberSaveable { mutableStateOf(existing?.analogs ?: "") }
    var body by rememberSaveable { mutableStateOf(existing?.body ?: "") }
    var photos by remember { mutableStateOf(existing?.photos ?: emptyList()) }
    var confirmDel by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) photos = photos + vm.importPhotos(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
                title = { Text(if (articleId == null) "Нова стаття" else "Редагування", fontWeight = FontWeight.Bold) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(section, { section = it }, Modifier.weight(1f), label = { Text("Розділ") }, singleLine = true)
                    OutlinedTextField(subsection, { subsection = it }, Modifier.weight(1f), label = { Text("Підрозділ") }, singleLine = true)
                }
            }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Назва статті") }, singleLine = true) }
            item { OutlinedTextField(marking, { marking = it }, Modifier.fillMaxWidth(), label = { Text("Маркування деталей") }, singleLine = true) }
            item { OutlinedTextField(analogs, { analogs = it }, Modifier.fillMaxWidth(), label = { Text("Аналоги") }, singleLine = true) }
            item { OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(220.dp), label = { Text("Текст статті") }) }
            item {
                Column {
                    Text("Фото (${photos.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { name ->
                            PhotoThumb(vm.photoPath(name), onRemove = { photos = photos.filter { it != name } })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { picker.launch("image/*") }) { Text("📷 Додати фото") }
                }
            }
            item {
                ExtendedFloatingActionButton(
                    onClick = {
                        vm.saveArticle(articleId, section, subsection, title, marking, analogs, body, photos)
                        onClose()
                    },
                    icon = { Text("✒", style = MaterialTheme.typography.titleMedium) },
                    text = { Text(if (articleId == null) "Опублікувати у талмуд" else "Зберегти") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmDel && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("Видалити статтю?") },
            text = { Text("«${existing.title}» буде стерто з талмуда разом із фото.") },
            confirmButton = { TextButton(onClick = { confirmDel = false; vm.deleteArticle(existing.id); onClose() }) { Text("Видалити") } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun PhotoThumb(path: String, onRemove: () -> Unit) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
                .background(Color(0xCC000000)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 12.sp) }
    }
}

// ============================= «Що нового» =============================

@Composable
fun WhatsNewDialog(vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissWhatsNew() },
        title = { Text("🆕 Що нового", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (rel in vm.whatsNew) {
                    Text("Версія ${rel.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    for (ch in rel.changes) {
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium)
                            Text(ch, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.dismissWhatsNew() }) { Text("Чудово!") } },
    )
}
