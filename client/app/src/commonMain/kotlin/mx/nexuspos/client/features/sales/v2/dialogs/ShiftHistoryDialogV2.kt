package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money

private val shiftFilters = listOf(
    null to "Todos",
    "COMPLETED" to "Completados",
    "CANCELLED" to "Cancelados",
    "REFUNDED" to "Devueltos"
)

@Composable
fun ShiftHistoryDialogV2(
    sales: List<SaleResponse>,
    selectedSale: SaleResponse?,
    isLoading: Boolean,
    searchFolio: String,
    statusFilter: String?,
    cashSessionId: String?,
    cashierId: String,
    requiresSupervisor: Boolean,
    viewModel: SalesViewModel,
    onSearchFolioChanged: (String) -> Unit,
    onStatusFilterChanged: (String?) -> Unit,
    onSelectSale: (SaleResponse) -> Unit,
    onReload: () -> Unit,
    onPrint: suspend (SaleResponse) -> Result<Unit>,
    onOpenWhatsApp: (String) -> Result<Unit>,
    onCopyText: (String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val matchingSales = remember(sales, searchFolio, statusFilter) {
        val folio = searchFolio.trim()
        sales.filter { sale ->
            sale.folio.toString().contains(folio) && when (statusFilter) {
                null -> true
                "REFUNDED" -> sale.status == "REFUNDED" || sale.status == "PARTIALLY_REFUNDED"
                else -> sale.status == statusFilter
            }
        }
    }
    val visibleSelection = selectedSale?.takeIf { selection -> matchingSales.any { it.id == selection.id } }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val width = if (windowSize.width > 0.dp) (windowSize.width - 32.dp).coerceIn(1.dp, 1080.dp) else 1080.dp
    val height = if (windowSize.height > 0.dp) (windowSize.height - 32.dp).coerceIn(1.dp, 720.dp) else 720.dp
    val compact = width < 850.dp
    val listState = rememberLazyListState()
    var detailRequested by remember { mutableStateOf(false) }
    val showCompactDetail = compact && detailRequested && visibleSelection != null
    val scope = rememberCoroutineScope()
    var activeAction by remember { mutableStateOf<Pair<ShiftHistoryAction, SaleResponse>?>(null) }
    var authorizing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun finishAction() {
        activeAction = null
        authorizing = false
        error = null
    }

    fun goBack() {
        if (busy) return
        when {
            authorizing -> { authorizing = false; error = null }
            activeAction != null -> finishAction()
            showCompactDetail -> detailRequested = false
            else -> onDismiss()
        }
    }

    fun runAction(operation: suspend () -> Result<String>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val result = operation()
                ensureActive()
                result.fold(
                    onSuccess = { message ->
                        if (statusFilter == "COMPLETED" &&
                            (activeAction?.first == ShiftHistoryAction.CANCEL || activeAction?.first == ShiftHistoryAction.REFUND)
                        ) onStatusFilterChanged(null)
                        notice = message
                        finishAction()
                    },
                    onFailure = { error = it.message ?: "No se pudo completar la acción." }
                )
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(searchFolio, statusFilter) { listState.scrollToItem(0) }

    Dialog(
        onDismissRequest = ::goBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.size(width, height),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = activeAction
                if (current != null) {
                    val (currentAction, sale) = current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::goBack, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(currentAction.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                listOfNotNull("Folio #${sale.folio}", sale.customerName?.takeIf { it.isNotBlank() })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    val authorize = { authorizing = true; error = null }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (currentAction) {
                            ShiftHistoryAction.CANCEL -> ShiftCancelPane(
                                sale, requiresSupervisor, authorizing, error, busy, ::goBack, authorize
                            ) { reason, pin ->
                                runAction {
                                    viewModel.cancelShiftSale(sale, reason, pin).map {
                                        "Venta #${it.folio} cancelada."
                                    }
                                }
                            }
                            ShiftHistoryAction.REFUND -> ShiftRefundPane(
                                sale, cashSessionId, cashierId, requiresSupervisor, authorizing,
                                error, busy, ::goBack, authorize
                            ) { request ->
                                runAction {
                                    viewModel.refundShiftSale(sale, request).map {
                                        "Devolución #${it.folio} registrada por ${Money(it.totalCents).format()}."
                                    }
                                }
                            }
                            ShiftHistoryAction.WHATSAPP -> ShiftWhatsAppPane(
                                message = remember(sale) { viewModel.formatWhatsAppTicket(sale) },
                                countryCode = viewModel.currentSettings?.whatsappCountryCode ?: "52",
                                error = error,
                                onBack = ::goBack,
                                onCopy = onCopyText,
                                onOpen = onOpenWhatsApp,
                                onOpened = {
                                    notice = "Mensaje del folio #${sale.folio} abierto en WhatsApp."
                                    finishAction()
                                },
                                onError = { error = it }
                            )
                            ShiftHistoryAction.REPRINT -> ShiftReprintPane(
                                receipt = remember(sale) { viewModel.formatReprint(sale) },
                                error = error,
                                busy = busy,
                                onBack = ::goBack,
                                onPrint = {
                                    runAction {
                                        if (viewModel.canPerform("transactions.reprint")) onPrint(sale).map { "Copia del folio #${sale.folio} enviada a la impresora." }
                                        else Result.failure(IllegalStateException("Tu rol no permite reimprimir comprobantes."))
                                    }
                                }
                            )
                        }
                    }
                    return@Column
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCompactDetail) {
                        IconButton(onClick = { detailRequested = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a los folios")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Ventas del turno", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            cashSessionId?.let { "Sesión de caja · $it" } ?: "Sesión de caja actual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onReload, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, "Actualizar historial")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar historial")
                    }
                }

                notice?.let { ShiftActionMessage(it) }
                if (!showCompactDetail) ShiftSalesSummary(sales)

                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!showCompactDetail) {
                        Column(
                            Modifier.weight(if (compact) 1f else 0.42f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShiftHistoryFilters(searchFolio, statusFilter, onSearchFolioChanged, onStatusFilterChanged)
                            when {
                                isLoading -> HistoryMessage(Modifier.weight(1f)) { CircularProgressIndicator() }
                                matchingSales.isEmpty() -> HistoryMessage(Modifier.weight(1f)) {
                                    Text(
                                        if (sales.isEmpty()) "Aún no hay tickets en este turno."
                                        else "Ningún ticket coincide con la búsqueda.",
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(matchingSales, key = { it.id }) { sale ->
                                        ShiftHistorySaleRow(sale, sale.id == visibleSelection?.id) {
                                            notice = null
                                            onSelectSale(sale)
                                            detailRequested = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!compact || showCompactDetail) {
                        if (visibleSelection != null) {
                            key(visibleSelection.id) {
                                ShiftHistorySaleDetail(
                                    sale = visibleSelection,
                                    actionsEnabled = !isLoading,
                                    onAction = { requested ->
                                        notice = null
                                        activeAction = requested to visibleSelection
                                    },
                                    modifier = Modifier.weight(if (compact) 1f else 0.58f).fillMaxHeight()
                                )
                            }
                        } else {
                            HistoryMessage(Modifier.weight(0.58f).fillMaxHeight()) {
                                Text("Selecciona un folio para ver el ticket.", textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftSalesSummary(sales: List<SaleResponse>) {
    val completedTotal = remember(sales) { sales.filter { it.status == "COMPLETED" }.sumOf { it.totalCents } }
    val cancelledCount = remember(sales) { sales.count { it.status == "CANCELLED" } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShiftMetric("Ventas completadas", Money(completedTotal).format(), Modifier.weight(1.4f))
            ShiftMetric("Tickets", sales.size.toString(), Modifier.weight(1f))
            ShiftMetric("Cancelados", cancelledCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShiftMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftHistoryFilters(
    searchFolio: String,
    statusFilter: String?,
    onSearchFolioChanged: (String) -> Unit,
    onStatusFilterChanged: (String?) -> Unit
) {
    OutlinedTextField(
        value = searchFolio,
        onValueChange = onSearchFolioChanged,
        label = { Text("Buscar folio") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shiftFilters.forEach { (status, label) ->
            FilterChip(
                selected = statusFilter == status,
                onClick = { onStatusFilterChanged(status) },
                label = { Text(label) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = statusFilter == status,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
private fun ShiftHistorySaleRow(sale: SaleResponse, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected },
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) colors.secondaryContainer else colors.surface,
            contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface
        ),
        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Folio #${sale.folio}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatShiftHistoryTimestamp(sale.createdAt, includeDate = false)} · ${sale.customerName ?: "Público general"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) LocalContentColor.current else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money(sale.totalCents).format(), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                ShiftSaleStatus(sale.status)
            }
        }
    }
}

@Composable
private fun HistoryMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            content()
        }
    }
}
