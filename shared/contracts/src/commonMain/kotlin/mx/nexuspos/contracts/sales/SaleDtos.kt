package mx.nexuspos.contracts.sales

import kotlinx.serialization.Serializable

@Serializable
data class SaleLineItemRequest(
    val presentationId: String? = null,
    val productId: String? = null,
    val description: String,
    val quantityMillis: Long, // 1000 = 1 pieza, 250 = 0.250 kg
    val expectedUnitPriceCents: Long,
    val expectedTotalCents: Long,
    val taxRate: Double = 0.0
)

@Serializable
data class SalePaymentRequest(
    val method: String, // CASH, CARD, TRANSFER, CREDIT
    val amountCents: Long,
    val receivedCents: Long = 0L,
    val reference: String = ""
)

@Serializable
data class CreateSaleRequest(
    val idempotencyKey: String,
    val cashSessionId: String,
    val cashierUserId: String,
    val customerId: String? = null,
    val supervisorAuthPin: String? = null,
    val supervisorAuthReason: String? = null,
    val lines: List<SaleLineItemRequest>,
    val payments: List<SalePaymentRequest>,
    val expectedTotalCents: Long
)

@Serializable
data class SaleLineItemResponse(
    val id: String,
    val productId: String?,
    val presentationId: String?,
    val description: String,
    val quantityMillis: Long,
    val unitPriceCents: Long,
    val taxRate: Double,
    val taxCents: Long,
    val totalCents: Long
)

@Serializable
data class SalePaymentResponse(
    val id: String,
    val method: String,
    val amountCents: Long,
    val receivedCents: Long,
    val changeCents: Long,
    val reference: String = ""
)

@Serializable
data class SaleResponse(
    val id: String,
    val folio: Long,
    val cashSessionId: String,
    val cashierName: String,
    val customerName: String? = null,
    val customerRemainingCreditCents: Long? = null,
    val status: String, // COMPLETED, CANCELLED, REFUNDED
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val lines: List<SaleLineItemResponse>,
    val payments: List<SalePaymentResponse>,
    val createdAt: Long
)

@Serializable
data class CancelSaleRequest(
    val reason: String,
    val authorizedByUserId: String? = null
)
