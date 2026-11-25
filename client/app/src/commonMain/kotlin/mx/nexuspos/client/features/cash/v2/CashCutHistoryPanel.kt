package mx.nexuspos.client.features.cash.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.CashCutHistoryAccessDenied
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.features.settings.v2.SettingsChoice
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.core.Money

private enum class CutDetailSection { SALES, MOVEMENTS }

private data class CutFilters(
    val from: String,
    val to: String,
    val register: String = "",
    val cashier: String = ""
) {
    fun dateError(): String? {
        val start = from.trim().takeIf(String::isNotEmpty)?.let { parse(it) ?: return "Usa AAAA-MM-DD en «Desde»." }
        val end = to.trim().takeIf(String::isNotEmpty)?.let { parse(it) ?: return "Usa AAAA-MM-DD en «Hasta»." }
        if (start != null && end != null && start > end) return "La fecha inicial debe ser igual o anterior a la final."
        return null
    }

    private fun parse(text: String): LocalDate? =
        text.takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashCutHistoryPanel(api: ApiClient, zoneId: String, today: LocalDate) {
    val scope = rememberCoroutineScope()
    val defaults = remember(today) { CutFilters(today.minus(29, DateTimeUnit.DAY).toString(), today.toString()) }
    var filters by remember { mutableStateOf(defaults) }
    var applied by remember { mutableStateOf(filters) }
    var page by remember { mutableStateOf<CashCutHistoryPage?>(null) }
    var trail by remember { mutableStateOf(listOf<CashCutCursor?>(null)) }
    var detail by remember { mutableStateOf<CashCutHistoryDetail?>(null) }
    var section by remember { mutableStateOf<CutDetailSection?>(null) }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun lockHistory() {
        page = null
        detail = null
        section = null
        pin = ""
        filters = defaults
        applied = defaults
        trail = listOf(null)
        error = null
    }

    fun showError(failure: Throwable, fallback: String) {
        if (failure is CashCutHistoryAccessDenied) lockHistory()
        error = failure.message ?: fallback
    }

    fun search(target: CutFilters, destination: List<CashCutCursor?>) {
        if (busy || pin.length != 4 || target.dateError() != null) return
        val request = CashCutHistoryRequest(
            pin = pin,
            timeZone = zoneId,
            fromDate = target.from.trim().ifBlank { null },
            toDate = target.to.trim().ifBlank { null },
            registerId = target.register.ifBlank { null },
            cashierId = target.cashier.ifBlank { null },
            before = destination.last()
        )
        busy = true
        error = null
        scope.launch {
            api.searchCashCutHistory(request).onSuccess {
                applied = target
                trail = destination
                page = it
            }.onFailure { showError(it, "No se pudo consultar el historial.") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FeedbackMessageHost(errorMessage = error, successMessage = null, onDismissError = { error = null }, onDismissSuccess = {})
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val activePage = page
        when {
            activePage == null -> {
                Text("Consultar cortes cerrados", style = MaterialTheme.typography.titleLarge)
                Text("Ingresa el PIN de un encargado para abrir el historial.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 6 && value.all { it in '0'..'9' }) pin = value },
                    label = { Text("PIN de encargado") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.width(220.dp)
                )
                Button(onClick = { search(filters, listOf(null)) }, enabled = pin.length in setOf(4, 6) && !busy) {
                    Text("Abrir historial")
                }
            }
            section == CutDetailSection.SALES && detail != null -> ClosedShiftSalesPanel(
                cut = checkNotNull(detail).cut,
                api = api,
                pin = pin,
                onBackToCut = { section = null },
                onAccessDenied = { showError(it, "Se cerró el acceso al historial.") }
            )
            section == CutDetailSection.MOVEMENTS && detail != null -> {
                val cut = checkNotNull(detail).cut
                CashMovementAuditPanel(
                    api, cut.sessionId, cut.registerName, cutPin = pin,
                    onBackToCut = { section = null },
                    onAccessDenied = { showError(it, "Se cerró el acceso al historial.") }
                )
            }
            detail != null -> CashCutDetail(
                checkNotNull(detail),
                onBack = {
                    detail = null
                    error = null
                },
                onOpenSales = { section = CutDetailSection.SALES },
                onOpenMovements = { section = CutDetailSection.MOVEMENTS }
            )
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cortes cerrados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { lockHistory() }, enabled = !busy) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bloquear historial")
                    }
                }
                Text("Filtra por fecha de cierre (AAAA-MM-DD). Deja las fechas vacías para consultar todos los cortes.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filters.from, onValueChange = { filters = filters.copy(from = it) },
                        label = { Text("Desde") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = filters.to, onValueChange = { filters = filters.copy(to = it) },
                        label = { Text("Hasta") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsChoice(
                        "Caja", filters.register, listOf("" to "Todas las cajas") + activePage.registers.map { it.id to it.label },
                        { filters = filters.copy(register = it) }, modifier = Modifier.weight(1f), enabled = !busy
                    )
                    SettingsChoice(
                        "Cajero de apertura", filters.cashier,
                        listOf("" to "Todos los cajeros") + activePage.cashiers.map { it.id to it.label },
                        { filters = filters.copy(cashier = it) }, modifier = Modifier.weight(1f), enabled = !busy
                    )
                }
                val dateError = filters.dateError()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { search(filters, listOf(null)) }, enabled = !busy && dateError == null) {
                        Text("Aplicar filtros")
                    }
                    if (dateError != null) Text(dateError, color = MaterialTheme.colorScheme.error)
                    else if (filters != applied) Text("Hay filtros sin aplicar.", style = MaterialTheme.typography.bodySmall)
                }
                if (activePage.cuts.isEmpty()) Text("No hay cortes cerrados con los filtros aplicados.")
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activePage.cuts, key = { it.sessionId }) { cut ->
                        OutlinedCard(onClick = {
                            if (!busy) {
                                busy = true
                                error = null
                                scope.launch {
                                    api.getClosedCashCut(cut.sessionId, pin).onSuccess { detail = it }
                                        .onFailure { showError(it, "No se pudo cargar el corte.") }
                                    busy = false
                                }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text("${cut.registerName} · ${cut.cashierName}") },
                                supportingContent = { Text("${formatShiftHistoryTimestamp(cut.closedAt, true)} · ${cut.sessionId}") },
                                trailingContent = { Text(cut.differenceCents?.let { "Diferencia ${Money(it).format()}" } ?: "Sin diferencia registrada") }
                            )
                        }
                    }
                }
                if (trail.size > 1 || activePage.next != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { search(applied, trail.dropLast(1)) },
                            enabled = !busy && trail.size > 1
                        ) { Text("Anterior") }
                        OutlinedButton(
                            onClick = { activePage.next?.let { search(applied, trail + it) } },
                            enabled = !busy && activePage.next != null
                        ) { Text("Siguiente") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashCutDetail(
    detail: CashCutHistoryDetail, onBack: () -> Unit, onOpenSales: () -> Unit, onOpenMovements: () -> Unit
) {
    val cut = detail.cut
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Volver al historial")
            }
            Text("Corte Z · ${cut.sessionId}", style = MaterialTheme.typography.headlineSmall)
            Text("${cut.registerName} · Cajero de apertura: ${cut.cashierName}")
            Text("Apertura ${formatShiftHistoryTimestamp(cut.openedAt, true)}")
            Text("Cierre ${formatShiftHistoryTimestamp(cut.closedAt, true)}")
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onOpenSales) { Text("Ver ventas del turno") }
                OutlinedButton(onOpenMovements) { Text("Ver movimientos de caja") }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CutAmount("Fondo inicial", cut.initialFundCents)
                    CutAmount("Efectivo esperado al cierre", cut.expectedCashCents)
                    CutAmount("Efectivo contado", cut.countedCashCents)
                    CutAmount("Diferencia", cut.differenceCents)
                }
            }
        }
        if (detail.notes.isNotBlank()) item {
            Text("Notas del cierre", style = MaterialTheme.typography.titleMedium)
            Text(detail.notes)
        }
        item {
            Text("Desglose del arqueo", style = MaterialTheme.typography.titleMedium)
            if (detail.denominations.isEmpty()) Text("El cierre se registró sin desglose de denominaciones.")
        }
        items(detail.denominations) { denomination ->
            ListItem(
                headlineContent = { Text("${if (denomination.isCoin) "Moneda" else "Billete"} de ${Money(denomination.denominationCents).format()}") },
                supportingContent = { Text("${denomination.pieces} piezas") },
                trailingContent = { Text(Money(denomination.totalCents).format()) }
            )
        }
    }
}

@Composable
private fun CutAmount(label: String, cents: Long?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f))
        Text(cents?.let { Money(it).format() } ?: "Sin registro")
    }
}
