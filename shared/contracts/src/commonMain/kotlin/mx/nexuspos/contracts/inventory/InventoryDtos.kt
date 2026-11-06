package mx.nexuspos.contracts.inventory

import kotlinx.serialization.Serializable

@Serializable
data class StockMovementDto(
    val id: String,
    val productId: String,
    val productName: String,
    val productSku: String,
    val type: String, // ENTRY, ADJUSTMENT, LOSS, SALE, REFUND
    val quantityMillis: Long,
    val balanceAfterMillis: Long? = null,
    val reason: String,
    val reference: String = "",
    val userId: String,
    val userName: String? = null,
    val createdAt: Long
)

@Serializable
data class RegisterStockMovementRequest(
    val productId: String,
    val type: String, // ENTRY, ADJUSTMENT, LOSS
    val quantityMillis: Long, // positive for entry/increase, negative for loss/decrease
    val reason: String,
    val reference: String = "",
    val userId: String = "usr-cajero"
)

@Serializable
data class InventorySummaryDto(
    val totalProductsCount: Int,
    val trackedProductsCount: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val totalValuationCents: Long
)
