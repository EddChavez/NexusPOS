package mx.nexuspos.client.features.purchases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.purchases.*
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurchasesScreenV2(viewModel: PurchasesViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    val locked = state.saving || state.awaitingConfirmation
    val showingList = state.page == PurchasesPage.HISTORY || state.page == PurchasesPage.SUPPLIERS
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.page != PurchasesPage.HISTORY) {
                IconButton(onClick = { viewModel.back() }, enabled = !locked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
            Text(
                when (state.page) {
                    PurchasesPage.HISTORY -> "Compras"
                    PurchasesPage.SUPPLIERS -> "Proveedores"
                    PurchasesPage.SUPPLIER_FORM -> if (state.supplier?.id == null) "Nuevo proveedor" else "Editar proveedor"
                    PurchasesPage.RECEIVE -> "Recibir mercancía"
                    PurchasesPage.DETAIL -> "Detalle de compra"
                    PurchasesPage.RETURN -> "Devolución a proveedor"
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (showingList || state.page == PurchasesPage.DETAIL || state.page == PurchasesPage.RECEIVE) {
                IconButton(viewModel::refresh, enabled = !state.loading && !locked) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
        }
        if (showingList) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.page == PurchasesPage.HISTORY,
                    onClick = { viewModel.openHistory() },
                    label = { Text("Historial") }
                )
                FilterChip(
                    selected = state.page == PurchasesPage.SUPPLIERS,
                    onClick = viewModel::openSuppliers,
                    label = { Text("Proveedores") }
                )
                Button(onClick = {
                    if (state.page == PurchasesPage.SUPPLIERS) viewModel.editSupplier() else viewModel.startReceipt()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.page == PurchasesPage.SUPPLIERS) "Nuevo proveedor" else "Nueva recepción")
                }
            }
        }
        FeedbackMessageHost(
            errorMessage = state.error,
            successMessage = state.success,
            onDismissError = { viewModel.dismissMessage() },
            onDismissSuccess = { viewModel.dismissMessage() }
        )
        if (state.awaitingConfirmation) {
            Text(
                "El servidor puede haber registrado la operación. Reintenta con el mismo PIN para confirmar el resultado.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state.page) {
                PurchasesPage.HISTORY -> PurchaseHistory(state, viewModel)
                PurchasesPage.SUPPLIERS -> SupplierList(state, viewModel)
                PurchasesPage.SUPPLIER_FORM -> state.supplier?.let { SupplierForm(it, state.saving, viewModel) }
                PurchasesPage.RECEIVE -> state.receipt?.let { ReceiptForm(it, state, viewModel) }
                PurchasesPage.DETAIL -> state.details?.let { PurchaseDetail(it, viewModel, state.loading) }
                PurchasesPage.RETURN -> state.details?.let { purchase ->
                    state.returnDraft?.let { ReturnForm(purchase, it, state, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun PurchaseHistory(state: PurchasesState, viewModel: PurchasesViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.history.isEmpty() && !state.loading && state.error == null) Text("No hay compras registradas en esta página.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.history, key = PurchaseSummary::id) { receipt ->
                OutlinedCard(onClick = { viewModel.openDetails(receipt.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(receipt.supplierName, style = MaterialTheme.typography.titleMedium)
                        Text("${receipt.documentType.label()} ${receipt.documentNumber} · ${formatShiftHistoryTimestamp(receipt.createdAt, true)}")
                        Text("Compra ${Money(receipt.totalCents).format()} · Devuelto ${Money(receipt.returnedCents).format()}")
                    }
                }
            }
        }
        if (state.historyOffset > 0 || state.hasMoreHistory) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openHistory(state.historyOffset - PURCHASE_HISTORY_PAGE_SIZE) },
                    enabled = state.historyOffset > 0 && !state.loading
                ) { Text("Anterior") }
                OutlinedButton(
                    onClick = { viewModel.openHistory(state.historyOffset + PURCHASE_HISTORY_PAGE_SIZE) },
                    enabled = state.hasMoreHistory && !state.loading
                ) { Text("Siguiente") }
            }
        }
    }
}

@Composable
private fun SupplierList(state: PurchasesState, viewModel: PurchasesViewModel) {
    var query by remember { mutableStateOf("") }
    val term = query.trim()
    val filtered = state.suppliers.filter {
        it.name.contains(term, ignoreCase = true) || it.taxId.contains(term, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PurchaseField("Buscar por nombre o RFC", query, { query = it })
        if (filtered.isEmpty() && !state.loading && state.error == null) Text("No hay proveedores para mostrar.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = SupplierDto::id) { supplier ->
                OutlinedCard(onClick = { viewModel.editSupplier(supplier) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(supplier.name, style = MaterialTheme.typography.titleMedium)
                        Text((listOf(if (supplier.isActive) "Activo" else "Inactivo") + listOf(supplier.taxId, supplier.phone, supplier.email).filter(String::isNotBlank)).joinToString(" · "))
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplierForm(draft: SupplierDraft, saving: Boolean, viewModel: PurchasesViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PurchaseField("Nombre o razón social", draft.name, { viewModel.changeSupplier(draft.copy(name = it)) }, enabled = !saving)
            }
            item { PurchaseField("RFC", draft.taxId, { viewModel.changeSupplier(draft.copy(taxId = it)) }, enabled = !saving) }
            item { PurchaseField("Teléfono", draft.phone, { viewModel.changeSupplier(draft.copy(phone = it)) }, enabled = !saving) }
            item { PurchaseField("Correo", draft.email, { viewModel.changeSupplier(draft.copy(email = it)) }, enabled = !saving) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = draft.active, onCheckedChange = { viewModel.changeSupplier(draft.copy(active = it)) }, enabled = !saving)
                    Text("Proveedor activo")
                }
            }
        }
        PurchaseConfirmation(
            "Guardar proveedor", saving, draft.name.isNotBlank() && draft.name.trim().length <= 256 &&
                draft.taxId.trim().length <= 32 && draft.phone.trim().length <= 64 && draft.email.trim().length <= 254,
            viewModel::saveSupplier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PurchaseConfirmation(
    action: String, saving: Boolean, valid: Boolean, onConfirm: (String) -> Unit, amount: Long? = null
) {
    var pin by remember { mutableStateOf("") }
    HorizontalDivider()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        amount?.let { Text("Total ${Money(it).format()}", style = MaterialTheme.typography.titleMedium) }
        OutlinedTextField(
            value = pin,
            onValueChange = { input -> if (input.length <= 6 && input.all { it in '0'..'9' }) pin = input },
            label = { Text("PIN de encargado") },
            enabled = !saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.width(192.dp)
        )
        Button(onClick = { onConfirm(pin) }, enabled = valid && pin.length in setOf(4, 6) && !saving) {
            Text(if (saving) "Registrando…" else action)
        }
    }
}

@Composable
internal fun PurchaseField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, multiline: Boolean = false, error: Boolean = false
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) },
        enabled = enabled, singleLine = !multiline, maxLines = if (multiline) 3 else 1, isError = error
    )
}

internal fun purchaseQuantity(millis: Long, unit: String): String = Quantity(millis).format(ProductUnit.fromCode(unit))
