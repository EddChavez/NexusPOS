package mx.nexuspos.client.features.products

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
import mx.nexuspos.core.Money

@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel,
    onBackToSales: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Form Dialog (Crear / Editar)
    if (state.showFormDialog) {
        ProductFormDialog(
            productToEdit = state.productToEdit,
            departments = state.departments,
            onCreateDepartment = { name, onCreated ->
                viewModel.createDepartment(name, onCreated)
            },
            onSave = { sku, name, barcode, departmentId, baseUnit, costCents, priceCents, taxRate, usesInventory, initialStockMillis, minStockMillis, maxStockMillis, isActive ->
                viewModel.saveProduct(
                    sku = sku,
                    name = name,
                    barcode = barcode,
                    departmentId = departmentId,
                    baseUnit = baseUnit,
                    costCents = costCents,
                    priceCents = priceCents,
                    taxRate = taxRate,
                    usesInventory = usesInventory,
                    initialStockMillis = initialStockMillis,
                    minStockMillis = minStockMillis,
                    maxStockMillis = maxStockMillis,
                    isActive = isActive
                )
            },
            onDismiss = { viewModel.closeFormDialog() }
        )
    }

    // Diálogo de Gestión de Departamentos
    if (state.showDepartmentsDialog) {
        DepartmentsManagementDialog(
            departments = state.departments,
            isSubmitting = state.isDepartmentSubmitting,
            onCreateDepartment = { viewModel.createDepartment(it) },
            onUpdateDepartment = { id, name -> viewModel.updateDepartment(id, name) },
            onDeleteDepartment = { viewModel.deleteDepartment(it) },
            onDismiss = { viewModel.closeDepartmentsDialog() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.bgCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP TOOLBAR & FILTROS
        ProductsTopToolbar(
            searchQuery = state.searchQuery,
            departments = state.departments,
            selectedDepartmentId = state.selectedDepartmentId,
            includeInactive = state.includeInactive,
            onSearchChanged = { viewModel.onSearchQueryChanged(it) },
            onDepartmentSelected = { viewModel.onDepartmentSelected(it) },
            onToggleInactive = { viewModel.onToggleIncludeInactive() },
            onNewProduct = { viewModel.openCreateDialog() },
            onManageDepartments = { viewModel.openDepartmentsDialog() },
            onBackToSales = onBackToSales
        )

        // BANNERS DE MENSAJES (ERROR / ÉXITO)
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

        // TABLA PRINCIPAL DE PRODUCTOS
        ProductsTablePanel(
            products = state.products,
            selectedProduct = state.selectedProduct,
            isLoading = state.isLoading,
            onSelectProduct = { viewModel.selectProduct(it) },
            onEditProduct = { viewModel.openEditDialog(it) },
            onDeactivateProduct = { viewModel.deactivateProduct(it) },
            onReactivateProduct = { viewModel.reactivateProduct(it) },
            onNewProduct = { viewModel.openCreateDialog() },
            onClearSearch = { viewModel.onSearchQueryChanged("") },
            searchQuery = state.searchQuery,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
private fun ProductsTopToolbar(
    searchQuery: String,
    departments: List<DepartmentDto>,
    selectedDepartmentId: String?,
    includeInactive: Boolean,
    onSearchChanged: (String) -> Unit,
    onDepartmentSelected: (String?) -> Unit,
    onToggleInactive: () -> Unit,
    onNewProduct: () -> Unit,
    onManageDepartments: () -> Unit,
    onBackToSales: () -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChanged,
                        placeholder = { Text("Buscar por nombre, código de barras o SKU...") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = NexusTheme.colors.textSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchChanged("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.width(420.dp).height(48.dp)
                    )

                    FilterChip(
                        selected = includeInactive,
                        onClick = onToggleInactive,
                        label = { Text("Ver Inactivos") },
                        leadingIcon = {
                            if (includeInactive) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onNewProduct,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("[INS] Nuevo Producto", fontWeight = FontWeight.Bold)
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

            // Barra horizontal de categorías/departamentos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Departamentos:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(end = 4.dp)
                )

                FilterChip(
                    selected = selectedDepartmentId == null,
                    onClick = { onDepartmentSelected(null) },
                    label = { Text("Todos") }
                )

                departments.forEach { dept ->
                    FilterChip(
                        selected = selectedDepartmentId == dept.id,
                        onClick = { onDepartmentSelected(dept.id) },
                        label = { Text(dept.name) }
                    )
                }

                Spacer(Modifier.width(4.dp))

                OutlinedButton(
                    onClick = onManageDepartments,
                    border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = NexusTheme.colors.btnSecondaryText
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Gestionar Deptos", fontSize = 11.sp, color = NexusTheme.colors.btnSecondaryText)
                }
            }
        }
    }
}

@Composable
private fun ProductsTablePanel(
    products: List<ProductDto>,
    selectedProduct: ProductDto?,
    isLoading: Boolean,
    onSelectProduct: (ProductDto) -> Unit,
    onEditProduct: (ProductDto) -> Unit,
    onDeactivateProduct: (ProductDto) -> Unit,
    onReactivateProduct: (ProductDto) -> Unit,
    onNewProduct: () -> Unit,
    onClearSearch: () -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Título de la tabla y total
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catálogo de Productos (${products.size} encontrados)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }

            HorizontalDivider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))

            // ENCABEZADO DE LA TABLA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.bgSurfaceSubtle, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SKU",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = "CÓDIGO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(130.dp)
                )
                Text(
                    text = "DESCRIPCIÓN",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "DEPTO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = "UM",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(55.dp)
                )
                Text(
                    text = "PRECIO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = "COSTO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(85.dp)
                )
                Text(
                    text = "EXISTENCIA",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = "ESTADO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = "ACCIONES",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.width(100.dp)
                )
            }

            // CONTENIDO
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NexusTheme.colors.accent)
                }
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = NexusTheme.colors.textSecondary
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No se encontraron productos coincidentes" else "El catálogo está vacío",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NexusTheme.colors.textPrimary
                        )
                        if (searchQuery.isNotBlank()) {
                            OutlinedButton(onClick = onClearSearch) {
                                Text("Limpiar Filtros")
                            }
                        } else {
                            Button(
                                onClick = onNewProduct,
                                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Registrar Primer Producto")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        val isSelected = selectedProduct?.id == product.id
                        val primaryBarcode = product.presentations.firstOrNull()?.barcode ?: "S/C"
                        val primaryPriceCents = product.presentations.firstOrNull()?.priceCents ?: 0L

                        Surface(
                            color = if (isSelected) NexusTheme.colors.accentLight.copy(alpha = 0.4f) else NexusTheme.colors.bgSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = if (isSelected) BorderStroke(1.dp, NexusTheme.colors.accent) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProduct(product) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // SKU
                                Text(
                                    text = product.sku,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NexusTheme.colors.accent,
                                    modifier = Modifier.width(90.dp)
                                )

                                // Código de barras
                                Text(
                                    text = primaryBarcode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.width(130.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Descripción
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (product.isActive) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (product.presentations.size > 1) {
                                        Text(
                                            text = "+${product.presentations.size - 1} presentación(es)",
                                            fontSize = 10.sp,
                                            color = NexusTheme.colors.textSecondary
                                        )
                                    }
                                }

                                // Departamento
                                Text(
                                    text = product.departmentName ?: "General",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.width(110.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Unidad
                                Text(
                                    text = product.baseUnit,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.width(55.dp)
                                )

                                // Precio de venta
                                Text(
                                    text = Money.fromCents(primaryPriceCents).format(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NexusTheme.colors.textPrimary,
                                    modifier = Modifier.width(90.dp)
                                )

                                // Costo
                                Text(
                                    text = Money.fromCents(product.costCents).format(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.width(85.dp)
                                )

                                // Existencia
                                Box(modifier = Modifier.width(110.dp)) {
                                    if (!product.usesInventory) {
                                        Text(
                                            text = "Sin control",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NexusTheme.colors.textSecondary
                                        )
                                    } else {
                                        val stock = product.currentStockMillis / 1000.0
                                        val formattedStock = if (product.currentStockMillis % 1000L == 0L) {
                                            "${product.currentStockMillis / 1000L}"
                                        } else {
                                            "$stock"
                                        }

                                        if (product.currentStockMillis <= 0L) {
                                            Surface(
                                                color = NexusTheme.colors.dangerBg,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder)
                                            ) {
                                                Text(
                                                    text = "Agotado (0)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NexusTheme.colors.dangerText,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (product.currentStockMillis <= product.minStockMillis) {
                                            Surface(
                                                color = NexusTheme.colors.warningBg,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, NexusTheme.colors.warningBorder)
                                            ) {
                                                Text(
                                                    text = "Bajo ($formattedStock)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NexusTheme.colors.warningText,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "$formattedStock ${product.baseUnit}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = NexusTheme.colors.textPrimary
                                            )
                                        }
                                    }
                                }

                                // Estado
                                Box(modifier = Modifier.width(80.dp)) {
                                    if (product.isActive) {
                                        Surface(
                                            color = NexusTheme.colors.surfaceGreen,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, NexusTheme.colors.borderGreen)
                                        ) {
                                            Text(
                                                text = "Activo",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NexusTheme.colors.textGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            color = NexusTheme.colors.bgSurfaceSubtle,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                                        ) {
                                            Text(
                                                text = "Inactivo",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NexusTheme.colors.textSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Acciones
                                Row(
                                    modifier = Modifier.width(100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEditProduct(product) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = NexusTheme.colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    if (product.isActive) {
                                        IconButton(
                                            onClick = { onDeactivateProduct(product) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Desactivar",
                                                tint = NexusTheme.colors.dangerText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { onReactivateProduct(product) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Reactivar",
                                                tint = NexusTheme.colors.textGreen,
                                                modifier = Modifier.size(16.dp)
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
