package mx.nexuspos.contracts.cash

import kotlinx.serialization.Serializable
import mx.nexuspos.contracts.sales.SaleResponse

@Serializable
data class ClosedShiftSalesRequest(
    val pin: String,
    val folio: Long? = null,
    val beforeFolio: Long? = null
)

@Serializable
data class ClosedShiftSaleRow(
    val id: String,
    val folio: Long,
    val cashierName: String,
    val status: String,
    val totalCents: Long,
    val createdAt: Long
)

@Serializable
data class ClosedShiftSalesPage(
    val items: List<ClosedShiftSaleRow>,
    val nextBeforeFolio: Long? = null
)

@Serializable
data class HistoricalSaleCancellation(val reason: String, val cancelledAt: Long)

@Serializable
data class ClosedShiftSaleDetail(
    val sale: SaleResponse,
    val cancellation: HistoricalSaleCancellation? = null
)
