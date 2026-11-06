package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money

@Composable
fun CancelSaleReasonDialog(
    sale: SaleResponse,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val predefinedReasons = listOf(
        "Error al capturar productos",
        "Cliente desistió de la compra",
        "Producto defectuoso / devuelto",
        "Cobro duplicado",
        "Otro motivo..."
    )

    var selectedReason by remember { mutableStateOf(predefinedReasons.first()) }
    var customNotes by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgSurface),
            border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NexusTheme.colors.dangerText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Cancelar Venta Folio #${sale.folio}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.dangerText
                        )
                        Text(
                            text = "Total a revertir: ${Money(sale.totalCents).format()} • Cajero: ${sale.cashierName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Alert notice
                Surface(
                    color = NexusTheme.colors.dangerBg.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Esta acción cancelará el ticket y revertirá automáticamente el inventario de los productos, el saldo de la caja y el crédito del cliente de forma atómica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTheme.colors.dangerText,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Predefined reasons
                Text(
                    text = "Seleccione el motivo de cancelación:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    predefinedReasons.forEach { reason ->
                        val isSelected = reason == selectedReason
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else NexusTheme.colors.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReason = reason },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Custom notes
                OutlinedTextField(
                    value = customNotes,
                    onValueChange = {
                        customNotes = it
                        errorText = null
                    },
                    label = { Text("Observaciones adicionales (opcional)") },
                    placeholder = { Text("Especifique detalles del motivo...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(8.dp)
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = NexusTheme.colors.dangerText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Regresar")
                    }

                    Button(
                        onClick = {
                            val finalReason = if (customNotes.isNotBlank()) {
                                "$selectedReason: ${customNotes.trim()}"
                            } else {
                                selectedReason
                            }
                            onConfirm(finalReason)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Confirmar Cancelación", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
