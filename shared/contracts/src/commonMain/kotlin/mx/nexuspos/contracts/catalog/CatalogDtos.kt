package mx.nexuspos.contracts.catalog

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: String,
    val name: String
)

@Serializable
data class ProductPresentationDto(
    val id: String,
    val productId: String,
    val barcode: String,
    val name: String,
    val factorMillis: Long, // 1000 = 1 base unit, 24000 = caja de 24
    val priceCents: Long
)

@Serializable
data class ProductDto(
    val id: String,
    val sku: String,
    val name: String,
    val departmentId: String? = null,
    val departmentName: String? = null,
    val baseUnit: String = "PZA", // PZA, KG, LT
    val costCents: Long = 0L,
    val taxRate: Double = 0.0, // 0.0, 0.16
    val usesInventory: Boolean = true,
    val currentStockMillis: Long = 0L,
    val minStockMillis: Long = 0L,
    val maxStockMillis: Long = 0L,
    val isActive: Boolean = true,
    val presentations: List<ProductPresentationDto> = emptyList(),
    val updatedAt: Long = 0L
)

@Serializable
data class CreateProductRequest(
    val sku: String,
    val name: String,
    val departmentId: String? = null,
    val baseUnit: String = "PZA",
    val costCents: Long = 0L,
    val taxRate: Double = 0.0,
    val usesInventory: Boolean = true,
    val initialStockMillis: Long = 0L,
    val minStockMillis: Long = 0L,
    val maxStockMillis: Long = 0L,
    val presentations: List<CreatePresentationRequest> = emptyList()
)

@Serializable
data class CreatePresentationRequest(
    val barcode: String,
    val name: String,
    val factorMillis: Long,
    val priceCents: Long
)
