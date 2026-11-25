package mx.nexuspos.client.features.customers.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.customers.CustomersUiState
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftActionMessage
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftWhatsAppPane
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

private enum class CustomerTab(val label: String) {
    PROFILE("Ficha"), STATEMENT("Estado de cuenta"), WALLET("Monedero")
}

@Composable
fun CustomersScreenV2(
    viewModel: CustomersViewModel,
    cashSessionId: String?,
    cashierUserId: String,
    onOpenWhatsApp: (String) -> Result<Unit>,
    onCopyText: (String) -> Result<Unit>
) {
    val state by viewModel.uiState.collectAsState()
    val customer = state.selectedCustomer
    var compactDetail by remember { mutableStateOf(false) }
    var tab by remember(customer?.id) { mutableStateOf(CustomerTab.PROFILE) }
    var pendingDeactivation by remember { mutableStateOf<CustomerDto?>(null) }
    var shareError by remember(state.whatsAppMessageText, state.showWhatsAppDialog) { mutableStateOf<String?>(null) }
    val inAction = state.showFormDialog || state.showPaymentDialog || state.showWhatsAppDialog || state.showAdjustLoyaltyDialog
    val listState = rememberLazyListState()
    val paneFocus = remember { FocusRequester() }
    LaunchedEffect(state.searchQuery, state.includeInactive) { listState.requestScrollToItem(0) }

    fun showTab(target: CustomerTab) {
        tab = target
        if (target == CustomerTab.WALLET && customer != null) viewModel.openLoyaltyDialog(customer)
        else viewModel.closeLoyaltyDialog()
    }

    pendingDeactivation?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeactivation = null },
            title = { Text("Desactivar cliente") },
            text = { Text("${target.name} quedará fuera de las búsquedas de clientes activos.") },
            confirmButton = {
                Button(onClick = {
                    pendingDeactivation = null
                    viewModel.deactivateCustomer(target)
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Desactivar")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeactivation = null }) { Text("Volver") } }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val compact = maxWidth < 780.dp
        val showCompactDetail = compact && compactDetail && customer != null
        fun back() {
            when {
                state.isSubmitting -> Unit
                state.showAdjustLoyaltyDialog -> viewModel.closeAdjustLoyaltyDialog()
                state.showFormDialog -> viewModel.closeFormDialog()
                state.showPaymentDialog -> viewModel.closePaymentDialog()
                state.showWhatsAppDialog -> viewModel.closeWhatsAppDialog()
                showCompactDetail -> compactDetail = false
            }
        }
        LaunchedEffect(inAction, showCompactDetail) {
            if (inAction || showCompactDetail) paneFocus.requestFocus()
        }
        Column(
            Modifier.fillMaxSize().focusRequester(paneFocus).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape &&
                    (inAction || showCompactDetail)
                ) {
                    back()
                    true
                } else false
            }.focusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (inAction || showCompactDetail) {
                    IconButton(onClick = ::back, enabled = !state.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
                Text(
                    when {
                        state.showFormDialog -> if (state.customerToEdit == null) "Nuevo cliente" else "Editar cliente"
                        state.showPaymentDialog -> if (state.isFullLiquidation) "Liquidar cuenta" else "Registrar abono"
                        state.showWhatsAppDialog -> "Compartir por WhatsApp"
                        state.showAdjustLoyaltyDialog -> "Ajustar monedero"
                        else -> "Clientes"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (!inAction && !showCompactDetail) {
                    Button(onClick = viewModel::openCreateDialog, enabled = !state.isSubmitting) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nuevo cliente")
                    }
                }
            }

            if (!inAction) {
                state.errorMessage?.takeUnless { it == state.statementErrorMessage || it == state.loyaltyErrorMessage }?.let { message ->
                    CustomerNotice(message, true, viewModel::dismissMessages)
                }
                state.successMessage?.let { CustomerNotice(it, false, viewModel::dismissMessages) }
            }

            when {
                state.showFormDialog -> key(state.customerToEdit?.id) {
                    CustomerFormPaneV2(
                        customer = state.customerToEdit,
                        busy = state.isSubmitting,
                        error = state.errorMessage,
                        onBack = ::back,
                        onSave = { draft ->
                            compactDetail = true
                            viewModel.saveCustomer(
                                draft.name, draft.phone, draft.email, draft.rfc, draft.address, draft.notes,
                                draft.creditEnabled, draft.hasCreditLimit, draft.creditLimitCents
                            )
                        }
                    )
                }
                state.showPaymentDialog && customer != null -> key(customer.id, state.isFullLiquidation) {
                    CustomerPaymentPaneV2(
                        customer = customer,
                        full = state.isFullLiquidation,
                        cashSessionReady = !cashSessionId.isNullOrBlank(),
                        busy = state.isSubmitting,
                        error = state.errorMessage,
                        onBack = ::back,
                        onConfirm = { cents, method, notes ->
                            viewModel.submitPayment(cashSessionId.orEmpty(), cashierUserId, cents, method, notes)
                        }
                    )
                }
                state.showAdjustLoyaltyDialog && customer != null -> key(customer.id) {
                    CustomerWalletAdjustmentV2(
                        customer = customer,
                        busy = state.isSubmitting,
                        error = state.errorMessage,
                        onBack = ::back,
                        onConfirm = { cents, reason -> viewModel.adjustLoyaltyBalance(cents, reason, cashierUserId) }
                    )
                }
                state.showWhatsAppDialog -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(state.whatsAppDialogTitle, style = MaterialTheme.typography.titleMedium)
                    ShiftWhatsAppPane(
                        message = state.whatsAppMessageText,
                        countryCode = viewModel.currentSettings?.whatsappCountryCode ?: "52",
                        error = shareError,
                        onBack = ::back,
                        onCopy = onCopyText,
                        onOpen = onOpenWhatsApp,
                        onOpened = ::back,
                        onError = { shareError = it },
                        initialPhone = state.whatsAppInitialPhone
                    )
                }
                else -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!showCompactDetail) CustomerDirectoryV2(
                        state = state,
                        listState = listState,
                        modifier = if (compact) Modifier.weight(1f).fillMaxHeight()
                        else Modifier.width(320.dp).fillMaxHeight(),
                        onSearch = viewModel::onSearchQueryChanged,
                        onToggleInactive = viewModel::onToggleIncludeInactive,
                        onSelect = {
                            viewModel.selectCustomer(it)
                            compactDetail = true
                        }
                    )
                    if (!compact || showCompactDetail) {
                        if (customer == null) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text("Selecciona un cliente para abrir su ficha.")
                            }
                        } else key(customer.id) {
                            CustomerDetailV2(
                                customer = customer,
                                state = state,
                                tab = tab,
                                onTab = ::showTab,
                                cashSessionReady = !cashSessionId.isNullOrBlank(),
                                onEdit = { viewModel.openEditDialog(customer) },
                                onLoyalty = { showTab(CustomerTab.WALLET) },
                                onAdjustLoyalty = viewModel::openAdjustLoyaltyDialog,
                                onReloadWallet = { viewModel.loadLoyaltyMovements(customer.id) },
                                onDeactivate = { pendingDeactivation = customer },
                                onPayment = {
                                    showTab(CustomerTab.STATEMENT)
                                    viewModel.openPaymentDialog(it)
                                },
                                onRetry = { viewModel.loadCustomerStatement(customer.id) },
                                onShare = { viewModel.openStatementWhatsAppDialog(customer) },
                                onShareReceipt = { viewModel.openPaymentReceiptWhatsAppDialog(customer, it) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerNotice(text: String, error: Boolean, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { ShiftActionMessage(text, isError = error) }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Clear, "Ocultar aviso") }
    }
}

@Composable
private fun CustomerDirectoryV2(
    state: CustomersUiState,
    listState: LazyListState,
    modifier: Modifier,
    onSearch: (String) -> Unit,
    onToggleInactive: () -> Unit,
    onSelect: (CustomerDto) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.searchQuery.isEmpty()) null else {
                    { IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, "Limpiar búsqueda") } }
                },
                label = { Text("Buscar cliente") },
                placeholder = { Text("Nombre, folio, teléfono o RFC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
                selected = state.includeInactive,
                onClick = onToggleInactive,
                label = { Text("Incluir inactivos") }
            )
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.customers.isEmpty() && !state.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay clientes para esta búsqueda.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.customers, key = { it.id }) { customer ->
                        val selected = customer.id == state.selectedCustomer?.id
                        OutlinedCard(
                            onClick = { onSelect(customer) },
                            enabled = !state.isLoading && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selected) colors.secondaryContainer else colors.surface
                            )
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val details = listOf(customer.folio, customer.phone, if (customer.isActive) "" else "Inactivo")
                                    .filter { it.isNotBlank() }
                                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDetailV2(
    customer: CustomerDto,
    state: CustomersUiState,
    tab: CustomerTab,
    onTab: (CustomerTab) -> Unit,
    cashSessionReady: Boolean,
    onEdit: () -> Unit,
    onLoyalty: () -> Unit,
    onAdjustLoyalty: () -> Unit,
    onReloadWallet: () -> Unit,
    onDeactivate: () -> Unit,
    onPayment: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onShareReceipt: (CreditMovementDto) -> Unit,
    modifier: Modifier
) {
    val statement = state.statement?.takeIf { it.customer.id == customer.id }
    val canPay = customer.currentBalanceCents > 0 && cashSessionReady && !state.isSubmitting
    OutlinedCard(modifier, shape = MaterialTheme.shapes.large) {
        Column {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(customer.folio, if (customer.isActive) "Activo" else "Inactivo").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onPayment(false) }, enabled = canPay) { Text("Registrar abono") }
                    OutlinedButton(onClick = { onPayment(true) }, enabled = canPay) { Text("Liquidar cuenta") }
                    TextButton(onClick = onEdit, enabled = !state.isSubmitting) { Text("Editar ficha") }
                }
                if (customer.currentBalanceCents > 0 && !cashSessionReady) {
                    Text("Abre un turno de caja para registrar abonos.", style = MaterialTheme.typography.bodySmall)
                }
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                CustomerTab.entries.forEach { option ->
                    Tab(selected = tab == option, onClick = { onTab(option) }, text = { Text(option.label) })
                }
            }
            when (tab) {
                CustomerTab.PROFILE -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomerAmount("Saldo pendiente", Money(customer.currentBalanceCents).format())
                            CustomerAmount(
                                "Crédito disponible",
                                when {
                                    !customer.creditEnabled -> "Deshabilitado"
                                    !customer.hasCreditLimit -> "Sin límite"
                                    else -> Money(customer.availableCreditCents).format()
                                }
                            )
                            CustomerAmount("Monedero", Money(customer.loyaltyBalanceCents).format())
                        }
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                    val details = listOf(
                        "Teléfono" to customer.phone, "Correo" to customer.email, "RFC" to customer.rfc,
                        "Dirección" to customer.address, "Observaciones" to customer.notes
                    ).filter { it.second.isNotBlank() }
                    items(details, key = { it.first }) { (label, value) -> CustomerAmount(label, value) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onLoyalty, enabled = !state.isSubmitting) { Text("Ver monedero") }
                            if (customer.isActive) {
                                TextButton(
                                    onClick = onDeactivate,
                                    enabled = !state.isSubmitting,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Desactivar cliente") }
                            }
                        }
                    }
                }
                CustomerTab.WALLET -> CustomerWalletV2(
                    customer = customer,
                    state = state,
                    onAdjust = onAdjustLoyalty,
                    onRetry = onReloadWallet,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                CustomerTab.STATEMENT -> Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                    when {
                        state.isStatementLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.statementErrorMessage != null -> {
                            ShiftActionMessage(state.statementErrorMessage, isError = true)
                            TextButton(onClick = onRetry) { Text("Reintentar") }
                        }
                        statement == null -> TextButton(onClick = onRetry) { Text("Consultar estado de cuenta") }
                        else -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomerAmount("Cargos", Money(statement.totalChargesCents).format())
                                CustomerAmount("Abonos", Money(statement.totalPaymentsCents).format())
                                TextButton(onClick = onShare) { Text("Compartir estado de cuenta") }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            if (statement.movements.isEmpty()) {
                                Text("Este cliente aún no tiene movimientos.", Modifier.padding(8.dp))
                            } else {
                                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(statement.movements.asReversed(), key = { it.id }) {
                                        CustomerMovementRow(it, onShareReceipt)
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

@Composable
private fun CustomerAmount(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun CustomerMovementRow(movement: CreditMovementDto, onShareReceipt: (CreditMovementDto) -> Unit) {
    val kind = when (movement.type) {
        "CHARGE" -> "Cargo"
        "PAYMENT" -> "Abono"
        "REVERSAL" -> "Reversión"
        "ADJUSTMENT" -> "Ajuste"
        else -> movement.type
    }
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(kind, style = MaterialTheme.typography.titleSmall)
                if (movement.description.isNotBlank()) Text(movement.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        formatShiftHistoryTimestamp(movement.createdAt, includeDate = true),
                        movement.receiptFolio?.let { "Recibo $it" },
                        movement.userName.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(movement.amountCents).format(), style = MaterialTheme.typography.titleSmall)
                Text("Saldo ${Money(movement.balanceAfterCents).format()}", style = MaterialTheme.typography.bodySmall)
            }
            if (movement.type == "PAYMENT") {
                IconButton(onClick = { onShareReceipt(movement) }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, "Compartir recibo de abono")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomerWalletV2(
    customer: CustomerDto,
    state: CustomersUiState,
    onAdjust: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CustomerAmount("Saldo disponible", Money(customer.loyaltyBalanceCents).format())
                CustomerAmount("Acumulado", Money(customer.totalLoyaltyEarnedCents).format())
                CustomerAmount("Canjeado", Money(customer.totalLoyaltyRedeemedCents).format())
                OutlinedButton(onClick = onAdjust, enabled = !state.isSubmitting) { Text("Ajustar saldo") }
            }
        }
        when {
            state.isLoyaltyLoading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.loyaltyErrorMessage != null -> item {
                ShiftActionMessage(state.loyaltyErrorMessage, isError = true)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
            state.loyaltyMovements.isEmpty() -> item { Text("Este monedero aún no tiene movimientos.") }
            else -> items(state.loyaltyMovements, key = { it.id }) { movement ->
                val label = when (movement.type) {
                    "EARN" -> "Acumulación"
                    "REDEEM" -> "Canje"
                    "ADJUSTMENT" -> "Ajuste"
                    "REFUND_REVERSAL" -> "Reversión"
                    "REFUND_CREDIT" -> "Reembolso"
                    else -> movement.type
                }
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = {
                        Column {
                            if (movement.description.isNotBlank()) Text(movement.description)
                            Text(formatShiftHistoryTimestamp(movement.createdAt, includeDate = true))
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money(movement.amountCents).format())
                            Text("Saldo ${Money(movement.balanceAfterCents).format()}", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
        }
    }
}
