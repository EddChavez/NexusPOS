package mx.nexuspos.desktop.inventory

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.inventory.InventoryTab
import mx.nexuspos.client.features.inventory.InventoryViewModel
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.*
import kotlin.test.*

class InventoryWorkspaceTest {
    private val product = ProductDto("p", "SKU", "Café", baseUnit = "KG", currentStockMillis = 3250)

    @Test
    fun clearingTheLedgerFilterIgnoresTheLateProductResponseAndPreservesStockSelection() {
        val filtered = CompletableDeferred<Result<List<StockMovementDto>>>()
        val api = object : InventoryApi() {
            override suspend fun getStockMovements(productId: String?, type: String?, limit: Int) =
                if (productId == product.id) filtered.await() else Result.success(listOf(movement("general")))
        }
        val viewModel = InventoryViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        viewModel.selectProduct(product)
        viewModel.openProductKardex(product)
        assertTrue(viewModel.uiState.value.isMovementsLoading)

        viewModel.selectMovementProduct(null)
        filtered.complete(Result.success(listOf(movement("filtrado"))))

        val state = viewModel.uiState.value
        assertEquals(InventoryTab.KARDEX, state.activeTab)
        assertNull(state.movementProduct)
        assertEquals(product.id, state.selectedProduct?.id)
        assertFalse(state.isMovementsLoading)
        assertEquals(listOf("general"), state.movements.map { it.id })
    }

    @Test
    fun conflictingCountKeepsTheEditorOpenAndUsesTheRefreshedStock() {
        val response = CompletableDeferred<Result<StockMovementDto>>()
        val requests = mutableListOf<RegisterStockMovementRequest>()
        val api = object : InventoryApi() {
            override suspend fun registerStockMovement(req: RegisterStockMovementRequest): Result<StockMovementDto> {
                requests.add(req)
                return response.await()
            }

            override suspend fun getProduct(id: String) = Result.success(product.copy(currentStockMillis = 3750))
        }
        val viewModel = InventoryViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        viewModel.openMovementDialog(product)
        viewModel.submitStockMovement(product.id, "ADJUSTMENT", -250, "Conteo", "", "cashier", expectedStockMillis = 3250)
        viewModel.submitStockMovement(product.id, "ADJUSTMENT", -250, "Conteo", "", "cashier", expectedStockMillis = 3250)
        viewModel.closeMovementDialog()

        assertEquals(1, requests.size)
        assertEquals(3250L, requests.single().expectedStockMillis)
        assertEquals("cashier", requests.single().userId)
        assertTrue(viewModel.uiState.value.showMovementDialog)
        response.complete(Result.failure(IllegalStateException("El saldo cambió")))

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertTrue(state.showMovementDialog)
        assertEquals(3750L, state.productForMovement?.currentStockMillis)
        assertEquals("El saldo cambió", state.errorMessage)
    }

    private open inner class InventoryApi : ApiClient() {
        override suspend fun getDepartments() = Result.success(emptyList<DepartmentDto>())
        override suspend fun getInventorySummary() = Result.success(InventorySummaryDto(1, 1, 0, 0, 0))
        override suspend fun searchProducts(barcode: String?, query: String?, departmentId: String?, includeInactive: Boolean) =
            Result.success(listOf(product))
        override suspend fun getStockMovements(productId: String?, type: String?, limit: Int) =
            Result.success(emptyList<StockMovementDto>())
        override suspend fun getReorderSuggestions(departmentId: String?, onlyOutOfStock: Boolean) =
            Result.success(ReorderSuggestionsReportDto(0, 0, 0, 0, emptyList()))
        override suspend fun getExpirationsReport() =
            Result.success(ExpirationsReportDto(0, 0, 0, 0, 0, 0, emptyList()))
    }

    private fun movement(id: String) = StockMovementDto(
        id, product.id, product.name, product.sku, "ENTRY", 1000,
        reason = "Entrada", userId = "cashier", createdAt = 1
    )
}
