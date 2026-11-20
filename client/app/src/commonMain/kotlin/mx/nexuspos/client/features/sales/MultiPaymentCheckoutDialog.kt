package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.core.Money

enum class CheckoutPaymentMethod(val label: String, val methodCode: String) {
    CASH("Efectivo", "CASH"),
    CARD("Tarjeta", "CARD"),
    TRANSFER("Transferencia", "TRANSFER"),
    LOYALTY("Monedero", "LOYALTY"),
    CREDIT("Crédito / Fiado", "CREDIT")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiPaymentCheckoutDialog(
    isOpen: Boolean,
    total: Money,
    selectedCustomer: CustomerDto? = null,
    onRequestSelectCustomer: () -> Unit = {},
    onConfirmCheckout: (payments: List<SalePaymentRequest>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val totalCents = total.cents
    val addedPayments = remember { mutableStateListOf<SalePaymentRequest>() }
    var selectedMethod by remember { mutableStateOf(CheckoutPaymentMethod.CASH) }

    // Totals calculations
    val coveredCents = addedPayments.sumOf { it.amountCents }
    val remainingCents = maxOf(0L, totalCents - coveredCents)
    val isFullyCovered = coveredCents >= totalCents

    // Cash portion inputs
    var cashAmountText by remember(remainingCents) {
        mutableStateOf(if (remainingCents > 0L) (remainingCents / 100.0).toString() else "")
    }
    var cashReceivedText by remember(remainingCents) {
        mutableStateOf(if (remainingCents > 0L) (remainingCents / 100.0).toString() else "")
    }

    // Card inputs
    var cardAmountText by remember(remainingCents) {
        mutableStateOf(if (remainingCents > 0L) (remainingCents / 100.0).toString() else "")
    }
    var cardReferenceText by remember { mutableStateOf("") }

    // Transfer inputs
    var transferAmountText by remember(remainingCents) {
        mutableStateOf(if (remainingCents > 0L) (remainingCents / 100.0).toString() else "")
    }
    var transferReferenceText by remember { mutableStateOf("") }

    // Credit inputs
    var creditAmountText by remember(remainingCents) {
        mutableStateOf(if (remainingCents > 0L) (remainingCents / 100.0).toString() else "")
    }

    // Loyalty inputs
    var loyaltyAmountText by remember(remainingCents, selectedCustomer) {
        val custBalance = selectedCustomer?.loyaltyBalanceCents ?: 0L
        val maxUsable = minOf(remainingCents, custBalance)
        mutableStateOf(if (maxUsable > 0L) (maxUsable / 100.0).toString() else "")
    }

    // Cash change calculation
    val totalCashChangeCents = addedPayments
        .filter { it.method == "CASH" }
        .sumOf { maxOf(0L, it.receivedCents - it.amountCents) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            tonalElevation = 8.dp,
            modifier = modifier
                .width(720.dp)
                .height(640.dp)
                .padding(12.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else false
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Cobro de Venta (Multi-Pago)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Efectivo, Tarjeta, Transferencia o Crédito",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // KPI Status Cards (Total, Cubierto, Restante, Cambio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total a Cobrar
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TOTAL A COBRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(total.format(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Monto Cubierto
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgCanvas),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("CUBIERTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                            Text(Money(coveredCents).format(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = NexusTheme.colors.textPrimary)
                        }
                    }

                    // Restante o Cambio
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFullyCovered) Color(0xFF059669).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, if (isFullyCovered) Color(0xFF059669).copy(alpha = 0.4f) else Color(0xFFDC2626).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isFullyCovered) "CAMBIO" else "RESTA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFullyCovered) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                            Text(
                                text = if (isFullyCovered) Money(totalCashChangeCents).format() else Money(remainingCents).format(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFullyCovered) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // Quick Fast-Cash Button if no payments have been added yet
                if (addedPayments.isEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val fastReceived = (cashReceivedText.toDoubleOrNull() ?: (totalCents / 100.0)) * 100.0
                            val p = SalePaymentRequest(
                                method = "CASH",
                                amountCents = totalCents,
                                receivedCents = maxOf(totalCents, fastReceived.toLong())
                            )
                            onConfirmCheckout(listOf(p))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Cobro Rápido en Efectivo (Exacto / Total: ${total.format()})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Method Selector Tabs
                Text(
                    text = "Seleccionar Forma de Pago a Agregar:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CheckoutPaymentMethod.entries.forEach { method ->
                        val isSelected = method == selectedMethod
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMethod = method },
                            label = { Text(method.label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (method) {
                                        CheckoutPaymentMethod.CASH -> Icons.Default.Payments
                                        CheckoutPaymentMethod.CARD -> Icons.Default.CreditCard
                                        CheckoutPaymentMethod.TRANSFER -> Icons.Default.AccountBalance
                                        CheckoutPaymentMethod.LOYALTY -> Icons.Default.AccountBalanceWallet
                                        CheckoutPaymentMethod.CREDIT -> Icons.Default.Receipt
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dynamic Form based on selected method
                Card(
                    colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgCanvas),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedMethod) {
                            CheckoutPaymentMethod.CASH -> {
                                Text("Pago con Efectivo", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cashAmountText,
                                        onValueChange = { cashAmountText = it },
                                        label = { Text("Importe a Pagar ($)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = cashReceivedText,
                                        onValueChange = { cashReceivedText = it },
                                        label = { Text("Efectivo Entregado ($)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            val amt = ((cashAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                            val rec = ((cashReceivedText.toDoubleOrNull() ?: (amt / 100.0)) * 100.0).toLong()
                                            if (amt > 0L) {
                                                addedPayments.add(SalePaymentRequest("CASH", amt, maxOf(amt, rec), ""))
                                            }
                                        }),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Quick Cash Denomination Buttons
                                Text("Denominaciones rápidas:", fontSize = 11.sp, color = NexusTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            cashAmountText = (remainingCents / 100.0).toString()
                                            cashReceivedText = (remainingCents / 100.0).toString()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Exacto ($${remainingCents / 100.0})", fontSize = 11.sp)
                                    }
                                    listOf(20, 50, 100, 200, 500, 1000).forEach { denom ->
                                        OutlinedButton(
                                            onClick = {
                                                cashReceivedText = denom.toString()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("$$denom", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val amt = ((cashAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                        val rec = ((cashReceivedText.toDoubleOrNull() ?: (amt / 100.0)) * 100.0).toLong()
                                        if (amt > 0L) {
                                            addedPayments.add(SalePaymentRequest("CASH", amt, maxOf(amt, rec), ""))
                                        }
                                    },
                                    enabled = (cashAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Agregar Pago en Efectivo")
                                }
                            }

                            CheckoutPaymentMethod.CARD -> {
                                Text("Pago con Tarjeta Bancaria (Débito / Crédito)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = cardAmountText,
                                        onValueChange = { cardAmountText = it },
                                        label = { Text("Importe a Tarjeta ($)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.5f)
                                    )

                                    OutlinedTextField(
                                        value = cardReferenceText,
                                        onValueChange = { cardReferenceText = it },
                                        label = { Text("Voucher / Referencia (Opcional)") },
                                        placeholder = { Text("ej. 4812 Banorte") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.5f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        val amt = ((cardAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                        if (amt > 0L) {
                                            addedPayments.add(SalePaymentRequest("CARD", amt, amt, cardReferenceText.trim()))
                                            cardReferenceText = ""
                                        }
                                    },
                                    enabled = (cardAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Agregar Pago con Tarjeta")
                                }
                            }

                            CheckoutPaymentMethod.TRANSFER -> {
                                Text("Pago con Transferencia Electrónica (SPEI / CoDi)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = transferAmountText,
                                        onValueChange = { transferAmountText = it },
                                        label = { Text("Importe Transferencia ($)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.5f)
                                    )

                                    OutlinedTextField(
                                        value = transferReferenceText,
                                        onValueChange = { transferReferenceText = it },
                                        label = { Text("Clave Rastreo / Folio (Opcional)") },
                                        placeholder = { Text("ej. SPEI 99281") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.5f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        val amt = ((transferAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                        if (amt > 0L) {
                                            addedPayments.add(SalePaymentRequest("TRANSFER", amt, amt, transferReferenceText.trim()))
                                            transferReferenceText = ""
                                        }
                                    },
                                    enabled = (transferAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Agregar Pago por Transferencia")
                                }
                            }

                            CheckoutPaymentMethod.LOYALTY -> {
                                Text("Pago con Monedero Electrónico", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                if (selectedCustomer != null) {
                                    val loyaltyBal = selectedCustomer.loyaltyBalanceCents
                                    Surface(
                                        color = NexusTheme.colors.bgSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(selectedCustomer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(
                                                    "Saldo en Monedero: $${loyaltyBal / 100.0}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (loyaltyBal > 0) Color(0xFF059669) else NexusTheme.colors.textSecondary
                                                )
                                            }
                                            TextButton(onClick = onRequestSelectCustomer) {
                                                Text("Cambiar", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    if (loyaltyBal <= 0L) {
                                        Text(
                                            "El cliente no cuenta con saldo acumulado en su monedero electrónico.",
                                            fontSize = 12.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val maxUsable = minOf(remainingCents, loyaltyBal)
                                            FilledTonalButton(
                                                onClick = {
                                                    loyaltyAmountText = (maxUsable / 100.0).toString()
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Pagar Restante ($${maxUsable / 100.0})", fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    loyaltyAmountText = (loyaltyBal / 100.0).toString()
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Usar Todo ($${loyaltyBal / 100.0})", fontSize = 11.sp)
                                            }
                                        }

                                        OutlinedTextField(
                                            value = loyaltyAmountText,
                                            onValueChange = { loyaltyAmountText = it },
                                            label = { Text("Importe a Redimir ($)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        val enteredLoyaltyAmt = ((loyaltyAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                        val isOverBalance = enteredLoyaltyAmt > loyaltyBal

                                        if (isOverBalance) {
                                            Text(
                                                "El monto excede el saldo disponible del cliente ($${loyaltyBal / 100.0})",
                                                fontSize = 11.sp,
                                                color = Color(0xFFDC2626)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (enteredLoyaltyAmt > 0L && !isOverBalance) {
                                                    addedPayments.add(SalePaymentRequest("LOYALTY", enteredLoyaltyAmt, 0L, "Monedero: ${selectedCustomer.name}"))
                                                }
                                            },
                                            enabled = enteredLoyaltyAmt > 0L && !isOverBalance,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Agregar Pago con Monedero")
                                        }
                                    }
                                } else {
                                    Surface(
                                        color = Color(0xFFDC2626).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                                Text("Se requiere seleccionar un cliente para usar su monedero", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = onRequestSelectCustomer,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Seleccionar Cliente")
                                            }
                                        }
                                    }
                                }
                            }

                            CheckoutPaymentMethod.CREDIT -> {
                                Text("Cargo a Crédito Fiado del Cliente", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                if (selectedCustomer != null) {
                                    Surface(
                                        color = NexusTheme.colors.bgSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(selectedCustomer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Saldo actual: $${selectedCustomer.currentBalanceCents / 100.0} • Disp: $${selectedCustomer.availableCreditCents / 100.0}", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                            }
                                            TextButton(onClick = onRequestSelectCustomer) {
                                                Text("Cambiar", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = creditAmountText,
                                        onValueChange = { creditAmountText = it },
                                        label = { Text("Importe a Crédito ($)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            val amt = ((creditAmountText.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                            if (amt > 0L) {
                                                addedPayments.add(SalePaymentRequest("CREDIT", amt, 0L, selectedCustomer.name))
                                            }
                                        },
                                        enabled = (creditAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Agregar Cargo a Crédito")
                                    }
                                } else {
                                    Surface(
                                        color = Color(0xFFDC2626).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                                Text("Se requiere seleccionar un cliente para fiado", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = onRequestSelectCustomer,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Seleccionar Cliente")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Added Payments List
                if (addedPayments.isNotEmpty()) {
                    Text(
                        text = "Pagos Agregados (${addedPayments.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        addedPayments.forEachIndexed { index, p ->
                            Surface(
                                color = NexusTheme.colors.bgCanvas,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (p.method) {
                                                "CASH" -> Icons.Default.Payments
                                                "CARD" -> Icons.Default.CreditCard
                                                "TRANSFER" -> Icons.Default.AccountBalance
                                                "LOYALTY" -> Icons.Default.AccountBalanceWallet
                                                else -> Icons.Default.Receipt
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = when (p.method) {
                                                    "CASH" -> "Efectivo"
                                                    "CARD" -> "Tarjeta" + if (p.reference.isNotBlank()) " (${p.reference})" else ""
                                                    "TRANSFER" -> "Transferencia" + if (p.reference.isNotBlank()) " (${p.reference})" else ""
                                                    "LOYALTY" -> "Monedero Electrónico"
                                                    else -> "Crédito Fiado"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            if (p.method == "CASH" && p.receivedCents > p.amountCents) {
                                                Text(
                                                    "Recibido: $${p.receivedCents / 100.0} • Cambio: $${(p.receivedCents - p.amountCents) / 100.0}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF059669)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = Money(p.amountCents).format(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                        IconButton(
                                            onClick = { addedPayments.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Dialog Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.35f)
                    ) {
                        Text("Cancelar [Esc]")
                    }

                    Button(
                        onClick = {
                            if (isFullyCovered) {
                                onConfirmCheckout(addedPayments.toList())
                            }
                        },
                        enabled = isFullyCovered,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFullyCovered) NexusTheme.colors.actionPrimary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(0.65f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isFullyCovered) "Confirmar y Cobrar [F12]" else "Faltan $${remainingCents / 100.0}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
