package mx.nexuspos.client.features.inventory

import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.StockMovementDto

enum class InventoryTab {
    STOCK_TABLE,
    KARDEX,
    REORDER_SUGGESTIONS
}

enum class StockFilter {
    ALL,
    LOW_STOCK,
    OUT_OF_STOCK
}

data class InventoryUiState(
    val summary: InventorySummaryDto? = null,
    val products: List<ProductDto> = emptyList(),
    val movements: List<StockMovementDto> = emptyList(),
    val departments: List<DepartmentDto> = emptyList(),
    val selectedProduct: ProductDto? = null,
    val selectedDepartmentId: String? = null,
    val searchQuery: String = "",
    val stockFilter: StockFilter = StockFilter.ALL,
    val activeTab: InventoryTab = InventoryTab.STOCK_TABLE,
    val showMovementDialog: Boolean = false,
    val productForMovement: ProductDto? = null,
    val reorderReport: ReorderSuggestionsReportDto? = null,
    val editedOrderQuantities: Map<String, Long> = emptyMap(),
    val isReorderLoading: Boolean = false,
    val reorderDepartmentId: String? = null,
    val reorderOnlyOutOfStock: Boolean = false,
    val isLoading: Boolean = false,
    val isMovementsLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
