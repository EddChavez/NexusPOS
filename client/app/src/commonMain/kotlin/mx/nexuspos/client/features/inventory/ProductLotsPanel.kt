package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.core.Money

@Composable
fun ProductLotsPanel(
    report: ExpirationsReportDto?,
    selectedStatusFilter: LotExpirationStatus?,
    isLoading: Boolean,
    onFilterStatus: (LotExpirationStatus?) -> Unit,
    onCreateLotClick: () -> Unit,
    onWriteOffLotClick: (ProductLotDto) -> Unit,
    onPrintLotsReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val totalLots = report?.totalLotsCount ?: 0
    val expiredCount = report?.expiredLotsCount ?: 0
    val nearExpCount = report?.nearExpirationLotsCount ?: 0
    val optimalCount = (totalLots - expiredCount - nearExpCount).coerceAtLeast(0)

    val expiredValuation = report?.totalExpiredValuationCents ?: 0L
    val atRiskValuation = report?.totalAtRiskValuationCents ?: 0L

    val displayedLots = report?.lots?.filter {
        selectedStatusFilter == null || it.status == selectedStatusFilter
    } ?: emptyList()

    Column(modifier = modifier.fillMaxSize()) {
        // 1. KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LotKpiCard(
                title = "Total Lotes Activos",
                count = "$totalLots",
                subtitle = "En inventario",
                highlightColor = NexusTheme.colors.actionPrimary,
                modifier = Modifier.weight(1f)
            )
            LotKpiCard(
                title = "🔴 Merma Confirmada",
                count = "$expiredCount lotes",
                subtitle = "Pérdida: ${Money.fromCents(expiredValuation).format()}",
                highlightColor = if (expiredCount > 0) NexusTheme.colors.dangerText else NexusTheme.colors.textSecondary,
                modifier = Modifier.weight(1.1f)
            )
            LotKpiCard(
                title = "🟡 Capital en Riesgo",
                count = "$nearExpCount lotes",
                subtitle = "Valor: ${Money.fromCents(atRiskValuation).format()}",
                highlightColor = if (nearExpCount > 0) NexusTheme.colors.warningText else NexusTheme.colors.textSecondary,
                modifier = Modifier.weight(1.1f)
            )
            LotKpiCard(
                title = "🟢 Estado Óptimo",
                count = "$optimalCount lotes",
                subtitle = "Caducidad lejana",
                highlightColor = NexusTheme.colors.textGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // 2. Toolbar & Filtros
        Surface(
            color = NexusTheme.colors.bgSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chips de filtro por semáforo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtrar por semáforo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textSecondary)

                    FilterChip(
                        selected = selectedStatusFilter == null,
                        onClick = { onFilterStatus(null) },
                        label = { Text("Todos ($totalLots)", fontSize = 11.5.sp) }
                    )

                    FilterChip(
                        selected = selectedStatusFilter == LotExpirationStatus.EXPIRED,
                        onClick = { onFilterStatus(LotExpirationStatus.EXPIRED) },
                        label = { Text("🔴 Caducados ($expiredCount)", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusTheme.colors.dangerBg,
                            selectedLabelColor = NexusTheme.colors.dangerText
                        )
                    )

                    FilterChip(
                        selected = selectedStatusFilter == LotExpirationStatus.NEAR_EXPIRATION,
                        onClick = { onFilterStatus(LotExpirationStatus.NEAR_EXPIRATION) },
                        label = { Text("🟡 Próximos ($nearExpCount)", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusTheme.colors.warningBg,
                            selectedLabelColor = NexusTheme.colors.warningText
                        )
                    )

                    FilterChip(
                        selected = selectedStatusFilter == LotExpirationStatus.OPTIMAL,
                        onClick = { onFilterStatus(LotExpirationStatus.OPTIMAL) },
                        label = { Text("🟢 Óptimos ($optimalCount)", fontSize = 11.5.sp) }
                    )
                }

                // Acciones: Imprimir & Nuevo Lote
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onPrintLotsReport != null) {
                        OutlinedButton(
                            onClick = onPrintLotsReport,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Imprimir Reporte", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onCreateLotClick,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar Nuevo Lote", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Tabla de Lotes
        Surface(
            color = NexusTheme.colors.bgSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusTheme.colors.actionPrimary)
                }
            } else if (displayedLots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (totalLots == 0) "No hay lotes registrados todavía. Registra uno con el botón superior." else "No hay lotes que coincidan con el filtro seleccionado.",
                        color = NexusTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexusTheme.colors.bgSurfaceSubtle)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PRODUCTO / SKU", modifier = Modifier.weight(2.2f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("LOTE", modifier = Modifier.weight(1.0f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("FECHA CADUCIDAD", modifier = Modifier.weight(1.1f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("SEMÁFORO / DÍAS", modifier = Modifier.weight(1.4f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("CANTIDAD", modifier = Modifier.weight(1.0f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("VALUACIÓN", modifier = Modifier.weight(1.1f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                        Text("ACCIÓN", modifier = Modifier.width(130.dp), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                    }

                    HorizontalDivider(color = NexusTheme.colors.borderPrimary)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayedLots, key = { it.id }) { lot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Producto
                                Column(modifier = Modifier.weight(2.2f)) {
                                    Text(lot.productName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = NexusTheme.colors.textPrimary)
                                    Text("SKU: ${lot.productSku} | ${lot.barcode}", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                }

                                // Lote
                                Text(lot.lotNumber, modifier = Modifier.weight(1.0f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)

                                // Fecha
                                Text(lot.expirationDate, modifier = Modifier.weight(1.1f), fontSize = 12.5.sp, color = NexusTheme.colors.textPrimary)

                                // Semáforo / Días
                                Box(modifier = Modifier.weight(1.4f)) {
                                    LotStatusBadge(lot)
                                }

                                // Cantidad
                                Text(
                                    "${lot.quantityMillis / 1000.0} ${lot.baseUnit}",
                                    modifier = Modifier.weight(1.0f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NexusTheme.colors.textPrimary
                                )

                                // Valuación
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Text(Money.fromCents(lot.valuationCents).format(), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = NexusTheme.colors.textPrimary)
                                    Text("c/u ${Money.fromCents(lot.costCents).format()}", fontSize = 10.5.sp, color = NexusTheme.colors.textSecondary)
                                }

                                // Acción: Baja por Merma
                                Box(modifier = Modifier.width(130.dp)) {
                                    Button(
                                        onClick = { onWriteOffLotClick(lot) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NexusTheme.colors.dangerBg,
                                            contentColor = NexusTheme.colors.dangerText
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Dar Baja", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = NexusTheme.colors.borderPrimary.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotStatusBadge(lot: ProductLotDto) {
    val (label, bg, border, text) = when (lot.status) {
        LotExpirationStatus.EXPIRED -> {
            val d = -lot.daysUntilExpiration
            val textDesc = if (d == 0) "VENCE HOY" else "CADUCADO (-$d d)"
            Quad(textDesc, NexusTheme.colors.dangerBg, NexusTheme.colors.dangerBorder, NexusTheme.colors.dangerText)
        }
        LotExpirationStatus.NEAR_EXPIRATION -> {
            Quad("Vence en ${lot.daysUntilExpiration} d", NexusTheme.colors.warningBg, NexusTheme.colors.warningBorder, NexusTheme.colors.warningText)
        }
        LotExpirationStatus.OPTIMAL -> {
            Quad("${lot.daysUntilExpiration} días (OK)", NexusTheme.colors.surfaceGreen, NexusTheme.colors.borderGreen, NexusTheme.colors.textGreen)
        }
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun LotKpiCard(
    title: String,
    count: String,
    subtitle: String,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(count, fontSize = 19.sp, fontWeight = FontWeight.Black, color = highlightColor)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
        }
    }
}
