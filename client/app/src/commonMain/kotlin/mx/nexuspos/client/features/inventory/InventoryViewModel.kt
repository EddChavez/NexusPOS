package mx.nexuspos.client.features.inventory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.LotWriteOffRequest
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest

class InventoryViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val productRequests = MutableStateFlow(0L)
    private val movementRequests = MutableStateFlow(0L)
    private val reorderRequests = MutableStateFlow(0L)

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadSummary()
        loadDepartments()
        loadProducts()
        loadMovements()
        loadReorderSuggestions()
        loadLotsReport()
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
        val snapshot = _uiState.value
        if (query != snapshot.searchQuery || departmentId != snapshot.selectedDepartmentId) return
        val request = productRequests.updateAndGet { it + 1L }
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
            val result = apiClient.searchProducts(query = query, departmentId = departmentId)
            ensureActive()
            _uiState.update { state ->
                if (productRequests.value != request || state.searchQuery != query || state.selectedDepartmentId != departmentId) {
                    return@update state
                }
                result.fold(
                    onSuccess = { products ->
                        val selection = state.selectedProduct
                        state.copy(
                            products = products,
                            selectedProduct = if (selection == null) products.firstOrNull()
                                else products.firstOrNull { it.id == selection.id } ?: selection,
                            isLoading = false
                        )
                    },
                    onFailure = { state.copy(isLoading = false, errorMessage = it.message ?: "No se pudo cargar el inventario.") }
                )
            }
        }
    }

    fun loadMovements(productId: String? = _uiState.value.movementProduct?.id, type: String? = null) {
        val request = movementRequests.updateAndGet { it + 1L }
        _uiState.update { it.copy(isMovementsLoading = true) }
        scope.launch {
            val result = apiClient.getStockMovements(productId = productId, type = type, limit = 150)
            ensureActive()
            _uiState.update { state ->
                if (movementRequests.value != request) state
                else result.fold(
                    onSuccess = { state.copy(movements = it, isMovementsLoading = false) },
                    onFailure = { state.copy(isMovementsLoading = false, errorMessage = it.message ?: "No se pudo consultar el kárdex.") }
                )
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
        when (tab) {
            InventoryTab.KARDEX -> loadMovements()
            InventoryTab.REORDER_SUGGESTIONS -> loadReorderSuggestions()
            InventoryTab.LOTS_AND_EXPIRATIONS -> loadLotsReport()
            InventoryTab.STOCK_TABLE -> {}
        }
    }

    fun loadReorderSuggestions(
        departmentId: String? = _uiState.value.reorderDepartmentId,
        onlyOutOfStock: Boolean = _uiState.value.reorderOnlyOutOfStock
    ) {
        val request = reorderRequests.updateAndGet { it + 1L }
        _uiState.update { it.copy(isReorderLoading = true) }
        scope.launch {
            val result = apiClient.getReorderSuggestions(departmentId, onlyOutOfStock)
            ensureActive()
            _uiState.update { state ->
                if (reorderRequests.value != request || state.reorderDepartmentId != departmentId ||
                    state.reorderOnlyOutOfStock != onlyOutOfStock
                ) state else result.fold(
                    onSuccess = { state.copy(reorderReport = it, isReorderLoading = false) },
                    onFailure = { state.copy(isReorderLoading = false, errorMessage = it.message ?: "No se pudo calcular el pedido.") }
                )
            }
        }
    }

    fun onReorderDepartmentSelected(departmentId: String?) {
        _uiState.update { it.copy(reorderDepartmentId = departmentId) }
        loadReorderSuggestions(departmentId = departmentId)
    }

    fun onToggleReorderOnlyOutOfStock(onlyOutOfStock: Boolean) {
        _uiState.update { it.copy(reorderOnlyOutOfStock = onlyOutOfStock) }
        loadReorderSuggestions(onlyOutOfStock = onlyOutOfStock)
    }

    fun updateSuggestedQuantity(productId: String, quantityMillis: Long) {
        _uiState.update { current ->
            val updatedMap = current.editedOrderQuantities.toMutableMap()
            if (quantityMillis <= 0L) {
                updatedMap.remove(productId)
            } else {
                updatedMap[productId] = quantityMillis
            }
            current.copy(editedOrderQuantities = updatedMap)
        }
    }

    fun resetEditedQuantities() {
        _uiState.update { it.copy(editedOrderQuantities = emptyMap()) }
    }

    fun selectProduct(product: ProductDto?) {
        _uiState.update { it.copy(selectedProduct = product) }
        if (_uiState.value.activeTab == InventoryTab.KARDEX) selectMovementProduct(product)
    }

    fun selectMovementProduct(product: ProductDto?) {
        _uiState.update { it.copy(movementProduct = product, movements = emptyList()) }
        loadMovements(product?.id)
    }

    fun openProductKardex(product: ProductDto) {
        _uiState.update { it.copy(activeTab = InventoryTab.KARDEX, movementProduct = product, movements = emptyList()) }
        loadMovements(product.id)
    }

    fun openMovementDialog(product: ProductDto) {
        if (!product.isActive || !product.usesInventory || product.kitComponents.isNotEmpty()) return
        _uiState.update { state ->
            if (state.isSubmitting || state.showMovementDialog || state.showCreateLotDialog || state.lotForWriteOff != null) state
            else state.copy(showMovementDialog = true, productForMovement = product, errorMessage = null)
        }
    }

    fun closeMovementDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showMovementDialog = false, productForMovement = null) }
    }

    private fun beginSubmission(): Boolean = !_uiState.getAndUpdate {
        if (it.isSubmitting) it else it.copy(isSubmitting = true, errorMessage = null)
    }.isSubmitting

    fun submitStockMovement(
        productId: String,
        type: String, // ENTRY, LOSS, ADJUSTMENT
        quantityMillis: Long,
        reason: String,
        reference: String,
        userId: String = "usr-cajero",
        expectedStockMillis: Long? = null,
        onSaved: () -> Unit = {}
    ) {
        if (!beginSubmission()) return
        scope.launch {
            val req = RegisterStockMovementRequest(
                productId = productId,
                type = type,
                quantityMillis = quantityMillis,
                reason = reason,
                reference = reference,
                userId = userId,
                expectedStockMillis = expectedStockMillis
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
                onSaved()
                loadSummary()
                loadProducts()
                loadMovements()
            }.onFailure { err ->
                val latest = if (expectedStockMillis != null) apiClient.getProduct(productId).getOrNull() else null
                _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        productForMovement = if (latest != null && state.productForMovement?.id == productId) latest
                            else state.productForMovement,
                        errorMessage = err.message ?: "No se pudo registrar el movimiento."
                    )
                }
            }
        }
    }

    fun loadLotsReport() {
        scope.launch {
            _uiState.update { it.copy(isLotsLoading = true, errorMessage = null) }
            val res = apiClient.getExpirationsReport()
            res.onSuccess { report ->
                _uiState.update { it.copy(lotsReport = report, isLotsLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLotsLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun onLotStatusFilterSelected(status: LotExpirationStatus?) {
        _uiState.update { it.copy(selectedLotStatusFilter = status) }
    }

    fun openCreateLotDialog() {
        _uiState.update {
            if (it.isSubmitting || it.showMovementDialog || it.lotForWriteOff != null) it
            else it.copy(showCreateLotDialog = true, errorMessage = null)
        }
    }

    fun closeCreateLotDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showCreateLotDialog = false) }
    }

    fun openLotWriteOffDialog(lot: ProductLotDto) {
        _uiState.update {
            if (it.isSubmitting || it.showMovementDialog || it.showCreateLotDialog) it
            else it.copy(lotForWriteOff = lot, errorMessage = null)
        }
    }

    fun closeLotWriteOffDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(lotForWriteOff = null) }
    }

    suspend fun searchLotProducts(query: String): Result<List<ProductDto>> =
        apiClient.searchProducts(query = query).map { it.filter { product ->
            product.isActive && product.kitComponents.isEmpty()
        } }

    fun createLot(
        productId: String,
        lotNumber: String,
        expirationDate: String,
        quantityMillis: Long,
        costCents: Long,
        addToGeneralStock: Boolean,
        notes: String,
        userId: String = "usr-admin",
        onSaved: () -> Unit = {}
    ) {
        if (!beginSubmission()) return
        scope.launch {
            val req = CreateProductLotRequest(
                productId = productId,
                lotNumber = lotNumber,
                expirationDate = expirationDate,
                quantityMillis = quantityMillis,
                costCents = costCents,
                addToGeneralStock = addToGeneralStock,
                notes = notes,
                userId = userId
            )
            val res = apiClient.createProductLot(req)
            res.onSuccess { createdLot ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        showCreateLotDialog = false,
                        successMessage = "Lote '${createdLot.lotNumber}' registrado con éxito para '${createdLot.productName}'"
                    )
                }
                onSaved()
                loadLotsReport()
                loadSummary()
                loadProducts()
                loadMovements()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun writeOffLot(
        lotId: String,
        quantityMillis: Long?,
        reason: String,
        userId: String = "usr-admin",
        onSaved: () -> Unit = {}
    ) {
        if (!beginSubmission()) return
        scope.launch {
            val req = LotWriteOffRequest(
                quantityMillis = quantityMillis,
                reason = reason,
                userId = userId
            )
            val res = apiClient.writeOffProductLot(lotId, req)
            res.onSuccess { updatedLot ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        lotForWriteOff = null,
                        successMessage = "Baja de merma procesada para lote '${updatedLot.lotNumber}' (${updatedLot.productName})"
                    )
                }
                onSaved()
                loadLotsReport()
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
