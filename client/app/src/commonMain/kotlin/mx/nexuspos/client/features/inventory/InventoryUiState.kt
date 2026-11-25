package mx.nexuspos.client.features.inventory

import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.StockMovementDto

enum class InventoryTab {
    STOCK_TABLE,
    KARDEX,
    REORDER_SUGGESTIONS,
    LOTS_AND_EXPIRATIONS
}

enum class StockFilter {
    ALL,
    LOW_STOCK,
    OUT_OF_STOCK
}

fun StockFilter.includes(product: ProductDto): Boolean = when (this) {
    StockFilter.ALL -> true
    StockFilter.LOW_STOCK -> product.usesInventory && product.currentStockMillis in 1L..product.minStockMillis
    StockFilter.OUT_OF_STOCK -> product.usesInventory && product.currentStockMillis <= 0L
}

data class InventoryUiState(
    val summary: InventorySummaryDto? = null,
    val products: List<ProductDto> = emptyList(),
    val movements: List<StockMovementDto> = emptyList(),
    val movementProduct: ProductDto? = null,
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
    val lotsReport: ExpirationsReportDto? = null,
    val isLotsLoading: Boolean = false,
    val selectedLotStatusFilter: LotExpirationStatus? = null,
    val showCreateLotDialog: Boolean = false,
    val lotForWriteOff: ProductLotDto? = null,
    val isLoading: Boolean = false,
    val isMovementsLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
