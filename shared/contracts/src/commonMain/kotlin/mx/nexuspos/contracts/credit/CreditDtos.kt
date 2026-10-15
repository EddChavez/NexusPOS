package mx.nexuspos.contracts.credit

import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String = "",
    val creditEnabled: Boolean = false,
    val creditLimitCents: Long = 0L,
    val currentBalanceCents: Long = 0L,
    val availableCreditCents: Long = 0L,
    val isActive: Boolean = true
)

@Serializable
data class CreateCustomerRequest(
    val name: String,
    val phone: String = "",
    val creditEnabled: Boolean = false,
    val creditLimitCents: Long = 0L
)

@Serializable
data class CreditMovementDto(
    val id: String,
    val customerId: String,
    val type: String, // CHARGE, PAYMENT, REFUND, ADJUSTMENT
    val amountCents: Long,
    val balanceAfterCents: Long,
    val saleId: String? = null,
    val cashSessionId: String? = null,
    val description: String,
    val createdAt: Long
)

@Serializable
data class RegisterCreditPaymentRequest(
    val customerId: String,
    val cashSessionId: String,
    val cashierUserId: String,
    val amountCents: Long,
    val paymentMethod: String = "CASH", // CASH, TRANSFER, CARD
    val notes: String = ""
)
