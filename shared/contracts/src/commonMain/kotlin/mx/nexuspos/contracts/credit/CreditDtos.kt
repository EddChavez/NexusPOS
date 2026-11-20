package mx.nexuspos.contracts.credit

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String,
    val folio: String = "",
    val name: String,
    val phone: String = "",
    val email: String = "",
    val rfc: String = "",
    val address: String = "",
    val notes: String = "",
    val creditEnabled: Boolean = false,
    val hasCreditLimit: Boolean = true,
    val creditLimitCents: Long = 0L,
    val currentBalanceCents: Long = 0L,
    val availableCreditCents: Long = 0L,
    val loyaltyBalanceCents: Long = 0L,
    val totalLoyaltyEarnedCents: Long = 0L,
    val totalLoyaltyRedeemedCents: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class CreateCustomerRequest(
    val name: String,
    val phone: String = "",
    val email: String = "",
    val rfc: String = "",
    val address: String = "",
    val notes: String = "",
    val creditEnabled: Boolean = false,
    val hasCreditLimit: Boolean = true,
    val creditLimitCents: Long = 0L
)

@Serializable
data class UpdateCustomerRequest(
    val name: String,
    val phone: String = "",
    val email: String = "",
    val rfc: String = "",
    val address: String = "",
    val notes: String = "",
    val creditEnabled: Boolean = false,
    val hasCreditLimit: Boolean = true,
    val creditLimitCents: Long = 0L,
    val isActive: Boolean = true
)

@Serializable
data class CreditMovementDto(
    val id: String,
    val customerId: String,
    val type: String, // CHARGE, PAYMENT, REVERSAL, ADJUSTMENT
    val amountCents: Long,
    val balanceAfterCents: Long,
    val saleId: String? = null,
    val cashSessionId: String? = null,
    val receiptFolio: String? = null,
    val description: String,
    val userId: String = "",
    val userName: String = "",
    val createdAt: Long
)

@Serializable
data class RegisterCreditPaymentRequest(
    val customerId: String,
    val cashSessionId: String,
    val cashierUserId: String,
    val amountCents: Long,
    val paymentMethod: String = "CASH", // CASH, TRANSFER, CARD
    val notes: String = "",
    val receiptFolio: String? = null
)

@Serializable
data class CustomerStatementDto(
    val customer: CustomerDto,
    val movements: List<CreditMovementDto>,
    val totalChargesCents: Long = 0L,
    val totalPaymentsCents: Long = 0L
)
