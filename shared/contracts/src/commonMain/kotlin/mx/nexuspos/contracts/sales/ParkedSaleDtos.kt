package mx.nexuspos.contracts.sales

import kotlinx.serialization.Serializable
import mx.nexuspos.contracts.catalog.PresentationPriceTier
import mx.nexuspos.contracts.catalog.KitComponent

@Serializable
data class ParkedSaleItemDto(
    val id: String,
    val productId: String? = null,
    val presentationId: String? = null,
    val barcode: String = "",
    val name: String,
    val unitCode: String = "PZA",
    val quantityMillis: Long,
    val unitPriceCents: Long,
    val taxRate: Double = 0.0,
    val totalCents: Long,
    val isWeighed: Boolean = false,
    val discountCents: Long = 0,
    val discountPercent: Double = 0.0,
    val fixedDiscountCents: Long? = null,
    val publicPriceCents: Long? = null,
    val priceTiers: List<PresentationPriceTier> = emptyList(),
    val kitComponents: List<KitComponent>? = null
)

@Serializable
data class ParkSaleRequest(
    val customLabel: String,
    val cashierUserId: String,
    val cashierName: String,
    val registerId: String = "caja-1",
    val customerId: String? = null,
    val customerName: String? = null,
    val items: List<ParkedSaleItemDto>,
    val totalCents: Long,
    val note: String = ""
)

@Serializable
data class ParkedSaleResponse(
    val id: String,
    val customLabel: String,
    val cashierUserId: String,
    val cashierName: String,
    val registerId: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val totalCents: Long,
    val itemsCount: Int,
    val items: List<ParkedSaleItemDto>,
    val parkedAt: Long,
    val status: String, // PARKED, RETRIEVED, DISCARDED
    val retrievedAt: Long? = null,
    val retrievedByUserId: String? = null,
    val note: String = ""
)

@Serializable
data class RetrieveParkedSaleRequest(
    val retrievedByUserId: String,
    val registerId: String = "caja-1"
)
