package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.core.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onBackToSales: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Diálogo de Movimiento de Stock
    if (state.showMovementDialog && state.productForMovement != null) {
        StockMovementDialog(
            product = state.productForMovement!!,
            onConfirm = { type, deltaMillis, reason, ref ->
                viewModel.submitStockMovement(
                    productId = state.productForMovement!!.id,
                    type = type,
                    quantityMillis = deltaMillis,
                    reason = reason,
                    reference = ref
                )
            },
            onDismiss = { viewModel.closeMovementDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.bgCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. BARRA SUPERIOR Y TARJETAS KPI
        InventoryHeaderCards(
            summary = state.summary,
            stockFilter = state.stockFilter,
            onFilterSelected = { viewModel.onStockFilterSelected(it) },
            onBackToSales = onBackToSales
        )

        // BANNERS DE NOTIFICACIÓN
        if (state.errorMessage != null) {
            Surface(
                color = NexusTheme.colors.dangerBg,
                border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = NexusTheme.colors.dangerText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { viewModel.dismissMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTheme.colors.dangerText)
                    }
                }
            }
        }

        if (state.successMessage != null) {
            Surface(
                color = NexusTheme.colors.surfaceGreen,
                border = BorderStroke(1.dp, NexusTheme.colors.borderGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.successMessage ?: "",
                        color = NexusTheme.colors.textGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { viewModel.dismissMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTheme.colors.textGreen)
                    }
                }
            }
        }

        // 2. SELECTOR DE PESTAÑAS (Existencias vs Kardex)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabButton(
                    text = "📦 Existencias y Alertas",
                    isSelected = state.activeTab == InventoryTab.STOCK_TABLE,
                    onClick = { viewModel.onTabSelected(InventoryTab.STOCK_TABLE) }
                )
                TabButton(
                    text = "📜 Kardex de Movimientos",
                    isSelected = state.activeTab == InventoryTab.KARDEX,
                    onClick = { viewModel.onTabSelected(InventoryTab.KARDEX) }
                )
            }

            if (state.activeTab == InventoryTab.STOCK_TABLE && state.selectedProduct != null) {
                Button(
                    onClick = { viewModel.openMovementDialog(state.selectedProduct!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("[INS] Ajustar Stock Seleccionado", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. CONTENIDO PRINCIPAL SEGÚN PESTAÑA
        when (state.activeTab) {
            InventoryTab.STOCK_TABLE -> {
                StockTablePanel(
                    products = state.products,
                    departments = state.departments,
                    selectedDepartmentId = state.selectedDepartmentId,
                    stockFilter = state.stockFilter,
                    searchQuery = state.searchQuery,
                    selectedProduct = state.selectedProduct,
                    isLoading = state.isLoading,
                    onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                    onDepartmentSelected = { viewModel.onDepartmentSelected(it) },
                    onFilterSelected = { viewModel.onStockFilterSelected(it) },
                    onSelectProduct = { viewModel.selectProduct(it) },
                    onAdjustStock = { viewModel.openMovementDialog(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            InventoryTab.KARDEX -> {
                KardexPanel(
                    movements = state.movements,
                    isLoading = state.isMovementsLoading,
                    selectedProduct = state.selectedProduct,
                    onClearProductFilter = { viewModel.selectProduct(null) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) NexusTheme.colors.actionPrimary else NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected) NexusTheme.colors.actionPrimary else NexusTheme.colors.borderPrimary),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else NexusTheme.colors.textPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun InventoryHeaderCards(
    summary: InventorySummaryDto?,
    stockFilter: StockFilter,
    onFilterSelected: (StockFilter) -> Unit,
    onBackToSales: () -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4 KPI Cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KpiCard(
                    title = "Artículos en Catálogo",
                    value = "${summary?.trackedProductsCount ?: 0}",
                    subvalue = "Controlados",
                    isSelected = stockFilter == StockFilter.ALL,
                    onClick = { onFilterSelected(StockFilter.ALL) }
                )

                KpiCard(
                    title = "Bajo Mínimo",
                    value = "${summary?.lowStockCount ?: 0}",
                    subvalue = "Reordenar pronto",
                    highlightColor = NexusTheme.colors.warningText,
                    isSelected = stockFilter == StockFilter.LOW_STOCK,
                    onClick = { onFilterSelected(StockFilter.LOW_STOCK) }
                )

                KpiCard(
                    title = "Agotados",
                    value = "${summary?.outOfStockCount ?: 0}",
                    subvalue = "Sin existencias",
                    highlightColor = NexusTheme.colors.dangerText,
                    isSelected = stockFilter == StockFilter.OUT_OF_STOCK,
                    onClick = { onFilterSelected(StockFilter.OUT_OF_STOCK) }
                )

                KpiCard(
                    title = "Valuación de Inventario",
                    value = summary?.let { Money.fromCents(it.totalValuationCents).format() } ?: "$0.00",
                    subvalue = "Costo total en tienda",
                    highlightColor = NexusTheme.colors.actionPrimary
                )
            }

            OutlinedButton(
                onClick = onBackToSales,
                border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = NexusTheme.colors.btnSecondaryText
                )
                Spacer(Modifier.width(6.dp))
                Text("[Esc] Ventas", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subvalue: String,
    highlightColor: androidx.compose.ui.graphics.Color? = null,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = if (isSelected) NexusTheme.colors.accentLight.copy(alpha = 0.5f) else NexusTheme.colors.bgSurfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) NexusTheme.colors.accent else NexusTheme.colors.borderSubtle
        ),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = NexusTheme.colors.textSecondary
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = highlightColor ?: NexusTheme.colors.textPrimary
            )
            Text(
                text = subvalue,
                fontSize = 10.sp,
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun StockTablePanel(
    products: List<ProductDto>,
    departments: List<DepartmentDto>,
    selectedDepartmentId: String?,
    stockFilter: StockFilter,
    searchQuery: String,
    selectedProduct: ProductDto?,
    isLoading: Boolean,
    onSearchChanged: (String) -> Unit,
    onDepartmentSelected: (String?) -> Unit,
    onFilterSelected: (StockFilter) -> Unit,
    onSelectProduct: (ProductDto) -> Unit,
    onAdjustStock: (ProductDto) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filtrado local según stockFilter
    val filteredProducts = remember(products, stockFilter) {
        when (stockFilter) {
            StockFilter.ALL -> products
            StockFilter.LOW_STOCK -> products.filter {
                it.usesInventory && it.currentStockMillis <= it.minStockMillis && it.currentStockMillis > 0
            }
            StockFilter.OUT_OF_STOCK -> products.filter {
                it.usesInventory && it.currentStockMillis <= 0
            }
        }
    }

    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Filtros superiores
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Buscar por nombre, código de barras o SKU...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NexusTheme.colors.textSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = NexusTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.width(360.dp).height(46.dp)
                )

                // Chips de estado rápido
                FilterChip(
                    selected = stockFilter == StockFilter.ALL,
                    onClick = { onFilterSelected(StockFilter.ALL) },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = stockFilter == StockFilter.LOW_STOCK,
                    onClick = { onFilterSelected(StockFilter.LOW_STOCK) },
                    label = { Text("⚠️ Stock Bajo") }
                )
                FilterChip(
                    selected = stockFilter == StockFilter.OUT_OF_STOCK,
                    onClick = { onFilterSelected(StockFilter.OUT_OF_STOCK) },
                    label = { Text("🚨 Agotados") }
                )
            }

            // Chips de departamentos
            if (departments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Depto:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    FilterChip(
                        selected = selectedDepartmentId == null,
                        onClick = { onDepartmentSelected(null) },
                        label = { Text("Todos", fontSize = 11.sp) }
                    )
                    departments.forEach { dept ->
                        FilterChip(
                            selected = selectedDepartmentId == dept.id,
                            onClick = { onDepartmentSelected(dept.id) },
                            label = { Text(dept.name, fontSize = 11.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(bottom = 6.dp))

            // Encabezado de la tabla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.bgSurfaceSubtle, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SKU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                Text("CÓDIGO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(120.dp))
                Text("PRODUCTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                Text("DEPTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(100.dp))
                Text("EXISTENCIA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(100.dp))
                Text("MÍNIMO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
                Text("COSTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                Text("VALUACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(95.dp))
                Text("ESTADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                Text("ACCIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(90.dp))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusTheme.colors.accent)
                }
            } else if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No hay productos coincidentes con los filtros seleccionados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        val isSelected = selectedProduct?.id == prod.id
                        val primaryBarcode = prod.presentations.firstOrNull()?.barcode ?: "S/C"
                        val stockUnits = prod.currentStockMillis / 1000.0
                        val minStockUnits = prod.minStockMillis / 1000.0
                        val valuationCents = (prod.currentStockMillis * prod.costCents) / 1000L

                        Surface(
                            color = if (isSelected) NexusTheme.colors.accentLight.copy(alpha = 0.4f) else NexusTheme.colors.bgSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = if (isSelected) BorderStroke(1.dp, NexusTheme.colors.accent) else null,
                            modifier = Modifier.fillMaxWidth().clickable { onSelectProduct(prod) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prod.sku, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.accent, modifier = Modifier.width(85.dp))
                                Text(primaryBarcode, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(prod.departmentName ?: "General", fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

                                // Existencia
                                Text(
                                    text = "$stockUnits ${prod.baseUnit}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (prod.currentStockMillis <= 0) NexusTheme.colors.dangerText else if (prod.currentStockMillis <= prod.minStockMillis) NexusTheme.colors.warningText else NexusTheme.colors.textPrimary,
                                    modifier = Modifier.width(100.dp)
                                )

                                Text("$minStockUnits ${prod.baseUnit}", fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(80.dp))
                                Text(Money.fromCents(prod.costCents).format(), fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(85.dp))
                                Text(Money.fromCents(valuationCents.coerceAtLeast(0L)).format(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary, modifier = Modifier.width(95.dp))

                                // Badge de Estado
                                Box(modifier = Modifier.width(85.dp)) {
                                    if (prod.currentStockMillis <= 0) {
                                        StatusBadge("Agotado", NexusTheme.colors.dangerBg, NexusTheme.colors.dangerBorder, NexusTheme.colors.dangerText)
                                    } else if (prod.currentStockMillis <= prod.minStockMillis) {
                                        StatusBadge("Bajo", NexusTheme.colors.warningBg, NexusTheme.colors.warningBorder, NexusTheme.colors.warningText)
                                    } else {
                                        StatusBadge("Óptimo", NexusTheme.colors.surfaceGreen, NexusTheme.colors.borderGreen, NexusTheme.colors.textGreen)
                                    }
                                }

                                // Botón Ajustar
                                Button(
                                    onClick = { onAdjustStock(prod) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.width(90.dp).height(30.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ajustar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun StatusBadge(
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    text: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun KardexPanel(
    movements: List<StockMovementDto>,
    isLoading: Boolean,
    selectedProduct: ProductDto?,
    onClearProductFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Header del Kardex
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedProduct != null) "Kardex de '${selectedProduct.name}'" else "Kardex General de Movimientos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    Text(
                        text = "${movements.size} transacciones registradas cronológicamente",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTheme.colors.textSecondary
                    )
                }

                if (selectedProduct != null) {
                    OutlinedButton(onClick = onClearProductFilter) {
                        Text("Ver Todos los Productos")
                    }
                }
            }

            HorizontalDivider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))

            // Encabezado de tabla Kardex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.bgSurfaceSubtle, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FECHA / HORA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(130.dp))
                Text("PRODUCTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                Text("TIPO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(100.dp))
                Text("CANTIDAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(90.dp))
                Text("MOTIVO / CONCEPTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(220.dp))
                Text("REFERENCIA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(110.dp))
                Text("USUARIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(110.dp))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusTheme.colors.accent)
                }
            } else if (movements.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No hay movimientos registrados en el kardex",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(movements, key = { it.id }) { mov ->
                        val qtyUnits = mov.quantityMillis / 1000.0
                        val dateFormatted = dateFormat.format(Date(mov.createdAt))

                        Surface(
                            color = NexusTheme.colors.bgSurface,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dateFormatted, fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(130.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mov.productName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(mov.productSku, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NexusTheme.colors.accent)
                                }

                                // Badge de Tipo
                                Box(modifier = Modifier.width(100.dp)) {
                                    when (mov.type) {
                                        "ENTRY" -> StatusBadge("ENTRADA", NexusTheme.colors.surfaceGreen, NexusTheme.colors.borderGreen, NexusTheme.colors.textGreen)
                                        "LOSS" -> StatusBadge("MERMA", NexusTheme.colors.dangerBg, NexusTheme.colors.dangerBorder, NexusTheme.colors.dangerText)
                                        "ADJUSTMENT" -> StatusBadge("AJUSTE", NexusTheme.colors.warningBg, NexusTheme.colors.warningBorder, NexusTheme.colors.warningText)
                                        "SALE" -> StatusBadge("VENTA", NexusTheme.colors.accentLight, NexusTheme.colors.accent, NexusTheme.colors.accent)
                                        "REFUND" -> StatusBadge("DEVOLUCIÓN", NexusTheme.colors.bgSurfaceSubtle, NexusTheme.colors.borderSubtle, NexusTheme.colors.textPrimary)
                                        else -> StatusBadge(mov.type, NexusTheme.colors.bgSurfaceSubtle, NexusTheme.colors.borderSubtle, NexusTheme.colors.textSecondary)
                                    }
                                }

                                // Cantidad con signo
                                Text(
                                    text = if (mov.quantityMillis > 0) "+$qtyUnits" else "$qtyUnits",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mov.quantityMillis > 0) NexusTheme.colors.textGreen else NexusTheme.colors.dangerText,
                                    modifier = Modifier.width(90.dp)
                                )

                                Text(mov.reason, fontSize = 11.sp, color = NexusTheme.colors.textPrimary, modifier = Modifier.width(220.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(mov.reference.ifBlank { "—" }, fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(mov.userName ?: "Cajero", fontSize = 11.sp, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
