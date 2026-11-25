package mx.nexuspos.client.features.customers.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftActionPane
import mx.nexuspos.client.features.sales.v2.dialogs.checkoutAmountText
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomerPaymentPaneV2(
    customer: CustomerDto,
    full: Boolean,
    cashSessionReady: Boolean,
    allowedMethods: Set<String> = setOf("CASH", "CARD", "TRANSFER"),
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: (Long, String, String) -> Unit
) {
    val debt = customer.currentBalanceCents
    var input by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var method by remember(allowedMethods) { mutableStateOf(allowedMethods.firstOrNull().orEmpty()) }
    val amount = if (full) debt else parseCheckoutCents(input)
    val valid = amount != null && amount in 1L..debt
    val drawerMessage = if (!cashSessionReady) "Abre un turno de caja para registrar el abono." else null

    ShiftActionPane(error ?: drawerMessage, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            onClick = { if (valid && amount != null) onConfirm(amount, method, notes.trim()) },
            enabled = valid && cashSessionReady && !busy
        ) { Text(if (full) "Confirmar liquidación" else "Registrar abono") }
    }) {
        Text(listOf(customer.name, customer.folio).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleLarge)
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentAmount("Saldo actual", Money(debt).format())
                PaymentAmount("Saldo después del abono", if (valid && amount != null) Money(debt - amount).format() else "—")
            }
        }
        Row(
            Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = if (full) checkoutAmountText(debt) else input,
                onValueChange = { input = it },
                label = { Text("Importe del abono") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                readOnly = full,
                enabled = !busy,
                isError = input.isNotBlank() && !valid,
                supportingText = {
                    if (input.isNotBlank() && !valid) Text("Ingresa un importe positivo dentro del saldo pendiente.")
                },
                modifier = Modifier.weight(1f)
            )
            if (!full) TextButton(onClick = { input = checkoutAmountText(debt) }, enabled = !busy && debt > 0) {
                Text("Todo")
            }
        }
        Text("Método de pago", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transferencia").filter { it.first in allowedMethods }.forEach { (code, label) ->
                FilterChip(
                    selected = method == code,
                    onClick = { method = code },
                    enabled = !busy,
                    label = { Text(label) }
                )
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Observaciones (opcional)") },
            enabled = !busy,
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun PaymentAmount(label: String, amount: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(amount, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomerFormPaneV2(
    customer: CustomerDto?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (CreateCustomerRequest) -> Unit
) {
    var name by remember { mutableStateOf(customer?.name.orEmpty()) }
    var phone by remember { mutableStateOf(customer?.phone.orEmpty()) }
    var email by remember { mutableStateOf(customer?.email.orEmpty()) }
    var rfc by remember { mutableStateOf(customer?.rfc.orEmpty()) }
    var address by remember { mutableStateOf(customer?.address.orEmpty()) }
    var notes by remember { mutableStateOf(customer?.notes.orEmpty()) }
    var creditEnabled by remember { mutableStateOf(customer?.creditEnabled ?: true) }
    var hasLimit by remember { mutableStateOf(customer?.hasCreditLimit ?: true) }
    var limit by remember { mutableStateOf(customer?.creditLimitCents?.let(::checkoutAmountText).orEmpty()) }
    val limitCents = if (!creditEnabled || !hasLimit || limit.isBlank()) 0L else parseCheckoutCents(limit)
    val valid = name.isNotBlank() && limitCents != null

    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            enabled = valid && !busy,
            onClick = {
                onSave(CreateCustomerRequest(
                    name.trim(), phone.trim(), email.trim(), rfc.trim().uppercase(), address.trim(), notes.trim(),
                    creditEnabled, hasLimit, limitCents ?: 0L
                ))
            }
        ) { Text("Guardar ficha") }
    }) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Teléfono") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.widthIn(max = 280.dp)
                )
                OutlinedTextField(
                    email, { email = it }, label = { Text("Correo") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.widthIn(max = 360.dp)
                )
            }
            OutlinedTextField(rfc, { rfc = it }, label = { Text("RFC") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                address, { address = it }, label = { Text("Dirección") }, minLines = 2, maxLines = 3,
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Observaciones") }, minLines = 2, maxLines = 4,
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = creditEnabled, onCheckedChange = { creditEnabled = it }, enabled = !busy)
                Text("Habilitar crédito")
            }
            if (creditEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = hasLimit, onCheckedChange = { hasLimit = it }, enabled = !busy)
                    Text("Aplicar límite de crédito")
                }
                if (hasLimit) {
                    OutlinedTextField(
                        limit, { limit = it }, label = { Text("Límite de crédito") }, prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !busy, singleLine = true, isError = limitCents == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomerWalletAdjustmentV2(
    customer: CustomerDto,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var add by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val amount = parseCheckoutCents(input)
    val valid = amount != null && amount > 0L &&
        if (add) amount <= Long.MAX_VALUE - customer.loyaltyBalanceCents else amount <= customer.loyaltyBalanceCents

    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            enabled = valid && !busy,
            onClick = { if (valid && amount != null) onConfirm(if (add) amount else -amount, reason.trim()) }
        ) { Text("Registrar ajuste") }
    }) {
        Text(customer.name, style = MaterialTheme.typography.titleLarge)
        PaymentAmount("Saldo disponible", Money(customer.loyaltyBalanceCents).format())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = add, onClick = { add = true }, enabled = !busy, label = { Text("Agregar saldo") })
            FilterChip(selected = !add, onClick = { add = false }, enabled = !busy, label = { Text("Retirar saldo") })
        }
        OutlinedTextField(
            input, { input = it }, label = { Text("Importe") }, prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !busy, singleLine = true, isError = input.isNotBlank() && !valid,
            supportingText = { if (!add) Text("El retiro debe caber en el saldo disponible.") },
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        )
        OutlinedTextField(
            reason, { reason = it }, label = { Text("Motivo (opcional)") },
            enabled = !busy, minLines = 2, maxLines = 3,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        )
    }
}
