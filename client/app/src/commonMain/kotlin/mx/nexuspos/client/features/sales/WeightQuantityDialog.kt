package mx.nexuspos.client.features.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.Quantity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightQuantityDialog(
    product: ProductDto,
    presentation: ProductPresentationDto,
    onConfirm: (Quantity) -> Unit,
    onDismiss: () -> Unit
) {
    var weightText by remember { mutableStateOf("0.250") }
    val pricePerKg = Money.fromCents(presentation.priceCents)

    val currentQty = remember(weightText) {
        val parsed = weightText.toDoubleOrNull() ?: 0.0
        Quantity.fromDouble(parsed)
    }

    val previewTotal = remember(currentQty, pricePerKg) {
        PricingRules.calculateLineTotal(pricePerKg, currentQty)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Venta al Peso (Granel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(presentation.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("Precio por kilo: ${pricePerKg.format()}", style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Peso en kilogramos (ej. 0.350)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (currentQty.isPositive()) onConfirm(currentQty)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Atajos rápidos:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("0.250", "0.500", "0.750", "1.000", "1.500", "2.000").forEach { preset ->
                        FilledTonalButton(
                            onClick = { weightText = preset },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$preset kg", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Importe calculado:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        previewTotal.format(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (currentQty.isPositive()) onConfirm(currentQty) },
                enabled = currentQty.isPositive()
            ) {
                Text("Agregar al Carrito")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
