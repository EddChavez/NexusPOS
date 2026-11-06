package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SalesHistoryDialog(
    sales: List<SaleResponse>,
    selectedSale: SaleResponse?,
    isLoading: Boolean,
    searchFolio: String,
    statusFilter: String?,
    cashSessionId: String?,
    onSearchFolioChanged: (String) -> Unit,
    onStatusFilterChanged: (String?) -> Unit,
    onSelectSale: (SaleResponse) -> Unit,
    onReload: () -> Unit,
    onReprintTicket: (SaleResponse) -> Unit,
    onCancelSale: (SaleResponse) -> Unit,
    onDismiss: () -> Unit
) {
    // Filter sales in memory
    val filteredSales = remember(sales, searchFolio, statusFilter) {
        sales.filter { sale ->
            val matchesFolio = if (searchFolio.isBlank()) true else sale.folio.toString().contains(searchFolio.trim())
            val matchesStatus = when (statusFilter) {
                "COMPLETED" -> sale.status == "COMPLETED"
                "CANCELLED" -> sale.status == "CANCELLED"
                else -> true
            }
            matchesFolio && matchesStatus
        }
    }

    // KPI Metrics for the shift
    val completedSales = sales.filter { it.status == "COMPLETED" }
    val totalShiftSalesCents = completedSales.sumOf { it.totalCents }
    val totalCancelledCount = sales.count { it.status == "CANCELLED" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgSurface),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(1040.dp)
                .height(680.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. TOP HEADER & METRICS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Ventas del Turno",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Sesión de Caja: ${cashSessionId ?: "Actual"} • Tickets Emitidos en Mostrador",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Shift KPI summary chips
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        KpiChip(label = "Total Ventas", value = Money(totalShiftSalesCents).format(), color = Color(0xFF059669))
                        KpiChip(label = "Tickets", value = "${sales.size}", color = MaterialTheme.colorScheme.primary)
                        KpiChip(label = "Cancelados", value = "$totalCancelledCount", color = if (totalCancelledCount > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.outline)

                        IconButton(onClick = onReload, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                Divider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)

                // 2. MAIN BODY (SPLIT: LIST ON LEFT, DETAIL ON RIGHT)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN: FILTERS & TICKETS LIST
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Filters row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchFolio,
                                onValueChange = onSearchFolioChanged,
                                placeholder = { Text("Buscar folio...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            )

                            // Status filters
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterTabButton(
                                    label = "Todos",
                                    selected = statusFilter == null,
                                    onClick = { onStatusFilterChanged(null) }
                                )
                                FilterTabButton(
                                    label = "Completados",
                                    selected = statusFilter == "COMPLETED",
                                    onClick = { onStatusFilterChanged("COMPLETED") }
                                )
                                FilterTabButton(
                                    label = "Cancelados",
                                    selected = statusFilter == "CANCELLED",
                                    onClick = { onStatusFilterChanged("CANCELLED") }
                                )
                            }
                        }

                        // Tickets list
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (filteredSales.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (sales.isEmpty()) "No se han emitido tickets en este turno aún."
                                    else "No se encontraron tickets con el criterio seleccionado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredSales, key = { it.id }) { sale ->
                                    val isSelected = sale.id == selectedSale?.id
                                    SaleCardItem(
                                        sale = sale,
                                        isSelected = isSelected,
                                        onClick = { onSelectSale(sale) }
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN: SELECTED TICKET DETAIL & ACTIONS
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                    ) {
                        if (selectedSale != null) {
                            SaleDetailPanel(
                                sale = selectedSale,
                                onReprint = { onReprintTicket(selectedSale) },
                                onCancel = { onCancelSale(selectedSale) }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Seleccione un ticket de la lista para ver el desglose y opciones",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiChip(label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "$label:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun FilterTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else NexusTheme.colors.borderSubtle),
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SaleCardItem(sale: SaleResponse, isSelected: Boolean, onClick: () -> Unit) {
    val isCancelled = sale.status == "CANCELLED"
    val timeStr = remember(sale.createdAt) {
        try {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sale.createdAt))
        } catch (e: Exception) {
            ""
        }
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else if (isCancelled) Color(0xFFFEF2F2)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary
            else if (isCancelled) Color(0xFFFCA5A5)
            else NexusTheme.colors.borderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Folio #${sale.folio}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = sale.customerName ?: "Público en General",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Money(sale.totalCents).format(),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isCancelled) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = if (isCancelled) Color(0xFFFEE2E2) else Color(0xFFD1FAE5),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isCancelled) "CANCELADA" else "COMPLETADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCancelled) Color(0xFFB91C1C) else Color(0xFF047857),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleDetailPanel(
    sale: SaleResponse,
    onReprint: () -> Unit,
    onCancel: () -> Unit
) {
    val isCancelled = sale.status == "CANCELLED"
    val dateStr = remember(sale.createdAt) {
        try {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(sale.createdAt))
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Folio #${sale.folio}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(text = "Fecha: $dateStr • Cajero: ${sale.cashierName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sale.customerName != null) {
                        Text(text = "Cliente: ${sale.customerName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Surface(
                    color = if (isCancelled) Color(0xFFFEE2E2) else Color(0xFFD1FAE5),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isCancelled) "CANCELADA" else "COMPLETADA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isCancelled) Color(0xFFB91C1C) else Color(0xFF047857),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)

            // Items breakdown
            Text(text = "Artículos de la Venta (${sale.lines.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sale.lines.forEach { line ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = line.description, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(
                                    text = "${Quantity(line.quantityMillis).format()} x ${Money(line.unitPriceCents).format()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(text = Money(line.totalCents).format(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Divider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)

            // Financial breakdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(sale.subtotalCents).format(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IVA:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(sale.taxCents).format(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL:", fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(Money(sale.totalCents).format(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Payment records
            Divider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)
            Text(text = "Métodos de Pago:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            sale.payments.forEach { pay ->
                val methodLabel = when (pay.method) {
                    "CASH" -> "Efectivo"
                    "CREDIT" -> "Crédito Fiado"
                    "CARD" -> "Tarjeta"
                    "TRANSFER" -> "Transferencia"
                    else -> pay.method
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("• $methodLabel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money(pay.amountCents).format(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Actions: Reprint Ticket and Cancel Sale
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReprint,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reimprimir Ticket", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onCancel,
                enabled = !isCancelled,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (!isCancelled) Color(0xFFDC2626) else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (!isCancelled) Color(0xFFDC2626) else MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isCancelled) "Ticket Cancelado" else "Cancelar Venta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
