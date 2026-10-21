package mx.nexuspos.client.features.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.credit.CustomerDto

@Composable
fun CreditPaymentDialog(
    customer: CustomerDto,
    isFullLiquidation: Boolean,
    onConfirmPayment: (amountCents: Long, paymentMethod: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val totalDebtPesos = customer.currentBalanceCents / 100.0
    var amountPesos by remember {
        mutableStateOf(if (isFullLiquidation) totalDebtPesos.toString() else "")
    }
    var paymentMethod by remember { mutableStateOf("CASH") } // CASH, TRANSFER, CARD
    var notes by remember { mutableStateOf(if (isFullLiquidation) "Liquidación total de adeudo" else "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val enteredCents = (amountPesos.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
    val remainingBalanceCents = (customer.currentBalanceCents - enteredCents).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isFullLiquidation) "Liquidar Cuenta de Cliente" else "Registrar Abono a Cuenta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                Text(
                    text = "${customer.name} (${customer.folio})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 440.dp, max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Card de Saldo Actual y Proyección
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Deuda Actual:",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = "$${"%.2f".format(totalDebtPesos)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = NexusTheme.colors.dangerText
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Saldo Resultante:",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = "$${"%.2f".format(remainingBalanceCents / 100.0)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (remainingBalanceCents == 0L) NexusTheme.colors.actionPrimary else NexusTheme.colors.totalDisplay
                            )
                        }
                    }
                }

                if (validationError != null) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Importe a Abonar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = amountPesos,
                        onValueChange = {
                            amountPesos = it
                            validationError = null
                        },
                        label = { Text("Monto a Abonar ($ MXN) *") },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            amountPesos = totalDebtPesos.toString()
                            validationError = null
                        },
                        border = BorderStroke(1.dp, NexusTheme.colors.accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusTheme.colors.accent),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("Todo")
                    }
                }

                // Método de Pago
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Método de Pago",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paymentMethod == "CASH",
                            onClick = { paymentMethod = "CASH" },
                            label = { Text("Efectivo (Cajón)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMethod == "TRANSFER",
                            onClick = { paymentMethod = "TRANSFER" },
                            label = { Text("Transferencia") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMethod == "CARD",
                            onClick = { paymentMethod = "CARD" },
                            label = { Text("Tarjeta") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones / Nota") },
                    placeholder = { Text("Ej. Pago parcial quincena, abono en tienda...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountPesos.toDoubleOrNull()
                    if (parsed == null || parsed <= 0) {
                        validationError = "Ingrese un importe mayor a $0.00"
                        return@Button
                    }
                    val amountCents = (parsed * 100).toLong()
                    if (amountCents > customer.currentBalanceCents) {
                        validationError = "El abono ($${"%.2f".format(parsed)}) no puede ser mayor al adeudo ($${"%.2f".format(totalDebtPesos)})"
                        return@Button
                    }

                    onConfirmPayment(amountCents, paymentMethod, notes.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
            ) {
                Text(if (isFullLiquidation) "Confirmar Liquidación" else "Registrar Abono")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder)
            ) {
                Text("Cancelar", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    )
}
