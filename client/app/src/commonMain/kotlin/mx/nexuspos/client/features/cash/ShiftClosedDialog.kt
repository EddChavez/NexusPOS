package mx.nexuspos.client.features.cash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.core.Money

@Composable
fun ShiftClosedDialog(
    session: CashSessionSummary,
    receipt: String?,
    onNewShift: () -> Unit,
    onExit: () -> Unit,
    onReprint: (() -> Unit)? = null
) {
    var showReceipt by remember(session.sessionId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Turno cerrado") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(session.registerName.ifBlank { session.registerId }, style = MaterialTheme.typography.titleSmall)
                        Text(session.operatorName, style = MaterialTheme.typography.bodyMedium)
                        session.closedAt?.let { Text("Cierre · ${formatShiftHistoryTimestamp(it, true)}") }
                        session.differenceCents?.let { Text("Diferencia · ${Money(it).format()}") }
                    }
                }
                if (receipt != null) {
                    TextButton(onClick = { showReceipt = !showReceipt }) {
                        Text(if (showReceipt) "Ocultar comprobante" else "Ver comprobante")
                    }
                    if (showReceipt) {
                        Text(receipt, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (onReprint != null) OutlinedButton(onClick = onReprint) { Text("Reimprimir corte") }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onNewShift) { Text("Nuevo turno") } },
        dismissButton = { TextButton(onClick = onExit) { Text("Salir de NexusPOS") } }
    )
}
