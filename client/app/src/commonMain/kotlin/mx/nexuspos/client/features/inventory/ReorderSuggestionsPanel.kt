package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.core.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSuggestionsPanel(
    report: ReorderSuggestionsReportDto?,
    departments: List<DepartmentDto>,
    selectedDepartmentId: String?,
    onlyOutOfStock: Boolean,
    editedQuantities: Map<String, Long>,
    isLoading: Boolean,
    onSelectDepartment: (String?) -> Unit,
    onToggleOnlyOutOfStock: (Boolean) -> Unit,
    onUpdateQuantity: (productId: String, quantityMillis: Long) -> Unit,
    onExportCsv: (items: List<ReorderSuggestionItemDto>) -> Unit,
    onCopyWhatsApp: (items: List<ReorderSuggestionItemDto>) -> Unit,
    onPrintTicket: (items: List<ReorderSuggestionItemDto>) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(report, editedQuantities) {
        report?.items?.map { item ->
            val edited = editedQuantities[item.productId]
            if (edited != null) {
                val newEstCost = (edited * item.costCents) / 1000L
                item.copy(suggestedOrderMillis = edited, estimatedInvestmentCents = newEstCost)
            } else {
                item
            }
        } ?: emptyList()
    }

    val totalInvestmentCents = remember(items) {
        items.sumOf { it.estimatedInvestmentCents }
    }
    val criticalCount = remember(items) {
        items.count { it.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. KPI CARDS DEL PEDIDO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = NexusTheme.colors.bgSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Partidas a Resurtir", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                    Text("${items.size}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = NexusTheme.colors.textPrimary)
                    Text("Productos bajo mínimo", fontSize = 10.5.sp, color = NexusTheme.colors.textSecondary)
                }
            }

            Surface(
                color = NexusTheme.colors.bgSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Inversión Estimada", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                    Text(Money.fromCents(totalInvestmentCents).format(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = NexusTheme.colors.accent)
                    Text("Cálculo al costo base", fontSize = 10.5.sp, color = NexusTheme.colors.textSecondary)
                }
            }

            Surface(
                color = if (criticalCount > 0) NexusTheme.colors.dangerBg.copy(alpha = 0.5f) else NexusTheme.colors.bgSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (criticalCount > 0) NexusTheme.colors.dangerBorder else NexusTheme.colors.borderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Agotados Críticos", fontSize = 11.sp, color = if (criticalCount > 0) NexusTheme.colors.dangerText else NexusTheme.colors.textSecondary)
                    Text("$criticalCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (criticalCount > 0) NexusTheme.colors.dangerText else NexusTheme.colors.textPrimary)
                    Text("Existencia <= 0", fontSize = 10.5.sp, color = NexusTheme.colors.textSecondary)
                }
            }
        }

        // 2. BARRA DE FILTROS Y ACCIONES DE EXPORTACIÓN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filtros de departamento y agotados
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedDepartmentId == null,
                    onClick = { onSelectDepartment(null) },
                    label = { Text("Todos los Deptos", fontSize = 11.sp) }
                )

                departments.forEach { dept ->
                    FilterChip(
                        selected = selectedDepartmentId == dept.id,
                        onClick = { onSelectDepartment(dept.id) },
                        label = { Text(dept.name, fontSize = 11.sp) }
                    )
                }

                Spacer(Modifier.width(8.dp))

                FilterChip(
                    selected = onlyOutOfStock,
                    onClick = { onToggleOnlyOutOfStock(!onlyOutOfStock) },
                    label = { Text("🚨 Solo Agotados", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NexusTheme.colors.dangerBg,
                        selectedLabelColor = NexusTheme.colors.dangerText
                    )
                )
            }

            Spacer(Modifier.width(10.dp))

            // Botones de salida
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onCopyWhatsApp(items) },
                    enabled = items.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF25D366))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onExportCsv(items) },
                    enabled = items.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exportar CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onPrintTicket(items) },
                    enabled = items.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Imprimir Lista", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. TABLA DE REABASTECIMIENTO
        Surface(
            color = NexusTheme.colors.bgSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusTheme.colors.bgSurfaceSubtle, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("URGENCIA", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(90.dp))
                    Text("SKU / CÓDIGO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(130.dp))
                    Text("PRODUCTO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                    Text("DEPTO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(90.dp))
                    Text("EXISTENCIA", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                    Text("MÍN / MÁX", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                    Text("PEDIR (AJUSTABLE)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.accent, modifier = Modifier.width(130.dp))
                    Text("COSTO U.", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
                    Text("SUBTOTAL EST.", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(95.dp))
                }

                Spacer(Modifier.height(4.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusTheme.colors.accent)
                    }
                } else if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NexusTheme.colors.textGreen, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Inventario Óptimo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("No hay artículos por debajo del stock mínimo con los filtros seleccionados", fontSize = 12.sp, color = NexusTheme.colors.textSecondary)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(items, key = { it.productId }) { item ->
                            val isCritical = item.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK
                            val stockUnits = item.currentStockMillis / 1000.0
                            val minUnits = item.minStockMillis / 1000.0
                            val maxUnits = item.maxStockMillis / 1000.0
                            val orderUnits = item.suggestedOrderMillis / 1000.0
                            val isEdited = editedQuantities.containsKey(item.productId)

                            Surface(
                                color = if (isCritical) NexusTheme.colors.dangerBg.copy(alpha = 0.2f) else NexusTheme.colors.bgSurface,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Urgencia
                                    Box(modifier = Modifier.width(90.dp)) {
                                        if (isCritical) {
                                            ReorderStatusBadge("🚨 Agotado", NexusTheme.colors.dangerBg, NexusTheme.colors.dangerBorder, NexusTheme.colors.dangerText)
                                        } else {
                                            ReorderStatusBadge("⚠️ Bajo", NexusTheme.colors.warningBg, NexusTheme.colors.warningBorder, NexusTheme.colors.warningText)
                                        }
                                    }

                                    // SKU / Barcode
                                    Column(modifier = Modifier.width(130.dp)) {
                                        Text(item.sku, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.accent)
                                        Text(item.barcode, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NexusTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    // Producto
                                    Text(item.productName, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)

                                    // Depto
                                    Text(item.departmentName ?: "General", fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

                                    // Existencia
                                    Text(
                                        text = "$stockUnits ${item.baseUnit}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCritical) NexusTheme.colors.dangerText else NexusTheme.colors.warningText,
                                        modifier = Modifier.width(85.dp)
                                    )

                                    // Mín / Máx
                                    Text(
                                        text = "$minUnits / ${if (item.maxStockMillis > 0) maxUnits else "-"}",
                                        fontSize = 10.5.sp,
                                        color = NexusTheme.colors.textSecondary,
                                        modifier = Modifier.width(85.dp)
                                    )

                                    // Stepper Editable de Cantidad
                                    Row(
                                        modifier = Modifier.width(130.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val step = if (item.baseUnit == "KG") 500L else 1000L
                                                val next = (item.suggestedOrderMillis - step).coerceAtLeast(step)
                                                onUpdateQuantity(item.productId, next)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Menos", modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            text = "$orderUnits ${item.baseUnit}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isEdited) Color(0xFFD97706) else NexusTheme.colors.accent,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )

                                        IconButton(
                                            onClick = {
                                                val step = if (item.baseUnit == "KG") 500L else 1000L
                                                val next = item.suggestedOrderMillis + step
                                                onUpdateQuantity(item.productId, next)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Más", modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    // Costo Unitario
                                    Text(Money.fromCents(item.costCents).format(), fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(80.dp))

                                    // Subtotal Estimado
                                    Text(
                                        text = Money.fromCents(item.estimatedInvestmentCents).format(),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NexusTheme.colors.textPrimary,
                                        modifier = Modifier.width(95.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderStatusBadge(label: String, bg: Color, border: Color, text: Color) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
