package mx.nexuspos.client.features.cash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.nexuspos.contracts.cash.CashSessionSummary

@Composable
fun CashSessionDialog(
    activeSession: CashSessionSummary?,
    onOpenSession: (fundCents: Long) -> Unit,
    onCloseSession: (countedCashCents: Long, notes: String) -> Unit,
    onDismiss: () -> Unit,
    isClosing: Boolean = false
) {
    if (activeSession == null) {
        // Open session mode
        var fundText by remember { mutableStateOf("500") }
        val fundMoney = (fundText.toDoubleOrNull() ?: 0.0) * 100.0

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("Abrir Turno de Caja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Capture el fondo inicial de efectivo para dar cambio al iniciar el turno.")
                    OutlinedTextField(
                        value = fundText,
                        onValueChange = { fundText = it },
                        label = { Text("Fondo Inicial en Efectivo ($)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onOpenSession(fundMoney.toLong()) },
                    enabled = fundMoney >= 0
                ) {
                    Text("Abrir Caja")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    } else {
        // Close session (Corte) mode
        var countedText by remember(activeSession.sessionId, activeSession.cashCountVisible) {
            mutableStateOf(if (activeSession.cashCountVisible) (activeSession.expectedCashCents / 100.0).toString() else "")
        }
        var notesText by remember { mutableStateOf("") }

        val countedCents = mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents(countedText)?.takeIf { it >= 0 }
        val differenceCents = (countedCents ?: 0L) - activeSession.expectedCashCents

        AlertDialog(
            onDismissRequest = { if (!isClosing) onDismiss() },
            title = {
                Text("Corte y Cierre de Turno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeSession.cashCountVisible) Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fondo Inicial:", style = MaterialTheme.typography.bodyMedium)
                                Text("$${activeSession.initialFundCents / 100.0}", fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ventas en Efectivo:", style = MaterialTheme.typography.bodyMedium)
                                Text("$${activeSession.salesCashCents / 100.0}", fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Abonos en Efectivo:", style = MaterialTheme.typography.bodyMedium)
                                Text("$${activeSession.creditPaymentsCashCents / 100.0}", fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EFECTIVO ESPERADO:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "$${activeSession.expectedCashCents / 100.0}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = countedText,
                        onValueChange = { countedText = it },
                        label = { Text("Efectivo Contado en Caja ($)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (activeSession.cashCountVisible) Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DIFERENCIA (Arqueo):", fontWeight = FontWeight.Bold)
                        val diffSign = if (differenceCents > 0) "+$" else if (differenceCents < 0) "-$" else "$"
                        val diffVal = kotlin.math.abs(differenceCents / 100.0)
                        Text(
                            "$diffSign$diffVal",
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                differenceCents == 0L -> Color(0xFF2E7D32)
                                differenceCents > 0L -> Color(0xFF1565C0) // Sobrante
                                else -> MaterialTheme.colorScheme.error // Faltante
                            }
                        )
                    }

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notas u observaciones de corte") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { countedCents?.let { onCloseSession(it, notesText) } },
                    enabled = countedCents != null && !isClosing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isClosing) "Cerrando turno…" else "Cerrar turno")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !isClosing) {
                    Text("Cancelar")
                }
            }
        )
    }
}
