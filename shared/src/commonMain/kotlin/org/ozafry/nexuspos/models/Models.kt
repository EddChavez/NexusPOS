package org.ozafry.nexuspos.models

import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val colorHex: String? = null,
    val icon: String? = null
)

@Serializable
data class ProductDto(
    val id: String,
    val sku: String,
    val barcode: String? = null,
    val name: String,
    val categoryId: String? = null,
    val price: Double,
    val costPrice: Double = 0.0,
    val taxRate: Double = 0.16,
    val stockQuantity: Double = 0.0,
    val isActive: Boolean = true,
    val updatedAt: Long
)

@Serializable
data class CashShiftDto(
    val id: String,
    val registerId: String,
    val cashierName: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val initialCash: Double,
    val cashSales: Double = 0.0,
    val cardSales: Double = 0.0,
    val transferSales: Double = 0.0,
    val cashIn: Double = 0.0,
    val cashOut: Double = 0.0,
    val expectedCash: Double = 0.0,
    val actualCash: Double? = null,
    val notes: String? = null,
    val status: String = "OPEN"
)

@Serializable
data class SaleItemDto(
    val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val subtotal: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val discountAmount: Double = 0.0,
    val total: Double
)

@Serializable
data class SaleDto(
    val id: String,
    val shiftId: String,
    val registerId: String,
    val cashierName: String,
    val customerName: String? = null,
    val subtotal: Double,
    val taxTotal: Double,
    val discountTotal: Double = 0.0,
    val total: Double,
    val paymentMethod: String,
    val amountPaid: Double,
    val changeGiven: Double = 0.0,
    val status: String = "COMPLETED",
    val createdAt: Long,
    val items: List<SaleItemDto> = emptyList()
)

@Serializable
data class SyncEventDto(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val action: String,
    val payloadJson: String,
    val createdAt: Long
)

@Serializable
data class SyncPushRequest(
    val registerId: String,
    val events: List<SyncEventDto>
)

@Serializable
data class SyncPushResponse(
    val acceptedEventIds: List<String>,
    val rejectedEventIds: Map<String, String> = emptyMap(),
    val serverTimestamp: Long
)

@Serializable
data class SyncPullResponse(
    val products: List<ProductDto>,
    val categories: List<CategoryDto>,
    val serverTimestamp: Long
)

@Serializable
data class TelemetryEvent(
    val type: String,
    val registerId: String,
    val cashierName: String,
    val message: String,
    val totalAmount: Double? = null,
    val timestamp: Long
)
