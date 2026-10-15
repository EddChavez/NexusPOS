package mx.nexuspos.client.features.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashPaymentDialog(
    total: Money,
    onConfirm: (receivedCents: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var receivedText by remember { mutableStateOf((total.cents / 100).toString()) }

    val receivedMoney = remember(receivedText) {
        val parsed = receivedText.toDoubleOrNull() ?: 0.0
        Money.pesos(parsed)
    }

    val change = remember(total, receivedMoney) {
        PricingRules.calculateChange(total, receivedMoney)
    }

    val isSufficient = receivedMoney >= total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cobro en Efectivo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL A COBRAR", style = MaterialTheme.typography.labelMedium)
                        Text(total.format(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }

                OutlinedTextField(
                    value = receivedText,
                    onValueChange = { receivedText = it },
                    label = { Text("Efectivo Recibido ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (isSufficient) onConfirm(receivedMoney.cents)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Denominaciones rápidas:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { receivedText = (total.cents / 100.0).toString() }) {
                        Text("Exacto", fontSize = 12.sp)
                    }
                    listOf(20, 50, 100, 200, 500).forEach { denom ->
                        OutlinedButton(
                            onClick = { receivedText = denom.toString() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("$$denom", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CAMBIO A ENTREGAR:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isSufficient) change.format() else "$0.00",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSufficient) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                if (!isSufficient) {
                    Text(
                        "El monto recibido es menor al total a cobrar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isSufficient) onConfirm(receivedMoney.cents) },
                enabled = isSufficient
            ) {
                Text("Confirmar Cobro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
