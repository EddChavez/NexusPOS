package mx.nexuspos.client.features.cash.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.CashCutHistoryAccessDenied
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftHistorySaleDetail
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftSaleStatus
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.cash.ClosedCashCut
import mx.nexuspos.contracts.cash.ClosedShiftSaleDetail
import mx.nexuspos.contracts.cash.ClosedShiftSalesPage
import mx.nexuspos.contracts.cash.ClosedShiftSalesRequest
import mx.nexuspos.core.Money

@Composable
internal fun ClosedShiftSalesPanel(
    cut: ClosedCashCut,
    api: ApiClient,
    pin: String,
    onBackToCut: () -> Unit,
    onAccessDenied: (CashCutHistoryAccessDenied) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    var folioInput by remember { mutableStateOf("") }
    var appliedFolio by remember { mutableStateOf<Long?>(null) }
    var page by remember { mutableStateOf<ClosedShiftSalesPage?>(null) }
    var trail by remember { mutableStateOf(listOf<Long?>(null)) }
    var detail by remember { mutableStateOf<ClosedShiftSaleDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun showError(failure: Throwable) {
        if (failure is CashCutHistoryAccessDenied) onAccessDenied(failure)
        else error = failure.message ?: "No se pudo consultar la venta."
    }

    fun load(folio: Long?, destination: List<Long?>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            api.getClosedShiftSales(cut.sessionId, ClosedShiftSalesRequest(pin, folio, destination.last())).onSuccess {
                appliedFolio = folio
                trail = destination
                page = it
                scroll.requestScrollToItem(0)
            }.onFailure(::showError)
            busy = false
        }
    }

    LaunchedEffect(cut.sessionId) { load(null, listOf(null)) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val selected = detail
        TextButton(onClick = {
            if (selected == null) onBackToCut() else {
                detail = null
                error = null
            }
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (selected == null) "Volver al corte" else "Volver a ventas del turno")
        }
        Text("Ventas del turno", style = MaterialTheme.typography.titleLarge)
        Text("${cut.registerName} · Cierre ${formatShiftHistoryTimestamp(cut.closedAt, true)} · ${cut.sessionId}",
            style = MaterialTheme.typography.bodySmall)
        FeedbackMessageHost(errorMessage = error, successMessage = null, onDismissError = { error = null }, onDismissSuccess = {})
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (selected != null) {
            ShiftHistorySaleDetail(
                sale = selected.sale,
                actionsEnabled = false,
                onAction = null,
                modifier = Modifier.fillMaxWidth().weight(1f),
                cancellation = selected.cancellation
            )
        } else {
            val folio = folioInput.trim().removePrefix("#").trim().toLongOrNull()?.takeIf { it > 0 }
            val invalid = folioInput.isNotBlank() && folio == null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = folioInput, onValueChange = { if (it.length <= 24) folioInput = it },
                    label = { Text("Folio de venta") }, singleLine = true, enabled = !busy, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).widthIn(max = 280.dp)
                )
                Button(onClick = { load(folio, listOf(null)) }, enabled = !busy && !invalid) { Text("Buscar") }
                TextButton(onClick = {
                    folioInput = ""
                    load(null, listOf(null))
                }, enabled = !busy && (folioInput.isNotBlank() || appliedFolio != null)) { Text("Ver todas") }
            }
            if (invalid) Text("Ingresa un folio positivo.", color = MaterialTheme.colorScheme.error)
            val activePage = page
            if (!busy && activePage?.items?.isEmpty() == true) {
                Text(if (appliedFolio == null) "Este turno no tiene ventas." else "El folio #$appliedFolio no pertenece a este turno.")
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(activePage?.items.orEmpty(), key = { it.id }) { row ->
                    OutlinedCard(onClick = {
                        if (!busy) {
                            busy = true
                            error = null
                            scope.launch {
                                api.getClosedShiftSale(cut.sessionId, row.id, pin).onSuccess { detail = it }.onFailure(::showError)
                                busy = false
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Folio #${row.folio}") },
                            supportingContent = {
                                Text("${formatShiftHistoryTimestamp(row.createdAt, true)} · ${row.cashierName}")
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(Money(row.totalCents).format())
                                    ShiftSaleStatus(row.status)
                                }
                            }
                        )
                    }
                }
            }
            if (trail.size > 1 || activePage?.nextBeforeFolio != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { load(appliedFolio, trail.dropLast(1)) }, enabled = !busy && trail.size > 1) {
                        Text("Anterior")
                    }
                    OutlinedButton(
                        onClick = { activePage?.nextBeforeFolio?.let { load(appliedFolio, trail + it) } },
                        enabled = !busy && activePage?.nextBeforeFolio != null
                    ) { Text("Siguiente") }
                }
            }
        }
    }
}
