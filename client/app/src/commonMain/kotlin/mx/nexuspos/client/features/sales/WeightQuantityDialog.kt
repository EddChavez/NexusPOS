package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.Quantity
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightQuantityDialog(
    product: ProductDto,
    presentation: ProductPresentationDto,
    scaleReading: ScaleReading? = null,
    onConfirm: (Quantity) -> Unit,
    onDismiss: () -> Unit
) {
    val initialWeight = remember {
        if (scaleReading != null && scaleReading.isStableWeight) {
            String.format(Locale.US, "%.3f", scaleReading.weightKg)
        } else {
            "0.250"
        }
    }
    var weightText by remember { mutableStateOf(initialWeight) }
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
                // Live scale reading card if available
                if (scaleReading != null) {
                    val isStable = scaleReading.status == ScaleStatus.STABLE
                    Surface(
                        color = if (isStable) NexusTheme.colors.surfaceGreen else NexusTheme.colors.bgSurfaceSubtle,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isStable) NexusTheme.colors.textGreen else NexusTheme.colors.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚖ Báscula:")
                                Text(
                                    scaleReading.weightText,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStable) NexusTheme.colors.textGreen else NexusTheme.colors.textPrimary
                                )
                                Text(
                                    "[${scaleReading.status.label}]",
                                    fontSize = 11.sp,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }

                            if (scaleReading.weightKg > 0.0) {
                                OutlinedButton(
                                    onClick = {
                                        weightText = String.format(Locale.US, "%.3f", scaleReading.weightKg)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Tomar Peso", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

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

