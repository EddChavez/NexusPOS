package mx.nexuspos.client.features.products.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ensureActive
import mx.nexuspos.client.features.products.*
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@Composable
fun ProductsScreenV2(
    viewModel: ProductsViewModel,
    onPrintShelfLabel: (ProductDto, Int) -> Unit,
    onCatalogChanged: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val product = state.selectedProduct
    var compactDetail by remember { mutableStateOf(false) }
    var pricingTarget by remember { mutableStateOf<ProductDto?>(null) }
    var kitTarget by remember { mutableStateOf<ProductDto?>(null) }
    var archiveTarget by remember { mutableStateOf<ProductDto?>(null) }
    val paneFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(state.searchQuery, state.selectedDepartmentId, state.includeInactive) { listState.requestScrollToItem(0) }

    pricingTarget?.let { target ->
        PresentationPricingDialogV2(
            product = target,
            loadLists = viewModel::loadPriceLists,
            onSaveListPrice = { presentationId, level, cents ->
                viewModel.saveListPrice(target.id, presentationId, level, cents).onSuccess {
                    pricingTarget = it
                    onCatalogChanged()
                }
            },
            onSave = { presentationId, tiers ->
                viewModel.savePresentationPrices(target.id, presentationId, tiers).onSuccess {
                    pricingTarget = it
                    onCatalogChanged()
                }
            },
            onSavePromotion = { presentationId, schedule ->
                viewModel.savePromotion(target.id, presentationId, schedule).onSuccess {
                    pricingTarget = it
                    onCatalogChanged()
                }
            },
            onDismiss = { pricingTarget = null }
        )
    }
    kitTarget?.let { target ->
        KitEditorDialogV2(
            product = target,
            onLoadComponent = viewModel::loadKitComponent,
            onSearch = viewModel::searchKitComponents,
            onSave = { components ->
                viewModel.saveKit(target.id, components).onSuccess {
                    kitTarget = null
                    onCatalogChanged()
                }
            },
            onDismiss = { kitTarget = null }
        )
    }
    archiveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Desactivar producto") },
            text = { Text("${target.name} quedará fuera del catálogo de caja. Su historial seguirá disponible.") },
            confirmButton = {
                Button(
                    onClick = {
                        archiveTarget = null
                        viewModel.deactivateProduct(target, onCatalogChanged)
                    },
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Desactivar") }
            },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Volver") } }
        )
    }

    ProductCatalogTools(state, viewModel, onPrintShelfLabel, onCatalogChanged)

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val compact = maxWidth < 760.dp
        val detailOnly = compact && compactDetail && product != null
        val editing = state.showFormDialog

        fun back() {
            if (editing) viewModel.closeFormDialog()
            else compactDetail = false
        }

        LaunchedEffect(editing, detailOnly) {
            if (editing || detailOnly) paneFocus.requestFocus()
        }
        Column(
            Modifier.fillMaxSize().focusRequester(paneFocus).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && (editing || detailOnly)) {
                    back()
                    true
                } else false
            }.focusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editing || detailOnly) {
                    IconButton(onClick = ::back, enabled = !state.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver al catálogo")
                    }
                }
                Text(
                    if (!editing) "Productos" else if (state.productToEdit == null) "Nuevo producto" else "Editar producto",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (!editing) {
                    if (!detailOnly) {
                        Button(onClick = viewModel::openCreateDialog, enabled = !state.isSubmitting) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nuevo producto")
                        }
                    }
                    CatalogToolsMenu(
                        onDepartments = viewModel::openDepartmentsDialog,
                        onCsv = viewModel::openImportExportDialog,
                        onLabels = { viewModel.openShelfLabelsDialog(product) },
                        enabled = !state.isSubmitting
                    )
                }
            }

            if (editing) {
                key(state.productToEdit?.id) {
                    ProductFormPaneV2(
                        loadPreferences = viewModel::catalogPreferences,
                        product = state.productToEdit,
                        departments = state.departments,
                        busy = state.isSubmitting,
                        serverError = state.errorMessage,
                        onManageDepartments = viewModel::openDepartmentsDialog,
                        onBack = ::back,
                        onSave = { draft ->
                            viewModel.saveProduct(
                                sku = draft.sku,
                                name = draft.name,
                                barcode = draft.barcode,
                                departmentId = draft.departmentId,
                                baseUnit = draft.baseUnit,
                                costCents = draft.costCents,
                                priceCents = draft.priceCents,
                                taxRate = draft.taxRate,
                                usesInventory = draft.usesInventory,
                                initialStockMillis = draft.initialStockMillis,
                                minStockMillis = draft.minStockMillis,
                                maxStockMillis = draft.maxStockMillis,
                                isActive = state.productToEdit?.isActive ?: true,
                                isPerishable = draft.isPerishable,
                                shelfLifeAlertDays = draft.shelfLifeAlertDays,
                                onSaved = onCatalogChanged
                            )
                            compactDetail = true
                        }
                    )
                }
            } else {
                FeedbackMessageHost(
                    errorMessage = state.errorMessage,
                    successMessage = state.successMessage,
                    onDismissError = viewModel::dismissMessages,
                    onDismissSuccess = viewModel::dismissMessages
                )
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!detailOnly) {
                        ProductDirectory(
                            state = state,
                            listState = listState,
                            onSearch = viewModel::onSearchQueryChanged,
                            onDepartment = viewModel::onDepartmentSelected,
                            onToggleInactive = viewModel::onToggleIncludeInactive,
                            onSelect = {
                                viewModel.selectProduct(it)
                                compactDetail = true
                            },
                            modifier = if (compact) Modifier.weight(1f).fillMaxHeight() else Modifier.width(330.dp).fillMaxHeight()
                        )
                    }
                    if (!compact || detailOnly) {
                        if (product == null) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text("Selecciona un producto para ver su ficha.")
                            }
                        } else key(product.id) {
                            ProductDetail(
                                product = product,
                                busy = state.isSubmitting,
                                onEdit = { viewModel.openEditDialog(product) },
                                onPricing = { pricingTarget = product },
                                onKit = { kitTarget = product },
                                onLabel = { viewModel.openShelfLabelsDialog(product) },
                                onToggleActive = {
                                    if (product.isActive) archiveTarget = product
                                    else viewModel.reactivateProduct(product, onCatalogChanged)
                                },
                                onLoadComponent = viewModel::loadKitComponent,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCatalogTools(
    state: ProductsUiState,
    viewModel: ProductsViewModel,
    onPrintShelfLabel: (ProductDto, Int) -> Unit,
    onCatalogChanged: () -> Unit
) {
    if (state.showDepartmentsDialog) {
        DepartmentsManagementDialog(
            departments = state.departments,
            isSubmitting = state.isDepartmentSubmitting,
            onCreateDepartment = { viewModel.createDepartment(it) },
            onUpdateDepartment = viewModel::updateDepartment,
            onDeleteDepartment = viewModel::deleteDepartment,
            onDismiss = viewModel::closeDepartmentsDialog
        )
    }
    if (state.showImportExportDialog) {
        CatalogImportExportDialog(
            isOpen = true,
            exportedCsv = state.exportedCsvContent,
            isExporting = state.isExporting,
            isImporting = state.isImporting,
            importResult = state.importResult,
            onExport = { viewModel.exportCatalogCsv() },
            onImport = { viewModel.importCatalogCsv(it, onCatalogChanged) },
            onClearResult = viewModel::clearImportResult,
            onDismiss = viewModel::closeImportExportDialog
        )
    }
    if (state.showShelfLabelsDialog) {
        ShelfLabelsDialog(
            isOpen = true,
            products = state.products,
            selectedProduct = state.selectedProductForLabel,
            onSelectProduct = viewModel::selectProductForLabel,
            onPrintLabel = onPrintShelfLabel,
            onDismiss = viewModel::closeShelfLabelsDialog
        )
    }
}

@Composable
private fun CatalogToolsMenu(onDepartments: () -> Unit, onCsv: () -> Unit, onLabels: () -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, "Herramientas de catálogo") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Departamentos") }, onClick = { open = false; onDepartments() })
            DropdownMenuItem(text = { Text("Importar o exportar CSV") }, onClick = { open = false; onCsv() })
            DropdownMenuItem(text = { Text("Etiquetas de anaquel") }, onClick = { open = false; onLabels() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductDepartmentFilter(
    departments: List<DepartmentDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allLabel: String = "Sin departamento"
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = departments.firstOrNull { it.id == selectedId }?.name ?: allLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Departamento") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { expanded = false; onSelect(null) })
            departments.forEach { department ->
                DropdownMenuItem(text = { Text(department.name) }, onClick = { expanded = false; onSelect(department.id) })
            }
        }
    }
}

@Composable
private fun ProductDirectory(
    state: ProductsUiState,
    listState: LazyListState,
    onSearch: (String) -> Unit,
    onDepartment: (String?) -> Unit,
    onToggleInactive: () -> Unit,
    onSelect: (ProductDto) -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                label = { Text("Buscar producto") },
                placeholder = { Text("Nombre, SKU o código de barras") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.searchQuery.isEmpty()) null else {
                    { IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, "Limpiar búsqueda") } }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ProductDepartmentFilter(state.departments, state.selectedDepartmentId, onDepartment, Modifier.fillMaxWidth(), allLabel = "Todos")
            FilterChip(selected = state.includeInactive, onClick = onToggleInactive, label = { Text("Incluir inactivos") })
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.products.isEmpty() && !state.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay productos para esta búsqueda.")
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        val selected = state.selectedProduct?.id == product.id
                        OutlinedCard(
                            onClick = { onSelect(product) },
                            enabled = !state.isLoading && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selected) colors.secondaryContainer else colors.surface
                            )
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(product.sku, "Kit".takeIf { product.kitComponents.isNotEmpty() }, "Inactivo".takeUnless { product.isActive })
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                product.presentations.firstOrNull()?.let {
                                    Text(Money(it.priceCents).format(), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDetail(
    product: ProductDto,
    busy: Boolean,
    onEdit: () -> Unit,
    onPricing: () -> Unit,
    onKit: () -> Unit,
    onLabel: () -> Unit,
    onToggleActive: () -> Unit,
    onLoadComponent: suspend (String) -> Result<ProductDto>,
    modifier: Modifier
) {
    val unit = ProductUnit.fromCode(product.baseUnit)
    val isKit = product.kitComponents.isNotEmpty()
    val components = remember(product.kitComponents) { mutableStateMapOf<String, ProductDto>() }
    LaunchedEffect(product.kitComponents) {
        for (component in product.kitComponents) {
            val info = onLoadComponent(component.productId)
            ensureActive()
            info.onSuccess { components[component.productId] = it }
        }
    }
    OutlinedCard(modifier, shape = MaterialTheme.shapes.large) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(product.sku, product.departmentName, if (product.isActive) "Activo" else "Inactivo")
                            .filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onEdit, enabled = !busy) { Text("Editar ficha") }
                    OutlinedButton(onClick = onPricing, enabled = !busy && product.presentations.isNotEmpty()) { Text("Precios de presentaciones") }
                    if (isKit || (!product.usesInventory && unit == ProductUnit.PIECE && product.presentations.isNotEmpty())) {
                        OutlinedButton(onClick = onKit, enabled = !busy) { Text(if (isKit) "Editar kit" else "Configurar kit") }
                    }
                    TextButton(onClick = onLabel, enabled = !busy && product.presentations.isNotEmpty()) { Text("Etiqueta") }
                }
            }
            item {
                HorizontalDivider()
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProductMetric("Costo", Money(product.costCents).format())
                    ProductMetric("IVA", productTaxLabel(product.taxRate))
                    ProductMetric("Unidad base", unit.label)
                    ProductMetric(
                        "Existencia",
                        when {
                            isKit -> "Por componente"
                            product.usesInventory -> Quantity(product.currentStockMillis).format(unit)
                            else -> "Sin control"
                        }
                    )
                    if (product.usesInventory) {
                        ProductMetric("Mínimo", Quantity(product.minStockMillis).format(unit))
                        if (product.maxStockMillis > 0) ProductMetric("Máximo", Quantity(product.maxStockMillis).format(unit))
                        if (product.isPerishable) ProductMetric("Alerta de caducidad", "${product.shelfLifeAlertDays} días")
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("Presentaciones", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
            }
            if (product.presentations.isEmpty()) {
                item { Text("Agrega un código de barras desde la ficha del producto.") }
            }
            items(product.presentations, key = { "presentation-${it.id}" }) { presentation ->
                ListItem(
                    headlineContent = { Text(presentation.name) },
                    supportingContent = {
                        Column {
                            Text("${presentation.barcode} · ${Quantity(presentation.factorMillis).format(unit)} por presentación")
                            if (presentation.priceTiers.isNotEmpty()) Text("${presentation.priceTiers.size} tarifa(s) por cantidad")
                        }
                    },
                    trailingContent = { Text(Money(presentation.priceCents).format(), style = MaterialTheme.typography.labelLarge) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
            if (isKit) {
                item { Text("Componentes por kit", style = MaterialTheme.typography.titleMedium) }
                items(product.kitComponents, key = { "component-${it.productId}" }) { component ->
                    val info = components[component.productId]
                    val quantity = info?.let { Quantity(component.quantityMillis).format(ProductUnit.fromCode(it.baseUnit)) }
                        ?: Quantity(component.quantityMillis).format().substringBefore(' ')
                    ListItem(
                        headlineContent = { Text(info?.name ?: component.productId) },
                        supportingContent = info?.let { { Text(it.sku) } },
                        trailingContent = { Text(quantity) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }
            item {
                HorizontalDivider()
                TextButton(
                    enabled = !busy,
                    onClick = onToggleActive,
                    colors = if (product.isActive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.textButtonColors()
                ) { Text(if (product.isActive) "Desactivar producto" else "Reactivar producto") }
            }
        }
    }
}

@Composable
private fun ProductMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
    }
}
