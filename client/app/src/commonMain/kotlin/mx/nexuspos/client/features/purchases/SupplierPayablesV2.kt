package mx.nexuspos.client.features.purchases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.contracts.purchases.RecordSupplierPaymentRequest
import mx.nexuspos.contracts.purchases.RegisterSupplierPayableRequest
import mx.nexuspos.contracts.purchases.SupplierPayable
import mx.nexuspos.contracts.purchases.SupplierPaymentMethod
import mx.nexuspos.core.Money

@Composable
internal fun SupplierPayablesList(state: PurchasesState, viewModel: PurchasesViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.includeSettled, { viewModel.openPayables(false) }, label = { Text("Pendientes") })
            FilterChip(state.includeSettled, { viewModel.openPayables(true) }, label = { Text("Todas") })
        }
        if (state.payables.isEmpty() && !state.loading && state.error == null) {
            Text(if (state.includeSettled) "No hay cuentas registradas." else "No hay facturas con saldo pendiente.")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.payables, key = SupplierPayable::receiptId) { account ->
                OutlinedCard(onClick = { viewModel.openPayable(account.receiptId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(account.supplierName, style = MaterialTheme.typography.titleMedium)
                        Text("Factura ${account.documentNumber} · Vence ${account.dueDate}")
                        PayableBalance(account)
                    }
                }
            }
        }
        if (state.payablesOffset > 0 || state.hasMorePayables) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openPayables(state.includeSettled, state.payablesOffset - PURCHASE_HISTORY_PAGE_SIZE) },
                    enabled = state.payablesOffset > 0 && !state.loading
                ) { Text("Anterior") }
                OutlinedButton(
                    onClick = { viewModel.openPayables(state.includeSettled, state.payablesOffset + PURCHASE_HISTORY_PAGE_SIZE) },
                    enabled = state.hasMorePayables && !state.loading
                ) { Text("Siguiente") }
            }
        }
    }
}

@Composable
internal fun SupplierPayablePane(state: PurchasesState, viewModel: PurchasesViewModel, permissions: Set<String>, cashSessionId: String?) {
    val detail = state.payable
    val invoice = state.payableInvoice
    val editable = !state.loading && !state.saving && !state.awaitingConfirmation
    if (detail == null) {
        if (!state.payableLoaded) return
        if (invoice == null || "purchases.payables.register" !in permissions) {
            Text("Esta factura no tiene una cuenta por pagar.")
            return
        }
        var dueDate by remember(invoice.id) { mutableStateOf("") }
        val key = remember(invoice.id) { purchaseKey() }
        val validDate = dueDate.length == 10 && runCatching { LocalDate.parse(dueDate) }.isSuccess
        val net = invoice.totalCents - invoice.returnedCents
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${invoice.supplierName} · Factura ${invoice.documentNumber}", style = MaterialTheme.typography.titleLarge)
            Text("Importe de la factura después de devoluciones · ${Money(net).format()}")
            if (net <= 0L) return@Column
            Text("Registra esta cuenta si la factura se compró a crédito.")
            PurchaseField("Fecha de vencimiento (AAAA-MM-DD)", dueDate, { dueDate = it.take(10) }, enabled = editable,
                error = dueDate.length == 10 && !validDate)
            PurchaseConfirmation("Registrar cuenta", state.saving, validDate, { pin ->
                viewModel.registerPayable(pin, RegisterSupplierPayableRequest(key, dueDate))
            })
        }
        return
    }

    val account = detail.account
    val canPay = account.balanceCents > 0 && "purchases.payables.pay" in permissions
    var showPayment by remember(account.receiptId, detail.payments.size) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(account.supplierName, style = MaterialTheme.typography.titleLarge)
        Text("Factura ${account.documentNumber} · Vence ${account.dueDate}")
        PayableBalance(account)
        Text("Factura ${Money(account.totalCents).format()} · Devuelto ${Money(account.returnedCents).format()} · Pagado ${Money(account.paidCents).format()}")
        if (showPayment && canPay) {
            SupplierPaymentForm(account.balanceCents, state, viewModel, cashSessionId.takeIf { "cash.out" in permissions }) { showPayment = false }
        } else if (canPay) {
            Button(onClick = { showPayment = true }, enabled = editable) { Text("Registrar abono") }
        }
        HorizontalDivider()
        Text("Pagos registrados", style = MaterialTheme.typography.titleMedium)
        if (detail.payments.isEmpty()) Text("Aún no hay pagos registrados.")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(detail.payments, key = { it.id }) { payment ->
                ListItem(
                    headlineContent = { Text("${Money(payment.amountCents).format()} · ${payment.method.label()}") },
                    supportingContent = {
                        Column {
                            Text("${payment.operatorName} · ${formatShiftHistoryTimestamp(payment.createdAt, true)}")
                            if (payment.reference.isNotBlank()) Text(payment.reference)
                            if (payment.cashMovementId != null) Text("Salida de caja · ${payment.cashMovementId}")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplierPaymentForm(
    balance: Long, state: PurchasesState, viewModel: PurchasesViewModel, cashSessionId: String?, onCancel: () -> Unit
) {
    val key = remember { purchaseKey() }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SupplierPaymentMethod.TRANSFER) }
    var reference by remember { mutableStateOf("") }
    var attemptedSession by remember { mutableStateOf<String?>(null) }
    val cash = method == SupplierPaymentMethod.CASH
    val session = if (!cash) null else if (state.awaitingConfirmation) attemptedSession else cashSessionId
    val cents = parsePurchaseDecimal(amount, 2)?.takeIf { it in 1..balance }
    val editable = !state.loading && !state.saving && !state.awaitingConfirmation
    Text(
        if (!cash) "Registra un pago ya realizado."
        else if (session == null) "Necesitas un turno propio abierto y permiso para registrar salidas."
        else "El abono generará una salida en tu turno de caja."
    )
    PurchaseField("Importe", amount, { amount = it.take(24) }, enabled = editable, error = amount.isNotBlank() && cents == null)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SupplierPaymentMethod.entries.forEach { option ->
            FilterChip(method == option, { method = option }, label = { Text(option.label()) }, enabled = editable)
        }
        TextButton({ amount = purchaseDecimal(balance, 2) }, enabled = editable) { Text("Usar saldo completo") }
    }
    PurchaseField("Referencia (opcional)", reference, { reference = it.take(120) }, enabled = editable)
    PurchaseConfirmation("Guardar pago", state.saving, cents != null && (!cash || session != null), { pin ->
        attemptedSession = session
        cents?.let { viewModel.paySupplier(pin, RecordSupplierPaymentRequest(key, it, method, reference, session)) }
    }, amount = cents)
    TextButton(onCancel, enabled = editable) { Text("Cancelar abono") }
}

@Composable
private fun PayableBalance(account: SupplierPayable) {
    Text(
        when {
            account.balanceCents > 0 -> "Saldo pendiente · ${Money(account.balanceCents).format()}"
            account.supplierCreditCents > 0 -> "Saldo a favor por devolución · ${Money(account.supplierCreditCents).format()}"
            else -> "Liquidada"
        },
        style = MaterialTheme.typography.titleSmall
    )
}

private fun SupplierPaymentMethod.label() = when (this) {
    SupplierPaymentMethod.CASH -> "Efectivo"
    SupplierPaymentMethod.TRANSFER -> "Transferencia"
    SupplierPaymentMethod.CARD -> "Tarjeta"
}
