package mx.nexuspos.contracts.sales

import kotlinx.serialization.Serializable

@Serializable
data class RefundLineItemRequest(
    val saleLineId: String,
    val quantityMillis: Long,
    val reenterStock: Boolean = true
)

@Serializable
data class CreateSaleRefundRequest(
    val cashSessionId: String,
    val cashierUserId: String,
    val reason: String, // DEFECTO, CADUCADO, CAMBIO, INCONFORMIDAD, OTRO
    val notes: String = "",
    val refundMethod: String = "CASH", // CASH, CREDIT
    val supervisorPin: String? = null,
    val lines: List<RefundLineItemRequest>
)

@Serializable
data class SaleRefundLineResponse(
    val id: String,
    val saleLineId: String,
    val productId: String?,
    val presentationId: String?,
    val description: String,
    val quantityMillis: Long,
    val unitPriceCents: Long,
    val taxRate: Double,
    val taxCents: Long,
    val totalCents: Long,
    val reenterStock: Boolean
)

@Serializable
data class SaleRefundResponse(
    val id: String,
    val folio: Long,
    val saleId: String,
    val saleFolio: Long,
    val cashSessionId: String,
    val cashierName: String,
    val authorizedByName: String? = null,
    val reason: String,
    val notes: String = "",
    val refundMethod: String,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val lines: List<SaleRefundLineResponse>,
    val createdAt: Long
)
