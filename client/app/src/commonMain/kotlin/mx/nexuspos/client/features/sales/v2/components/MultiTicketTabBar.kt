package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.TicketState

/** Pestañas de tickets y acceso a ventas en espera. */
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

    val ticketControls: @Composable RowScope.() -> Unit = {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scrollState),
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
                                modifier = Modifier.widthIn(max = 176.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = ticket.total.format(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    } else null,
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = MaterialTheme.shapes.small
                )
            }

        }

        FilledTonalIconButton(onClick = onNewTicket, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Add, "Crear ticket")
        }
    }

    val parkedControls: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasActiveCartLines) {
                OutlinedButton(
                    onClick = onParkCurrentSale,
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.PauseCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Estacionar")
                }
            }

            if (parkedSalesCount > 0) {
                TextButton(
                    onClick = onOpenParkedSales,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(parkedSalesCount.toString())
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("En espera")
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 440.dp && hasActiveCartLines && parkedSalesCount > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = ticketControls
                )
                parkedControls()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ticketControls()
                parkedControls()
            }
        }
    }
}
