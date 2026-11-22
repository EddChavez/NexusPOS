package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.TicketState

/**
 * Barra superior oficial Material Design 3 de pestañas para gestión de ventas paralelas (Multi-ticket).
 * Implementada con InputChip oficial, botón FilledTonalIconButton [+], acción de estacionar ticket [⏸️]
 * y AssistChip para Parked Sales (Requisito 4.2).
 */
@Composable
fun MultiTicketTabBar(
    tickets: List<TicketState>,
    activeTicketId: String,
    parkedSalesCount: Int,
    hasActiveCartLines: Boolean = false,
    onSelectTicket: (String) -> Unit,
    onNewTicket: () -> Unit,
    onCloseTicket: (String) -> Unit,
    onRenameTicket: (String) -> Unit,
    onParkCurrentSale: () -> Unit = {},
    onOpenParkedSales: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // =====================================================================
        // LISTA DE PESTAÑAS Y BOTÓN [+] (M3 InputChip & FilledTonalIconButton)
        // =====================================================================
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tickets.forEach { ticket ->
                val isSelected = ticket.id == activeTicketId

                InputChip(
                    selected = isSelected,
                    onClick = { onSelectTicket(ticket.id) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = ticket.title,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = ticket.total.format(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = if (isSelected) {
                        {
                            IconButton(
                                onClick = { onRenameTicket(ticket.id) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Renombrar ticket",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else null,
                    trailingIcon = if (tickets.size > 1 || ticket.cartLines.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { onCloseTicket(ticket.id) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar ${ticket.title}",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    } else null,
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.small
                )
            }

            // Botón rápido de nueva venta [+]
            FilledTonalIconButton(
                onClick = onNewTicket,
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nueva Venta (Ctrl+T / Cmd+T)",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // =====================================================================
        // REQUISITO 4.2: ACCIONES DE VENTAS EN ESPERA EN RED (PARKED SALES)
        // =====================================================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Botón para estacionar el ticket actual si tiene artículos cargados
            if (hasActiveCartLines) {
                AssistChip(
                    onClick = onParkCurrentSale,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PauseCircle,
                            contentDescription = "Estacionar ticket en red (Ctrl+E / ⌘E)",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Estacionar",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = null
                )
            }

            // Badge interactivo de ventas archivadas en red (Parked Sales)
            if (parkedSalesCount > 0) {
                AssistChip(
                    onClick = onOpenParkedSales,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "$parkedSalesCount en espera",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = null
                )
            }
        }
    }
}
