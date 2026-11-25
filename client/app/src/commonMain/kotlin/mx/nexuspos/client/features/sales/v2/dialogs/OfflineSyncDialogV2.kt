package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.offline.OfflineSaleStatus
import mx.nexuspos.client.features.offline.QueuedOfflineSale
import mx.nexuspos.core.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OfflineSyncDialogV2(
    queuedSales: List<QueuedOfflineSale>,
    connectionState: ServerConnectionState,
    serverUrl: String,
    isSyncing: Boolean,
    syncMessage: String?,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val window = LocalWindowInfo.current.containerDpSize
    val width = (window.width - 32.dp).takeIf { it > 0.dp }?.coerceAtMost(660.dp) ?: 660.dp
    val height = (window.height - 32.dp).takeIf { it > 0.dp }?.coerceAtMost(560.dp) ?: 560.dp
    val online = connectionState == ServerConnectionState.CONNECTED
    val pending = queuedSales.filter { it.status != OfflineSaleStatus.SYNCED }
    val conflicts = pending.count { !it.belongsTo(serverUrl) || !it.automaticRetry }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.size(width, height),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sincronización de ventas", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (online) "Servidor LAN conectado" else "Servidor LAN sin conexión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar sincronización") }
                }
                if (isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (syncMessage != null) ShiftActionMessage(syncMessage)
                if (conflicts > 0) {
                    ShiftActionMessage(
                        "$conflicts venta(s) requieren revisión. Verifica el detalle antes de reintentar.",
                        isError = true
                    )
                }

                if (queuedSales.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Aún no hay ventas registradas en esta cola.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(queuedSales.asReversed(), key = { it.localFolio }) { sale ->
                            SyncSaleRow(sale, eligible = sale.belongsTo(serverUrl))
                        }
                    }
                }

                HorizontalDivider()
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    Button(onClick = onSyncNow, enabled = online && !isSyncing && pending.any { it.belongsTo(serverUrl) }) {
                        Text("Reintentar pendientes")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSaleRow(sale: QueuedOfflineSale, eligible: Boolean) {
    val label = when {
        sale.status == OfflineSaleStatus.SYNCED -> "Confirmada · Folio #${sale.syncedFolio}"
        !eligible -> "Pendiente en el servidor donde se inició el cobro"
        sale.status == OfflineSaleStatus.FAILED && !sale.automaticRetry -> "Requiere revisión"
        else -> "Pendiente de confirmación"
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sale.localFolio, style = MaterialTheme.typography.titleSmall)
            Text(
                "$label · ${Money(sale.request.expectedTotalCents).format()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                formatShiftHistoryTimestamp(sale.createdAt, includeDate = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            sale.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
