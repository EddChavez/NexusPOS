package mx.nexuspos.desktop.products

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.products.ProductsViewModel
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import kotlin.test.*

class ProductsWorkspaceTest {
    @Test
    fun previousCatalogResponseCannotReplaceTheCurrentDepartmentOrSelection() {
        val delayed = CompletableDeferred<Result<List<ProductDto>>>()
        val first = ProductDto("A", "SKU-A", "Aceite")
        val second = ProductDto("B", "SKU-B", "Té", departmentId = "drinks")
        val api = object : ApiClient() {
            override suspend fun getDepartments() = Result.success(listOf(DepartmentDto("drinks", "Bebidas")))
            override suspend fun searchProducts(
                barcode: String?,
                query: String?,
                departmentId: String?,
                includeInactive: Boolean
            ) = if (departmentId == null) delayed.await() else Result.success(listOf(second))
        }
        val viewModel = ProductsViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        viewModel.onDepartmentSelected("drinks")
        viewModel.selectProduct(second)
        delayed.complete(Result.success(listOf(first)))

        val state = viewModel.uiState.value
        assertEquals("drinks", state.selectedDepartmentId)
        assertEquals(listOf(second), state.products)
        assertEquals(second.id, state.selectedProduct?.id)
        assertFalse(state.isLoading)
    }
}
