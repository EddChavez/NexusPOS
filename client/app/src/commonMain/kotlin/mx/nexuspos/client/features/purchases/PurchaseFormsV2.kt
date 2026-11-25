package mx.nexuspos.client.features.purchases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.features.settings.v2.SettingsChoice
import mx.nexuspos.contracts.purchases.PurchaseDetails
import mx.nexuspos.contracts.purchases.PurchaseDocumentType
import mx.nexuspos.contracts.purchases.purchaseLineTotal
import mx.nexuspos.core.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReceiptForm(draft: ReceiptDraft, state: PurchasesState, viewModel: PurchasesViewModel) {
    val editing = !state.saving && !state.awaitingConfirmation
    val suppliers = state.suppliers.filter { it.isActive }.map { it.id to it.name }
    val validSupplier = suppliers.any { it.first == draft.supplierId }
    val request = draft.toRequest()
    val amount = request?.lines?.fold(0L) { sum, line ->
        val value = purchaseLineTotal(line.quantityMillis, line.unitCostCents) ?: return@fold -1L
        if (sum < 0 || value > Long.MAX_VALUE - sum) -1 else sum + value
    }?.takeIf { it >= 0 }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Documento de compra", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsChoice("Proveedor", draft.supplierId, suppliers,
                                { id -> viewModel.editReceipt { copy(supplierId = id) } },
                                modifier = Modifier.weight(1f), enabled = editing && !state.loading && suppliers.isNotEmpty()
                            )
                            TextButton(onClick = { viewModel.editSupplier() }, enabled = editing) { Text("Nuevo proveedor") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsChoice(
                                "Tipo de documento", draft.documentType.name, PurchaseDocumentType.entries.map { it.name to it.label() },
                                { type -> viewModel.editReceipt { copy(documentType = PurchaseDocumentType.valueOf(type)) } },
                                modifier = Modifier.weight(1f), enabled = editing
                            )
                            PurchaseField(
                                "Folio del documento", draft.documentNumber,
                                { value -> viewModel.editReceipt { copy(documentNumber = value) } },
                                modifier = Modifier.weight(1f), enabled = editing
                            )
                        }
                        PurchaseField("Notas (opcional)", draft.notes, { value -> viewModel.editReceipt { copy(notes = value) } },
                            enabled = editing, multiline = true
                        )
                    }
                }
            }
            item {
                Text("Partidas · ${draft.lines.size}", style = MaterialTheme.typography.titleLarge)
                PurchaseField("Buscar producto por nombre, SKU o código", state.productQuery, viewModel::searchProducts,
                    enabled = editing && draft.lines.size < 200
                )
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.productQuery.isNotBlank() && !state.searching && state.productResults.isEmpty()) {
                    Text("Sin productos activos con control de existencias.", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(state.productResults, key = { "result-" + it.id }) { product ->
                OutlinedCard(onClick = { viewModel.addProduct(product) }, enabled = editing, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(product.name) }, supportingContent = { Text("${product.sku} · ${product.baseUnit}") })
                }
            }
            items(draft.lines, key = ReceiptLineDraft::key) { line ->
                ReceiptLineFields(line, editing,
                    onRemove = { viewModel.editReceipt { copy(lines = lines.filterNot { it.key == line.key }) } },
                    onChange = { changed -> viewModel.editReceipt { copy(lines = lines.map { if (it.key == changed.key) changed else it }) } }
                )
            }
        }
        PurchaseConfirmation(
            if (state.awaitingConfirmation) "Reintentar recepción" else "Confirmar recepción", state.saving,
            (validSupplier || state.awaitingConfirmation) && amount != null && !state.loading,
            viewModel::confirmReceipt, amount
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptLineFields(
    line: ReceiptLineDraft, enabled: Boolean, onRemove: () -> Unit, onChange: (ReceiptLineDraft) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(line.sku, line.unit, "Requiere lote y caducidad".takeIf { line.perishable == true }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onRemove, enabled = enabled) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Quitar ${line.name}")
                }
            }
            val quantity = parsePurchaseDecimal(line.quantity, 3)
            val cost = parsePurchaseDecimal(line.cost, 2)
            val total = if (quantity != null && cost != null) purchaseLineTotal(quantity, cost) else null
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PurchaseField("Cantidad (${line.unit})", line.quantity, { onChange(line.copy(quantity = it)) }, Modifier.weight(1f), enabled,
                    error = quantity == null || quantity <= 0 || (line.unit == "PZA" && quantity % 1000 != 0L)
                )
                PurchaseField("Costo por ${line.unit}", line.cost, { onChange(line.copy(cost = it)) }, Modifier.weight(1f), enabled, error = cost == null)
            }
            val label = if (line.perishable == true) "Lote requerido" else "Lote (opcional)"
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PurchaseField(label, line.lot, { onChange(line.copy(lot = it)) }, Modifier.weight(1f), enabled)
                PurchaseField("Caducidad AAAA-MM-DD", line.expiry, { onChange(line.copy(expiry = it)) }, Modifier.weight(1f), enabled)
            }
            total?.let { Text("Importe ${Money(it).format()}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun PurchaseDetail(purchase: PurchaseDetails, viewModel: PurchasesViewModel, loading: Boolean) {
    val header = purchase.summary
    val linesById = purchase.lines.associateBy { it.id }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(header.supplierName, style = MaterialTheme.typography.titleLarge)
        Text("${header.documentType.label()} ${header.documentNumber} · ${formatShiftHistoryTimestamp(header.createdAt, true)}")
        Text("Recibió ${purchase.receivedBy} · Compra ${Money(header.totalCents).format()} · Devuelto ${Money(header.returnedCents).format()}")
        if (purchase.notes.isNotBlank()) Text(purchase.notes)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(purchase.lines, key = { "line-" + it.id }) { line ->
                ListItem(
                    headlineContent = { Text(line.productName) },
                    supportingContent = {
                        Column {
                            Text("Recibido ${purchaseQuantity(line.quantityMillis, line.baseUnit)} · Devuelto ${purchaseQuantity(line.returnedMillis, line.baseUnit)}")
                            Text("Costo por ${line.baseUnit} · ${Money(line.unitCostCents).format()}")
                            line.lotNumber?.let { Text("Lote $it · Caduca ${line.expirationDate}") }
                        }
                    },
                    trailingContent = { Text(Money(line.totalCents).format()) }
                )
            }
            if (purchase.returns.isNotEmpty()) {
                item { Text("Devoluciones", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(purchase.returns, key = { "return-" + it.id }) { returned ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${Money(returned.totalCents).format()} · ${formatShiftHistoryTimestamp(returned.createdAt, true)}")
                            Text("${returned.returnedBy} · ${returned.reason}", style = MaterialTheme.typography.bodyMedium)
                            returned.lines.forEach { line ->
                                val product = linesById.getValue(line.purchaseLineId)
                                Text("${product.productName} · ${purchaseQuantity(line.quantityMillis, product.baseUnit)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(viewModel::startReturn, enabled = !loading && purchase.lines.any { it.returnedMillis < it.quantityMillis }) {
                Text("Devolver al proveedor")
            }
        }
    }
}

@Composable
internal fun ReturnForm(purchase: PurchaseDetails, draft: ReturnDraft, state: PurchasesState, viewModel: PurchasesViewModel) {
    val available = purchase.lines.filter { it.returnedMillis < it.quantityMillis }
    val canEdit = !state.saving && !state.awaitingConfirmation
    val request = draft.toRequest(purchase)
    val originals = available.associateBy { it.id }
    val amount = request?.lines?.sumOf { item ->
        val original = originals.getValue(item.purchaseLineId)
        val updated = purchaseLineTotal(original.returnedMillis + item.quantityMillis, original.unitCostCents) ?: 0
        val previous = purchaseLineTotal(original.returnedMillis, original.unitCostCents) ?: 0
        updated - previous
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${purchase.summary.supplierName} · ${purchase.summary.documentNumber}", style = MaterialTheme.typography.titleMedium)
        PurchaseField("Motivo de la devolución", draft.reason, { viewModel.changeReturn(draft.copy(reason = it)) },
            enabled = canEdit, multiline = true
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    viewModel.changeReturn(draft.copy(quantities = available.associate {
                        it.id to purchaseDecimal(it.quantityMillis - it.returnedMillis, 3)
                    }))
                }, enabled = canEdit
            ) { Text("Completar todas las cantidades") }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(available, key = { it.id }) { line ->
                val maximum = line.quantityMillis - line.returnedMillis
                val text = draft.quantities[line.id].orEmpty()
                val quantity = parsePurchaseDecimal(text, 3)
                val invalid = text.isNotBlank() && (quantity == null || quantity > maximum || (line.baseUnit == "PZA" && quantity % 1000L != 0L))
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.productName, style = MaterialTheme.typography.titleMedium)
                        Text("Pendiente de devolver ${purchaseQuantity(maximum, line.baseUnit)}")
                        line.lotNumber?.let { Text("Lote $it · Caduca ${line.expirationDate}") }
                        PurchaseField(
                            "Cantidad a devolver (${line.baseUnit})", text,
                            { viewModel.changeReturn(draft.copy(quantities = draft.quantities + (line.id to it))) },
                            enabled = canEdit, error = invalid
                        )
                    }
                }
            }
        }
        PurchaseConfirmation(
            if (state.awaitingConfirmation) "Reintentar devolución" else "Confirmar devolución", state.saving, amount != null,
            viewModel::confirmReturn, amount
        )
    }
}
