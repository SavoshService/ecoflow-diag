package ua.savosh.ecoflowdiag.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ua.savosh.ecoflowdiag.core.Crm
import ua.savosh.ecoflowdiag.core.Order
import ua.savosh.ecoflowdiag.core.OrderChannel
import ua.savosh.ecoflowdiag.core.OrderStatus
import ua.savosh.ecoflowdiag.core.PartItem

private fun statusColor(key: String): Color = when (key) {
    "green" -> Color(0xFF16A34A)
    "yellow" -> Color(0xFFD97706)
    else -> Color(0xFFDC2626)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var formOrderId by remember { mutableStateOf<Long?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var partFormId by remember { mutableStateOf<Long?>(null) }
    var partFormOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    if (formOpen) {
        OrderForm(vm, formOrderId, onClose = { formOpen = false })
        return
    }
    if (partFormOpen) {
        PartForm(vm, partFormId, onClose = { partFormOpen = false })
        return
    }
    if (settingsOpen) {
        CrmSettingsScreen(vm, onClose = { settingsOpen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("🧰 Майстерня", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = { formOrderId = null; formOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Нове замовлення") },
                )
                1 -> ExtendedFloatingActionButton(
                    onClick = { partFormId = null; partFormOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Нова деталь") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Замовлення") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Склад") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Бухгалтерія") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Корзина") })
            }
            when (tab) {
                0 -> OrdersTab(vm, onEdit = { id -> formOrderId = id; formOpen = true })
                1 -> PartsTab(vm, onEdit = { id -> partFormId = id; partFormOpen = true })
                2 -> AccountingTab(vm)
                else -> TrashTab(vm)
            }
        }
    }
}

// ============================= Вкладка: Замовлення =============================

@Composable
private fun OrdersTab(vm: AppViewModel, onEdit: (Long) -> Unit) {
    val list = vm.activeOrders()
    if (list.isEmpty()) {
        EmptyHint(
            "Поки що немає замовлень",
            "Натисніть «Нове замовлення», щоб прийняти станцію в ремонт. " +
                "Клієнт, пристрій, статус і суми зберігаються лише у вашому телефоні.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.id }) { o ->
            OrderCard(o, onEdit = { onEdit(o.id) }, onStatus = { vm.setOrderStatus(o.id, it) }, onTrash = { vm.trashOrder(o.id) })
        }
    }
}

@Composable
private fun OrderCard(o: Order, onEdit: () -> Unit, onStatus: (String) -> Unit, onTrash: () -> Unit) {
    var confirmTrash by remember { mutableStateOf(false) }
    val st = OrderStatus.of(o.status)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(o.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    Crm.orderNo(o.id),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(st.emoji + " " + st.label, style = MaterialTheme.typography.labelMedium, color = statusColor(o.status))
                Spacer(Modifier.weight(1f))
                Text(OrderChannel.of(o.channel).tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(o.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val device = listOf(o.deviceType, o.model).filter { it.isNotBlank() }.joinToString(" · ")
            if (device.isNotBlank()) {
                Text(device, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.phone.isNotBlank()) {
                Text(o.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(o.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
            }
            if (o.totalCost > 0 || o.partsCost > 0) {
                Spacer(Modifier.height(8.dp))
                Row {
                    MoneyPill("Запчастини", Crm.money(o.partsCost), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    MoneyPill("Оплата", Crm.money(o.totalCost), MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    MoneyPill("Прибуток", Crm.money(Crm.orderProfit(o)), statusColor("green"))
                }
            }
            Spacer(Modifier.height(10.dp))
            // Швидка зміна статусу
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in OrderStatus.entries) {
                    StatusChip(s, selected = o.status == s.key, onClick = { onStatus(s.key) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = onEdit) { Text("✎ Редагувати") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmTrash = true }) {
                    Text("🗑 У корзину", color = statusColor("red"))
                }
            }
        }
    }
    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("У корзину?") },
            text = { Text("Замовлення ${Crm.orderNo(o.id)} переміститься в корзину. Звідти його можна відновити.") },
            confirmButton = { TextButton(onClick = { confirmTrash = false; onTrash() }) { Text("У корзину") } },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun StatusChip(s: OrderStatus, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = statusColor(s.key)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            s.emoji,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) c else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StatusDot(key: String) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(key)))
}

@Composable
private fun MoneyPill(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ============================= Вкладка: Бухгалтерія =============================

@Composable
private fun AccountingTab(vm: AppViewModel) {
    var period by rememberSaveable { mutableStateOf("all") }
    val (from, to) = Crm.periodRange(period)
    val stats = Crm.accounting(vm.orders.toList(), from, to)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PeriodChip("Увесь час", period == "all", { period = "all" }, Modifier.weight(1f))
                PeriodChip("Цей місяць", period == "month", { period = "month" }, Modifier.weight(1f))
                PeriodChip("Мин. місяць", period == "prevMonth", { period = "prevMonth" }, Modifier.weight(1f))
            }
        }
        if (!vm.simpleAccounting) {
            item {
                StatCard("🔧 Витрачено на запчастини", Crm.money(stats.parts), MaterialTheme.colorScheme.onSurfaceVariant,
                    "Рахується окремо — це ваші витрати на деталі для готових замовлень.")
            }
            item {
                StatCard("💵 Оборот (брудний дохід)", Crm.money(stats.revenue), MaterialTheme.colorScheme.onSurface,
                    "Сума всіх оплат за готові (🟢) замовлення за період.")
            }
        }
        item {
            val pct = vm.reservePercent
            StatCard("✅ Чистий прибуток", Crm.money(stats.net), statusColor("green"),
                if (pct > 0) "Оборот − запчастини (до відрахування ${fmtNum(pct)}%)."
                else "Оборот − запчастини. Без жодних відрахувань.")
        }
        if (vm.reservePercent > 0) {
            item {
                StatCard("📉 Після відрахування ${fmtNum(vm.reservePercent)}%",
                    Crm.money(Crm.afterReserve(stats.net, vm.reservePercent)), MaterialTheme.colorScheme.primary,
                    "Ваш власний відсоток (резерв / податок). Змінюється в налаштуваннях ⚙.")
            }
        }
        item {
            Text(
                "Готових замовлень за період: ${stats.count}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (stats.orders.isNotEmpty()) {
            item {
                Text("Деталізація", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            items(stats.orders.sortedByDescending { it.completedAt ?: it.updatedAt }, key = { it.id }) { o ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Crm.orderNo(o.id)} · ${o.clientName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val device = listOf(o.deviceType, o.model).filter { it.isNotBlank() }.joinToString(" · ")
                            if (device.isNotBlank()) Text(device, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Оплата ${Crm.money(o.totalCost)} · запч. ${Crm.money(o.partsCost)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Mono)
                        }
                        Text("+${Crm.money(Crm.orderProfit(o))}", style = MaterialTheme.typography.titleSmall,
                            fontFamily = Mono, color = statusColor("green"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) c else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatCard(title: String, value: String, valueColor: Color, hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = Mono, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================= Вкладка: Корзина =============================

@Composable
private fun TrashTab(vm: AppViewModel) {
    val list = vm.trashedOrders()
    var confirmEmpty by remember { mutableStateOf(false) }
    if (list.isEmpty()) {
        EmptyHint("Корзина порожня", "Видалені замовлення зберігаються тут, доки ви їх не відновите або не видалите остаточно.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = { confirmEmpty = true }, modifier = Modifier.fillMaxWidth()) {
                Text("🗑 Очистити корзину (${list.size})", color = statusColor("red"))
            }
        }
        items(list, key = { it.id }) { o ->
            var confirmDel by remember { mutableStateOf(false) }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${Crm.orderNo(o.id)} · ${o.clientName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val device = listOf(o.deviceType, o.model).filter { it.isNotBlank() }.joinToString(" · ")
                    if (device.isNotBlank()) Text(device, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = { vm.restoreOrder(o.id) }) { Text("↩ Відновити") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirmDel = true }) { Text("Видалити назавжди", color = statusColor("red")) }
                    }
                }
            }
            if (confirmDel) {
                AlertDialog(
                    onDismissRequest = { confirmDel = false },
                    title = { Text("Видалити назавжди?") },
                    text = { Text("Замовлення ${Crm.orderNo(o.id)} буде стерто безповоротно.") },
                    confirmButton = { TextButton(onClick = { confirmDel = false; vm.deleteOrderForever(o.id) }) { Text("Видалити") } },
                    dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Скасувати") } },
                )
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Очистити корзину?") },
            text = { Text("Усі ${list.size} замовлень у корзині буде стерто безповоротно.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; vm.emptyTrash() }) { Text("Очистити") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Скасувати") } },
        )
    }
}

// ============================= Форма замовлення =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderForm(vm: AppViewModel, orderId: Long?, onClose: () -> Unit) {
    val existing = vm.orderById(orderId)
    var deviceType by rememberSaveable { mutableStateOf(existing?.deviceType ?: "") }
    var model by rememberSaveable { mutableStateOf(existing?.model ?: "") }
    var clientName by rememberSaveable { mutableStateOf(existing?.clientName?.takeIf { it != "Невідомо" } ?: "") }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var channel by rememberSaveable { mutableStateOf(existing?.channel ?: "call") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: "red") }
    var partsCost by rememberSaveable { mutableStateOf(existing?.partsCost?.let { if (it == 0.0) "" else fmtNum(it) } ?: "") }
    var totalCost by rememberSaveable { mutableStateOf(existing?.totalCost?.let { if (it == 0.0) "" else fmtNum(it) } ?: "") }

    val parts = parseNum(partsCost)
    val total = parseNum(totalCost)
    val profit = total - parts

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text(if (orderId == null) "Нове замовлення" else "Замовлення ${Crm.orderNo(orderId)}", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(clientName, { clientName = it }, Modifier.fillMaxWidth(),
                    label = { Text("Ім'я клієнта") }, singleLine = true)
            }
            item {
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(),
                    label = { Text("Телефон") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            item {
                Text("Канал звернення", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (ch in OrderChannel.entries) {
                        PeriodChip(ch.label, channel == ch.key, { channel = ch.key }, Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(deviceType, { deviceType = it }, Modifier.weight(1f),
                        label = { Text("Тип (АКБ, станція…)") }, singleLine = true)
                    OutlinedTextField(model, { model = it }, Modifier.weight(1f),
                        label = { Text("Модель") }, singleLine = true)
                }
            }
            item {
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
                    label = { Text("Опис несправності / робіт") }, maxLines = 5)
            }
            item {
                Text("Статус", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in OrderStatus.entries) {
                        PeriodChip(s.emoji + " " + s.label, status == s.key, { status = s.key }, Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(partsCost, { partsCost = it }, Modifier.weight(1f),
                        label = { Text("Запчастини, ₴") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(totalCost, { totalCost = it }, Modifier.weight(1f),
                        label = { Text("Оплата клієнта, ₴") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Чистий прибуток (оплата − запчастини)", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Crm.money(profit), style = MaterialTheme.typography.titleMedium, fontFamily = Mono,
                            fontWeight = FontWeight.Bold, color = statusColor("green"))
                    }
                }
            }
            item {
                ExtendedFloatingActionButton(
                    onClick = {
                        vm.saveOrder(orderId, deviceType, model, clientName, phone, channel, description, status, parts, total)
                        onClose()
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (orderId == null) "Зберегти замовлення" else "Зберегти зміни") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "📷 Фото пристрою додамо в наступному оновленні.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ============================= Вкладка: Склад деталей =============================

@Composable
private fun PartsTab(vm: AppViewModel, onEdit: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = vm.partsSorted(query)
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 4.dp),
            label = { Text("🔍 Пошук: назва, ящик, код") }, singleLine = true,
        )
        if (vm.parts.isEmpty()) {
            EmptyHint(
                "На складі порожньо",
                "Натисніть «Нова деталь», щоб вести перелік усіх деталей майстерні: кількість, № ящика/полиці, QR або штрих-код.",
            )
            return
        }
        if (list.isEmpty()) {
            EmptyHint("Нічого не знайдено", "Спробуйте інший запит.")
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { p ->
                PartCard(p, onEdit = { onEdit(p.id) }, onPlus = { vm.bumpPartQty(p.id, 1) }, onMinus = { vm.bumpPartQty(p.id, -1) })
            }
        }
    }
}

@Composable
private fun PartCard(p: PartItem, onEdit: () -> Unit, onPlus: () -> Unit, onMinus: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                // Лічильник кількості
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                Text("${p.quantity}", Modifier.padding(horizontal = 12.dp), fontFamily = Mono,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (p.location.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("📦 ${p.location}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.code.isNotBlank()) {
                Text("🔖 ${p.code}", style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
            }
            if (p.price > 0) {
                Spacer(Modifier.height(4.dp))
                Text("≈ ${Crm.money(p.price)}", style = MaterialTheme.typography.labelMedium, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartForm(vm: AppViewModel, partId: Long?, onClose: () -> Unit) {
    val existing = vm.partById(partId)
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var qty by rememberSaveable { mutableStateOf(existing?.quantity?.toString() ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var code by rememberSaveable { mutableStateOf(existing?.code ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var price by rememberSaveable { mutableStateOf(existing?.price?.let { if (it == 0.0) "" else fmtNum(it) } ?: "") }
    var confirmDel by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { code = it.trim() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = { Text(if (partId == null) "Нова деталь" else "Деталь", fontWeight = FontWeight.Bold) },
                actions = {
                    if (partId != null) {
                        IconButton(onClick = { confirmDel = true }) { Text("🗑", style = MaterialTheme.typography.titleMedium) }
                    }
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
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text("Назва деталі") }, singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(qty, { qty = it.filter { c -> c.isDigit() } }, Modifier.weight(1f),
                        label = { Text("Кількість") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(location, { location = it }, Modifier.weight(1f),
                        label = { Text("№ ящика / полиці") }, singleLine = true)
                }
            }
            item {
                OutlinedTextField(
                    code, { code = it }, Modifier.fillMaxWidth(),
                    label = { Text("QR / штрих-код") }, singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            scanLauncher.launch(
                                ScanOptions()
                                    .setPrompt("Наведіть на QR / штрих-код деталі або ящика")
                                    .setBeepEnabled(true)
                                    .setOrientationLocked(false),
                            )
                        }) { Text("📷") }
                    },
                )
            }
            item {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(),
                    label = { Text("Примітка (аналоги, донор, де купити…)") }, maxLines = 5)
            }
            item {
                OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(),
                    label = { Text("Орієнтовна ціна, ₴ (необовʼязково)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            item {
                ExtendedFloatingActionButton(
                    onClick = {
                        vm.savePart(partId, name, qty.toIntOrNull() ?: 0, location, code, note, parseNum(price))
                        onClose()
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (partId == null) "Додати на склад" else "Зберегти") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmDel && partId != null) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("Видалити деталь?") },
            text = { Text("«${existing?.name}» буде стерто зі складу.") },
            confirmButton = { TextButton(onClick = { confirmDel = false; vm.deletePart(partId); onClose() }) { Text("Видалити") } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Скасувати") } },
        )
    }
}

// ============================= Налаштування бухгалтерії =============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrmSettingsScreen(vm: AppViewModel, onClose: () -> Unit) {
    var pct by rememberSaveable { mutableStateOf(if (vm.reservePercent == 0.0) "" else fmtNum(vm.reservePercent)) }
    var simple by remember { mutableStateOf(vm.simpleAccounting) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        vm.updateReservePercent(parseNum(pct))
                        vm.updateSimpleAccounting(simple)
                        onClose()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = { Text("⚙ Налаштування", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Бухгалтерія", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Мій відсоток відрахування", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Резерв або податок, який віднімається від чистого прибутку. 0 — вимкнено.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(pct, { pct = it }, Modifier.fillMaxWidth(),
                            label = { Text("Відсоток, %") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().clickable { simple = !simple }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Спрощена бухгалтерія", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Ховати «Запчастини» й «Оборот», показувати лише чистий прибуток.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = simple, onCheckedChange = { simple = it })
                    }
                }
            }
            item {
                Text("Зміни зберігаються автоматично, щойно вийдете назад.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun parseNum(s: String): Double =
    s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

private fun fmtNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
