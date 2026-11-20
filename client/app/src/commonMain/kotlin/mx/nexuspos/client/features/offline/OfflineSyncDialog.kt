package mx.nexuspos.client.features.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.core.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineSyncDialog(
    isOpen: Boolean,
    queuedSales: List<QueuedOfflineSale>,
    connectionState: ServerConnectionState,
    isSyncing: Boolean,
    syncMessage: String?,
    onSyncNow: () -> Unit,
    onClearSynced: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val pendingCount = queuedSales.count { it.status == OfflineSaleStatus.PENDING || it.status == OfflineSaleStatus.FAILED }
    val isOnline = connectionState == ServerConnectionState.CONNECTED

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier
                .width(680.dp)
                .fillMaxHeight(0.85f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CloudSync else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isOnline) Color(0xFF10B981) else Color(0xFFD97706),
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Sincronización de Ventas en Contingencia",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = if (isOnline) "Servidor central en línea. Listo para transmitir." else "Sin conexión al servidor central. Ventas retenidas localmente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) Color(0xFF10B981) else Color(0xFFD97706)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                // Summary KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (pendingCount > 0) Color(0xFFFEF3C7) else NexusTheme.colors.bgSurfaceSubtle,
                        border = BorderStroke(1.dp, if (pendingCount > 0) Color(0xFFF59E0B) else NexusTheme.colors.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Ventas Pendientes", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                            Text(
                                text = "$pendingCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) Color(0xFFB45309) else NexusTheme.colors.textPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = NexusTheme.colors.bgSurfaceSubtle,
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Encoladas Totales", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                            Text(
                                text = "${queuedSales.size}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        color = NexusTheme.colors.bgSurfaceSubtle,
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Estado de Servidor LAN", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                            Text(
                                text = if (isOnline) "🟢 EN LÍNEA" else "🔴 DESCONECTADO",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                }

                // Status Message / Notification
                if (!syncMessage.isNullOrBlank()) {
                    Surface(
                        color = NexusTheme.colors.accentLight,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NexusTheme.colors.accent)
                            } else {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexusTheme.colors.accent)
                            }
                            Text(
                                text = syncMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // List Table
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NexusTheme.colors.bgSurfaceSubtle)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("FOLIO LOCAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(110.dp))
                            Text("FECHA / HORA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(150.dp))
                            Text("TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(90.dp))
                            Text("ESTADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
                            Text("DETALLE / SERVIDOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                        }

                        HorizontalDivider(color = NexusTheme.colors.borderPrimary)

                        if (queuedSales.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No hay ventas registradas en la cola local de contingencia.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(queuedSales.reversed(), key = { it.localFolio }) { sale ->
                                    val totalCents = sale.request.expectedTotalCents
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sale.localFolio,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = NexusTheme.colors.textPrimary,
                                            modifier = Modifier.width(110.dp)
                                        )

                                        Text(
                                            text = dateFormat.format(Date(sale.createdAt)),
                                            fontSize = 11.sp,
                                            color = NexusTheme.colors.textSecondary,
                                            modifier = Modifier.width(150.dp)
                                        )

                                        Text(
                                            text = Money(totalCents).format(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NexusTheme.colors.textPrimary,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(90.dp)
                                        )

                                        val (statusBg, statusText, statusLabel) = when (sale.status) {
                                            OfflineSaleStatus.PENDING -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "PENDIENTE")
                                            OfflineSaleStatus.SYNCED -> Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "SINCRONIZADO")
                                            OfflineSaleStatus.FAILED -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "ERROR")
                                        }

                                        Box(modifier = Modifier.width(110.dp), contentAlignment = Alignment.Center) {
                                            Surface(
                                                color = statusBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    color = statusText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val detailText = when (sale.status) {
                                            OfflineSaleStatus.SYNCED -> "Folio Servidor: #${sale.syncedFolio ?: "OK"}"
                                            OfflineSaleStatus.FAILED -> sale.lastError ?: "Fallo al enviar"
                                            OfflineSaleStatus.PENDING -> "${sale.request.lines.size} artículos • ${sale.request.payments.firstOrNull()?.method ?: "CASH"}"
                                        }

                                        Text(
                                            text = detailText,
                                            fontSize = 11.sp,
                                            color = if (sale.status == OfflineSaleStatus.FAILED) Color(0xFFDC2626) else NexusTheme.colors.textSecondary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    }
                                    HorizontalDivider(color = NexusTheme.colors.borderSubtle.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }

                // Action Buttons Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClearSynced,
                        enabled = queuedSales.any { it.status == OfflineSaleStatus.SYNCED },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Limpiar Sincronizadas", fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSyncNow,
                            enabled = isOnline && pendingCount > 0 && !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Sincronizando...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sincronizar Ahora ($pendingCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Cerrar [Esc]", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
