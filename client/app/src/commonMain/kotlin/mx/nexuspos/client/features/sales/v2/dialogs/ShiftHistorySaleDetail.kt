package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity

internal expect fun formatShiftHistoryTimestamp(timestamp: Long, includeDate: Boolean): String

@Composable
internal fun ShiftSaleStatus(status: String) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        "COMPLETED" -> Triple("Completada", colors.primaryContainer, colors.onPrimaryContainer)
        "CANCELLED" -> Triple("Cancelada", colors.errorContainer, colors.onErrorContainer)
        "REFUNDED" -> Triple("Devuelta", colors.secondaryContainer, colors.onSecondaryContainer)
        "PARTIALLY_REFUNDED" -> Triple("Devolución parcial", colors.secondaryContainer, colors.onSecondaryContainer)
        else -> Triple(status, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Badge(containerColor = container, contentColor = content) {
        Text(label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
internal fun ShiftHistorySaleDetail(
    sale: SaleResponse,
    onReprint: () -> Unit,
    onRefund: () -> Unit,
    onCancel: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Folio #${sale.folio}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        ShiftSaleStatus(sale.status)
                    }
                    SecondaryText("${formatShiftHistoryTimestamp(sale.createdAt, includeDate = true)} · Cajero ${sale.cashierName}")
                    sale.customerName?.let { SecondaryText("Cliente · $it") }
                }
            }

            item { HistorySection("Artículos (${sale.lines.size})") }
            items(sale.lines, key = { "item-${it.id}" }) { line ->
                HistoryDetailRow(
                    title = line.description,
                    amount = Money(line.totalCents).format(),
                    supporting = {
                        SecondaryText("${historyQuantity(line.quantityMillis)} × ${Money(line.unitPriceCents).format()}")
                        if (line.refundedQuantityMillis > 0) {
                            SecondaryText("Cantidad devuelta · ${historyQuantity(line.refundedQuantityMillis)}")
                        }
                    }
                )
            }

            if (sale.refunds.isNotEmpty()) {
                item { HistorySection("Devoluciones (${sale.refunds.size})") }
                items(sale.refunds, key = { "refund-${it.id}" }) { refund ->
                    HistoryDetailRow(
                        title = "Nota #${refund.folio}",
                        amount = Money(-refund.totalCents).format(),
                        supporting = {
                            SecondaryText("${formatShiftHistoryTimestamp(refund.createdAt, includeDate = true)} · ${paymentLabel(refund.refundMethod)}")
                            SecondaryText("Motivo · ${refund.reason}")
                            if (refund.notes.isNotBlank()) SecondaryText(refund.notes)
                        }
                    )
                }
            }

            if (sale.payments.isNotEmpty()) {
                item { HistorySection("Pagos") }
                items(sale.payments, key = { "payment-${it.id}" }) { payment ->
                    HistoryDetailRow(paymentLabel(payment.method), Money(payment.amountCents).format())
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    HistoryAmount("Subtotal", Money(sale.subtotalCents).format())
                    HistoryAmount("IVA", Money(sale.taxCents).format())
                    if (sale.discountCents > 0) HistoryAmount("Descuento", Money(-sale.discountCents).format())
                    HistoryAmount("Total", Money(sale.totalCents).format(), emphasized = true)
                }
            }
        }
        HorizontalDivider()
        HistorySaleActions(sale, onReprint, onRefund, onCancel, onWhatsApp)
    }
}

@Composable
private fun HistorySection(title: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HistoryDetailRow(title: String, amount: String, supporting: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { content -> { Column { content() } } },
        trailingContent = {
            Text(
                amount,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun HistoryAmount(label: String, amount: String, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style)
        Text(amount, style = style.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.End)
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySaleActions(
    sale: SaleResponse,
    onReprint: () -> Unit,
    onRefund: () -> Unit,
    onCancel: () -> Unit,
    onWhatsApp: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            enabled = sale.status == "COMPLETED" && sale.refunds.isEmpty(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cancelar venta")
        }
        OutlinedButton(
            onClick = onRefund,
            enabled = (sale.status == "COMPLETED" || sale.status == "PARTIALLY_REFUNDED") &&
                sale.lines.any { it.returnableQuantityMillis > 0 }
        ) {
            Text("Devolución")
        }
        TextButton(onClick = onWhatsApp) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("WhatsApp")
        }
        FilledTonalButton(onClick = onReprint) {
            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reimprimir")
        }
    }
}

private fun historyQuantity(millis: Long): String = Quantity(millis).format().substringBefore(' ')

private fun paymentLabel(method: String): String = when (method) {
    "CASH" -> "Efectivo"
    "CREDIT" -> "Crédito fiado"
    "CARD" -> "Tarjeta"
    "TRANSFER" -> "Transferencia"
    "LOYALTY" -> "Monedero electrónico"
    else -> method
}
