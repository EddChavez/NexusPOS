package mx.nexuspos.contracts.catalog

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: String,
    val name: String
)

@Serializable
data class CreateDepartmentRequest(
    val name: String
)

@Serializable
data class UpdateDepartmentRequest(
    val name: String
)

@Serializable
data class ProductPresentationDto(
    val id: String,
    val productId: String,
    val barcode: String,
    val name: String,
    val factorMillis: Long, // 1000 = 1 base unit, 24000 = caja de 24
    val priceCents: Long,
    val priceTiers: List<PresentationPriceTier> = emptyList(),
    val listPrices: Map<Int, Long> = emptyMap(),
    val promotion: PresentationPromotion? = null
) {
    fun priceFor(quantityMillis: Long): Long = resolvePresentationPrice(priceCents, priceTiers, quantityMillis)
}

@Serializable
data class PresentationPriceTier(val minimumQuantityMillis: Long, val unitPriceCents: Long)

@Serializable
data class UpdatePresentationPricesRequest(val tiers: List<PresentationPriceTier>)

fun resolvePresentationPrice(
    publicPriceCents: Long,
    tiers: List<PresentationPriceTier>,
    quantityMillis: Long
): Long = tiers.asSequence()
    .filter { it.minimumQuantityMillis <= quantityMillis }
    .maxByOrNull { it.minimumQuantityMillis }
    ?.unitPriceCents?.coerceAtMost(publicPriceCents) ?: publicPriceCents

@Serializable
data class KitComponent(val productId: String, val quantityMillis: Long)

@Serializable
data class UpdateProductKitRequest(val components: List<KitComponent>)

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
    val isPerishable: Boolean = false,
    val shelfLifeAlertDays: Int = 7,
    val isActive: Boolean = true,
    val presentations: List<ProductPresentationDto> = emptyList(),
    val updatedAt: Long = 0L,
    val kitComponents: List<KitComponent> = emptyList()
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
    val isPerishable: Boolean = false,
    val shelfLifeAlertDays: Int = 7,
    val presentations: List<CreatePresentationRequest> = emptyList()
)

@Serializable
data class CreatePresentationRequest(
    val id: String? = null,
    val barcode: String,
    val name: String,
    val factorMillis: Long = 1000L,
    val priceCents: Long
)

@Serializable
data class UpdateProductRequest(
    val sku: String,
    val name: String,
    val departmentId: String? = null,
    val baseUnit: String = "PZA",
    val costCents: Long = 0L,
    val taxRate: Double = 0.0,
    val usesInventory: Boolean = true,
    val minStockMillis: Long = 0L,
    val maxStockMillis: Long = 0L,
    val isPerishable: Boolean = false,
    val shelfLifeAlertDays: Int = 7,
    val isActive: Boolean = true,
    val presentations: List<CreatePresentationRequest> = emptyList()
)

@Serializable
data class CatalogPriceList(val level: Int, val name: String, val enabled: Boolean)

@Serializable
data class UpdateCatalogPriceList(val name: String, val enabled: Boolean)

@Serializable
data class UpdatePresentationListPrice(val priceCents: Long?)
