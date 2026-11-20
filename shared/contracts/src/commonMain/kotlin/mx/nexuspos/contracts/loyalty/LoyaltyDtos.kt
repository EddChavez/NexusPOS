package mx.nexuspos.contracts.loyalty

import kotlinx.serialization.Serializable

@Serializable
enum class LoyaltyMovementType {
    EARN,            // Acumulación por compra con dinero real
    REDEEM,          // Pago de venta usando monedero
    ADJUSTMENT,      // Ajuste manual de supervisor
    REFUND_REVERSAL, // Reversión de cashback por devolución de venta
    REFUND_CREDIT    // Reembolso abonado a monedero por devolución
}

@Serializable
data class LoyaltyMovementDto(
    val id: String,
    val customerId: String,
    val saleId: String? = null,
    val type: String, // EARN, REDEEM, ADJUSTMENT, REFUND_REVERSAL, REFUND_CREDIT
    val amountCents: Long,
    val balanceAfterCents: Long,
    val description: String = "",
    val userId: String = "usr-cajero",
    val createdAt: Long = 0L
)

@Serializable
data class SaleLoyaltyRewardDto(
    val earnedCents: Long = 0L,
    val redeemedCents: Long = 0L,
    val previousBalanceCents: Long = 0L,
    val finalBalanceCents: Long = 0L,
    val cashbackPercentApplied: Double = 1.0
)

@Serializable
data class AdjustLoyaltyBalanceRequest(
    val customerId: String,
    val amountCents: Long, // Puede ser positivo (abono) o negativo (cargo)
    val reason: String = "",
    val userId: String = "usr-supervisor"
)

@Serializable
data class LoyaltySummaryDto(
    val customerId: String,
    val customerFolio: String = "",
    val customerName: String,
    val currentBalanceCents: Long = 0L,
    val totalEarnedCents: Long = 0L,
    val totalRedeemedCents: Long = 0L,
    val movements: List<LoyaltyMovementDto> = emptyList()
)
