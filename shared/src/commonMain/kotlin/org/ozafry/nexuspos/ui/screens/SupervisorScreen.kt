package org.ozafry.nexuspos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ozafry.nexuspos.models.ProductDto
import org.ozafry.nexuspos.models.SaleDto
import org.ozafry.nexuspos.repository.OfflineSaleRepository
import org.ozafry.nexuspos.ui.theme.AmberWarning
import org.ozafry.nexuspos.ui.theme.EmeraldSuccess
import org.ozafry.nexuspos.ui.theme.RoseDanger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorScreen(
    repository: OfflineSaleRepository,
    onBackToCashier: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val products = remember { repository.getProducts() }
    val currentShift = remember { repository.getCurrentShift() }
    val pendingEvents = remember { repository.getPendingSyncEvents() }

    // Metrics
    val lowStockCount = remember(products) { products.count { it.stockQuantity < 15 } }
    val openShiftCashier = currentShift?.cashierName ?: "Ninguna"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel de Supervisión", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Monitoreo en tiempo real y telemetría multicaja", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onBackToCashier,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("← Terminal de Caja", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // KPI Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Ventas en Turno",
                    value = "$${currentShift?.let { it.cashSales + it.cardSales + it.transferSales } ?: 0.0}",
                    subtitle = "Turno actual",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Caja Activa",
                    value = if (currentShift != null) "1 Operando" else "Cerrada",
                    subtitle = "Cajero: $openShiftCashier",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Alertas de Stock",
                    value = "$lowStockCount ítems",
                    subtitle = "Stock menor a 15",
                    containerColor = if (lowStockCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (lowStockCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Cola de Sync",
                    value = "${pendingEvents.size} pendientes",
                    subtitle = "Outbox offline-first",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Inventario y Alertas") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Eventos de Sincronización") })
            }

            when (selectedTab) {
                0 -> {
                    // Inventory list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(products) { p ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("SKU: ${p.sku} | Barcode: ${p.barcode ?: "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("$${p.price} / ${p.unit}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                p.stockQuantity <= 5.0 -> RoseDanger.copy(alpha = 0.2f)
                                                p.stockQuantity <= 15.0 -> AmberWarning.copy(alpha = 0.2f)
                                                else -> EmeraldSuccess.copy(alpha = 0.2f)
                                            }
                                        ) {
                                            val stockFormatted = if (p.unit == "KG") "${p.stockQuantity} kg" else "${p.stockQuantity.toInt()} pza"
                                            Text(
                                                "Stock: $stockFormatted",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = when {
                                                    p.stockQuantity <= 5.0 -> RoseDanger
                                                    p.stockQuantity <= 15.0 -> AmberWarning
                                                    else -> EmeraldSuccess
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Outbox events list
                    if (pendingEvents.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("✓ No hay eventos pendientes de sincronización. Todo al día.", color = EmeraldSuccess, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pendingEvents) { evt ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("${evt.aggregateType} • ${evt.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("ID: ${evt.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "PENDIENTE",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, color = contentColor.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, color = contentColor, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = contentColor.copy(alpha = 0.7f))
        }
    }
}
