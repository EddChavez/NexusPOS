package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.CheckoutPaymentMethod
import mx.nexuspos.client.features.sales.CheckoutPaymentMethod.*
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.core.Money

internal data class CheckoutPaymentInput(
    val amount: String,
    val received: String,
    val reference: String = ""
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckoutMethodPicker(
    selectedMethod: CheckoutPaymentMethod,
    onSelectMethod: (CheckoutPaymentMethod) -> Unit,
    isOffline: Boolean,
    enabled: Boolean
) {
    Column {
        Text("Forma de pago", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckoutPaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = selectedMethod == method,
                    enabled = enabled && (!isOffline || !method.requiresCustomer),
                    onClick = { onSelectMethod(method) },
                    label = { Text(method.displayLabel) },
                    leadingIcon = { Icon(method.icon, null, Modifier.size(18.dp)) }
                )
            }
        }
        if (isOffline) {
            Text(
                "Monedero y crédito requieren conexión al servidor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckoutPaymentEntry(
    selectedMethod: CheckoutPaymentMethod,
    input: CheckoutPaymentInput,
    onInputChanged: (CheckoutPaymentInput) -> Unit,
    remainingCents: Long,
    availableCents: Long,
    methodAlreadyAdded: Boolean,
    customer: CustomerDto?,
    enabled: Boolean,
    onRequestSelectCustomer: () -> Unit,
    onAddPayment: (SalePaymentRequest) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (remainingCents == 0L) {
            Text("Importe cubierto", style = MaterialTheme.typography.titleMedium)
            return@Column
        }

        if (selectedMethod.requiresCustomer) {
            CheckoutCustomer(selectedMethod, customer, availableCents, enabled, onRequestSelectCustomer)
            if (availableCents <= 0L || methodAlreadyAdded) {
                if (methodAlreadyAdded) {
                    Text(
                        "Este pago ya está aplicado. Retíralo para cambiar su importe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }
        }

        val isCash = selectedMethod == CASH
        val maxAmount = minOf(remainingCents, availableCents)
        val amount = parseCheckoutCents(input.amount)
        val received = if (isCash) parseCheckoutCents(input.received) else amount
        val amountError = when {
            amount == null || amount <= 0L -> "Ingresa un importe mayor que cero."
            amount > maxAmount -> "Máximo disponible: ${Money(maxAmount).format()}"
            else -> null
        }
        val receivedError = when {
            received == null -> "Ingresa el efectivo recibido."
            amount != null && received < amount -> "El efectivo recibido es menor que el importe."
            else -> null
        }
        val canAdd = enabled && amountError == null && receivedError == null
        val addPayment: () -> Unit = {
            if (canAdd && amount != null && received != null) {
                val reference = when (selectedMethod) {
                    CREDIT -> customer?.name.orEmpty()
                    LOYALTY -> "Monedero: ${customer?.name.orEmpty()}"
                    else -> input.reference.trim()
                }
                onAddPayment(
                    SalePaymentRequest(
                        method = selectedMethod.methodCode,
                        amountCents = amount,
                        receivedCents = if (selectedMethod.requiresCustomer) 0L else received,
                        reference = reference
                    )
                )
            }
        }

        val focus = remember { FocusRequester() }
        val amountField: @Composable (Modifier) -> Unit = { modifier ->
            PaymentAmountField(
                label = "Importe a aplicar",
                value = input.amount,
                onChange = { value ->
                    onInputChanged(input.copy(amount = value, received = if (input.received == input.amount) value else input.received))
                },
                error = amountError,
                enabled = enabled,
                onSubmit = addPayment,
                modifier = if (isCash) modifier else modifier.focusRequester(focus)
            )
        }

        val detailField: @Composable (Modifier) -> Unit = { modifier ->
            if (isCash) {
                PaymentAmountField(
                    label = "Efectivo recibido",
                    value = input.received,
                    onChange = { onInputChanged(input.copy(received = it)) },
                    error = receivedError,
                    enabled = enabled,
                    onSubmit = addPayment,
                    modifier = modifier.focusRequester(focus)
                )
            } else {
                OutlinedTextField(
                    value = input.reference,
                    onValueChange = { onInputChanged(input.copy(reference = it)) },
                    label = { Text(if (selectedMethod == CARD) "Voucher (opcional)" else "Clave de rastreo (opcional)") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addPayment() }),
                    modifier = modifier.onPreviewKeyEvent { handlePaymentEnter(it, addPayment) }
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (selectedMethod.requiresCustomer) {
                amountField(Modifier.fillMaxWidth())
            } else if (maxWidth >= 400.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    amountField(Modifier.weight(1f))
                    detailField(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    amountField(Modifier.fillMaxWidth())
                    detailField(Modifier.fillMaxWidth())
                }
            }
            LaunchedEffect(selectedMethod, enabled) { if (enabled) focus.requestFocus() }
        }

        if (isCash) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SuggestionChip(
                    onClick = { onInputChanged(input.copy(received = input.amount)) },
                    enabled = enabled,
                    label = { Text("Exacto") }
                )
                for (denomination in listOf(20, 50, 100, 200, 500, 1000)) {
                    SuggestionChip(
                        onClick = { onInputChanged(input.copy(received = denomination.toString())) },
                        enabled = enabled,
                        label = { Text("$$denomination") }
                    )
                }
            }
            if (amount != null && received != null && received > amount && canAdd) {
                Text(
                    "Cambio · ${Money(received - amount).format()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        FilledTonalButton(onClick = addPayment, enabled = canAdd) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar pago")
        }
    }
}

@Composable
private fun PaymentAmountField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    OutlinedTextField(
        value = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length)),
        onValueChange = {
            fieldValue = it
            onChange(it.text)
        },
        label = { Text(label) },
        prefix = { Text("$") },
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        modifier = modifier
            .onFocusChanged { if (it.isFocused) fieldValue = TextFieldValue(value, TextRange(0, value.length)) }
            .onPreviewKeyEvent { handlePaymentEnter(it, onSubmit) }
    )
}

private fun handlePaymentEnter(event: KeyEvent, onSubmit: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || (event.key != Key.Enter && event.key != Key.NumPadEnter) ||
        event.isMetaPressed || event.isCtrlPressed || event.isAltPressed
    ) return false
    onSubmit()
    return true
}

@Composable
private fun CheckoutCustomer(
    method: CheckoutPaymentMethod,
    customer: CustomerDto?,
    available: Long,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(customer?.name ?: "Selecciona un cliente") },
            supportingContent = {
                val detail = when {
                    customer == null -> "Se requiere un cliente para usar ${method.displayLabel.lowercase()}."
                    method == LOYALTY -> "Saldo de monedero · ${Money(available).format()}"
                    !customer.creditEnabled -> "El cliente no tiene crédito habilitado."
                    !customer.hasCreditLimit -> "Crédito sin límite"
                    else -> "Crédito disponible · ${Money(available).format()}"
                }
                Text(detail)
            },
            trailingContent = {
                TextButton(onClick = onSelect, enabled = enabled) {
                    Text(if (customer == null) "Seleccionar" else "Cambiar")
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
internal fun CheckoutPaymentBreakdown(
    payments: List<SalePaymentRequest>,
    enabled: Boolean,
    onRemove: (Int) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            "Pagos aplicados",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        if (payments.isEmpty()) {
            Text(
                "Los pagos que agregues aparecerán aquí.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        payments.forEachIndexed { index, payment ->
            val method = payment.checkoutMethod()
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(method.icon, null, Modifier.size(20.dp)) },
                headlineContent = { Text(method.displayLabel) },
                supportingContent = when {
                    method == CASH -> {
                        {
                            Column {
                                Text("Recibido · ${Money(payment.receivedCents).format()}")
                                if (payment.receivedCents > payment.amountCents) {
                                    Text("Cambio · ${Money(payment.receivedCents - payment.amountCents).format()}")
                                }
                            }
                        }
                    }
                    payment.reference.isNotBlank() && !method.requiresCustomer -> {
                        { Text(payment.reference) }
                    }
                    else -> null
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Money(payment.amountCents).format(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                        )
                        IconButton(onClick = { onRemove(index) }, enabled = enabled) {
                            Icon(Icons.Default.Close, "Retirar pago ${index + 1} de ${method.displayLabel}")
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    }
}

private val CheckoutPaymentMethod.displayLabel: String
    get() = if (this == CREDIT) "Crédito" else label

private val CheckoutPaymentMethod.icon: ImageVector
    get() = when (this) {
        CASH -> Icons.Default.Payments
        CARD -> Icons.Default.CreditCard
        TRANSFER -> Icons.Default.AccountBalance
        LOYALTY -> Icons.Default.AccountBalanceWallet
        CREDIT -> Icons.Default.Receipt
    }
