package mx.nexuspos.client.features.inventory.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.inventory.InventoryUiState
import mx.nexuspos.client.features.products.v2.ProductDepartmentFilter
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryLotsPanel(
    state: InventoryUiState,
    onFilter: (LotExpirationStatus?) -> Unit,
    onNewLot: () -> Unit,
    onWriteOff: (ProductLotDto) -> Unit,
    onPrint: () -> Unit
) {
    val report = state.lotsReport
    val lots = report?.lots.orEmpty().filter { state.selectedLotStatusFilter == null || it.status == state.selectedLotStatusFilter }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            InventoryMetric("Lotes activos", (report?.totalLotsCount ?: 0).toString())
            InventoryMetric("Vencidos", (report?.expiredLotsCount ?: 0).toString())
            InventoryMetric("Por vencer", (report?.nearExpirationLotsCount ?: 0).toString())
            InventoryMetric("Valuación en riesgo", Money(report?.totalAtRiskValuationCents ?: 0).format())
            Button(onClick = onNewLot, enabled = !state.isSubmitting) { Text("Registrar lote") }
            OutlinedButton(onClick = onPrint, enabled = !state.isLotsLoading && report != null) { Text("Imprimir reporte") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(null) + LotExpirationStatus.entries).forEach { status ->
                FilterChip(
                    selected = state.selectedLotStatusFilter == status,
                    onClick = { onFilter(status) },
                    label = { Text(status?.let(::lotStatusLabel) ?: "Todos") }
                )
            }
        }
        when {
            state.isLotsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            lots.isEmpty() -> Text("No hay lotes para este filtro.", Modifier.padding(8.dp))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(lots, key = { it.id }) { lot ->
                    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lot.productName, style = MaterialTheme.typography.titleSmall)
                            Text("${lot.productSku} · Lote ${lot.lotNumber} · ${lot.expirationDate}", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                val colors = MaterialTheme.colorScheme
                                Text(
                                    lotStatusLabel(lot.status),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (lot.status == LotExpirationStatus.EXPIRED) colors.error else colors.onSurfaceVariant
                                )
                                Text(Quantity(lot.quantityMillis).format(ProductUnit.fromCode(lot.baseUnit)))
                                Text(Money(lot.valuationCents).format())
                                TextButton(
                                    onClick = { onWriteOff(lot) }, enabled = lot.isActive && lot.quantityMillis > 0 && !state.isSubmitting
                                ) { Text("Registrar merma") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lotStatusLabel(status: LotExpirationStatus): String = when (status) {
    LotExpirationStatus.EXPIRED -> "Vencido"
    LotExpirationStatus.NEAR_EXPIRATION -> "Por vencer"
    LotExpirationStatus.OPTIMAL -> "Vigente"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InventoryReorderPanel(
    state: InventoryUiState,
    onDepartment: (String?) -> Unit,
    onOnlyOutOfStock: (Boolean) -> Unit,
    onQuantity: (String, Long) -> Unit,
    onReset: () -> Unit,
    onCsv: (List<ReorderSuggestionItemDto>) -> Unit,
    onWhatsApp: (List<ReorderSuggestionItemDto>) -> Unit,
    onPrint: (List<ReorderSuggestionItemDto>) -> Unit
) {
    val items = remember(state.reorderReport, state.editedOrderQuantities) {
        state.reorderReport?.items.orEmpty().map { item ->
            val edited = state.editedOrderQuantities[item.productId]
            if (edited == null) item else item.copy(
                suggestedOrderMillis = edited,
                estimatedInvestmentCents = edited * item.costCents / 1000L
            )
        }
    }
    val ready = items.isNotEmpty() && !state.isReorderLoading
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            ProductDepartmentFilter(
                state.departments, state.reorderDepartmentId, onDepartment,
                Modifier.widthIn(max = 280.dp), allLabel = "Todos"
            )
            FilterChip(
                selected = state.reorderOnlyOutOfStock,
                onClick = { onOnlyOutOfStock(!state.reorderOnlyOutOfStock) },
                label = { Text("Solo agotados") }
            )
            InventoryMetric("Partidas", items.size.toString())
            InventoryMetric("Inversión estimada", Money(items.sumOf { it.estimatedInvestmentCents }).format())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCsv(items) }, enabled = ready) { Text("Exportar CSV") }
            OutlinedButton(onClick = { onWhatsApp(items) }, enabled = ready) { Text("Copiar para WhatsApp") }
            OutlinedButton(onClick = { onPrint(items) }, enabled = ready) { Text("Imprimir pedido") }
            TextButton(onClick = onReset, enabled = state.editedOrderQuantities.isNotEmpty()) { Text("Restablecer cantidades") }
        }
        when {
            state.isReorderLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            items.isEmpty() -> Text("No hay productos para resurtir con estos filtros.", Modifier.padding(8.dp))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.productId }) { item ->
                    val unit = ProductUnit.fromCode(item.baseUnit)
                    val step = if (unit == ProductUnit.KILOGRAM) 500L else 1000L
                    val maximum = Long.MAX_VALUE / item.costCents.coerceAtLeast(1L)
                    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.productName, style = MaterialTheme.typography.titleSmall)
                            val urgency = if (item.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK) "Agotado" else "Bajo mínimo"
                            Text("${item.sku} · $urgency · Existencia ${Quantity(item.currentStockMillis).format(unit)}", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onQuantity(item.productId, (item.suggestedOrderMillis - step).coerceAtLeast(step)) },
                                        enabled = item.suggestedOrderMillis > step
                                    ) {
                                        Icon(Icons.Default.Remove, "Reducir pedido de ${item.productName}")
                                    }
                                    InventoryMetric("Cantidad a pedir", Quantity(item.suggestedOrderMillis).format(unit))
                                    IconButton(
                                        onClick = { onQuantity(item.productId, item.suggestedOrderMillis + step) },
                                        enabled = item.suggestedOrderMillis <= maximum - step
                                    ) {
                                        Icon(Icons.Default.Add, "Aumentar pedido de ${item.productName}")
                                    }
                                }
                                InventoryMetric("Estimado", Money(item.estimatedInvestmentCents).format())
                            }
                        }
                    }
                }
            }
        }
    }
}
