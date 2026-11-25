package mx.nexuspos.client.features.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    cashSessionId: String = "session-active",
    cashierUserId: String = "usr-cajero",
    onOpenWhatsApp: (url: String) -> Unit = {},
    onCopyText: (text: String) -> Unit = {},
    onBackToSales: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Dialogs
    if (state.showFormDialog) {
        CustomerFormDialog(
            customerToEdit = state.customerToEdit,
            onSave = { name, phone, email, rfc, address, notes, creditEnabled, hasLimit, limitCents ->
                viewModel.saveCustomer(name, phone, email, rfc, address, notes, creditEnabled, hasLimit, limitCents)
            },
            onDismiss = { viewModel.closeFormDialog() }
        )
    }

    if (state.showPaymentDialog && state.selectedCustomer != null) {
        CreditPaymentDialog(
            customer = state.selectedCustomer!!,
            isFullLiquidation = state.isFullLiquidation,
            onConfirmPayment = { amountCents, method, notes ->
                viewModel.submitPayment(cashSessionId, cashierUserId, amountCents, method, notes)
            },
            onDismiss = { viewModel.closePaymentDialog() }
        )
    }

    if (state.showLoyaltyDialog && state.selectedCustomer != null) {
        CustomerLoyaltyDialog(
            isOpen = true,
            customer = state.selectedCustomer,
            movements = state.loyaltyMovements,
            isLoading = state.isLoyaltyLoading,
            onAdjustBalance = { amountCents, reason ->
                viewModel.adjustLoyaltyBalance(amountCents, reason, cashierUserId)
            },
            onDismiss = { viewModel.closeLoyaltyDialog() }
        )
    }

    if (state.showWhatsAppDialog) {
        mx.nexuspos.client.features.whatsapp.WhatsAppSendDialog(
            isOpen = true,
            title = state.whatsAppDialogTitle,
            initialPhone = state.whatsAppInitialPhone,
            messageText = state.whatsAppMessageText,
            defaultCountryCode = viewModel.currentSettings?.whatsappCountryCode ?: "52",
            onSend = onOpenWhatsApp,
            onCopyText = onCopyText,
            onDismiss = { viewModel.closeWhatsAppDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.bgCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP TOOLBAR & NOTIFICATIONS
        CustomersTopToolbar(
            searchQuery = state.searchQuery,
            includeInactive = state.includeInactive,
            onSearchChanged = { viewModel.onSearchQueryChanged(it) },
            onToggleInactive = { viewModel.onToggleIncludeInactive() },
            onNewCustomer = { viewModel.openCreateDialog() },
            onBackToSales = onBackToSales
        )

        // Banners de notificación unificados
        FeedbackMessageHost(
            errorMessage = state.errorMessage,
            successMessage = state.successMessage,
            onDismissError = viewModel::dismissMessages,
            onDismissSuccess = viewModel::dismissMessages
        )

        // MAIN SPLIT CONTENT
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // LEFT PANEL: Listado de Clientes (360dp)
            CustomersListPanel(
                customers = state.customers,
                selectedCustomer = state.selectedCustomer,
                isLoading = state.isLoading,
                onSelectCustomer = { viewModel.selectCustomer(it) },
                modifier = Modifier.width(360.dp).fillMaxHeight()
            )

            // RIGHT PANEL: Detalle, Tarjetas de Crédito y Estado de Cuenta
            CustomerDetailPanel(
                customer = state.selectedCustomer,
                statement = state.statement,
                isStatementLoading = state.isStatementLoading,
                onEdit = { cust -> viewModel.openEditDialog(cust) },
                onPayment = { viewModel.openPaymentDialog(false) },
                onLiquidate = { viewModel.openPaymentDialog(true) },
                onReactivate = viewModel::reactivateCustomer,
                isSubmitting = state.isSubmitting,
                onOpenLoyalty = { cust -> viewModel.openLoyaltyDialog(cust) },
                onSendWhatsAppStatement = {
                    state.selectedCustomer?.let { viewModel.openStatementWhatsAppDialog(it) }
                },
                onSendPaymentReceipt = { mov ->
                    state.selectedCustomer?.let { viewModel.openPaymentReceiptWhatsAppDialog(it, mov) }
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CustomersTopToolbar(
    searchQuery: String,
    includeInactive: Boolean,
    onSearchChanged: (String) -> Unit,
    onToggleInactive: () -> Unit,
    onNewCustomer: () -> Unit,
    onBackToSales: () -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar por nombre, folio, teléfono o RFC...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = NexusTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = NexusTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.width(380.dp).height(48.dp)
                )

                FilterChip(
                    selected = includeInactive,
                    onClick = onToggleInactive,
                    label = { Text("Ver Inactivos") },
                    leadingIcon = {
                        if (includeInactive) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onNewCustomer,
                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("[INS] Nuevo Cliente", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onBackToSales,
                    border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp), tint = NexusTheme.colors.btnSecondaryText)
                    Spacer(Modifier.width(6.dp))
                    Text("[Esc] Ventas", color = NexusTheme.colors.btnSecondaryText)
                }
            }
        }
    }
}

@Composable
private fun CustomersListPanel(
    customers: List<CustomerDto>,
    selectedCustomer: CustomerDto?,
    isLoading: Boolean,
    onSelectCustomer: (CustomerDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catálogo (${customers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }

            Divider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusTheme.colors.accent)
                }
            } else if (customers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No se encontraron clientes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(customers, key = { it.id }) { customer ->
                        val isSelected = customer.id == selectedCustomer?.id
                        CustomerListItem(
                            customer = customer,
                            isSelected = isSelected,
                            onClick = { onSelectCustomer(customer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerListItem(
    customer: CustomerDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val debtPesos = customer.currentBalanceCents / 100.0
    val hasDebt = customer.currentBalanceCents > 0L

    Surface(
        color = if (isSelected) NexusTheme.colors.accentLight else NexusTheme.colors.bgSurfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) NexusTheme.colors.accent else NexusTheme.colors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = customer.folio,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = NexusTheme.colors.accent
                    )
                    if (!customer.isActive) {
                        Surface(
                            color = NexusTheme.colors.dangerBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "INACTIVO",
                                color = NexusTheme.colors.dangerText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (customer.phone.isNotBlank()) {
                    Text(
                        text = "Tel: ${customer.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hasDebt) "Deuda: $${"%.2f".format(debtPesos)}" else "$0.00",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (hasDebt) NexusTheme.colors.dangerText else NexusTheme.colors.actionPrimary
                )
                if (customer.loyaltyBalanceCents > 0L) {
                    Text(
                        text = "Monedero: $${"%.2f".format(customer.loyaltyBalanceCents / 100.0)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                } else {
                    Text(
                        text = if (!customer.creditEnabled) "Sin Crédito" else if (!customer.hasCreditLimit) "Ilimitado" else "Disp: $${"%.0f".format(customer.availableCreditCents / 100.0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailPanel(
    customer: CustomerDto?,
    statement: mx.nexuspos.contracts.credit.CustomerStatementDto?,
    isStatementLoading: Boolean,
    onEdit: (CustomerDto) -> Unit,
    onPayment: () -> Unit,
    onLiquidate: () -> Unit,
    onReactivate: (CustomerDto) -> Unit,
    isSubmitting: Boolean,
    onOpenLoyalty: (CustomerDto) -> Unit,
    onSendWhatsAppStatement: () -> Unit = {},
    onSendPaymentReceipt: (CreditMovementDto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        if (customer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBox, contentDescription = null, modifier = Modifier.size(48.dp), tint = NexusTheme.colors.textSecondary)
                    Text(
                        text = "Seleccione un cliente para ver su detalle y estado de cuenta",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. FICHA SUPERIOR CON DATOS DEL CLIENTE Y ACCIONES
                CustomerHeaderCard(
                    customer = customer,
                    onEdit = { onEdit(customer) },
                    onPayment = onPayment,
                    onLiquidate = onLiquidate,
                    onReactivate = { onReactivate(customer) },
                    isSubmitting = isSubmitting,
                    onOpenLoyalty = { onOpenLoyalty(customer) },
                    onSendWhatsAppStatement = onSendWhatsAppStatement
                )

                // 2. METRICAS FINANCIERAS DE CREDITO
                CustomerCreditMetricsRow(customer = customer)

                Divider(color = NexusTheme.colors.borderSubtle)

                // 3. ESTADO DE CUENTA (TABLA DE MOVIMIENTOS)
                CustomerStatementSection(
                    statement = statement,
                    isLoading = isStatementLoading,
                    onSendPaymentReceipt = onSendPaymentReceipt,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CustomerHeaderCard(
    customer: CustomerDto,
    onEdit: () -> Unit,
    onPayment: () -> Unit,
    onLiquidate: () -> Unit,
    onReactivate: () -> Unit,
    isSubmitting: Boolean,
    onOpenLoyalty: () -> Unit,
    onSendWhatsAppStatement: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = NexusTheme.colors.accentLight,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.accent)
                ) {
                    Text(
                        text = customer.folio,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = NexusTheme.colors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (customer.phone.isNotBlank()) {
                    Text("📞 ${customer.phone}", style = MaterialTheme.typography.bodySmall, color = NexusTheme.colors.textSecondary)
                }
                if (customer.email.isNotBlank()) {
                    Text("✉️ ${customer.email}", style = MaterialTheme.typography.bodySmall, color = NexusTheme.colors.textSecondary)
                }
                if (customer.rfc.isNotBlank()) {
                    Text("🏢 RFC: ${customer.rfc}", style = MaterialTheme.typography.bodySmall, color = NexusTheme.colors.textSecondary)
                }
            }

            if (customer.address.isNotBlank()) {
                Text("📍 ${customer.address}", style = MaterialTheme.typography.bodySmall, color = NexusTheme.colors.textSecondary)
            }
            if (customer.notes.isNotBlank()) {
                Text("📝 Nota: ${customer.notes}", style = MaterialTheme.typography.bodySmall, color = NexusTheme.colors.textSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            if (!customer.isActive) {
                OutlinedButton(onClick = onReactivate, enabled = !isSubmitting) {
                    Text("Reactivar cliente")
                }
            }
        }

        // Acciones Operativas
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val hasDebt = customer.currentBalanceCents > 0L

            Button(
                onClick = onPayment,
                enabled = hasDebt,
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.PriceCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("[F4] Abonar", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onLiquidate,
                enabled = hasDebt,
                border = BorderStroke(1.dp, if (hasDebt) NexusTheme.colors.accent else NexusTheme.colors.borderSubtle),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("[F8] Liquidar Todo", color = if (hasDebt) NexusTheme.colors.accent else NexusTheme.colors.textSecondary)
            }

            OutlinedButton(
                onClick = onSendWhatsAppStatement,
                border = BorderStroke(1.dp, Color(0xFF25D366)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF25D366))
                Spacer(Modifier.width(4.dp))
                Text("WhatsApp", color = Color(0xFF1E7E34), fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onOpenLoyalty,
                border = BorderStroke(1.dp, Color(0xFF059669)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF059669))
                Spacer(Modifier.width(4.dp))
                Text("Monedero ($${"%.2f".format(customer.loyaltyBalanceCents / 100.0)})", color = Color(0xFF059669), fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onEdit,
                border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = NexusTheme.colors.btnSecondaryText)
                Spacer(Modifier.width(4.dp))
                Text("Editar", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    }
}

@Composable
private fun CustomerCreditMetricsRow(customer: CustomerDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Límite Autorizado
        CreditMetricBox(
            title = "Límite Autorizado",
            value = if (!customer.creditEnabled) "NO AUTORIZADO" else if (!customer.hasCreditLimit) "SIN LÍMITE" else "$${"%.2f".format(customer.creditLimitCents / 100.0)}",
            valueColor = NexusTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        // Saldo Actual Deudor
        CreditMetricBox(
            title = "Saldo Pendiente (Deuda)",
            value = "$${"%.2f".format(customer.currentBalanceCents / 100.0)}",
            valueColor = if (customer.currentBalanceCents > 0L) NexusTheme.colors.dangerText else NexusTheme.colors.actionPrimary,
            modifier = Modifier.weight(1f)
        )

        // Crédito Disponible
        CreditMetricBox(
            title = "Crédito Disponible",
            value = if (!customer.creditEnabled) "$0.00" else if (!customer.hasCreditLimit) "ILIMITADO" else "$${"%.2f".format(customer.availableCreditCents / 100.0)}",
            valueColor = NexusTheme.colors.totalDisplay,
            modifier = Modifier.weight(1f)
        )

        // Monedero Electrónico
        CreditMetricBox(
            title = "Monedero Electrónico",
            value = "$${"%.2f".format(customer.loyaltyBalanceCents / 100.0)}",
            valueColor = if (customer.loyaltyBalanceCents > 0L) Color(0xFF059669) else NexusTheme.colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CreditMetricBox(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = NexusTheme.colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = valueColor)
        }
    }
}

@Composable
private fun CustomerStatementSection(
    statement: mx.nexuspos.contracts.credit.CustomerStatementDto?,
    isLoading: Boolean,
    onSendPaymentReceipt: (CreditMovementDto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Estado de Cuenta (Historial de Movimientos)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )

            if (statement != null) {
                Text(
                    text = "Total Cargos: $${"%.2f".format(statement.totalChargesCents / 100.0)} | Total Pagos: $${"%.2f".format(statement.totalPaymentsCents / 100.0)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusTheme.colors.accent)
            }
        } else if (statement == null || statement.movements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hay movimientos registrados en la cuenta de este cliente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary
                )
            }
        } else {
            // Tabla de Movimientos
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Encabezados
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexusTheme.colors.bgSurfaceSubtle)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FECHA / HORA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(130.dp))
                        Text("TIPO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
                        Text("DETALLE / NOTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                        Text("IMPORTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(90.dp))
                        Text("SALDO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(90.dp))
                        Text("CAJERO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                        Text("RECIBO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                    }

                    Divider(color = NexusTheme.colors.borderPrimary)

                    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(statement.movements.reversed(), key = { it.id }) { mov ->
                            StatementMovementRow(mov = mov, dateFormat = dateFormat, onSendPaymentReceipt = onSendPaymentReceipt)
                            Divider(color = NexusTheme.colors.borderSubtle.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementMovementRow(
    mov: CreditMovementDto,
    dateFormat: SimpleDateFormat,
    onSendPaymentReceipt: (CreditMovementDto) -> Unit = {}
) {
    val isCharge = mov.type == "CHARGE"
    val isPayment = mov.type == "PAYMENT"
    val isReversal = mov.type == "REVERSAL"

    val typeColor = when {
        isCharge -> NexusTheme.colors.dangerText
        isPayment -> NexusTheme.colors.actionPrimary
        isReversal -> NexusTheme.colors.warningText
        else -> NexusTheme.colors.textPrimary
    }

    val typeLabel = when (mov.type) {
        "CHARGE" -> "CARGO"
        "PAYMENT" -> "ABONO"
        "REVERSAL" -> "CANCELADO"
        else -> mov.type
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateFormat.format(Date(mov.createdAt)),
            fontSize = 11.5.sp,
            color = NexusTheme.colors.textSecondary,
            modifier = Modifier.width(130.dp)
        )

        Surface(
            color = typeColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                text = typeLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = typeColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))

        Text(
            text = mov.description,
            fontSize = 11.5.sp,
            color = NexusTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${if (isCharge) "+" else "-"}$${"%.2f".format(mov.amountCents / 100.0)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = typeColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(90.dp)
        )

        Text(
            text = "$${"%.2f".format(mov.balanceAfterCents / 100.0)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NexusTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(90.dp)
        )

        Text(
            text = mov.userName.ifBlank { "Cajero" },
            fontSize = 11.sp,
            color = NexusTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(80.dp)
        )

        Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
            if (isPayment) {
                IconButton(
                    onClick = { onSendPaymentReceipt(mov) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Enviar recibo por WhatsApp",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
