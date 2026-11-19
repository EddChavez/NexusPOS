package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.core.Money

@Composable
fun LotWriteOffDialog(
    lot: ProductLotDto,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (lotId: String, quantityMillis: Long?, reason: String) -> Unit
) {
    val totalAvailableQty = lot.quantityMillis / 1000.0
    var quantityText by remember { mutableStateOf(totalAvailableQty.toString()) }
    var reason by remember {
        val initialReason = if (lot.daysUntilExpiration <= 0) "Merma por Caducidad en Anaquel" else "Retiro Preventivo / Merma"
        mutableStateOf(initialReason)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
            modifier = Modifier.width(520.dp).wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = NexusTheme.colors.dangerText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Baja de Lote por Merma",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTheme.colors.textSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = NexusTheme.colors.borderPrimary)

                // Resumen del Lote
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(lot.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NexusTheme.colors.textPrimary)
                        Text("SKU: ${lot.productSku} | Lote: ${lot.lotNumber}", fontSize = 12.sp, color = NexusTheme.colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Caduca: ${lot.expirationDate} (${lot.daysUntilExpiration} días)", fontSize = 12.sp, color = NexusTheme.colors.textSecondary)
                            Text("Disponible: $totalAvailableQty ${lot.baseUnit}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary)
                        }
                        Text(
                            "Valuación Actual: ${Money.fromCents(lot.valuationCents).format()} (Costo: ${Money.fromCents(lot.costCents).format()} c/u)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.warningText
                        )
                    }
                }

                // Cantidad a dar de baja
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cantidad a retirar (${lot.baseUnit}):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                    TextButton(onClick = { quantityText = totalAvailableQty.toString() }) {
                        Text("Retirar Todo ($totalAvailableQty)", fontSize = 11.sp, color = NexusTheme.colors.actionPrimary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                // Motivo
                Text("Motivo de la merma / justificación:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips rápidos de motivos
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Caducidad en anaquel", "Empaque roto / dañado", "Descomposición").forEach { chipText ->
                        SuggestionChip(
                            onClick = { reason = chipText },
                            label = { Text(chipText, fontSize = 10.5.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Alerta de impacto
                Surface(
                    color = NexusTheme.colors.dangerBg.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NexusTheme.colors.dangerText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Esta acción descontará el stock del lote, rebajará el inventario general y registrará un movimiento LOSS en el Kardex.",
                            fontSize = 11.5.sp,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                validationError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = NexusTheme.colors.dangerText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                // Botones
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val qty = quantityText.toDoubleOrNull() ?: 0.0
                            if (qty <= 0.0) {
                                validationError = "La cantidad debe ser mayor a 0"
                                return@Button
                            }
                            if (qty > totalAvailableQty) {
                                validationError = "No puede retirar más del disponible ($totalAvailableQty)"
                                return@Button
                            }

                            onConfirm(
                                lot.id,
                                (qty * 1000.0).toLong(),
                                reason.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusTheme.colors.dangerBg,
                            contentColor = NexusTheme.colors.dangerText
                        ),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NexusTheme.colors.dangerText, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Confirmar Baja por Merma", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
