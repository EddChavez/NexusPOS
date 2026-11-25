package mx.nexuspos.contracts.inventory

import kotlinx.serialization.Serializable

@Serializable
enum class LotExpirationStatus {
    EXPIRED,          // Caducado (días <= 0)
    NEAR_EXPIRATION,  // Por vencer (0 < días <= días_alerta)
    OPTIMAL           // Vigente (días > días_alerta)
}

@Serializable
data class ProductLotDto(
    val id: String,
    val productId: String,
    val productName: String,
    val productSku: String,
    val barcode: String,
    val baseUnit: String = "PZA",
    val lotNumber: String = "S/L",
    val expirationDate: String, // YYYY-MM-DD
    val daysUntilExpiration: Int,
    val quantityMillis: Long,
    val initialQuantityMillis: Long,
    val costCents: Long = 0L,
    val valuationCents: Long = 0L,
    val status: LotExpirationStatus,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)

@Serializable
data class CreateProductLotRequest(
    val productId: String,
    val lotNumber: String = "S/L",
    val expirationDate: String, // YYYY-MM-DD
    val quantityMillis: Long,
    val costCents: Long = 0L,
    val notes: String = "",
    val addToGeneralStock: Boolean = true,
    val userId: String = "usr-admin"
)

@Serializable
data class LotWriteOffRequest(
    val quantityMillis: Long? = null, // null = todo el remanente del lote
    val reason: String = "Merma por Caducidad",
    val userId: String = "usr-admin"
)

@Serializable
data class ExpirationsReportDto(
    val generatedAt: Long,
    val totalLotsCount: Int,
    val expiredLotsCount: Int,
    val nearExpirationLotsCount: Int,
    val totalExpiredValuationCents: Long,
    val totalAtRiskValuationCents: Long,
    val lots: List<ProductLotDto>
)
