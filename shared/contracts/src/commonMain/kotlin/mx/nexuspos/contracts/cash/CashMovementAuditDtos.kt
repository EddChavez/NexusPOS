package mx.nexuspos.contracts.cash

import kotlinx.serialization.Serializable

@Serializable
data class CashMovementHistoryRequest(val pin: String, val before: CashMovementCursor? = null)

@Serializable
data class CashMovementCursor(val createdAt: Long, val id: String)

@Serializable
data class CashMovementHistoryPage(
    val isOpen: Boolean,
    val movements: List<AuditedCashMovement>,
    val next: CashMovementCursor? = null
)

@Serializable
data class AuditedCashMovement(
    val id: String,
    val sessionId: String,
    val type: String,
    val amountCents: Long,
    val concept: String,
    val cashierId: String,
    val cashierName: String,
    val createdAt: Long,
    val reversesId: String? = null,
    val reversedByMovementId: String? = null,
    val authorizedByUserId: String? = null,
    val authorizedByName: String? = null,
    val supplierPaymentId: String? = null
) {
    val isReversible: Boolean
        get() = supplierPaymentId == null && reversesId == null && reversedByMovementId == null && amountCents > 0 &&
            (type == "CASH_IN" || type == "CASH_OUT")
}

@Serializable
data class ReverseCashMovementRequest(val pin: String, val reason: String)
