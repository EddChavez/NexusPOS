package mx.nexuspos.client.features.inventory.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.inventory.InventoryTab
import mx.nexuspos.client.features.inventory.InventoryUiState
import mx.nexuspos.client.features.inventory.InventoryViewModel
import mx.nexuspos.client.features.inventory.StockFilter
import mx.nexuspos.client.features.inventory.includes
import mx.nexuspos.client.features.products.v2.ProductDepartmentFilter
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryScreenV2(
    viewModel: InventoryViewModel,
    cashierUserId: String,
    onRequestAdjustStock: (ProductDto) -> Unit,
    onExportReorderCsv: (List<ReorderSuggestionItemDto>) -> Unit,
    onCopyReorderWhatsApp: (List<ReorderSuggestionItemDto>) -> Unit,
    onPrintReorderTicket: (List<ReorderSuggestionItemDto>) -> Unit,
    onPrintLotsReport: () -> Unit,
    onStockChanged: () -> Unit,
    onReceiveFromReorder: ((List<ReorderSuggestionItemDto>) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val movement = state.productForMovement?.takeIf { state.showMovementDialog }
    val writeOff = state.lotForWriteOff
    val inAction = movement != null || writeOff != null || state.showCreateLotDialog
    val paneFocus = remember { FocusRequester() }
    val stockList = rememberLazyListState()
    LaunchedEffect(inAction) { if (inAction) paneFocus.requestFocus() }
    LaunchedEffect(state.searchQuery, state.selectedDepartmentId, state.stockFilter) {
        stockList.requestScrollToItem(0)
    }

    fun back() {
        when {
            movement != null -> viewModel.closeMovementDialog()
            writeOff != null -> viewModel.closeLotWriteOffDialog()
            state.showCreateLotDialog -> viewModel.closeCreateLotDialog()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).focusRequester(paneFocus).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && inAction) {
                back()
                true
            } else false
        }.focusable(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (inAction) {
                IconButton(onClick = ::back, enabled = !state.isSubmitting) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a Inventario")
                }
            }
            Text(
                when {
                    movement != null -> "Movimiento de inventario"
                    writeOff != null -> "Registrar merma de lote"
                    state.showCreateLotDialog -> "Registrar lote"
                    else -> "Inventario"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall
            )
            if (!inAction) {
                IconButton(onClick = viewModel::refreshAll, enabled = !state.isSubmitting) {
                    Icon(Icons.Default.Refresh, "Actualizar inventario")
                }
            }
        }

        when {
            movement != null -> key(movement.id) {
                StockMovementPaneV2(
                    movement, state.isSubmitting, state.errorMessage, ::back
                ) { type, delta, reason, reference, expected ->
                    viewModel.submitStockMovement(
                        movement.id, type, delta, reason, reference,
                        userId = cashierUserId, expectedStockMillis = expected, onSaved = onStockChanged
                    )
                }
            }
            writeOff != null -> key(writeOff.id) {
                InventoryLotWriteOffV2(writeOff, state.isSubmitting, state.errorMessage, ::back) { quantity, reason ->
                    viewModel.writeOffLot(writeOff.id, quantity, reason, cashierUserId, onStockChanged)
                }
            }
            state.showCreateLotDialog -> InventoryLotEntryV2(
                initialProduct = state.selectedProduct,
                busy = state.isSubmitting,
                error = state.errorMessage,
                onSearch = viewModel::searchLotProducts,
                onBack = ::back,
                onConfirm = { lot ->
                    viewModel.createLot(
                        lot.productId, lot.lotNumber, lot.expirationDate, lot.quantityMillis,
                        lot.costCents, lot.addToGeneralStock, lot.notes,
                        userId = cashierUserId, onSaved = onStockChanged
                    )
                }
            )
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inventoryTabs.forEach { (tab, label) ->
                        FilterChip(
                            selected = state.activeTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            label = { Text(label) }
                        )
                    }
                }
                FeedbackMessageHost(
                    errorMessage = state.errorMessage, successMessage = state.successMessage,
                    onDismissError = viewModel::dismissMessages, onDismissSuccess = viewModel::dismissMessages
                )
                OutlinedCard(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    when (state.activeTab) {
                        InventoryTab.STOCK_TABLE -> InventoryStockPanel(state, stockList, viewModel, onRequestAdjustStock)
                        InventoryTab.KARDEX -> InventoryLedgerPanel(state, { viewModel.selectMovementProduct(null) })
                        InventoryTab.LOTS_AND_EXPIRATIONS -> InventoryLotsPanel(
                            state, viewModel::onLotStatusFilterSelected, viewModel::openCreateLotDialog,
                            viewModel::openLotWriteOffDialog, onPrintLotsReport
                        )
                        InventoryTab.REORDER_SUGGESTIONS -> InventoryReorderPanel(
                            state, viewModel::onReorderDepartmentSelected, viewModel::onToggleReorderOnlyOutOfStock,
                            viewModel::updateSuggestedQuantity, viewModel::resetEditedQuantities,
                            onExportReorderCsv, onCopyReorderWhatsApp, onPrintReorderTicket, onReceiveFromReorder
                        )
                    }
                }
            }
        }
    }
}

private val inventoryTabs = listOf(
    InventoryTab.STOCK_TABLE to "Existencias",
    InventoryTab.KARDEX to "Kárdex",
    InventoryTab.LOTS_AND_EXPIRATIONS to "Lotes y caducidades",
    InventoryTab.REORDER_SUGGESTIONS to "Resurtido"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryStockPanel(
    state: InventoryUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: InventoryViewModel,
    onAdjust: (ProductDto) -> Unit
) {
    val products = remember(state.products, state.stockFilter) {
        state.products.filter(state.stockFilter::includes)
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.summary?.let { summary ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InventoryMetric("Productos", summary.totalProductsCount.toString())
                InventoryMetric("Bajo mínimo", summary.lowStockCount.toString())
                InventoryMetric("Sin stock", summary.outOfStockCount.toString())
                InventoryMetric("Valuación", Money(summary.totalValuationCents).format())
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = state.searchQuery, onValueChange = viewModel::onSearchQueryChanged,
                label = { Text("Buscar producto") }, placeholder = { Text("Nombre, SKU o código") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.searchQuery.isEmpty()) null else {
                    { IconButton(onClick = { viewModel.onSearchQueryChanged("") }) { Icon(Icons.Default.Clear, "Limpiar búsqueda") } }
                },
                singleLine = true,
                modifier = Modifier.widthIn(max = 360.dp)
            )
            ProductDepartmentFilter(
                state.departments, state.selectedDepartmentId, viewModel::onDepartmentSelected,
                Modifier.widthIn(max = 260.dp), allLabel = "Todos"
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(StockFilter.ALL to "Todos", StockFilter.LOW_STOCK to "Bajo mínimo", StockFilter.OUT_OF_STOCK to "Agotados")
                .forEach { (filter, label) ->
                    FilterChip(selected = state.stockFilter == filter, onClick = { viewModel.onStockFilterSelected(filter) }, label = { Text(label) })
                }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (products.isEmpty() && !state.isLoading) {
            Text("No hay productos para estos filtros.", Modifier.padding(8.dp))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(products, key = { it.id }) { product ->
                    val selected = product.id == state.selectedProduct?.id
                    val unit = ProductUnit.fromCode(product.baseUnit)
                    val inventory = when {
                        product.kitComponents.isNotEmpty() -> "Por componente"
                        product.usesInventory -> Quantity(product.currentStockMillis).format(unit)
                        else -> "Sin control"
                    }
                    OutlinedCard(
                        onClick = { viewModel.selectProduct(product) },
                        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                        enabled = !state.isLoading,
                        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) colors.secondaryContainer else colors.surface)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(product.sku, product.departmentName).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(inventory, style = MaterialTheme.typography.titleSmall)
                                if (product.usesInventory) Text(
                                    "Mínimo ${Quantity(product.minStockMillis).format(unit)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        val selected = products.firstOrNull { it.id == state.selectedProduct?.id }
        if (selected != null) {
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(selected.name, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { viewModel.openProductKardex(selected) }, enabled = !state.isLoading) { Text("Ver kárdex") }
                FilledTonalButton(
                    onClick = { onAdjust(selected) },
                    enabled = selected.usesInventory && selected.kitComponents.isEmpty() && !state.isLoading && !state.isSubmitting
                ) { Text("Ajustar existencias") }
            }
        }
    }
}

@Composable
private fun InventoryLedgerPanel(state: InventoryUiState, onClearFilter: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.movementProduct?.name ?: "Movimientos de todos los productos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (state.movementProduct != null) TextButton(onClick = onClearFilter) { Text("Ver todos") }
        }
        when {
            state.isMovementsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.movements.isEmpty() -> Text("Sin movimientos para este filtro.", Modifier.padding(8.dp))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.movements, key = { it.id }) { movement ->
                    val unit = movement.baseUnit?.let(ProductUnit::fromCode)
                    val quantity = Quantity(movement.quantityMillis)
                    val amount = unit?.let(quantity::format) ?: quantity.format().substringBefore(' ')
                    val kind = when (movement.type) {
                        "ENTRY" -> "Entrada"
                        "LOSS" -> "Merma"
                        "SALE" -> "Venta"
                        "REFUND" -> "Devolución"
                        "PURCHASE_RETURN" -> "Devolución a proveedor"
                        "ADJUSTMENT" -> "Ajuste"
                        else -> movement.type
                    }
                    ListItem(
                        headlineContent = { Text("${movement.productName} · $kind") },
                        supportingContent = {
                            Column {
                                Text(listOf(movement.reason, movement.reference).filter { it.isNotBlank() }.joinToString(" · "))
                                Text(
                                    listOfNotNull(formatShiftHistoryTimestamp(movement.createdAt, true), movement.userName)
                                        .joinToString(" · ")
                                )
                            }
                        },
                        trailingContent = { Text(if (movement.quantityMillis > 0) "+$amount" else amount) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }
        }
    }
}

@Composable
internal fun InventoryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}
