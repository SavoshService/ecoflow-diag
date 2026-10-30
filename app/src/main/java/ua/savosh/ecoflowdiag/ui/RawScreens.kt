package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    val all = vm.rawParams
    val rows = remember(all, filter) {
        val f = filter.trim()
        all.entries.filter { f.isEmpty() || it.key.contains(f, ignoreCase = true) }
            .map { it.key to valueText(it.value) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = {
                    Column {
                        Text("Сирі дані", fontWeight = FontWeight.Bold)
                        Text("${all.size} параметрів", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { shareText(context, vm.rawJson(), "EcoFlow ${vm.diagSn} — сирі дані") }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поділитися JSON")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Пошук: cycles, soh, cellVol, bms_slave…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(rows, key = { it.first }) { (k, v) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(k, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(v, fontFamily = Mono, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun valueText(v: Any?): String = when (v) {
    null -> "null"
    is List<*> -> "[" + v.joinToString(", ") { valueText(it) } + "]"
    is Map<*, *> -> "{" + v.entries.joinToString(", ") { "${it.key}: ${valueText(it.value)}" } + "}"
    is Double -> if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    else -> v.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    LaunchedEffect(vm.log.size) {
        if (vm.log.isNotEmpty()) state.scrollToItem(vm.log.size - 1)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = { Text("Журнал підключення", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { shareText(context, vm.logText(), "Power Station Diag — журнал") }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поділитися журналом")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(vm.log) { line ->
                Text(
                    "${line.time}  ${line.text}",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
