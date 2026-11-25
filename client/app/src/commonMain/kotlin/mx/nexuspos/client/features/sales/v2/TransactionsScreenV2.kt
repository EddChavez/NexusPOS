package mx.nexuspos.client.features.sales.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDate
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.client.features.sales.v2.dialogs.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.core.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreenV2(
    viewModel: SalesViewModel,
    onPrint: suspend (SaleResponse) -> Result<Unit>,
    onOpenWhatsApp: (String) -> Result<Unit>,
    onCopyText: (String) -> Result<Unit>
) {
    val sales by viewModel.uiState.collectAsState()
    val permissions = sales.activeCashierPermissions
    var request by remember { mutableStateOf(TransactionHistoryRequest()) }
    var page by remember { mutableStateOf<TransactionHistoryPage?>(null) }
    var selected by remember { mutableStateOf<TransactionRecord?>(null) }
    var sale by remember { mutableStateOf<SaleResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingSale by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var saleError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var saleRefresh by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun changeFilters(next: TransactionHistoryRequest) {
        request = next.copy(offset = 0)
        selected = null
        notice = null
    }

    val datesValid = request.period != TransactionPeriod.CUSTOM || runCatching {
        LocalDate.parse(request.from.orEmpty()) <= LocalDate.parse(request.through.orEmpty())
    }.getOrDefault(false)

    LaunchedEffect(request, refresh, datesValid) {
        listError = null
        if (!datesValid) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        if (request.query.isNotEmpty()) delay(250)
        try {
            viewModel.apiClient.searchTransactions(request).onSuccess { newPage ->
                page = newPage
                selected = selected?.let { old -> newPage.records.firstOrNull { it.id == old.id && it.type == old.type } }
            }.onFailure { listError = it.message ?: "No se pudieron cargar las transacciones." }
        } finally {
            loading = false
        }
    }

    val record = selected
    LaunchedEffect(record?.id, record?.type, saleRefresh) {
        sale = null
        saleError = null
        if (record?.type != TransactionType.SALE) return@LaunchedEffect
        loadingSale = true
        try {
            val result = viewModel.apiClient.getSale(record.id)
            ensureActive()
            result.onSuccess { sale = it }
                .onFailure { saleError = it.message ?: "No se pudo cargar el ticket." }
        } finally {
            loadingSale = false
        }
    }

    val typeChoices = buildList {
        add(null to "Todos")
        add(TransactionType.SALE to "Ventas")
        if ("cash.view" in permissions) {
            add(TransactionType.CASH_IN to "Entradas")
            add(TransactionType.CASH_OUT to "Salidas")
            add(TransactionType.OPENING_FUND to "Fondo de caja")
        }
        if ("customers.statement.view" in permissions || "customers.credit.payment" in permissions) {
            add(TransactionType.CREDIT_PAYMENT to "Abonos de crédito")
        }
    }
    val registerChoices = listOf(null to "Todas las cajas") + page?.registers.orEmpty().map { it.id to it.name }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transacciones", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { refresh++ }, enabled = !loading && datesValid) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar transacciones")
            }
        }
        notice?.let { ShiftActionMessage(it) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxWidth < 740.dp
            val listWidth = minOf(maxWidth * 0.4f, 360.dp)
            val showList = !compact || record == null
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showList) TransactionToolbar(request, typeChoices, registerChoices, ::changeFilters)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showList) {
                        val listModifier = if (compact) Modifier.weight(1f) else Modifier.width(listWidth)
                        OutlinedCard(listModifier.fillMaxHeight(), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Movimientos", style = MaterialTheme.typography.titleMedium)
                                val records = page?.records.orEmpty()
                                when {
                                    !datesValid -> Text("Ingresa un rango de fechas con formato AAAA-MM-DD.")
                                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                    listError != null -> ShiftActionMessage(listError.orEmpty(), isError = true)
                                    records.isEmpty() -> Text("No hay transacciones con estos filtros.")
                                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(records, key = { "${it.type}:${it.id}" }) { item ->
                                            TransactionListRow(item, item.id == record?.id && item.type == record.type) {
                                                selected = item
                                                notice = null
                                            }
                                        }
                                    }
                                }
                                if (datesValid && !loading && listError == null && (request.offset > 0 || page?.nextOffset != null)) {
                                    Row {
                                        TextButton(
                                            onClick = { request = request.copy(offset = (request.offset - 50).coerceAtLeast(0)); selected = null },
                                            enabled = request.offset > 0
                                        ) { Text("Anterior") }
                                        TextButton(
                                            onClick = { page?.nextOffset?.let { request = request.copy(offset = it); selected = null } },
                                            enabled = page?.nextOffset != null
                                        ) { Text("Siguiente") }
                                    }
                                }
                            }
                        }
                    }
                    if (!compact || record != null) {
                        Column(Modifier.weight(if (compact) 1f else 1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (compact) TextButton(onClick = { selected = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Volver a transacciones")
                            }
                            val ticket = sale?.takeIf { it.id == record?.id }
                            when {
                                record == null -> Text("Selecciona una transacción para consultar su detalle.")
                                record.type != TransactionType.SALE -> TransactionMovementDetail(record)
                                loadingSale -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                saleError != null -> {
                                    ShiftActionMessage(saleError.orEmpty(), isError = true)
                                    TextButton(onClick = { saleRefresh++ }) { Text("Reintentar") }
                                }
                                ticket != null -> key(ticket.id) {
                                    TransactionSalePane(
                                        ticket, record.sessionOpen, viewModel, onPrint, onOpenWhatsApp, onCopyText
                                    ) { message, changed ->
                                        notice = message
                                        if (changed) {
                                            refresh++
                                            saleRefresh++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val periodChoices = listOf(
    TransactionPeriod.TODAY to "Hoy",
    TransactionPeriod.YESTERDAY to "Ayer",
    TransactionPeriod.LAST_SEVEN_DAYS to "Últimos 7 días",
    TransactionPeriod.CUSTOM to "Rango de fechas"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionToolbar(
    request: TransactionHistoryRequest,
    types: List<Pair<TransactionType?, String>>,
    registers: List<Pair<String?, String>>,
    change: (TransactionHistoryRequest) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    request.query, { change(request.copy(query = it.take(120))) },
                    label = { Text("Buscar por folio, monto o contenido", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (request.query.isEmpty()) null else {
                        { IconButton(onClick = { change(request.copy(query = "")) }) { Icon(Icons.Default.Clear, "Limpiar búsqueda") } }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).widthIn(min = 250.dp)
                )
                TransactionFilter(request.period, periodChoices, Icons.Default.CalendarToday, "Fecha") {
                    change(request.copy(period = it))
                }
                TransactionFilter(request.registerId, registers, Icons.Default.Computer, "Caja") {
                    change(request.copy(registerId = it))
                }
            }
            if (request.period == TransactionPeriod.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        request.from.orEmpty(), { change(request.copy(from = it.take(10))) },
                        label = { Text("Desde") }, placeholder = { Text("AAAA-MM-DD") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        request.through.orEmpty(), { change(request.copy(through = it.take(10))) },
                        label = { Text("Hasta") }, placeholder = { Text("AAAA-MM-DD") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 2.dp), color = colors.outlineVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                types.forEach { (type, title) ->
                    FilterChip(
                        selected = request.type == type,
                        onClick = { change(request.copy(type = type)) },
                        label = { Text(title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> TransactionFilter(value: T, choices: List<Pair<T, String>>, icon: ImageVector, label: String, select: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }) {
            Icon(icon, label, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(choices.firstOrNull { it.first == value }?.second ?: label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (choice, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = if (choice == value) {
                        { Icon(Icons.Default.Check, contentDescription = "Seleccionado") }
                    } else null,
                    onClick = { open = false; select(choice) }
                )
            }
        }
    }
}

@Composable
private fun TransactionListRow(record: TransactionRecord, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) colors.secondaryContainer else colors.surface),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.title(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(Money(record.amountCents).format(), style = MaterialTheme.typography.titleSmall)
            }
            (record.customerName ?: record.description)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(listOfNotNull(formatShiftHistoryTimestamp(record.createdAt, true), record.registerName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionMovementDetail(record: TransactionRecord) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.title(), style = MaterialTheme.typography.titleLarge)
            Text(Money(record.amountCents).format(), style = MaterialTheme.typography.headlineSmall)
            record.description?.let { Text(it) }
            record.customerName?.let { Text("Cliente · $it") }
            record.operatorName?.let { Text("Operador · $it") }
            record.registerName?.let { Text("Caja · $it") }
            Text(formatShiftHistoryTimestamp(record.createdAt, true))
            val method = when (record.paymentMethod) {
                "CASH" -> "Efectivo"
                "TRANSFER" -> "Transferencia"
                "CARD" -> "Tarjeta"
                else -> null
            }
            method?.let { Text("Método de pago · $it") }
            when (record.status) {
                "REVERSED" -> Text("Movimiento revertido")
                "REVERSAL" -> Text("Reversión de movimiento")
            }
        }
    }
}

private fun TransactionRecord.title(): String = when (type) {
    TransactionType.SALE -> "Venta #$folio"
    TransactionType.CASH_IN -> "Entrada de efectivo"
    TransactionType.CASH_OUT -> "Salida de efectivo"
    TransactionType.OPENING_FUND -> "Fondo de caja"
    TransactionType.CREDIT_PAYMENT -> reference?.let { "Abono $it" } ?: "Abono de crédito"
}
