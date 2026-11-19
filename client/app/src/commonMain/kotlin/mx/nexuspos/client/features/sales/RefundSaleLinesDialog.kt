package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.RefundLineItemRequest
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundSaleLinesDialog(
    sale: SaleResponse,
    activeCashSessionId: String,
    currentUserId: String,
    isCashier: Boolean,
    onConfirmRefund: (request: CreateSaleRefundRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val returnableLines = remember(sale) {
        sale.lines.filter { it.returnableQuantityMillis > 0L }
    }

    // Map: saleLineId -> quantity to return in millis
    val returnQuantities = remember {
        mutableStateMapOf<String, Long>().apply {
            returnableLines.forEach { put(it.id, 0L) }
        }
    }

    // Map: saleLineId -> reenterStock boolean
    val reenterStockMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            returnableLines.forEach { put(it.id, true) }
        }
    }

    val reasons = listOf(
        "DEFECTO" to "Defecto / Daño",
        "CADUCADO" to "Caducado / Vencido",
        "CAMBIO" to "Cambio por otro",
        "INCONFORMIDAD" to "Inconformidad",
        "OTRO" to "Otro motivo"
    )
    var selectedReasonKey by remember { mutableStateOf(reasons.first().first) }
    var notesInput by remember { mutableStateOf("") }

    val hasCustomer = !sale.customerName.isNullOrBlank()
    var selectedRefundMethod by remember { mutableStateOf("CASH") }

    // Live calculation of refund total
    val (totalRefundCents, itemsCount) = remember(returnQuantities.toMap()) {
        var total = 0L
        var count = 0
        sale.lines.forEach { line ->
            val returnQty = returnQuantities[line.id] ?: 0L
            if (returnQty > 0L) {
                count++
                val lineRefund = ((line.totalCents.toDouble() * returnQty) / line.quantityMillis).roundToLong()
                total += lineRefund
            }
        }
        Pair(total, count)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 660.dp)
                .fillMaxHeight(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = null,
                            tint = NexusTheme.colors.accent
                        )
                        Column {
                            Text(
                                text = "Devolución de Mercancía",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Ticket Original #${sale.folio} • Cajero original: ${sale.cashierName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NexusTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // List of items
                Text(
                    text = "Seleccione las partidas y cantidades a devolver:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sale.lines) { line ->
                        val returnable = line.returnableQuantityMillis
                        val currentQty = returnQuantities[line.id] ?: 0L
                        val isReenter = reenterStockMap[line.id] ?: true

                        RefundLineItemCard(
                            line = line,
                            returnableMillis = returnable,
                            currentReturnMillis = currentQty,
                            reenterStock = isReenter,
                            onReturnQtyChanged = { newQty ->
                                returnQuantities[line.id] = newQty.coerceIn(0L, returnable)
                            },
                            onReenterStockChanged = { newReenter ->
                                reenterStockMap[line.id] = newReenter
                            }
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Motivo de Devolución
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Motivo de la devolución:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reasons.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedReasonKey == key,
                                onClick = { selectedReasonKey = key },
                                label = { Text(label, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }

                // Método de Reembolso (Efectivo vs Crédito)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reembolsar en:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    FilterChip(
                        selected = selectedRefundMethod == "CASH",
                        onClick = { selectedRefundMethod = "CASH" },
                        label = { Text("Efectivo (Gaveta de Caja)", fontSize = 11.5.sp) }
                    )
                    FilterChip(
                        selected = selectedRefundMethod == "CREDIT",
                        onClick = { if (hasCustomer) selectedRefundMethod = "CREDIT" },
                        enabled = hasCustomer,
                        label = {
                            Text(
                                if (hasCustomer) "Saldo a Favor (${sale.customerName})" else "Saldo a Favor (Sin cliente)",
                                fontSize = 11.5.sp
                            )
                        }
                    )
                }

                // Summary Card
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Artículos a devolver: $itemsCount partida(s)",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                            if (isCashier) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Requiere PIN de Supervisor para procesar",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TOTAL A REEMBOLSAR:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = Money(totalRefundCents).format(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalRefundCents > 0L) NexusTheme.colors.accent else NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Cancelar", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val refundLinesReq = sale.lines.mapNotNull { line ->
                                val qty = returnQuantities[line.id] ?: 0L
                                if (qty > 0L) {
                                    RefundLineItemRequest(
                                        saleLineId = line.id,
                                        quantityMillis = qty,
                                        reenterStock = reenterStockMap[line.id] ?: true
                                    )
                                } else null
                            }

                            if (refundLinesReq.isNotEmpty()) {
                                onConfirmRefund(
                                    CreateSaleRefundRequest(
                                        cashSessionId = activeCashSessionId,
                                        cashierUserId = currentUserId,
                                        reason = selectedReasonKey,
                                        notes = notesInput,
                                        refundMethod = selectedRefundMethod,
                                        supervisorPin = null, // Will be prompted via SupervisorAuthDialog if cashier
                                        lines = refundLinesReq
                                    )
                                )
                            }
                        },
                        enabled = totalRefundCents > 0L,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                        modifier = Modifier.weight(1.5f).height(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isCashier) "Solicitar Autorización y Devolver" else "Confirmar Devolución",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefundLineItemCard(
    line: SaleLineItemResponse,
    returnableMillis: Long,
    currentReturnMillis: Long,
    reenterStock: Boolean,
    onReturnQtyChanged: (Long) -> Unit,
    onReenterStockChanged: (Boolean) -> Unit
) {
    val isFullyRefunded = returnableMillis == 0L
    val lineSubtotalRefund = if (currentReturnMillis > 0L) {
        ((line.totalCents.toDouble() * currentReturnMillis) / line.quantityMillis).roundToLong()
    } else 0L

    Surface(
        color = if (currentReturnMillis > 0L) NexusTheme.colors.accent.copy(alpha = 0.08f) else NexusTheme.colors.bgSurfaceSubtle.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (currentReturnMillis > 0L) NexusTheme.colors.accent.copy(alpha = 0.6f) else NexusTheme.colors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = line.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    Text(
                        text = "Vendido: ${Quantity(line.quantityMillis).format()} • Ya devuelto: ${Quantity(line.refundedQuantityMillis).format()} • Disponible: ${Quantity(returnableMillis).format()}",
                        fontSize = 11.sp,
                        color = NexusTheme.colors.textSecondary
                    )
                }

                if (isFullyRefunded) {
                    Surface(
                        color = NexusTheme.colors.borderSubtle.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "100% Devuelto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = Money(lineSubtotalRefund).format(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (currentReturnMillis > 0L) NexusTheme.colors.accent else NexusTheme.colors.textSecondary
                    )
                }
            }

            if (!isFullyRefunded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stepper for quantity
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val stepMillis = if (line.quantityMillis % 1000L == 0L) 1000L else 100L
                        FilledTonalIconButton(
                            onClick = { onReturnQtyChanged((currentReturnMillis - stepMillis).coerceAtLeast(0L)) },
                            modifier = Modifier.size(28.dp),
                            enabled = currentReturnMillis > 0L
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        }

                        Text(
                            text = Quantity(currentReturnMillis).format(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (currentReturnMillis > 0L) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary
                        )

                        FilledTonalIconButton(
                            onClick = { onReturnQtyChanged((currentReturnMillis + stepMillis).coerceAtMost(returnableMillis)) },
                            modifier = Modifier.size(28.dp),
                            enabled = currentReturnMillis < returnableMillis
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        }

                        TextButton(
                            onClick = { onReturnQtyChanged(returnableMillis) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Devolver Todo", fontSize = 11.sp)
                        }
                    }

                    // Checkbox reenter stock
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = reenterStock,
                            onCheckedChange = onReenterStockChanged,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (reenterStock) "Reingresar a stock" else "Merma / Desecho",
                            fontSize = 11.5.sp,
                            color = if (reenterStock) NexusTheme.colors.textPrimary else NexusTheme.colors.dangerText
                        )
                    }
                }
            }
        }
    }
}
