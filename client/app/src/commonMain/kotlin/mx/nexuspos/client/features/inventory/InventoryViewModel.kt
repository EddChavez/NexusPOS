package mx.nexuspos.client.features.inventory

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
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest

class InventoryViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadSummary()
        loadDepartments()
        loadProducts()
        loadMovements()
    }

    fun loadSummary() {
        scope.launch {
            val res = apiClient.getInventorySummary()
            res.onSuccess { sum ->
                _uiState.update { it.copy(summary = sum) }
            }
        }
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
                includeInactive = false
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

    fun loadMovements(productId: String? = null, type: String? = null) {
        scope.launch {
            _uiState.update { it.copy(isMovementsLoading = true) }
            val res = apiClient.getStockMovements(productId = productId, type = type, limit = 150)
            res.onSuccess { list ->
                _uiState.update { it.copy(movements = list, isMovementsLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isMovementsLoading = false, errorMessage = err.message) }
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

    fun onStockFilterSelected(filter: StockFilter) {
        _uiState.update { it.copy(stockFilter = filter) }
    }

    fun onTabSelected(tab: InventoryTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == InventoryTab.KARDEX) {
            loadMovements(_uiState.value.selectedProduct?.id)
        }
    }

    fun selectProduct(product: ProductDto?) {
        _uiState.update { it.copy(selectedProduct = product) }
        if (_uiState.value.activeTab == InventoryTab.KARDEX && product != null) {
            loadMovements(product.id)
        }
    }

    fun openMovementDialog(product: ProductDto) {
        _uiState.update { it.copy(showMovementDialog = true, productForMovement = product, errorMessage = null) }
    }

    fun closeMovementDialog() {
        _uiState.update { it.copy(showMovementDialog = false, productForMovement = null) }
    }

    fun submitStockMovement(
        productId: String,
        type: String, // ENTRY, LOSS, ADJUSTMENT
        quantityMillis: Long,
        reason: String,
        reference: String,
        userId: String = "usr-cajero"
    ) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val req = RegisterStockMovementRequest(
                productId = productId,
                type = type,
                quantityMillis = quantityMillis,
                reason = reason,
                reference = reference,
                userId = userId
            )
            val res = apiClient.registerStockMovement(req)
            res.onSuccess { mov ->
                _uiState.update {
                    val typeLabel = when (mov.type) {
                        "ENTRY" -> "Entrada registrada"
                        "LOSS" -> "Merma registrada"
                        else -> "Ajuste de stock registrado"
                    }
                    it.copy(
                        isSubmitting = false,
                        showMovementDialog = false,
                        productForMovement = null,
                        successMessage = "$typeLabel con éxito para '${mov.productName}'"
                    )
                }
                loadSummary()
                loadProducts()
                loadMovements()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
