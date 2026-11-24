package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.features.sales.CheckoutPaymentMethod
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.client.features.sales.v2.shortcuts.ShortcutBadge
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.core.Money

@Composable
fun CheckoutDialogV2(
    total: Money,
    selectedCustomer: CustomerDto?,
    isOffline: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onRequestSelectCustomer: () -> Unit,
    onConfirmCheckout: (List<SalePaymentRequest>) -> Unit,
    onDismiss: () -> Unit
) {
    var payments by remember { mutableStateOf(emptyList<SalePaymentRequest>()) }
    var paymentCustomerId by remember { mutableStateOf(selectedCustomer?.id) }
    var method by remember { mutableStateOf(CheckoutPaymentMethod.CASH) }
    var inputs by remember { mutableStateOf(emptyMap<CheckoutPaymentMethod, CheckoutPaymentInput>()) }

    LaunchedEffect(selectedCustomer?.id) {
        if (paymentCustomerId != selectedCustomer?.id) {
            payments = payments.filterNot { it.isCustomerPayment() }
            inputs = inputs.filterKeys { !it.requiresCustomer }
            paymentCustomerId = selectedCustomer?.id
        }
    }
    LaunchedEffect(isOffline) {
        if (isOffline && method.requiresCustomer) method = CheckoutPaymentMethod.CASH
    }

    val paidCents = payments.sumOf { it.amountCents }
    val remainingCents = (total.cents - paidCents).coerceAtLeast(0L)
    val changeCents = payments.filter { it.method == CheckoutPaymentMethod.CASH.methodCode }
        .sumOf { it.receivedCents - it.amountCents }
    val invalidCustomerPayment = payments.any { payment ->
        payment.isCustomerPayment() &&
            (paymentCustomerId != selectedCustomer?.id ||
                payment.amountCents > payment.checkoutMethod().availableCents(selectedCustomer, isOffline))
    }
    val canConfirm = !isSubmitting && payments.isNotEmpty() && paidCents == total.cents && !invalidCustomerPayment
    val canPayExactCash = !isSubmitting && payments.isEmpty() && total.cents >= 0L
    val windowSize = LocalWindowInfo.current.containerDpSize
    val width = if (windowSize.width > 0.dp) (windowSize.width - 32.dp).coerceIn(1.dp, 960.dp) else 960.dp
    val height = if (windowSize.height > 0.dp) (windowSize.height - 32.dp).coerceIn(1.dp, 640.dp) else 640.dp
    val wide = width >= 850.dp

    val confirm: () -> Unit = { if (canConfirm) onConfirmCheckout(payments) }
    val dismiss: () -> Unit = { if (!isSubmitting) onDismiss() }

    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.size(width, height).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.F12 || (event.key == Key.Enter && event.isMetaPressed) -> {
                        confirm()
                        true
                    }
                    event.key == Key.Escape -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cobrar venta", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = dismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, "Cerrar cobro")
                    }
                }

                CheckoutTotals(total, paidCents, remainingCents, changeCents)

                val entry: @Composable () -> Unit = {
                    val methodAlreadyAdded = method.requiresCustomer && payments.any { it.method == method.methodCode }
                    val available = method.availableCents(selectedCustomer, isOffline)
                    val defaultAmount = minOf(remainingCents, available).takeIf { it > 0L }?.let(::checkoutAmountText).orEmpty()
                    val input = inputs[method] ?: CheckoutPaymentInput(amount = defaultAmount, received = defaultAmount)

                    CheckoutPaymentEntry(
                        selectedMethod = method,
                        input = input,
                        onInputChanged = { inputs = inputs + (method to it) },
                        remainingCents = remainingCents,
                        availableCents = available,
                        methodAlreadyAdded = methodAlreadyAdded,
                        customer = selectedCustomer,
                        enabled = !isSubmitting,
                        onRequestSelectCustomer = onRequestSelectCustomer,
                        onAddPayment = { payment ->
                            val outstanding = total.cents - payments.sumOf { it.amountCents }
                            val alreadyApplied = method.requiresCustomer && payments.any { it.method == payment.method }
                            if (payment.amountCents <= outstanding && !alreadyApplied) {
                                payments = payments + payment
                                inputs = emptyMap()
                            }
                        }
                    )
                }
                val breakdown: @Composable () -> Unit = {
                    CheckoutPaymentBreakdown(payments, !isSubmitting) { index ->
                        payments = payments.filterIndexed { position, _ -> position != index }
                        inputs = emptyMap()
                    }
                }

                if (remainingCents > 0L) {
                    CheckoutMethodPicker(method, { method = it }, isOffline, !isSubmitting)
                }

                if (remainingCents == 0L && payments.isNotEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { breakdown() }
                } else if (wide) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(0.60f).fillMaxHeight().verticalScroll(rememberScrollState())) { entry() }
                        Column(Modifier.weight(0.40f).fillMaxHeight().verticalScroll(rememberScrollState())) { breakdown() }
                    }
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        entry()
                        breakdown()
                    }
                }

                val feedback = errorMessage ?: if (invalidCustomerPayment) {
                    "El saldo disponible cambió. Retira el pago con crédito o monedero y vuelve a capturarlo."
                } else null
                if (feedback != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Text(feedback, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()
                CheckoutFooter(
                    canPayExactCash = canPayExactCash,
                    canConfirm = canConfirm,
                    isSubmitting = isSubmitting,
                    onPayExactCash = {
                        if (canPayExactCash) onConfirmCheckout(
                            listOf(SalePaymentRequest("CASH", total.cents, total.cents))
                        )
                    },
                    onConfirm = confirm,
                    onDismiss = dismiss
                )
            }
        }
    }
}

@Composable
private fun CheckoutTotals(total: Money, paidCents: Long, remainingCents: Long, changeCents: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckoutMetric("Total", total, Modifier.weight(1.2f), emphasized = true)
            CheckoutMetric("Aplicado", Money(paidCents), Modifier.weight(1f))
            CheckoutMetric(
                if (remainingCents > 0L) "Pendiente" else "Cambio",
                Money(if (remainingCents > 0L) remainingCents else changeCents),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CheckoutMetric(label: String, amount: Money, modifier: Modifier, emphasized: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            amount.format(),
            style = (if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                .copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutFooter(
    canPayExactCash: Boolean,
    canConfirm: Boolean,
    isSubmitting: Boolean,
    onPayExactCash: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancelar") }
        if (canPayExactCash) {
            OutlinedButton(onClick = onPayExactCash) {
                Icon(Icons.Default.FlashOn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cobrar en efectivo exacto")
            }
        }
        val confirmFocus = remember { FocusRequester() }
        Button(onClick = onConfirm, enabled = canConfirm, modifier = Modifier.focusRequester(confirmFocus)) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isSubmitting) "Procesando cobro…" else "Confirmar cobro")
            if (canConfirm) {
                Spacer(Modifier.width(8.dp))
                ShortcutBadge(
                    PosAction.CHECKOUT,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        LaunchedEffect(canConfirm) { if (canConfirm) confirmFocus.requestFocus() }
    }
}

internal fun SalePaymentRequest.checkoutMethod(): CheckoutPaymentMethod =
    CheckoutPaymentMethod.entries.first { it.methodCode == method }

private fun SalePaymentRequest.isCustomerPayment(): Boolean = checkoutMethod().requiresCustomer
