package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOs
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOsDetector
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.client.features.sales.v2.shortcuts.ShortcutBadge
import mx.nexuspos.core.Money

/**
 * Franja oficial de Cierre y Cobro Principal (Material Design 3).
 * Muestra el resumen de artículos y unidades acumuladas, acceso a notas del ticket [📝],
 * botón oficial de reimpresión de último ticket [🖨️] (Requisito 4.1) y el botón masivo de Cobrar [F12 / ⌘↵].
 */
@Composable
fun CheckoutSummaryBar(
    cartLines: List<CartLine>,
    total: Money,
    ticketNote: String,
    onOpenNoteDialog: () -> Unit,
    onReprintTicket: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
    os: HostOs = HostOsDetector.current
) {
    val totalArticles = cartLines.size
    val totalUnits = cartLines.sumOf { it.quantity.millis } / 1000.0

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. RESUMEN DE ARTÍCULOS, UNIDADES Y BOTONES COMPACTOS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        text = "$totalArticles artículos • ${String.format("%.2f", totalUnits)} unidades",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ticketNote.isNotBlank()) {
                        Text(
                            text = "Nota: ${ticketNote.take(24)}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Botón M3 de Nota [📝]
                OutlinedButton(
                    onClick = onOpenNoteDialog,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Nota de ticket",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nota", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                // Requisito 4.1: Botón M3 de Reimpresión de Último Ticket [🖨️]
                OutlinedButton(
                    onClick = onReprintTicket,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Reimprimir ticket",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reimprimir", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 2. BOTÓN OFICIAL M3 DE COBRO [F12 / ⌘↵]
            Button(
                onClick = onCheckout,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShortcutBadge(
                        action = PosAction.CHECKOUT,
                        os = os,
                        containerColor = Color.Black.copy(alpha = 0.22f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )

                    Text(
                        text = "COBRAR",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = total.format(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
