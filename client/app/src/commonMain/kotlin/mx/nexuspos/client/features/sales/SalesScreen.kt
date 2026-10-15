package mx.nexuspos.client.features.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.cash.CashSessionDialog
import mx.nexuspos.client.features.credit.CustomerSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(viewModel: SalesViewModel = remember { SalesViewModel() }) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("NexusPOS", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("• Abarrotes y Miscelánea", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = {
                    // Server Connection Pill
                    val (connBg, connText) = when (state.connectionState) {
                        ServerConnectionState.CONNECTED -> Color(0xFF2E7D32) to "🟢 Servidor Conectado"
                        ServerConnectionState.RECONNECTING -> Color(0xFFF57F17) to "🟡 Reconectando LAN..."
                        ServerConnectionState.NO_SERVER -> Color(0xFFC62828) to "🔴 Sin Servidor — Cobro Bloqueado"
                    }
                    Surface(
                        color = connBg.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = connText,
                            color = connBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Cash Session Pill
                    val sess = state.activeSession
                    if (sess != null) {
                        FilledTonalButton(
                            onClick = { viewModel.showSessionDialog(true) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Turno Abierto ($${sess.initialFundCents / 100.0})", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.showSessionDialog(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Abrir Turno", fontSize = 12.sp)
                        }
                    }

                    // Cashier Name
                    Text(
                        "👤 ${state.activeCashierName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT COLUMN: Scanner & Cart Table (68%)
            Column(
                modifier = Modifier.weight(0.68f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Barcode scanner field
                OutlinedTextField(
                    value = state.scannerInput,
                    onValueChange = { viewModel.onScannerInputChanged(it) },
                    label = { Text("Escanear código de barras o teclear nombre y presionar Enter...") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner") },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.onScannerSubmitted() }) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = "Agregar")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.onScannerSubmitted() }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Alerts / Messages
                state.errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMessages() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                state.successMessage?.let { msg ->
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMessages() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF2E7D32))
                            }
                        }
                    }
                }

                // Cart Items List
                ElevatedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (state.cartLines.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                                Text("El carrito está vacío", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                                Text("Escanee productos con el lector o busque por nombre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.cartLines) { line ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Unit badge
                                        val unitColor = if (line.unit.code == "KG") Color(0xFF1565C0) else Color(0xFF00796B)
                                        Surface(color = unitColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                                            Text(line.unit.code, color = unitColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                                        }

                                        // Name and Barcode
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(line.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(line.barcode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        // Quantity
                                        Text(
                                            line.quantity.format(line.unit),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )

                                        // Unit Price
                                        Text(
                                            line.unitPrice.format(),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Total
                                        Text(
                                            line.total.format(),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        // Delete
                                        IconButton(onClick = { viewModel.removeCartLine(line.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Keyboard Shortcuts Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "[Enter] Escanear/Agregar   •   [F5] Asignar Fiado   •   [F12] Cobrar   •   [Supr] Eliminar",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // RIGHT COLUMN: Totals & Checkout (32%)
            Column(
                modifier = Modifier.weight(0.32f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Customer Card (Fiado / Crédito)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cliente (Fiado)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { viewModel.showCustomerDialog(true) }) {
                                Text(if (state.selectedCustomer == null) "Asignar [F5]" else "Cambiar")
                            }
                        }

                        val cust = state.selectedCustomer
                        if (cust != null) {
                            Text(cust.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Saldo pendiente:", fontSize = 12.sp)
                                Text("$${cust.currentBalanceCents / 100.0}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Crédito disponible:", fontSize = 12.sp)
                                Text(
                                    "$${cust.availableCreditCents / 100.0}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                "Venta de mostrador general (Público en general)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Totals Card
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal neto:", style = MaterialTheme.typography.bodyMedium)
                            Text(state.subtotal.format(), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Impuestos (IVA):", style = MaterialTheme.typography.bodyMedium)
                            Text(state.tax.format(), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(
                                state.total.format(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Action Buttons
                val canCheckout = state.cartLines.isNotEmpty() &&
                        state.activeSession != null &&
                        state.connectionState == ServerConnectionState.CONNECTED

                Button(
                    onClick = { viewModel.showCashPaymentDialog(true) },
                    enabled = canCheckout,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Cobrar Efectivo [F12]", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (state.selectedCustomer != null) {
                    FilledTonalButton(
                        onClick = { viewModel.checkoutCredit() },
                        enabled = canCheckout,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Fiar a Cuenta de Cliente", fontWeight = FontWeight.Bold)
                    }
                }

                if (state.cartLines.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearCart() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Vaciar Carrito", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.connectionState == ServerConnectionState.NO_SERVER) {
                    Text(
                        "⚠️ El cobro está bloqueado porque no hay conexión con el servidor autoritativo de la tienda. El carrito permanece seguro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        // Modals
        state.isWeighingProduct?.let { (prod, pres) ->
            WeightQuantityDialog(
                product = prod,
                presentation = pres,
                onConfirm = { qty -> viewModel.confirmWeighedProduct(qty) },
                onDismiss = { viewModel.dismissWeighingDialog() }
            )
        }

        if (state.isShowingCashPayment) {
            CashPaymentDialog(
                total = state.total,
                onConfirm = { receivedCents -> viewModel.checkoutCash(receivedCents) },
                onDismiss = { viewModel.showCashPaymentDialog(false) }
            )
        }

        if (state.isShowingCustomerDialog) {
            CustomerSelectionDialog(
                apiClient = viewModel.apiClient,
                currentCustomer = state.selectedCustomer,
                onSelectCustomer = { cust -> viewModel.selectCustomer(cust) },
                onDismiss = { viewModel.showCustomerDialog(false) }
            )
        }

        if (state.isShowingSessionDialog) {
            CashSessionDialog(
                activeSession = state.activeSession,
                onOpenSession = { fund -> viewModel.openCashSession(fund) },
                onCloseSession = { counted, notes -> viewModel.closeCashSession(counted, notes) },
                onDismiss = { viewModel.showSessionDialog(false) }
            )
        }
    }
}
