package mx.nexuspos.contracts.inventory

import kotlinx.serialization.Serializable

@Serializable
enum class ReorderUrgency {
    CRITICAL_OUT_OF_STOCK, // Existencia <= 0
    LOW_STOCK               // 0 < Existencia <= Mínimo
}

@Serializable
data class ReorderSuggestionItemDto(
    val productId: String,
    val sku: String,
    val barcode: String,
    val productName: String,
    val departmentId: String? = null,
    val departmentName: String? = null,
    val baseUnit: String = "PZA",
    val currentStockMillis: Long,
    val minStockMillis: Long,
    val maxStockMillis: Long,
    val suggestedOrderMillis: Long,
    val costCents: Long,
    val estimatedInvestmentCents: Long,
    val urgency: ReorderUrgency
)

@Serializable
data class ReorderSuggestionsReportDto(
    val generatedAt: Long,
    val totalItemsCount: Int,
    val criticalOutOfStockCount: Int,
    val totalEstimatedInvestmentCents: Long,
    val items: List<ReorderSuggestionItemDto>
)
