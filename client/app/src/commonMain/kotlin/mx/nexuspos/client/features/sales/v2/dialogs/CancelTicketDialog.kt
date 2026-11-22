package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.core.Money

/**
 * Diálogo oficial Material Design 3 (AlertDialog) para Cancelación Formal de Ticket en Progreso.
 * Cumple con el requisito V1: Cancelación formal con motivos de auditoría y elevación de supervisor
 * cuando el cajero no cuenta con privilegios administrativos.
 */
@Composable
fun CancelTicketDialog(
    isOpen: Boolean,
    ticketTitle: String,
    ticketTotal: Money,
    requiresSupervisor: Boolean,
    onConfirm: (reason: String, supervisorPin: String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val predefinedReasons = listOf(
        "Cliente desistió de la compra",
        "Error en captura de productos",
        "Medio de pago no disponible",
        "Producto defectuoso / no disponible",
        "Otro motivo..."
    )

    var selectedReason by remember { mutableStateOf(predefinedReasons.first()) }
    var customNotes by remember { mutableStateOf("") }
    var supervisorPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Cancelar $ticketTitle (${ticketTotal.format()})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Selecciona el motivo de cancelación para la bitácora de auditoría:",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Lista de motivos predefinidos
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    predefinedReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reason,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (selectedReason == "Otro motivo...") {
                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        placeholder = { Text("Especifica el motivo...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Campo de PIN de supervisor si es requerido
                if (requiresSupervisor) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Autorización de Supervisor Requerida (PIN):",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedTextField(
                            value = supervisorPin,
                            onValueChange = { supervisorPin = it },
                            placeholder = { Text("PIN de supervisor...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requiresSupervisor && supervisorPin.isBlank()) {
                        errorMessage = "Introduce el PIN de supervisor para autorizar la cancelación"
                        return@Button
                    }
                    val finalReason = if (selectedReason == "Otro motivo..." && customNotes.isNotBlank()) {
                        customNotes.trim()
                    } else {
                        selectedReason
                    }
                    onConfirm(finalReason, if (requiresSupervisor) supervisorPin.trim() else null)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirmar Cancelación", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Descartar", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
