package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Modal oficial Material Design 3 (AlertDialog) para captura y edición de observaciones o notas del ticket activo.
 */
@Composable
fun TicketNoteDialog(
    isOpen: Boolean,
    initialNote: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var noteText by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nota del Ticket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Agrega indicaciones de entrega, mesa, cliente o notas especiales:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Ej. Entrega a domicilio calle Juárez #45...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteText) },
                shape = MaterialTheme.shapes.small
            ) {
                Text("Guardar Nota", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
