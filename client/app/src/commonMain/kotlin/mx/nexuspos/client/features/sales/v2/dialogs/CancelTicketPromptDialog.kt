package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Modal oficial Material Design 3 para cancelación formal de ticket en progreso (Requisito 4.5).
 * Exige selección o captura de motivo y registra auditoría antes de descartar las partidas del ticket.
 */
@Composable
fun CancelTicketPromptDialog(
    isOpen: Boolean,
    ticketTitle: String,
    itemsCount: Int,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val predefinedReasons = listOf(
        "Cliente desistió de la compra",
        "Error de captura en partidas",
        "Falta de fondos / pago rechazado",
        "Producto dañado o sin stock",
        "Otro motivo"
    )
    var selectedReason by remember { mutableStateOf(predefinedReasons.first()) }
    var customReason by remember { mutableStateOf("") }
    val isCustomSelected = selectedReason == "Otro motivo"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Cancelar Ticket en Progreso",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "El ticket \"$ticketTitle\" contiene $itemsCount artículo(s). Esta acción cancelará la venta y quedará registrada en la auditoría del turno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Seleccione el motivo de cancelación:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                predefinedReasons.forEach { reasonOption ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reasonOption }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reasonOption,
                            onClick = { selectedReason = reasonOption }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reasonOption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (isCustomSelected) {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Especifique el motivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (isCustomSelected) {
                        customReason.ifBlank { "Cancelación personalizada por cajero" }
                    } else {
                        selectedReason
                    }
                    onConfirm(finalReason)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Confirmar Cancelación")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Volver")
            }
        }
    )
}
