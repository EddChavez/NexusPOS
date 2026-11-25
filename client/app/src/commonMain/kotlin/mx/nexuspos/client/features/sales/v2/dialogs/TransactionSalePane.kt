package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money

@Composable
internal fun TransactionSalePane(
    sale: SaleResponse,
    sessionOpen: Boolean,
    viewModel: SalesViewModel,
    onPrint: suspend (SaleResponse) -> Result<Unit>,
    onOpenWhatsApp: (String) -> Result<Unit>,
    onCopyText: (String) -> Result<Unit>,
    onComplete: (message: String, changed: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val sessionId = state.activeSession?.takeIf { it.status == "OPEN" }?.sessionId
    val actions = buildSet {
        if (viewModel.canPerform("transactions.reprint")) add(ShiftHistoryAction.REPRINT)
        if (viewModel.canPerform("transactions.share")) add(ShiftHistoryAction.WHATSAPP)
        if (sessionId != null) add(ShiftHistoryAction.REFUND)
        if (sessionOpen) add(ShiftHistoryAction.CANCEL)
    }
    var action by remember(sale.id) { mutableStateOf<ShiftHistoryAction?>(null) }
    var authorizing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun finish() {
        action = null
        authorizing = false
        error = null
    }

    fun back() {
        if (!busy) {
            if (authorizing) {
                authorizing = false
                error = null
            } else finish()
        }
    }

    fun runAction(changed: Boolean, operation: suspend () -> Result<String>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                operation().onSuccess { message ->
                    finish()
                    onComplete(message, changed)
                }.onFailure { error = it.message ?: "No se pudo completar la acción." }
            } finally {
                busy = false
            }
        }
    }

    val current = action
    if (current == null) {
        ShiftHistorySaleDetail(sale, true, { action = it }, Modifier.fillMaxSize(), allowedActions = actions)
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al folio")
            }
            Text("${current.title} · #${sale.folio}", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        val authorize = { authorizing = true; error = null }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                ShiftHistoryAction.CANCEL -> ShiftCancelPane(
                    sale, state.activeCashierRole == "CASHIER" || !viewModel.canPerform("transactions.cancel"),
                    authorizing, error, busy, ::back, authorize
                ) { reason, pin ->
                    runAction(changed = true) { viewModel.cancelShiftSale(sale, reason, pin).map { "Venta #${it.folio} cancelada." } }
                }
                ShiftHistoryAction.REFUND -> ShiftRefundPane(
                    sale, sessionId, state.activeCashierId,
                    sale.cashSessionId != sessionId || !viewModel.canPerform("transactions.refund"),
                    authorizing, error, busy, ::back, authorize
                ) { request ->
                    runAction(changed = true) {
                        viewModel.refundShiftSale(sale, request).map {
                            "Devolución #${it.folio} registrada por ${Money(it.totalCents).format()}."
                        }
                    }
                }
                ShiftHistoryAction.WHATSAPP -> ShiftWhatsAppPane(
                    message = remember(sale) { viewModel.formatWhatsAppTicket(sale) },
                    countryCode = viewModel.currentSettings?.whatsappCountryCode ?: "52",
                    error = error, onBack = ::back, onCopy = onCopyText, onOpen = onOpenWhatsApp,
                    onOpened = { finish(); onComplete("Mensaje del folio #${sale.folio} abierto en WhatsApp.", false) },
                    onError = { error = it }
                )
                ShiftHistoryAction.REPRINT -> ShiftReprintPane(
                    receipt = remember(sale) { viewModel.formatReprint(sale) },
                    error = error, busy = busy, onBack = ::back,
                    onPrint = {
                        runAction(changed = false) {
                            onPrint(sale).map { "Copia del folio #${sale.folio} enviada a la impresora." }
                        }
                    }
                )
            }
        }
    }
}
