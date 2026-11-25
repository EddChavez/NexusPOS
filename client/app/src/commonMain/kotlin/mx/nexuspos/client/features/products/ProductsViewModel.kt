package mx.nexuspos.client.features.products

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.catalog.CsvCatalogHelper
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.PresentationPriceTier
import mx.nexuspos.contracts.catalog.UpdatePresentationPricesRequest
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest

class ProductsViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadDepartments()
        loadProducts()
    }

    fun loadDepartments() {
        scope.launch {
            val res = apiClient.getDepartments()
            res.onSuccess { depts ->
                _uiState.update { it.copy(departments = depts) }
            }
        }
    }

    fun loadProducts(
        query: String? = _uiState.value.searchQuery,
        departmentId: String? = _uiState.value.selectedDepartmentId
    ) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = apiClient.searchProducts(
                barcode = null,
                query = query,
                departmentId = departmentId,
                includeInactive = _uiState.value.includeInactive
            )
            res.onSuccess { list ->
                _uiState.update { current ->
                    val selected = current.selectedProduct?.let { sel -> list.firstOrNull { it.id == sel.id } }
                        ?: list.firstOrNull()
                    current.copy(
                        products = list,
                        selectedProduct = selected,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250) // Debounce
            loadProducts(query = newQuery)
        }
    }

    fun onDepartmentSelected(departmentId: String?) {
        _uiState.update { it.copy(selectedDepartmentId = departmentId) }
        loadProducts(departmentId = departmentId)
    }

    fun onToggleIncludeInactive() {
        _uiState.update { it.copy(includeInactive = !it.includeInactive) }
        loadProducts()
    }

    suspend fun savePresentationPrices(
        productId: String,
        presentationId: String,
        tiers: List<PresentationPriceTier>
    ): Result<ProductDto> =
        apiClient.updatePresentationPrices(productId, presentationId, UpdatePresentationPricesRequest(tiers))
            .onSuccess { product ->
                _uiState.update { state ->
                    state.copy(
                        products = state.products.map { if (it.id == product.id) product else it },
                        selectedProduct = if (state.selectedProduct?.id == product.id) product else state.selectedProduct,
                        successMessage = "Tarifas guardadas para ${product.name}."
                    )
                }
            }

    fun selectProduct(product: ProductDto?) {
        _uiState.update { it.copy(selectedProduct = product) }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showFormDialog = true, productToEdit = null, errorMessage = null) }
    }

    fun openEditDialog(product: ProductDto) {
        _uiState.update { it.copy(showFormDialog = true, productToEdit = product, errorMessage = null) }
    }

    fun closeFormDialog() {
        _uiState.update { it.copy(showFormDialog = false, productToEdit = null) }
    }

    fun saveProduct(
        sku: String,
        name: String,
        barcode: String,
        departmentId: String?,
        baseUnit: String,
        costCents: Long,
        priceCents: Long,
        taxRate: Double,
        usesInventory: Boolean,
        initialStockMillis: Long,
        minStockMillis: Long,
        maxStockMillis: Long,
        isActive: Boolean = true
    ) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val currentProduct = _uiState.value.productToEdit

            val primaryPresentation = CreatePresentationRequest(
                id = currentProduct?.presentations?.firstOrNull()?.id,
                barcode = barcode.trim(),
                name = name.trim(),
                factorMillis = currentProduct?.presentations?.firstOrNull()?.factorMillis ?: 1000L,
                priceCents = priceCents
            )

            // Si hay otras presentaciones existentes en el producto, las conservamos
            val otherPresentations = currentProduct?.presentations?.drop(1)?.map {
                CreatePresentationRequest(
                    id = it.id,
                    barcode = it.barcode,
                    name = it.name,
                    factorMillis = it.factorMillis,
                    priceCents = it.priceCents
                )
            } ?: emptyList()

            val allPresentations = listOf(primaryPresentation) + otherPresentations

            if (currentProduct == null) {
                // Crear producto
                val req = CreateProductRequest(
                    sku = sku.trim(),
                    name = name.trim(),
                    departmentId = departmentId,
                    baseUnit = baseUnit,
                    costCents = costCents,
                    taxRate = taxRate,
                    usesInventory = usesInventory,
                    initialStockMillis = initialStockMillis,
                    minStockMillis = minStockMillis,
                    maxStockMillis = maxStockMillis,
                    presentations = allPresentations
                )
                val res = apiClient.createProduct(req)
                res.onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showFormDialog = false,
                            selectedProduct = created,
                            successMessage = "Producto '${created.name}' registrado exitosamente"
                        )
                    }
                    loadProducts()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
            } else {
                // Actualizar producto
                val req = UpdateProductRequest(
                    sku = sku.trim().ifEmpty { currentProduct.sku },
                    name = name.trim(),
                    departmentId = departmentId,
                    baseUnit = baseUnit,
                    costCents = costCents,
                    taxRate = taxRate,
                    usesInventory = usesInventory,
                    minStockMillis = minStockMillis,
                    maxStockMillis = maxStockMillis,
                    isActive = isActive,
                    presentations = allPresentations
                )
                val res = apiClient.updateProduct(currentProduct.id, req)
                res.onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showFormDialog = false,
                            selectedProduct = updated,
                            successMessage = "Producto '${updated.name}' actualizado exitosamente"
                        )
                    }
                    loadProducts()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
            }
        }
    }

    fun deactivateProduct(product: ProductDto) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val res = apiClient.deactivateProduct(product.id)
            res.onSuccess { deactivated ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        selectedProduct = deactivated,
                        successMessage = "Producto '${deactivated.name}' desactivado"
                    )
                }
                loadProducts()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun reactivateProduct(product: ProductDto) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val res = apiClient.reactivateProduct(product.id)
            res.onSuccess { reactivated ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        selectedProduct = reactivated,
                        successMessage = "Producto '${reactivated.name}' reactivado"
                    )
                }
                loadProducts()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun openDepartmentsDialog() {
        _uiState.update { it.copy(showDepartmentsDialog = true, errorMessage = null) }
    }

    fun closeDepartmentsDialog() {
        _uiState.update { it.copy(showDepartmentsDialog = false) }
    }

    fun createDepartment(name: String, onCreated: ((DepartmentDto) -> Unit)? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre del departamento no puede estar vacío") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isDepartmentSubmitting = true, errorMessage = null) }
            val res = apiClient.createDepartment(CreateDepartmentRequest(trimmed))
            res.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isDepartmentSubmitting = false,
                        successMessage = "Departamento '${created.name}' creado exitosamente"
                    )
                }
                loadDepartments()
                onCreated?.invoke(created)
            }.onFailure { err ->
                _uiState.update { it.copy(isDepartmentSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun updateDepartment(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre del departamento no puede estar vacío") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isDepartmentSubmitting = true, errorMessage = null) }
            val res = apiClient.updateDepartment(id, UpdateDepartmentRequest(trimmed))
            res.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        isDepartmentSubmitting = false,
                        successMessage = "Departamento '${updated.name}' actualizado"
                    )
                }
                loadDepartments()
                loadProducts()
            }.onFailure { err ->
                _uiState.update { it.copy(isDepartmentSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun deleteDepartment(id: String) {
        scope.launch {
            _uiState.update { it.copy(isDepartmentSubmitting = true, errorMessage = null) }
            val res = apiClient.deleteDepartment(id)
            res.onSuccess {
                _uiState.update {
                    val newSelectedDept = if (it.selectedDepartmentId == id) null else it.selectedDepartmentId
                    it.copy(
                        isDepartmentSubmitting = false,
                        selectedDepartmentId = newSelectedDept,
                        successMessage = "Departamento eliminado (los productos asociados pasaron a General)"
                    )
                }
                loadDepartments()
                loadProducts()
            }.onFailure { err ->
                _uiState.update { it.copy(isDepartmentSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    // ==========================================
    // IMPORTACIÓN / EXPORTACIÓN Y ETIQUETAS
    // ==========================================

    fun openImportExportDialog() {
        _uiState.update { it.copy(showImportExportDialog = true, importResult = null) }
        exportCatalogCsv()
    }

    fun closeImportExportDialog() {
        _uiState.update { it.copy(showImportExportDialog = false, importResult = null) }
    }

    fun openShelfLabelsDialog(product: ProductDto? = null) {
        val target = product ?: _uiState.value.selectedProduct ?: _uiState.value.products.firstOrNull()
        _uiState.update { it.copy(showShelfLabelsDialog = true, selectedProductForLabel = target) }
    }

    fun closeShelfLabelsDialog() {
        _uiState.update { it.copy(showShelfLabelsDialog = false, selectedProductForLabel = null) }
    }

    fun selectProductForLabel(product: ProductDto?) {
        _uiState.update { it.copy(selectedProductForLabel = product) }
    }

    fun exportCatalogCsv(onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val res = apiClient.exportCatalogCsv()
            res.onSuccess { csv ->
                _uiState.update { it.copy(isExporting = false, exportedCsvContent = csv) }
                onComplete?.invoke(csv)
            }.onFailure { err ->
                _uiState.update { it.copy(isExporting = false, errorMessage = "Error al exportar catálogo: ${err.message}") }
            }
        }
    }

    fun importCatalogCsv(csvContent: String) {
        val rows = CsvCatalogHelper.parseCsvToRows(csvContent)
        if (rows.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "El contenido CSV no contiene filas de productos válidas.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            val res = apiClient.importCatalog(CatalogImportRequest(rows))
            res.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = response,
                        successMessage = "Importación completada: ${response.createdCount} creados, ${response.updatedCount} actualizados, ${response.errorCount} errores."
                    )
                }
                loadDepartments()
                loadProducts()
            }.onFailure { err ->
                _uiState.update { it.copy(isImporting = false, errorMessage = "Error al importar catálogo: ${err.message}") }
            }
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }
}
