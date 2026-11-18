package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ParkedSalesListDialog(
    isOpen: Boolean,
    parkedSales: List<ParkedSaleResponse>,
    selectedSale: ParkedSaleResponse? = null,
    isLoading: Boolean = false,
    onSelectSale: (ParkedSaleResponse) -> Unit,
    onRetrieve: (ParkedSaleResponse) -> Unit,
    onDiscard: (parkedSaleId: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val currentSelection = selectedSale ?: parkedSales.firstOrNull()

    fun formatTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "--:--"
        }
    }

    fun calculateElapsedMinutes(timestamp: Long): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val minutes = (diffMs / 60000).coerceAtLeast(0)
        return when {
            minutes == 0L -> "Justo ahora"
            minutes == 1L -> "Hace 1 min"
            minutes < 60L -> "Hace $minutes min"
            else -> "Hace ${minutes / 60} h"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.5.dp, NexusTheme.colors.borderSubtle),
            modifier = Modifier
                .width(820.dp)
                .height(540.dp)
                .padding(16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter -> {
                                if (currentSelection != null) {
                                    onRetrieve(currentSelection)
                                    true
                                } else false
                            }
                            Key.Delete, Key.Backspace -> {
                                if (currentSelection != null) {
                                    onDiscard(currentSelection.id)
                                    true
                                } else false
                            }
                            Key.DirectionDown -> {
                                val idx = parkedSales.indexOfFirst { it.id == currentSelection?.id }
                                if (idx != -1 && idx < parkedSales.size - 1) {
                                    onSelectSale(parkedSales[idx + 1])
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                val idx = parkedSales.indexOfFirst { it.id == currentSelection?.id }
                                if (idx > 0) {
                                    onSelectSale(parkedSales[idx - 1])
                                    true
                                } else false
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PauseCircle,
                            contentDescription = "Tickets en Espera",
                            tint = NexusTheme.colors.warningText,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tickets en Espera (Parked Sales)",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Surface(
                                    color = NexusTheme.colors.warningBg,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${parkedSales.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NexusTheme.colors.warningText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Ventas pausadas y sincronizadas en base de datos. Recuperables desde cualquier caja.",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NexusTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                if (parkedSales.isEmpty()) {
                    // Estado vacío
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Sin tickets pendientes",
                                tint = NexusTheme.colors.textGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No hay tickets en espera",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Puede poner un ticket en espera presionando [F4] durante cualquier venta en curso.",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                } else {
                    // Split view: Lista a la izquierda, detalle a la derecha
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Lista de tickets
                        Surface(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight(),
                            color = NexusTheme.colors.bgSurfaceSubtle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(parkedSales, key = { it.id }) { item ->
                                    val isSelected = currentSelection?.id == item.id
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) NexusTheme.colors.bgSurface else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.5.dp, NexusTheme.colors.borderPrimary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectSale(item) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.customLabel,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) NexusTheme.colors.borderPrimary else NexusTheme.colors.textPrimary
                                                    )
                                                    Surface(
                                                        color = NexusTheme.colors.surfaceGreen,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "${item.itemsCount} art.",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = NexusTheme.colors.textGreen,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${calculateElapsedMinutes(item.parkedAt)} • ${formatTime(item.parkedAt)} • ${item.registerId} (${item.cashierName})",
                                                    fontSize = 11.sp,
                                                    color = NexusTheme.colors.textSecondary
                                                )
                                                if (item.customerName != null) {
                                                    Text(
                                                        text = "Cliente: ${item.customerName}",
                                                        fontSize = 11.sp,
                                                        color = NexusTheme.colors.textMuted
                                                    )
                                                }
                                            }

                                            Text(
                                                text = Money.fromCents(item.totalCents).format(),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NexusTheme.colors.textGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Preview del ticket seleccionado
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            color = NexusTheme.colors.bgSurfaceSubtle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                        ) {
                            if (currentSelection != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Detalle del Ticket",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NexusTheme.colors.textPrimary
                                    )
                                    HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(currentSelection.items) { line ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = line.name,
                                                        fontSize = 12.sp,
                                                        color = NexusTheme.colors.textPrimary,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${Quantity(line.quantityMillis).format()} x ${Money.fromCents(line.unitPriceCents).format()}",
                                                        fontSize = 10.sp,
                                                        color = NexusTheme.colors.textSecondary
                                                    )
                                                }
                                                Text(
                                                    text = Money.fromCents(line.totalCents).format(),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = NexusTheme.colors.textPrimary
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = NexusTheme.colors.borderSubtle)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "TOTAL:",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NexusTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = Money.fromCents(currentSelection.totalCents).format(),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = NexusTheme.colors.textGreen
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Seleccione un ticket para ver sus artículos",
                                        fontSize = 12.sp,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Barra inferior de botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NexusTheme.colors.textSecondary
                        )
                    ) {
                        Text("Cerrar [Esc]")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentSelection != null) {
                            Button(
                                onClick = { onDiscard(currentSelection.id) },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NexusTheme.colors.dangerBg,
                                    contentColor = NexusTheme.colors.dangerText
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Descartar",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Descartar [Supr]")
                            }

                            Button(
                                onClick = { onRetrieve(currentSelection) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NexusTheme.colors.actionPrimary,
                                    contentColor = NexusTheme.colors.actionPrimaryFg
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reanudar",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Reanudar Ticket [Enter]",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
